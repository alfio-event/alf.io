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
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.util.TemplateManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.ZonedDateTime;
import java.util.*;

@Log4j2
@Component
public class WaitingQueueSubscriptionProcessor {

    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final ConfigurationManager configurationManager;
    private final WaitingQueueManager waitingQueueManager;
    private final NotificationManager notificationManager;
    private final MessageSource messageSource;
    private final TemplateManager templateManager;

    @Autowired
    public WaitingQueueSubscriptionProcessor(EventManager eventManager,
                                             TicketReservationManager ticketReservationManager,
                                             ConfigurationManager configurationManager,
                                             WaitingQueueManager waitingQueueManager,
                                             NotificationManager notificationManager,
                                             MessageSource messageSource,
                                             TemplateManager templateManager) {
        this.eventManager = eventManager;
        this.ticketReservationManager = ticketReservationManager;
        this.configurationManager = configurationManager;
        this.waitingQueueManager = waitingQueueManager;
        this.notificationManager = notificationManager;
        this.messageSource = messageSource;
        this.templateManager = templateManager;
    }

    public void handleWaitingTickets() {
        eventManager.getActiveEvents().stream()
            .filter(event -> configurationManager.getBooleanConfigValue(Configuration.event(event), ConfigurationKeys.ENABLE_WAITING_QUEUE, false))
            .forEach(this::distributeAvailableSeats);
    }

    void distributeAvailableSeats(Event event) {
        waitingQueueManager.distributeSeats(event).forEach(triple -> {
            Locale locale = triple.getLeft().getLocale();
            ZonedDateTime expiration = triple.getRight();
            String reservationId = createReservation(event.getId(), triple.getMiddle(), expiration, locale);
            String eventShortName = event.getShortName();
            String subject = messageSource.getMessage("email-waiting-queue-acquired.subject", new Object[]{eventShortName}, locale);
            Map<String, Object> model = new HashMap<>();
            model.put("event", event);
            model.put("subscription", triple.getLeft());
            model.put("reservationUrl", ticketReservationManager.reservationUrl(reservationId, event));
            model.put("reservationTimeout", expiration);
            model.put("organization", eventManager.loadOrganizerUsingSystemPrincipal(event));
            notificationManager.sendSimpleEmail(event,
                    triple.getLeft().getEmailAddress(),
                    subject,
                    () -> templateManager.renderClassPathResource("/alfio/templates/waiting-queue-reservation-email-txt.ms", model, locale, TemplateManager.TemplateOutput.TEXT));
        });
    }

    private String createReservation(int eventId, TicketReservationWithOptionalCodeModification reservation, ZonedDateTime expiration, Locale locale) {
        return ticketReservationManager.createTicketReservation(eventId,
                Collections.singletonList(reservation), Date.from(expiration.toInstant()),
                Optional.<String>empty(),
                Optional.<String>empty(),
                locale, true);
    }

}
