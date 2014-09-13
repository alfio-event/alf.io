/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.manager;

import io.bagarino.manager.user.UserManager;
import io.bagarino.model.Event;
import io.bagarino.model.modification.EventModification;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.join.EventOrganizationRepository;
import io.bagarino.repository.join.EventTicketCategoryRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class EventManager {

    public static final BigDecimal HUNDRED = new BigDecimal("100.0");
    private final UserManager userManager;
    private final EventOrganizationRepository eventOrganizationRepository;
    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventTicketCategoryRepository eventTicketCategoryRepository;

    @Autowired
    public EventManager(UserManager userManager,
                        EventOrganizationRepository eventOrganizationRepository,
                        EventRepository eventRepository,
                        TicketCategoryRepository ticketCategoryRepository,
                        EventTicketCategoryRepository eventTicketCategoryRepository) {
        this.userManager = userManager;
        this.eventOrganizationRepository = eventOrganizationRepository;
        this.eventRepository = eventRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.eventTicketCategoryRepository = eventTicketCategoryRepository;
    }

    public List<Event> getAllEvents(String username) {
        return userManager.findUserOrganizations(username)
                    .parallelStream()
                    .flatMap(o -> eventOrganizationRepository.findByOrganizationId(o.getId()).stream())
                    .map(eo -> eventRepository.findById(eo.getEventId()))
                    .collect(Collectors.toList());
    }

    @Transactional
    public void createEvent(EventModification eventModification) {//to be improved. Should we create an object which would fit better?
        final Pair<Integer, Integer> event = eventRepository.insert(eventModification.getDescription(), eventModification.getOrganizationId(), eventModification.getLocation(),
                "", "", eventModification.getStart().toDate(), eventModification.getEnd().toDate(), eventModification.getPrice(), eventModification.getCurrency(),
                eventModification.getSeats(), eventModification.isVatIncluded(), eventModification.getVat());
        final BigDecimal seats = new BigDecimal(eventModification.getSeats());
        eventModification.getTicketCategories().stream().forEach(tc -> {
            int maxSeats = tc.getSeats().divide(HUNDRED).multiply(seats).intValue();
            final Pair<Integer, Integer> category = ticketCategoryRepository.insert(tc.getInception().toDate(),
                    tc.getExpiration().toDate(), maxSeats, tc.getDiscount());
            eventTicketCategoryRepository.insert(event.getValue(), category.getValue());
        });
        //insert tickets...
    }

}
