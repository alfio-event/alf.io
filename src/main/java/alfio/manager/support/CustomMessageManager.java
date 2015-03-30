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
package alfio.manager.support;

import alfio.manager.EventManager;
import alfio.manager.NotificationManager;
import alfio.manager.TicketReservationManager;
import alfio.model.Event;
import alfio.model.modification.MessageModification;
import alfio.model.user.Organization;
import alfio.repository.TicketRepository;
import alfio.util.TemplateManager;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class CustomMessageManager {

    private final TemplateManager templateManager;
    private final EventManager eventManager;
    private final TicketRepository ticketRepository;
    private final TicketReservationManager ticketReservationManager;
    private final NotificationManager notificationManager;

    @Autowired
    public CustomMessageManager(TemplateManager templateManager,
                                EventManager eventManager,
                                TicketRepository ticketRepository,
                                TicketReservationManager ticketReservationManager,
                                NotificationManager notificationManager) {
        this.templateManager = templateManager;
        this.eventManager = eventManager;
        this.ticketRepository = ticketRepository;
        this.ticketReservationManager = ticketReservationManager;
        this.notificationManager = notificationManager;
    }

    public Map<String, Object> generatePreview(String eventName, List<MessageModification> input, String username) {
        Map<String, Object> result = new HashMap<>();
        result.put("affectedUsers", ticketRepository.countAllConfirmed(eventManager.getSingleEvent(eventName, username).getId()));
        result.put("preview", preview(eventName, input));
        return result;
    }

    public int sendMessages(String eventName, List<MessageModification> input, String username) {
        preview(eventName, input);//dry run for checking the syntax
        Event event = eventManager.getSingleEvent(eventName, username);
        Organization organization = eventManager.loadOrganizer(event, username);
        AtomicInteger counter = new AtomicInteger();
        Map<String, List<MessageModification>> byLanguage = input.stream().collect(Collectors.groupingBy(m -> m.getLocale().getLanguage()));
        ticketRepository.findAllConfirmed(eventManager.getSingleEvent(eventName, username).getId()).stream()
                .filter(t -> isNotBlank(t.getFullName()) && isNotBlank(t.getEmail()))
                .parallel()
                .map(t -> {
                    Model model = new ExtendedModelMap();
                    model.addAttribute("eventName", eventName);
                    model.addAttribute("fullName", t.getFullName());
                    model.addAttribute("organizationName", organization.getName());
                    model.addAttribute("organizationEmail", organization.getEmail());
                    model.addAttribute("reservationURL", ticketReservationManager.reservationUrl(t.getTicketsReservationId(), event));
                    model.addAttribute("reservationID", ticketReservationManager.getShortReservationID(t.getTicketsReservationId()));
                    return Triple.of(t.getUserLanguage(), t.getEmail(), model);
                })
                .forEach(triple -> {
                    MessageModification m = Optional.ofNullable(byLanguage.get(triple.getLeft())).orElseGet(() -> byLanguage.get("en")).get(0);
                    Model model = triple.getRight();
                    String subject = renderResource(m.getSubject(), model, m.getLocale(), templateManager);
                    String text = renderResource(m.getText(), model, m.getLocale(), templateManager);
                    counter.incrementAndGet();
                    notificationManager.sendSimpleEmail(event, triple.getMiddle(), subject, text);
                });
        return counter.get();

    }

    private List<MessageModification> preview(String eventName, List<MessageModification> input) {
        Model model = new ExtendedModelMap();
        model.addAttribute("eventName", eventName);
        model.addAttribute("fullName", "First Last");
        model.addAttribute("organizationName", "My organization");
        model.addAttribute("organizationEmail", "test@test.tld");
        model.addAttribute("reservationURL", "https://my-instance/reservations/abcd");
        model.addAttribute("reservationID", "ABCD");
        return input.stream()
                .map(m -> MessageModification.preview(m, renderResource(m.getSubject(), model, m.getLocale(), templateManager), renderResource(m.getText(), model, m.getLocale(), templateManager)))
                .collect(Collectors.toList());
    }

    private static String renderResource(String template, Model model, Locale locale, TemplateManager templateManager) {
        return templateManager.renderString(template, model.asMap(), locale, TemplateManager.TemplateOutput.TEXT);
    }
}
