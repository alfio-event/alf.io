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
import alfio.model.*;
import alfio.model.modification.TicketCategoryWithStatistic;
import alfio.model.modification.TicketWithStatistic;
import alfio.repository.*;
import alfio.util.EventUtil;
import alfio.util.MonetaryUtil;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    private List<Event> getAllEvents(String username) {
        return userManager.findUserOrganizations(username)
                .parallelStream()
                .flatMap(o -> eventRepository.findByOrganizationId(o.getId()).stream())
                .collect(Collectors.toList());
    }


    public List<EventStatistic> getAllEventsWithStatisticsFilteredBy(String username, Predicate<Event> predicate) {
        List<Event> events = getAllEvents(username).stream().filter(predicate).collect(toList());
        Map<Integer, Event> mappedEvent = events.stream().collect(Collectors.toMap(Event::getId, Function.identity()));
        if(!mappedEvent.isEmpty()) {
            List<EventStatisticView> stats = eventRepository.findStatisticsFor(mappedEvent.keySet());
            return stats.stream().map(stat -> new EventStatistic(mappedEvent.get(stat.getEventId()), stat)).sorted().collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }


    @Cacheable
    public List<EventStatistic> getAllEventsWithStatistics(String username) {
        return getAllEventsWithStatisticsFilteredBy(username, (e) -> true);
    }

    public EventWithAdditionalInfo getEventWithAdditionalInfo(String eventName, String username) {
        Event event = getSingleEvent(eventName, username);
        Map<String, String> description = eventDescriptionRepository.findByEventIdAsMap(event.getId());
        EventStatistic eventStatistic = new EventStatistic(event, eventRepository.findStatisticsFor(event.getId()));
        BigDecimal grossIncome = MonetaryUtil.centsToUnit(eventRepository.getGrossIncome(event.getId()));

        List<TicketCategory> ticketCategories = loadTicketCategories(event);
        List<Integer> ticketCategoriesIds = ticketCategories.stream().map(TicketCategory::getId).collect(Collectors.toList());

        Map<Integer, Map<String, String>> descriptions = ticketCategoryDescriptionRepository.descriptionsByTicketCategory(ticketCategoriesIds);
        Map<Integer, TicketCategoryStatisticView> ticketCategoriesStatistics = ticketCategoryRepository.findStatisticsForEventIdByCategoryId(event.getId());
        Map<Integer, List<SpecialPrice>> specialPrices = specialPriceRepository.findAllByCategoriesIdsMapped(ticketCategoriesIds);

        List<TicketCategoryWithAdditionalInfo> tWithInfo = ticketCategories.stream()
            .map(t -> new TicketCategoryWithAdditionalInfo(event, t, ticketCategoriesStatistics.get(t.getId()), descriptions.get(t.getId()), specialPrices.get(t.getId())))
            .collect(Collectors.toList());

        return new EventWithAdditionalInfo(event, tWithInfo, eventStatistic, description, grossIncome);
    }

    public List<TicketCategoryWithStatistic> loadTicketCategoriesWithStats(Event event) {

        List<TicketCategory> categories = loadTicketCategories(event);
        List<Integer> ticketCategoriesIds = categories.stream().map(TicketCategory::getId).collect(Collectors.toList());
        Map<Integer, Map<String, String>> descriptions = ticketCategoryDescriptionRepository.descriptionsByTicketCategory(ticketCategoriesIds);
        Map<Integer, List<SpecialPrice>> specialPrices = specialPriceRepository.findAllByCategoriesIdsMapped(ticketCategoriesIds);

        return loadTicketCategories(event).stream()
                .map(tc -> new TicketCategoryWithStatistic(tc, loadModifiedTickets(tc.getEventId(), tc.getId()),
                    specialPrices.get(tc.getId()), event, descriptions.get(tc.getId())))
                .sorted()
                .collect(toList());
    }

    private List<TicketCategory> loadTicketCategories(Event event) {
        return ticketCategoryRepository.findByEventId(event.getId());
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

    public List<TicketWithStatistic> loadModifiedTickets(int eventId, int categoryId) {
        Event event = eventRepository.findById(eventId);
        return ticketRepository.findAllModifiedTickets(eventId, categoryId).stream()
                .map(t -> new TicketWithStatistic(t, event, ticketReservationRepository.findReservationById(t.getTicketsReservationId()),
                        event.getZoneId(), transactionRepository.loadOptionalByReservationId(t.getTicketsReservationId())))
                .sorted()
                .collect(Collectors.toList());
    }

    public Predicate<Event> noSeatsAvailable() {
        return event -> {
            Map<Integer, TicketCategoryStatisticView> stats = ticketCategoryRepository.findStatisticsForEventIdByCategoryId(event.getId());
            EventStatisticView eventStatisticView = eventRepository.findStatisticsFor(event.getId());
            return ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().allMatch(tc -> EventUtil.determineAvailableSeats(stats.get(tc.getId()), eventStatisticView) == 0);
        };
    }

    public List<TicketSoldStatistic> getTicketSoldStatistics(int eventId, Date from, Date to) {
        return ticketReservationRepository.getSoldStatistic(eventId, from, to);
    }
}
