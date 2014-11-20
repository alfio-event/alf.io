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

import alfio.manager.location.LocationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.SpecialPrice;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.modification.EventModification;
import alfio.model.modification.EventWithStatistics;
import alfio.model.modification.TicketCategoryWithStatistic;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.SpecialPriceRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.util.MonetaryUtil;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.maxBy;
import static java.util.stream.Collectors.toList;

@Component
@Transactional
public class EventManager {

    private final UserManager userManager;
    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final TicketReservationManager ticketReservationManager;
    private final SpecialPriceRepository specialPriceRepository;
    private final LocationManager locationManager;
    private final NamedParameterJdbcTemplate jdbc;

    @Autowired
    public EventManager(UserManager userManager,
                        EventRepository eventRepository,
                        TicketCategoryRepository ticketCategoryRepository,
                        TicketRepository ticketRepository,
                        TicketReservationManager ticketReservationManager,
                        SpecialPriceRepository specialPriceRepository,
                        LocationManager locationManager,
                        NamedParameterJdbcTemplate jdbc) {
        this.userManager = userManager;
        this.eventRepository = eventRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketRepository = ticketRepository;
        this.ticketReservationManager = ticketReservationManager;
        this.specialPriceRepository = specialPriceRepository;
        this.locationManager = locationManager;
        this.jdbc = jdbc;
    }

    public List<Event> getAllEvents(String username) {
        return userManager.findUserOrganizations(username)
                .parallelStream()
                .flatMap(o -> eventRepository.findByOrganizationId(o.getId()).stream())
                .collect(Collectors.toList());
    }

    @Cacheable
    public List<EventWithStatistics> getAllEventsWithStatistics(String username) {
        return getAllEvents(username).stream()
                 .map(e -> new EventWithStatistics(e, loadTicketCategoriesWithStats(e)))
                 .collect(toList());
    }

    public Event getSingleEvent(String eventName, String username) {
        final Event event = eventRepository.findByShortName(eventName);
        //final int organizer = eventOrganizationRepository.getByEventId(event.getId()).getOrganizationId();
        checkOwnership(event, username, event.getOrganizationId());
        return event;
    }

    private void checkOwnership(Event event, String username, int organizationId) {
        Validate.isTrue(organizationId == event.getOrganizationId());
        userManager.findUserOrganizations(username)
                .stream()
                .filter(o -> o.getId() == organizationId)
                .findAny()
                .orElseThrow(IllegalArgumentException::new);
    }

    public EventWithStatistics getSingleEventWithStatistics(String eventName, String username) {
        final Event event = getSingleEvent(eventName, username);
        return new EventWithStatistics(event, loadTicketCategoriesWithStats(event));
    }

    public List<TicketCategory> loadTicketCategories(Event event) {
        return ticketCategoryRepository.findByEventId(event.getId());
    }

    public TicketCategoryWithStatistic loadTicketCategoryWithStats(int categoryId, Event event) {
        final TicketCategory tc = ticketCategoryRepository.getById(categoryId, event.getId());
        return new TicketCategoryWithStatistic(tc,
                ticketReservationManager.loadModifiedTickets(event.getId(), tc.getId()),
                specialPriceRepository.findAllByCategoryId(tc.getId()), event.getZoneId());
    }

    public List<TicketCategoryWithStatistic> loadTicketCategoriesWithStats(Event event) {
        return loadTicketCategories(event).stream()
                    .map(tc -> new TicketCategoryWithStatistic(tc, ticketReservationManager.loadModifiedTickets(tc.getEventId(), tc.getId()), specialPriceRepository.findAllByCategoryId(tc.getId()), event.getZoneId()))
                    .sorted()
                    .collect(toList());
    }

    public Organization loadOrganizer(Event event, String username) {
        return userManager.findOrganizationById(event.getOrganizationId(), username);
    }

    public Event findEventByTicketCategory(TicketCategory ticketCategory) {
        return eventRepository.findById(ticketCategory.getEventId());
    }

    public void createEvent(EventModification em) {
        int eventId = insertEvent(em);
        Event event = eventRepository.findById(eventId);
        distributeSeats(em, event);
        createAllTicketsForEvent(eventId, event);
    }

    public void updateEventHeader(int eventId, EventModification em, String username) {
        final Event original = eventRepository.findById(eventId);
        checkOwnership(original, username, em.getOrganizationId());
        final GeolocationResult geolocation = geolocate(em.getLocation());
        final ZoneId zoneId = geolocation.getZoneId();
        final ZonedDateTime begin = em.getBegin().toZonedDateTime(zoneId);
        final ZonedDateTime end = em.getEnd().toZonedDateTime(zoneId);
        eventRepository.updateHeader(eventId, em.getDescription(), em.getShortName(), em.getWebsiteUrl(), em.getTermsAndConditionsUrl(),
                em.getImageUrl(), em.getLocation(), geolocation.getLatitude(), geolocation.getLongitude(),
                begin, end, geolocation.getTimeZone(), em.getOrganizationId());
        //fix dates...
        getSingleEventWithStatistics(em.getShortName(), username).getTicketCategories().stream()
                .map(tc -> Triple.of(tc, tc.getInception(zoneId), tc.getExpiration(zoneId)))
                .filter(t -> t.getMiddle().isBefore(begin) || t.getRight().isAfter(end))
                .forEach(t -> fixTicketCategoryDates(begin, end, t.getLeft(), t.getMiddle(), t.getRight()));
    }

