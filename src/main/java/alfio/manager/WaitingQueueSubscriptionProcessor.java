/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager;

import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.EventAndOrganizationId;
import alfio.model.TicketInfo;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.user.Organization;
import alfio.repository.TicketRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.util.ClockProvider;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.sql.Date;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.ENABLE_PRE_REGISTRATION;
import static alfio.model.system.ConfigurationKeys.ENABLE_WAITING_QUEUE;
import static java.util.stream.Collectors.toList;

@Component
@Transactional
@RequiredArgsConstructor
@Log4j2
public class WaitingQueueSubscriptionProcessor {

    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final ConfigurationManager configurationManager;
    private final WaitingQueueManager waitingQueueManager;
    private final NotificationManager notificationManager;
    private final WaitingQueueRepository waitingQueueRepository;
    private final MessageSourceManager messageSourceManager;
    private final TemplateManager templateManager;
    private final TicketRepository ticketRepository;
    private final PlatformTransactionManager transactionManager;
    private final ClockProvider clockProvider;

    public void handleWaitingTickets() {
        Map<Boolean, List<Event>> activeEvents = eventManager.getActiveEvents().stream()
            .collect(Collectors.partitioningBy(this::isWaitingListFormEnabled));
        activeEvents.get(true).forEach(event -> {
            TransactionStatus transaction = transactionManager.getTransaction(new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
            try {
                ticketReservationManager.revertTicketsToFreeIfAccessRestricted(event.getId());
                revertTicketToFreeIfCategoryIsExpired(event);
                distributeAvailableSeats(event);
                transactionManager.commit(transaction);
            } catch(Exception ex) {
                if(!(ex instanceof TransactionException)) {
                    transactionManager.rollback(transaction);
                }
                log.error("cannot process waiting list for event {}", event.getShortName(), ex);
            }
        });
        activeEvents.get(false).forEach(eventManager::resetReleasedTickets);
    }

    public void revertTicketToFreeIfCategoryIsExpired(Event event) {
        int eventId = event.getId();
        List<TicketInfo> releasedButExpired = ticketRepository.findReleasedBelongingToExpiredCategories(eventId, event.now(clockProvider));
        Map<Pair<Integer, Boolean>, List<Integer>> releasedByCategory = releasedButExpired.stream().collect(Collectors.groupingBy(
            t-> Pair.of(t.getTicketCategoryId(), t.isTicketCategoryBounded()),
            Collectors.mapping(TicketInfo::getTicketId, toList())
        ));
        releasedByCategory.forEach((ticketCategory, ticketIds) -> {
            int ticketCategoryId = ticketCategory.getKey();
            boolean isTicketCategoryBounded = ticketCategory.getRight();
            if(!ticketIds.isEmpty()) {
                if (isTicketCategoryBounded) {
                    ticketRepository.revertToFree(eventId, ticketCategoryId, ticketIds);
                } else {
                    ticketRepository.unbindTicketsFromCategory(eventId, ticketCategoryId, ticketIds);
                }
            }
        });
    }

    private boolean isWaitingListFormEnabled(EventAndOrganizationId event) {
        var res = configurationManager.getFor(Set.of(ENABLE_WAITING_QUEUE, ENABLE_PRE_REGISTRATION), event.getConfigurationLevel());
        return res.get(ENABLE_WAITING_QUEUE).getValueAsBooleanOrDefault() || res.get(ENABLE_PRE_REGISTRATION).getValueAsBooleanOrDefault();
    }

    public void distributeAvailableSeats(Event event) {
        var messageSource = messageSourceManager.getMessageSourceFor(event);
        waitingQueueManager.distributeSeats(event).forEach(triple -> {
            WaitingQueueSubscription subscription = triple.getLeft();
            Locale locale = subscription.getLocale();
            ZonedDateTime expiration = triple.getRight();
            Organization organization = eventManager.loadOrganizerUsingSystemPrincipal(event);
            String reservationId = createReservation(event, triple.getMiddle(), expiration, locale);
            String subject = messageSource.getMessage("email-waiting-queue-acquired.subject", new Object[]{event.getDisplayName()}, locale);
            String reservationUrl = ticketReservationManager.reservationUrl(reservationId, event);
            Map<String, Object> model = TemplateResource.buildModelForWaitingQueueReservationEmail(organization, event, subscription, reservationUrl, expiration);
            notificationManager.sendSimpleEmail(event,
                    reservationId,
                    subscription.getEmailAddress(),
                    subject,
                    () -> templateManager.renderTemplate(event, TemplateResource.WAITING_QUEUE_RESERVATION_EMAIL, model, locale));
            waitingQueueRepository.flagAsPending(reservationId, subscription.getId());
        });
    }

    private String createReservation(Event event, TicketReservationWithOptionalCodeModification reservation, ZonedDateTime expiration, Locale locale) {
        return ticketReservationManager.createTicketReservation(event,
            Collections.singletonList(reservation),
            Collections.emptyList(),
            Date.from(expiration.toInstant()),
            Optional.empty(),
            locale,
            true,
            null); // set principal to null because this happens in a job
    }

}
