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

import io.bagarino.manager.location.LocationManager;
import io.bagarino.manager.user.UserManager;
import io.bagarino.model.Event;
import io.bagarino.model.SpecialPrice;
import io.bagarino.model.Ticket;
import io.bagarino.model.TicketCategory;
import io.bagarino.model.join.EventTicketCategory;
import io.bagarino.model.modification.EventModification;
import io.bagarino.model.modification.EventWithStatistics;
import io.bagarino.model.modification.TicketCategoryWithStatistic;
import io.bagarino.model.transaction.PaymentProxy;
import io.bagarino.model.user.Organization;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.SpecialPriceRepository;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.TicketRepository;
import io.bagarino.repository.join.EventTicketCategoryRepository;
import io.bagarino.util.MonetaryUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Component
@Transactional
public class EventManager {

    private final UserManager userManager;
    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventTicketCategoryRepository eventTicketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final LocationManager locationManager;
    private final NamedParameterJdbcTemplate jdbc;

    @Autowired
    public EventManager(UserManager userManager,
                        EventRepository eventRepository,
                        TicketCategoryRepository ticketCategoryRepository,
                        EventTicketCategoryRepository eventTicketCategoryRepository,
                        TicketRepository ticketRepository,
                        SpecialPriceRepository specialPriceRepository,
                        LocationManager locationManager,
                        NamedParameterJdbcTemplate jdbc) {
        this.userManager = userManager;
        this.eventRepository = eventRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.eventTicketCategoryRepository = eventTicketCategoryRepository;
        this.ticketRepository = ticketRepository;
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
        userManager.findUserOrganizations(username)
                .stream()
                .filter(o -> o.getId() == event.getOrganizationId())
                .findAny()
                .orElseThrow(IllegalArgumentException::new);
        return event;
    }

    public EventWithStatistics getSingleEventWithStatistics(String eventName, String username) {
        final Event event = getSingleEvent(eventName, username);
        return new EventWithStatistics(event, loadTicketCategoriesWithStats(event));
    }

    public List<TicketCategory> loadTicketCategories(Event event) {
        return eventTicketCategoryRepository.findByEventId(event.getId())
                    .stream()
                    .map(etc -> ticketCategoryRepository.getById(etc.getTicketCategoryId()))
                    .collect(toList());
    }

    public TicketCategoryWithStatistic loadTicketCategoryWithStats(int categoryId, Event event) {
        final TicketCategory tc = ticketCategoryRepository.getById(categoryId);
        return new TicketCategoryWithStatistic(tc,
                ticketRepository.countConfirmedTickets(event.getId(), tc.getId()),
                specialPriceRepository.findAllByCategoryId(tc.getId()), event.getZoneId());
    }

    public List<TicketCategoryWithStatistic> loadTicketCategoriesWithStats(Event event) {
        return loadTicketCategories(event).stream()
                    .map(tc -> new TicketCategoryWithStatistic(tc, ticketRepository.countConfirmedTickets(event.getId(), tc.getId()), specialPriceRepository.findAllByCategoryId(tc.getId()), event.getZoneId()))
                    .sorted()
                    .collect(toList());
    }

    public Organization loadOrganizer(Event event, String username) {
        return userManager.findOrganizationById(event.getOrganizationId(), username);
    }

    public Event findEventByTicketCategory(TicketCategory ticketCategory) {
        final EventTicketCategory etc = eventTicketCategoryRepository.findByTicketCategoryId(ticketCategory.getId());
        return eventRepository.findById(etc.getEventId());
    }

    public void createEvent(EventModification em) {
        int eventId = insertEvent(em);
        Event event = eventRepository.findById(eventId);
        distributeSeats(em, event);
        Date creation = new Date();
        //eventOrganizationRepository.create(eventId, em.getOrganizationId());
        final MapSqlParameterSource[] params = prepareTicketsBulkInsertParameters(eventId, creation, event, event.getRegularPriceInCents());
        jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);

    }

    public void reallocateTickets(int srcCategoryId, int targetCategoryId, int eventId) {
        Event event = eventRepository.findById(eventId);
        TicketCategoryWithStatistic src = loadTicketCategoryWithStats(srcCategoryId, event);
        TicketCategory target = ticketCategoryRepository.getById(targetCategoryId);
        ticketCategoryRepository.updateSeatsAvailability(srcCategoryId, src.getSoldTickets());
        ticketCategoryRepository.updateSeatsAvailability(targetCategoryId, target.getMaxTickets() + src.getNotSoldTickets());
        specialPriceRepository.cancelExpiredTokens(srcCategoryId);
    }

    private MapSqlParameterSource[] prepareTicketsBulkInsertParameters(int eventId,
                                                                       Date creation,
                                                                       Event event,
                                                                       int regularPrice) {
        return eventTicketCategoryRepository.findByEventId(event.getId()).stream()
                    .map(etc -> ticketCategoryRepository.getById(etc.getTicketCategoryId()))
                    .flatMap(tc -> Stream.generate(MapSqlParameterSource::new)
                            .limit(tc.getMaxTickets())
                            .map(ps -> buildParams(eventId, creation, tc, regularPrice, tc.getPriceInCents(), ps)))
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
            final Pair<Integer, Integer> category = ticketCategoryRepository.insert(tc.getInception().toDate(zoneId),
                    tc.getExpiration().toDate(zoneId), tc.getName(), tc.getDescription(), tc.getMaxTickets(), price, tc.isTokenGenerationRequested());
            eventTicketCategoryRepository.insert(eventId, category.getValue());
            if(tc.isTokenGenerationRequested()) {
                final MapSqlParameterSource[] args = prepareTokenBulkInsertParameters(ticketCategoryRepository.getById(category.getValue()));
                jdbc.batchUpdate(specialPriceRepository.bulkInsert(), args);
            }
        });
        final List<TicketCategory> ticketCategories = eventTicketCategoryRepository.findByEventId(eventId).stream()
                .map(etc -> ticketCategoryRepository.getById(etc.getTicketCategoryId()))
                .collect(Collectors.toList());
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
        String paymentProxies = em.getAllowedPaymentProxies()
                .stream()
                .map(PaymentProxy::name)
                .collect(joining(","));
        int actualPrice = evaluatePrice(em.getPriceInCents(), em.getVat(), em.isVatIncluded(), em.isFreeOfCharge());
        BigDecimal vat = em.isFreeOfCharge() ? BigDecimal.ZERO : em.getVat();
        String privateKey = UUID.randomUUID().toString();
        Pair<String, String> coordinates = locationManager.geocode(em.getLocation());
        TimeZone tz = locationManager.getTimezone(coordinates);
        String timeZone = tz.getID();
        ZoneId zoneId = tz.toZoneId();
        return eventRepository.insert(em.getDescription(), em.getShortName(), em.getLocation(),
                coordinates.getLeft(), coordinates.getRight(), em.getBegin().toZonedDateTime(zoneId), em.getEnd().toZonedDateTime(zoneId),
                timeZone, actualPrice, em.getCurrency(), em.getAvailableSeats(), em.isVatIncluded(), vat, paymentProxies,
                privateKey, em.getOrganizationId()).getValue();
    }
}
