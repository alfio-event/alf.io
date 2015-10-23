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

    public Map<String, Object> generatePreview(String eventName, Optional<Integer> categoryId, List<MessageModification> input, String username) {
        Map<String, Object> result = new HashMap<>();
        Event event = eventManager.getSingleEvent(eventName, username);
        result.put("affectedUsers", categoryId.map(id -> ticketRepository.countAssignedTickets(event.getId(), id)).orElseGet(() -> ticketRepository.countAllAssigned(event.getId())));
        result.put("preview", preview(event, input, username));
        return result;
    }

    public int sendMessages(String eventName, Optional<Integer> categoryId, List<MessageModification> input, String username) {
        Event event = eventManager.getSingleEvent(eventName, username);
        preview(event, input, username);//dry run for checking the syntax
        Organization organization = eventManager.loadOrganizer(event, username);
        AtomicInteger counter = new AtomicInteger();
        Map<String, List<MessageModification>> byLanguage = input.stream().collect(Collectors.groupingBy(m -> m.getLocale().getLanguage()));
        categoryId.map(id -> ticketRepository.findConfirmedByCategoryId(event.getId(), id))
                .orElseGet(() -> ticketRepository.findAllConfirmed(event.getId()))
                .stream()
                .filter(t -> isNotBlank(t.getFullName()) && isNotBlank(t.getEmail()))
                .parallel()
                .map(t -> {
                    Model model = new ExtendedModelMap();
                    model.addAttribute("eventName", eventName);
                    model.addAttribute("fullName", t.getFullName());
                    model.addAttribute("organizationName", organization.getName());
                    model.addAttribute("organizationEmail", organization.getEmail());
                    model.addAttribute("reservationURL", ticketReservationManager.reservationUrl(t.getTicketsReservationId(), event));
                    model.addAttribute("reservationID", ticketReservationManager.getShortReservationID(event, t.getTicketsReservationId()));
                    model.addAttribute("ticketURL", ticketReservationManager.ticketUpdateUrl(t.getTicketsReservationId(), event, t.getUuid()));
                    return Triple.of(t.getUserLanguage(), t.getEmail(), model);
                })
                .forEach(triple -> {
                    MessageModification m = Optional.ofNullable(byLanguage.get(triple.getLeft())).orElseGet(() -> byLanguage.get("en")).get(0);
                    Model model = triple.getRight();
                    String subject = renderResource(m.getSubject(), model, m.getLocale(), templateManager);
                    String text = renderResource(m.getText(), model, m.getLocale(), templateManager);
                    counter.incrementAndGet();
                    notificationManager.sendSimpleEmail(event, triple.getMiddle(), subject, () -> text);
                });
        return counter.get();

    }

    private List<MessageModification> preview(Event event, List<MessageModification> input, String username) {
        Model model = new ExtendedModelMap();
        Organization organization = eventManager.loadOrganizer(event, username);
        model.addAttribute("eventName", event.getDisplayName());
        model.addAttribute("fullName", "John Doe");
        model.addAttribute("organizationName", organization.getName());
        model.addAttribute("organizationEmail", organization.getEmail());
        model.addAttribute("reservationURL", "https://this-is-the-reservation-url");
        model.addAttribute("ticketURL", "https://this-is-the-ticket-url");
        model.addAttribute("reservationID", "RESID");
        return input.stream()
                .map(m -> MessageModification.preview(m, renderResource(m.getSubject(), model, m.getLocale(), templateManager), renderResource(m.getText(), model, m.getLocale(), templateManager)))
                .collect(Collectors.toList());
    }

    private static String renderResource(String template, Model model, Locale locale, TemplateManager templateManager) {
        return templateManager.renderString(template, model.asMap(), locale, TemplateManager.TemplateOutput.TEXT);
    }
}