    private void fixTicketCategoryDates(ZonedDateTime begin, ZonedDateTime end, TicketCategoryWithStatistic tc, ZonedDateTime inception, ZonedDateTime expiration) {
        ticketCategoryRepository.fixDates(tc.getId(), ObjectUtils.max(begin, inception), ObjectUtils.min(end, expiration));
    }

    private GeolocationResult geolocate(String location) {
        Pair<String, String> coordinates = locationManager.geocode(location);
        return new GeolocationResult(coordinates, locationManager.getTimezone(coordinates));
    }

    public void updateEvent(int eventId, EventModification em, String username) {
        final Event original = eventRepository.findById(eventId);
        final List<TicketCategoryWithStatistic> ticketCategories = getSingleEventWithStatistics(original.getShortName(), username)
                .getTicketCategories();
        int soldTickets = ticketCategories.stream()
                .mapToInt(TicketCategoryWithStatistic::getSoldTickets)
                .sum();
        int existingTickets = ticketCategories.stream()
                .mapToInt(TicketCategoryWithStatistic::getMaxTickets)
                .sum();

        if(soldTickets > 0 || ticketRepository.invalidateAllTickets(eventId) != existingTickets) {
            throw new IllegalStateException("Cannot update the event: some tickets have been already reserved/confirmed.");
        }
        ticketCategoryRepository.findAllTicketCategories(eventId).stream()
                .mapToInt(TicketCategory::getId)
                .forEach(ticketCategoryRepository::deactivate);
        
        internalUpdateEvent(eventId, em);
        final Event updated = eventRepository.findById(eventId);
        distributeSeats(em, updated);
        createAllTicketsForEvent(eventId, updated);
    }

    public void reallocateTickets(int srcCategoryId, int targetCategoryId, int eventId) {
        Event event = eventRepository.findById(eventId);
        TicketCategoryWithStatistic src = loadTicketCategoryWithStats(srcCategoryId, event);
        TicketCategory target = ticketCategoryRepository.getById(targetCategoryId, eventId);
        ticketCategoryRepository.updateSeatsAvailability(srcCategoryId, src.getSoldTickets());
        ticketCategoryRepository.updateSeatsAvailability(targetCategoryId, target.getMaxTickets() + src.getNotSoldTickets());
        specialPriceRepository.cancelExpiredTokens(srcCategoryId);
    }

    private MapSqlParameterSource[] prepareTicketsBulkInsertParameters(int eventId,
                                                                       ZonedDateTime creation,
                                                                       Event event,
                                                                       int regularPrice) {

        //FIXME: the date should be inserted as ZonedDateTime !
        Date creationDate = Date.from(creation.toInstant());

        return ticketCategoryRepository.findByEventId(event.getId()).stream()
                    .flatMap(tc -> Stream.generate(MapSqlParameterSource::new)
                            .limit(tc.getMaxTickets())
                            .map(ps -> buildParams(eventId, creationDate, tc, regularPrice, tc.getPriceInCents(), ps)))
                    .toArray(MapSqlParameterSource[]::new);
    }

    private MapSqlParameterSource buildParams(int eventId,
                                              Date creation,
                                              TicketCategory tc,
                                              int originalPrice,
                                              int paidPrice,
                                              MapSqlParameterSource ps) {
        return ps.addValue("uuid", UUID.randomUUID().toString())
                .addValue("creation", creation)
                .addValue("categoryId", tc.getId())
                .addValue("eventId", eventId)
                .addValue("status", Ticket.TicketStatus.FREE.name())
                .addValue("originalPrice", originalPrice)
                .addValue("paidPrice", paidPrice);
    }

