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

import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.TicketCategoryDescription;
import alfio.model.modification.EventWithStatistics;
import alfio.model.modification.TicketCategoryWithStatistic;
import alfio.model.modification.TicketWithStatistic;
import alfio.repository.*;
import alfio.util.EventUtil;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static alfio.util.OptionalWrapper.optionally;
import static java.util.stream.Collectors.toList;

@Component
public class EventStatisticsManager {

    private final EventRepository eventRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final UserManager userManager;
    private final TransactionRepository transactionRepository;

    @Autowired
    public EventStatisticsManager(EventRepository eventRepository,
                                  EventDescriptionRepository eventDescriptionRepository,
                                  TicketRepository ticketRepository,
                                  TicketCategoryRepository ticketCategoryRepository,
                                  TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository,
                                  TicketReservationRepository ticketReservationRepository,
                                  SpecialPriceRepository specialPriceRepository,
                                  UserManager userManager,
                                  TransactionRepository transactionRepository) {
        this.eventRepository = eventRepository;
        this.eventDescriptionRepository = eventDescriptionRepository;
        this.ticketRepository = ticketRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketCategoryDescriptionRepository = ticketCategoryDescriptionRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.specialPriceRepository = specialPriceRepository;
        this.userManager = userManager;
        this.transactionRepository = transactionRepository;
    }

    EventWithStatistics fillWithStatistics(Event event) {
        return new EventWithStatistics(event, eventDescriptionRepository.findByEventId(event.getId()), loadTicketCategoriesWithStats(event), ticketRepository.countReleasedTickets(event.getId()));
    }

    private List<Event> getAllEvents(String username) {
        return userManager.findUserOrganizations(username)
                .parallelStream()
                .flatMap(o -> eventRepository.findByOrganizationId(o.getId()).stream())
                .collect(Collectors.toList());
    }

    @Cacheable
    public List<EventWithStatistics> getAllEventsWithStatistics(String username) {
        return getAllEvents(username).stream()
                .map(this::fillWithStatistics)
                .collect(toList());
    }

    TicketCategoryWithStatistic loadTicketCategoryWithStats(int categoryId, Event event) {
        final TicketCategory tc = ticketCategoryRepository.getById(categoryId, event.getId());
        return new TicketCategoryWithStatistic(tc,
                loadModifiedTickets(event.getId(), tc.getId()),
                specialPriceRepository.findAllByCategoryId(tc.getId()),
                event,
                descriptionForTicketCategory(tc.getId()));
    }

    private Map<String, String> descriptionForTicketCategory(int ticketCategory) {
        return ticketCategoryDescriptionRepository.findByTicketCategoryId(ticketCategory).stream().collect(Collectors.toMap(TicketCategoryDescription::getLocale, TicketCategoryDescription::getDescription));
    }

    public List<TicketCategoryWithStatistic> loadTicketCategoriesWithStats(Event event) {
        return loadTicketCategories(event).stream()
                .map(tc -> new TicketCategoryWithStatistic(tc, loadModifiedTickets(tc.getEventId(), tc.getId()),
                    specialPriceRepository.findAllByCategoryId(tc.getId()), event,
                    descriptionForTicketCategory(tc.getId())))
                .sorted()
                .collect(toList());
    }

    public List<TicketCategory> loadTicketCategories(Event event) {
        return ticketCategoryRepository.findByEventId(event.getId());
    }

    public EventWithStatistics getSingleEventWithStatistics(String eventName, String username) {
        return fillWithStatistics(getSingleEvent(eventName, username));
    }

    private Event getSingleEvent(String eventName, String username) {
        final Event event = eventRepository.findByShortName(eventName);
        checkOwnership(event, username, event.getOrganizationId());
        return event;
    }

    private void checkOwnership(Event event, String username, int organizationId) {
        Validate.isTrue(organizationId == event.getOrganizationId(), "invalid organizationId");
        userManager.findUserOrganizations(username)
                .stream()
                .filter(o -> o.getId() == organizationId)
                .findAny()
                .orElseThrow(IllegalArgumentException::new);
    }

    private List<TicketWithStatistic> loadModifiedTickets(int eventId, int categoryId) {
        Event event = eventRepository.findById(eventId);
        return ticketRepository.findAllModifiedTickets(eventId, categoryId).stream()
                .map(t -> new TicketWithStatistic(t, ticketReservationRepository.findReservationById(t.getTicketsReservationId()),
                        event.getZoneId(), optionally(() -> transactionRepository.loadByReservationId(t.getTicketsReservationId()))))
                .sorted()
                .collect(Collectors.toList());
    }

    public Predicate<Event> noSeatsAvailable() {
        return event -> {
            EventWithStatistics eventWithStatistics = fillWithStatistics(event);
            return eventWithStatistics.getTicketCategories().stream().allMatch(tc -> EventUtil.determineAvailableSeats(tc, eventWithStatistics) == 0);
        };
    }

}
