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
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.TicketWithStatistic;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.util.EventUtil;
import alfio.util.MonetaryUtil;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Component
@AllArgsConstructor
public class EventStatisticsManager {

    private final EventRepository eventRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final ConfigurationManager configurationManager;
    private final UserManager userManager;

    private List<Event> getAllEvents(String username) {
        List<Integer> orgIds = userManager.findUserOrganizations(username).stream().map(Organization::getId).collect(toList());
        return orgIds.isEmpty() ? Collections.emptyList() : eventRepository.findByOrganizationIds(orgIds);
    }


    public List<EventStatistic> getAllEventsWithStatisticsFilteredBy(String username, Predicate<Event> predicate) {
        List<Event> events = getAllEvents(username).stream().filter(predicate).collect(toList());
        Map<Integer, Event> mappedEvent = events.stream().collect(Collectors.toMap(Event::getId, Function.identity()));
        if(!mappedEvent.isEmpty()) {
            boolean isOwner = userManager.isOwner(userManager.findUserByUsername(username));
            Set<Integer> ids = mappedEvent.keySet();
            Stream<EventStatisticView> stats = isOwner ? eventRepository.findStatisticsFor(ids).stream() : ids.stream().map(EventStatisticView::empty);
            return stats.map(stat -> {
                Event event = mappedEvent.get(stat.getEventId());
                return new EventStatistic(event, stat, displayStatisticsForEvent(event));
            }).sorted().collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private boolean displayStatisticsForEvent(Event event) {
        return configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.DISPLAY_STATS_IN_EVENT_DETAIL), true);
    }


    @Cacheable
    public List<EventStatistic> getAllEventsWithStatistics(String username) {
        return getAllEventsWithStatisticsFilteredBy(username, (e) -> true);
    }

    public EventWithAdditionalInfo getEventWithAdditionalInfo(String eventName, String username) {
        Event event = getEventAndCheckOwnership(eventName, username);
        Map<String, String> description = eventDescriptionRepository.findByEventIdAsMap(event.getId());
        boolean owner = userManager.isOwner(userManager.findUserByUsername(username));
        EventStatisticView statistics = owner ? eventRepository.findStatisticsFor(event.getId()) : EventStatisticView.empty(event.getId());
        EventStatistic eventStatistic = new EventStatistic(event, statistics, displayStatisticsForEvent(event));
        BigDecimal grossIncome = owner ? MonetaryUtil.centsToUnit(eventRepository.getGrossIncome(event.getId())) : BigDecimal.ZERO;

        List<TicketCategory> ticketCategories = loadTicketCategories(event);
        List<Integer> ticketCategoriesIds = ticketCategories.stream().map(TicketCategory::getId).collect(Collectors.toList());

        Map<Integer, Map<String, String>> descriptions = ticketCategoryDescriptionRepository.descriptionsByTicketCategory(ticketCategoriesIds);
        Map<Integer, TicketCategoryStatisticView> ticketCategoriesStatistics = owner ? ticketCategoryRepository.findStatisticsForEventIdByCategoryId(event.getId()) : ticketCategoriesIds.stream().collect(toMap(Function.identity(), id -> TicketCategoryStatisticView.empty(id, event.getId())));
        Map<Integer, List<SpecialPrice>> specialPrices = ticketCategoriesIds.isEmpty() ? Collections.emptyMap() : specialPriceRepository.findAllByCategoriesIdsMapped(ticketCategoriesIds);

        List<TicketCategoryWithAdditionalInfo> tWithInfo = ticketCategories.stream()
            .map(t -> new TicketCategoryWithAdditionalInfo(event, t, ticketCategoriesStatistics.get(t.getId()), descriptions.get(t.getId()), specialPrices.get(t.getId())))
            .collect(Collectors.toList());

        return new EventWithAdditionalInfo(event, tWithInfo, eventStatistic, description, grossIncome);
    }

    private List<TicketCategory> loadTicketCategories(Event event) {
        return ticketCategoryRepository.findByEventId(event.getId());
    }

    private Event getEventAndCheckOwnership(String eventName, String username) {
        Event event = eventRepository.findByShortName(eventName);
        userManager.findOrganizationById(event.getOrganizationId(), username);
        return event;
    }

    private static String prepareSearchTerm(String search) {
        String toSearch = StringUtils.trimToNull(search);
        return toSearch == null ? null : ("%" + toSearch + "%");
    }

    public List<TicketWithStatistic> loadModifiedTickets(int eventId, int categoryId, int page, String search) {
        Event event = eventRepository.findById(eventId);
        String toSearch = prepareSearchTerm(search);
        final int pageSize = 30;
        return ticketRepository.findAllModifiedTicketsWithReservationAndTransaction(eventId, categoryId, page * pageSize, pageSize, toSearch).stream()
            .map(t -> new TicketWithStatistic(t.getTicket(), event, t.getTicketReservation(), event.getZoneId(), t.getTransaction()))
            .sorted()
            .collect(Collectors.toList());
    }

    public Integer countModifiedTicket(int eventId, int categoryId, String search) {
        String toSearch = prepareSearchTerm(search);
        return ticketRepository.countAllModifiedTicketsWithReservationAndTransaction(eventId, categoryId, toSearch);
    }

    public Predicate<Event> noSeatsAvailable() {
        return event -> {
            Map<Integer, TicketCategoryStatisticView> stats = ticketCategoryRepository.findStatisticsForEventIdByCategoryId(event.getId());
            EventStatisticView eventStatisticView = eventRepository.findStatisticsFor(event.getId());
            return ticketCategoryRepository.findAllTicketCategories(event.getId())
                .stream()
                .filter(tc -> !tc.isAccessRestricted())
                .allMatch(tc -> EventUtil.determineAvailableSeats(stats.get(tc.getId()), eventStatisticView) == 0);
        };
    }

    public List<TicketSoldStatistic> getTicketSoldStatistics(int eventId, Date from, Date to) {
        return ticketReservationRepository.getSoldStatistic(eventId, from, to);
    }


}
