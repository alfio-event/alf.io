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
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.Configuration;
import alfio.model.user.Organization;
import alfio.repository.WaitingQueueRepository;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.ENABLE_PRE_REGISTRATION;
import static alfio.model.system.ConfigurationKeys.ENABLE_WAITING_QUEUE;

@Component
@Transactional
@RequiredArgsConstructor
public class WaitingQueueSubscriptionProcessor {

    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final ConfigurationManager configurationManager;
    private final WaitingQueueManager waitingQueueManager;
    private final NotificationManager notificationManager;
    private final WaitingQueueRepository waitingQueueRepository;
    private final MessageSource messageSource;
    private final TemplateManager templateManager;

    void handleWaitingTickets() {
        Map<Boolean, List<Event>> activeEvents = eventManager.getActiveEvents().stream()
            .collect(Collectors.partitioningBy(this::isWaitingListFormEnabled));
        activeEvents.get(true).forEach(this::distributeAvailableSeats);
        activeEvents.get(false).forEach(eventManager::resetReleasedTickets);
    }

    private boolean isWaitingListFormEnabled(Event event) {
        return configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE), false)
                || configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION), false);
    }

    void distributeAvailableSeats(Event event) {
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
