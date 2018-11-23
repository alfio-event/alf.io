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

import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.TicketInfo;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.Configuration;
import alfio.model.user.Organization;
import alfio.repository.TicketRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
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
    private final MessageSource messageSource;
    private final TemplateManager templateManager;
    private final TicketRepository ticketRepository;
    private final PlatformTransactionManager transactionManager;

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
                log.error("cannot process waiting queue for event {}", event.getShortName(), ex);
            }
        });
        activeEvents.get(false).forEach(eventManager::resetReleasedTickets);
    }

    public void revertTicketToFreeIfCategoryIsExpired(Event event) {
        int eventId = event.getId();
        List<TicketInfo> releasedButExpired = ticketRepository.findReleasedBelongingToExpiredCategories(eventId, ZonedDateTime.now(event.getZoneId()));
        Map<TicketCategory, List<TicketInfo>> releasedByCategory = releasedButExpired.stream().collect(Collectors.groupingBy(TicketInfo::getTicketCategory));
        for (Map.Entry<TicketCategory, List<TicketInfo>> entry : releasedByCategory.entrySet()) {
            TicketCategory category = entry.getKey();
            List<Integer> ids = entry.getValue().stream().map(ft -> ft.getTicket().getId()).collect(Collectors.toList());
            if(category.isBounded()) {
                ticketRepository.revertToFree(eventId, category.getId(), ids);
            } else {
                ticketRepository.unbindTicketsFromCategory(eventId, category.getId(), ids);
            }
        }

    }

    private boolean isWaitingListFormEnabled(Event event) {
        return configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE), false)
                || configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION), false);
    }

    public void distributeAvailableSeats(Event event) {
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
                    subscription.getEmailAddress(),
                    subject,
                    () -> templateManager.renderTemplate(event, TemplateResource.WAITING_QUEUE_RESERVATION_EMAIL, model, locale));
            waitingQueueRepository.flagAsPending(reservationId, subscription.getId());
        });
    }

    private String createReservation(Event event, TicketReservationWithOptionalCodeModification reservation, ZonedDateTime expiration, Locale locale) {
        return ticketReservationManager.createTicketReservation(event,
                Collections.singletonList(reservation), Collections.emptyList(), Date.from(expiration.toInstant()),
                Optional.empty(),
                Optional.empty(),
                locale, true);
    }

}
