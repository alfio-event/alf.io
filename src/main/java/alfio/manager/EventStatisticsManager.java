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

import alfio.manager.support.extension.ExtensionCapability;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.TicketWithStatistic;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.util.EventUtil;
import alfio.util.MonetaryUtil;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.DISPLAY_STATS_IN_EVENT_DETAIL;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@Component
@AllArgsConstructor
@Transactional(readOnly = true)
public class EventStatisticsManager {

    private final EventRepository eventRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketSearchRepository ticketSearchRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final ConfigurationManager configurationManager;
    private final UserManager userManager;
    private final SubscriptionRepository subscriptionRepository;
    private final ExtensionManager extensionManager;

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
            final Stream<EventStatisticView> stats;
            if(isOwner) {
                stats = eventRepository.findStatisticsFor(ids).stream();
            } else {
                stats = ids.stream().map(EventStatisticView::empty);
            }
            return stats.map(stat -> {
                Event event = mappedEvent.get(stat.getEventId());
                return new EventStatistic(event, stat, displayStatisticsForEvent(event));
            }).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private boolean displayStatisticsForEvent(EventAndOrganizationId event) {
        return configurationManager.getFor(DISPLAY_STATS_IN_EVENT_DETAIL, event.getConfigurationLevel()).getValueAsBooleanOrDefault();
    }


    public List<EventStatistic> getAllEventsWithStatistics(String username) {
        return getAllEventsWithStatisticsFilteredBy(username, e -> true);
    }

    public EventWithAdditionalInfo getEventWithAdditionalInfo(String eventName, String username) {
        Event event = getEventAndCheckOwnership(eventName, username);
        Map<String, String> description = eventDescriptionRepository.findByEventIdAsMap(event.getId());
        boolean owner = userManager.isOwner(userManager.findUserByUsername(username));
        EventStatisticView statistics = owner ? eventRepository.findStatisticsFor(event.getId()) : EventStatisticView.empty(event.getId());
        EventStatistic eventStatistic = new EventStatistic(event, statistics, displayStatisticsForEvent(event));
        BigDecimal grossIncome = owner ? MonetaryUtil.centsToUnit(eventRepository.getGrossIncome(event.getId()), event.getCurrency()) : BigDecimal.ZERO;

        List<TicketCategory> ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        List<Integer> ticketCategoriesIds = ticketCategories.stream().map(TicketCategory::getId).collect(Collectors.toList());

        Map<Integer, Map<String, String>> descriptions = ticketCategoryDescriptionRepository.descriptionsByTicketCategory(ticketCategoriesIds);
        Map<Integer, TicketCategoryStatisticView> ticketCategoriesStatistics = owner ? ticketCategoryRepository.findStatisticsForEventIdByCategoryId(event.getId()) : ticketCategoriesIds.stream().collect(toMap(Function.identity(), id -> TicketCategoryStatisticView.empty(id, event.getId())));
        Map<Integer, List<SpecialPrice>> specialPrices = ticketCategoriesIds.isEmpty() ? Collections.emptyMap() : specialPriceRepository.findAllByCategoriesIdsMapped(ticketCategoriesIds);

        var metadata = ticketCategoryRepository.findCategoryMetadataForEventGroupByCategoryId(event.getId());

        List<TicketCategoryWithAdditionalInfo> tWithInfo = ticketCategories.stream()
            .map(t -> new TicketCategoryWithAdditionalInfo(event, t, ticketCategoriesStatistics.get(t.getId()), descriptions.get(t.getId()), specialPrices.get(t.getId()), metadata.get(t.getId())))
            .collect(Collectors.toList());

        Set<ExtensionCapabilitySummary> supportedCapabilities = extensionManager.getSupportedCapabilities(EnumSet.allOf(ExtensionCapability.class), event);

        // TODO category and event settings

        return new EventWithAdditionalInfo(event,
            tWithInfo,
            eventStatistic,
            description,
            grossIncome,
            eventRepository.getMetadataForEvent(event.getId()),
            subscriptionRepository.findLinkedSubscriptionIds(event.getId(), event.getOrganizationId()),
            supportedCapabilities
        );
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
        return ticketSearchRepository.findAllModifiedTicketsWithReservationAndTransaction(eventId, categoryId, page * pageSize, pageSize, toSearch).stream()
            .map(t -> new TicketWithStatistic(t.getTicket(), t.getTicketReservation(), event.getZoneId(), t.getTransaction(), firstNonNull(t.getPromoCode(), t.getSpecialPriceToken())))
            .sorted()
            .collect(Collectors.toList());
    }

    public Integer countModifiedTicket(int eventId, int categoryId, String search) {
        String toSearch = prepareSearchTerm(search);
        return ticketSearchRepository.countAllModifiedTicketsWithReservationAndTransaction(eventId, categoryId, toSearch);
    }

    public Predicate<EventAndOrganizationId> noSeatsAvailable() {
        return event -> {
            Map<Integer, TicketCategoryStatisticView> stats = ticketCategoryRepository.findStatisticsForEventIdByCategoryId(event.getId());
            EventStatisticView eventStatisticView = eventRepository.findStatisticsFor(event.getId());
            return ticketCategoryRepository.findAllTicketCategories(event.getId())
                .stream()
                .filter(tc -> !tc.isAccessRestricted())
                .allMatch(tc -> EventUtil.determineAvailableSeats(stats.get(tc.getId()), eventStatisticView) == 0);
        };
    }

    public Optional<ZonedDateTime> getFirstReservationConfirmedTimestamp(int eventId) {
        return ticketReservationRepository.getFirstConfirmationTimestampForEvent(eventId);
    }

    public Optional<ZonedDateTime> getFirstReservationCreatedTimestamp(int eventId) {
        return ticketReservationRepository.getFirstReservationCreatedTimestampForEvent(eventId);
    }

    public List<TicketsByDateStatistic> getTicketSoldStatistics(int eventId, ZonedDateTime from, ZonedDateTime to, String granularity) {
        return ticketReservationRepository.getSoldStatistic(eventId, from, to, granularity);
    }

    public List<TicketsByDateStatistic> getTicketReservedStatistics(int eventId, ZonedDateTime from, ZonedDateTime to, String granularity) {
        return ticketReservationRepository.getReservedStatistic(eventId, from, to, granularity);
    }


}