    private void distributeSeats(EventModification em, Event event) {
        boolean freeOfCharge = em.isFreeOfCharge();
        boolean vatIncluded = em.isVatIncluded();
        ZoneId zoneId = TimeZone.getTimeZone(event.getTimeZone()).toZoneId();
        int eventId = event.getId();

        em.getTicketCategories().stream().forEach(tc -> {
            final int price = evaluatePrice(tc.getPriceInCents(), em.getVat(), vatIncluded, freeOfCharge);
            final Pair<Integer, Integer> category = ticketCategoryRepository.insert(tc.getInception().toZonedDateTime(zoneId),
                    tc.getExpiration().toZonedDateTime(zoneId), tc.getName(), tc.getDescription(), tc.getMaxTickets(), price, tc.isTokenGenerationRequested(), eventId);
            if(tc.isTokenGenerationRequested()) {
                final MapSqlParameterSource[] args = prepareTokenBulkInsertParameters(ticketCategoryRepository.getById(category.getValue(), event.getId()));
                jdbc.batchUpdate(specialPriceRepository.bulkInsert(), args);
            }
        });
        final List<TicketCategory> ticketCategories = ticketCategoryRepository.findByEventId(event.getId());
        int notAssignedTickets = em.getAvailableSeats() - ticketCategories.stream().mapToInt(TicketCategory::getMaxTickets).sum();

        if(notAssignedTickets < 0) {
            TicketCategory last = ticketCategories.stream()
                                  .sorted((tc1, tc2) -> tc2.getExpiration(event.getZoneId()).compareTo(tc1.getExpiration(event.getZoneId())))
                                  .findFirst().get();
            ticketCategoryRepository.updateSeatsAvailability(last.getId(), last.getMaxTickets() + notAssignedTickets);
        }
    }

    private MapSqlParameterSource[] prepareTokenBulkInsertParameters(TicketCategory tc) {
        return Stream.generate(MapSqlParameterSource::new)
                .limit(tc.getMaxTickets())
                .map(ps -> {
                    ps.addValue("code", UUID.randomUUID().toString());
                    ps.addValue("priceInCents", tc.getPriceInCents());
                    ps.addValue("ticketCategoryId", tc.getId());
                    ps.addValue("status", SpecialPrice.Status.WAITING.name());
                    return ps;
                })
                .toArray(MapSqlParameterSource[]::new);
    }

    private void createAllTicketsForEvent(int eventId, Event event) {
        final MapSqlParameterSource[] params = prepareTicketsBulkInsertParameters(eventId, ZonedDateTime.now(event.getZoneId()), event, event.getRegularPriceInCents());
        jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);
    }

    static int evaluatePrice(int price, BigDecimal vat, boolean vatIncluded, boolean freeOfCharge) {
        if(freeOfCharge) {
            return 0;
        }
        if(!vatIncluded) {
            return price;
        }
        return MonetaryUtil.removeVAT(price, vat);
    }

    private int insertEvent(EventModification em) {
        String paymentProxies = collectPaymentProxies(em);
        int actualPrice = evaluatePrice(em.getPriceInCents(), em.getVat(), em.isVatIncluded(), em.isFreeOfCharge());
        BigDecimal vat = em.isFreeOfCharge() ? BigDecimal.ZERO : em.getVat();
        String privateKey = UUID.randomUUID().toString();
        final GeolocationResult result = geolocate(em.getLocation());
        return eventRepository.insert(em.getDescription(), em.getShortName(), em.getWebsiteUrl(), em.getTermsAndConditionsUrl(), em.getImageUrl(), em.getLocation(),
                result.getLatitude(), result.getLongitude(), em.getBegin().toZonedDateTime(result.getZoneId()), em.getEnd().toZonedDateTime(result.getZoneId()),
                result.getTimeZone(), actualPrice, em.getCurrency(), em.getAvailableSeats(), em.isVatIncluded(), vat, paymentProxies,
                privateKey, em.getOrganizationId()).getValue();
    }

    private String collectPaymentProxies(EventModification em) {
        return em.getAllowedPaymentProxies()
                .stream()
                .map(PaymentProxy::name)
                .collect(joining(","));
    }

    private void internalUpdateEvent(int id, EventModification em) {
        final Event existing = eventRepository.findById(id);
        if(!em.getId().equals(existing.getId())) {
            throw new IllegalArgumentException("invalid event id");
        }

        String paymentProxies = collectPaymentProxies(em);
        int actualPrice = evaluatePrice(em.getPriceInCents(), em.getVat(), em.isVatIncluded(), em.isFreeOfCharge());
        BigDecimal vat = em.isFreeOfCharge() ? BigDecimal.ZERO : em.getVat();
        final GeolocationResult result = geolocate(em.getLocation());
        eventRepository.update(id, em.getDescription(), em.getShortName(), em.getWebsiteUrl(), em.getTermsAndConditionsUrl(), em.getImageUrl(), em.getLocation(),
                result.getLatitude(), result.getLongitude(), em.getBegin().toZonedDateTime(result.getZoneId()), em.getEnd().toZonedDateTime(result.getZoneId()),
                result.getTimeZone(), actualPrice, em.getCurrency(), em.getAvailableSeats(), em.isVatIncluded(), vat, paymentProxies, em.getOrganizationId());

    }

	public TicketCategory getTicketCategoryById(int id, int eventId) {
		return ticketCategoryRepository.getById(id, eventId);
	}

    @Data
    private static final class GeolocationResult {
        private final Pair<String, String> coordinates;
        private final TimeZone tz;

        public String getLatitude() {
            return coordinates.getLeft();
        }

        public String getLongitude() {
            return coordinates.getRight();
        }

        public String getTimeZone() {
            return tz.getID();
        }

        public ZoneId getZoneId() {
            return tz.toZoneId();
        }
    }
}
