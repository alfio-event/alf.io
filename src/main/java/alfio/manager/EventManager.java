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
import alfio.manager.support.OrderSummary;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.model.modification.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.util.MonetaryUtil;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.util.OptionalWrapper.optionally;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Component
@Transactional
@Log4j2
public class EventManager {

    private final UserManager userManager;
    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final TicketReservationManager ticketReservationManager;
    private final SpecialPriceRepository specialPriceRepository;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final LocationManager locationManager;
    private final NamedParameterJdbcTemplate jdbc;
    private final ConfigurationManager configurationManager;

    @Autowired
    public EventManager(UserManager userManager,
                        EventRepository eventRepository,
                        TicketCategoryRepository ticketCategoryRepository,
                        TicketRepository ticketRepository,
                        TicketReservationManager ticketReservationManager,
                        SpecialPriceRepository specialPriceRepository,
                        PromoCodeDiscountRepository promoCodeRepository,
                        LocationManager locationManager,
                        NamedParameterJdbcTemplate jdbc,
                        ConfigurationManager configurationManager) {
        this.userManager = userManager;
        this.eventRepository = eventRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketRepository = ticketRepository;
        this.ticketReservationManager = ticketReservationManager;
        this.specialPriceRepository = specialPriceRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.locationManager = locationManager;
        this.jdbc = jdbc;
        this.configurationManager = configurationManager;
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
                 .map(this::fillWithStatistics)
                 .collect(toList());
    }

    public Event getSingleEvent(String eventName, String username) {
        final Event event = eventRepository.findByShortName(eventName);
        checkOwnership(event, username, event.getOrganizationId());
        return event;
    }

    void checkOwnership(Event event, String username, int organizationId) {
        Validate.isTrue(organizationId == event.getOrganizationId(), "invalid organizationId");
        userManager.findUserOrganizations(username)
                .stream()
                .filter(o -> o.getId() == organizationId)
                .findAny()
                .orElseThrow(IllegalArgumentException::new);
    }

    public EventWithStatistics getSingleEventWithStatistics(String eventName, String username) {
        return fillWithStatistics(getSingleEvent(eventName, username));
    }

    private EventWithStatistics fillWithStatistics(Event event) {
        return new EventWithStatistics(event, loadTicketCategoriesWithStats(event));
    }

    public List<TicketCategory> loadTicketCategories(Event event) {
        return ticketCategoryRepository.findByEventId(event.getId());
    }

    public TicketCategoryWithStatistic loadTicketCategoryWithStats(int categoryId, Event event) {
        final TicketCategory tc = ticketCategoryRepository.getById(categoryId, event.getId());
        return new TicketCategoryWithStatistic(tc,
                ticketReservationManager.loadModifiedTickets(event.getId(), tc.getId()),
                specialPriceRepository.findAllByCategoryId(tc.getId()), event.getZoneId(),
                categoryPriceCalculator(event));
    }

    public List<TicketCategoryWithStatistic> loadTicketCategoriesWithStats(Event event) {
        return loadTicketCategories(event).stream()
                    .map(tc -> new TicketCategoryWithStatistic(tc, ticketReservationManager.loadModifiedTickets(tc.getEventId(), tc.getId()), specialPriceRepository.findAllByCategoryId(tc.getId()), event.getZoneId(), categoryPriceCalculator(event)))
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
                em.getImageUrl(), em.getFileBlobId(), em.getLocation(), geolocation.getLatitude(), geolocation.getLongitude(),
                begin, end, geolocation.getTimeZone(), em.getOrganizationId());
        if(!original.getBegin().equals(begin) || !original.getEnd().equals(end)) {
            fixOutOfRangeCategories(em, username, zoneId, end);
        }
    }

    public void updateEventPrices(int eventId, EventModification em, String username) {
        final Event original = eventRepository.findById(eventId);
        checkOwnership(original, username, em.getOrganizationId());
        if(original.getAvailableSeats() > em.getAvailableSeats()) {
            final EventWithStatistics eventWithStatistics = fillWithStatistics(original);
            int allocatedSeats = eventWithStatistics.getTicketCategories().stream()
                    .mapToInt(TicketCategoryWithStatistic::getMaxTickets)
                    .sum();
            if(em.getAvailableSeats() < allocatedSeats) {
                throw new IllegalArgumentException(format("cannot reduce max tickets to %d. There are already %d tickets allocated. Try updating categories first.", em.getAvailableSeats(), allocatedSeats));
            }
        }
        String paymentProxies = collectPaymentProxies(em);
        int actualPrice = evaluatePrice(em.getPriceInCents(), em.getVat(), em.isVatIncluded(), em.isFreeOfCharge());
        BigDecimal vat = em.isFreeOfCharge() ? BigDecimal.ZERO : em.getVat();
        eventRepository.updatePrices(actualPrice, em.getCurrency(), em.getAvailableSeats(), em.isVatIncluded(), vat, paymentProxies, eventId);
    }

    public void insertCategory(int eventId, TicketCategoryModification tcm, String username) {
        final Event event = eventRepository.findById(eventId);
        checkOwnership(event, username, event.getOrganizationId());
        int sum = ticketCategoryRepository.findByEventId(eventId).stream()
                .mapToInt(TicketCategory::getMaxTickets)
                .sum();
        Validate.isTrue(sum + tcm.getMaxTickets() <= event.getAvailableSeats(), "Not enough seats");
        Validate.isTrue(tcm.getExpiration().toZonedDateTime(event.getZoneId()).isBefore(event.getEnd()), "expiration must be before the end of the event");
        insertCategory(tcm, event.getVat(), event.isVatIncluded(), event.isFreeOfCharge(), event.getZoneId(), eventId);
    }

    public void updateCategory(int categoryId, int eventId, TicketCategoryModification tcm, String username) {
        final Event event = eventRepository.findById(eventId);
        checkOwnership(event, username, event.getOrganizationId());
        final List<TicketCategory> categories = ticketCategoryRepository.findByEventId(eventId);
        final TicketCategory existing = categories.stream().filter(tc -> tc.getId() == categoryId).findFirst().orElseThrow(IllegalArgumentException::new);
        Validate.isTrue(tcm.getExpiration().toZonedDateTime(event.getZoneId()).isBefore(event.getEnd()), "expiration must be before the end of the event");
        Validate.isTrue(tcm.getMaxTickets() - existing.getMaxTickets() + categories.stream().mapToInt(TicketCategory::getMaxTickets).sum() <= event.getAvailableSeats(), "not enough seats");
        if((tcm.isTokenGenerationRequested() ^ existing.isAccessRestricted()) && ticketRepository.countConfirmedAndPendingTickets(eventId, categoryId) > 0) {
            throw new IllegalStateException("cannot update the category. There are tickets already sold.");
        }
        updateCategory(tcm, event.getVat(), event.isVatIncluded(), event.isFreeOfCharge(), event.getZoneId(), eventId);
    }

    void fixOutOfRangeCategories(EventModification em, String username, ZoneId zoneId, ZonedDateTime end) {
        getSingleEventWithStatistics(em.getShortName(), username).getTicketCategories().stream()
                .map(tc -> Triple.of(tc, tc.getInception(zoneId), tc.getExpiration(zoneId)))
                .filter(t -> t.getRight().isAfter(end))
                .forEach(t -> fixTicketCategoryDates(end, t.getLeft(), t.getMiddle(), t.getRight()));
    }

    private void fixTicketCategoryDates(ZonedDateTime end, TicketCategoryWithStatistic tc, ZonedDateTime inception, ZonedDateTime expiration) {
        final ZonedDateTime newExpiration = ObjectUtils.min(end, expiration);
        Objects.requireNonNull(newExpiration);
        Validate.isTrue(inception.isBefore(newExpiration), format("Cannot fix dates for category \"%s\" (id: %d), try updating that category first.", tc.getName(), tc.getId()));
        ticketCategoryRepository.fixDates(tc.getId(), inception, newExpiration);
    }

    private GeolocationResult geolocate(String location) {
        Pair<String, String> coordinates = locationManager.geocode(location);
        return new GeolocationResult(coordinates, locationManager.getTimezone(coordinates));
    }

    public void reallocateTickets(int srcCategoryId, int targetCategoryId, int eventId) {
        Event event = eventRepository.findById(eventId);
        TicketCategoryWithStatistic src = loadTicketCategoryWithStats(srcCategoryId, event);
        TicketCategory target = ticketCategoryRepository.getById(targetCategoryId, eventId);
        int notSoldTickets = src.getNotSoldTickets();
        if(notSoldTickets == 0) {
            log.info("since all the ticket have been sold, ticket moving is not needed anymore.");
            return;
        }
        List<Integer> lockedTickets = ticketRepository.selectTicketInCategoryForUpdate(eventId, srcCategoryId, notSoldTickets);
        int locked = lockedTickets.size();
        if(locked != notSoldTickets) {
            throw new IllegalStateException(String.format("Expected %d free tickets, got %d.", notSoldTickets, locked));
        }
        ticketCategoryRepository.updateSeatsAvailability(srcCategoryId, src.getSoldTickets());
        ticketCategoryRepository.updateSeatsAvailability(targetCategoryId, target.getMaxTickets() + locked);
        ticketRepository.moveToAnotherCategory(lockedTickets, targetCategoryId, target.getPriceInCents(), target.getPriceInCents());
        specialPriceRepository.cancelExpiredTokens(srcCategoryId);
        if(target.isAccessRestricted()) {
            insertTokens(target, locked);
        }
    }

    private MapSqlParameterSource[] prepareTicketsBulkInsertParameters(int eventId,
                                                                       ZonedDateTime creation,
                                                                       Event event,
                                                                       int regularPrice) {

        //FIXME: the date should be inserted as ZonedDateTime !
        Date creationDate = Date.from(creation.toInstant());

        return ticketCategoryRepository.findByEventId(event.getId()).stream()
                    .flatMap(tc -> generateTicketsForCategory(tc, eventId, creationDate, regularPrice, 0))
                    .toArray(MapSqlParameterSource[]::new);
    }

    private Stream<MapSqlParameterSource> generateTicketsForCategory(TicketCategory tc,
                                                                     int eventId,
                                                                     Date creationDate,
                                                                     int regularPrice,
                                                                     int existing) {
        return Stream.generate(MapSqlParameterSource::new)
                    .limit(Math.abs(tc.getMaxTickets() - existing))
                    .map(ps -> buildParams(eventId, creationDate, tc, regularPrice, tc.getPriceInCents(), ps));
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
                    tc.getExpiration().toZonedDateTime(zoneId), tc.getName(), tc.getDescription(), tc.getMaxTickets(), price, tc.isTokenGenerationRequested(), eventId, tc.isBounded());
            if(tc.isTokenGenerationRequested()) {
                final TicketCategory ticketCategory = ticketCategoryRepository.getById(category.getValue(), event.getId());
                final MapSqlParameterSource[] args = prepareTokenBulkInsertParameters(ticketCategory, ticketCategory.getMaxTickets());
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

    private void insertCategory(TicketCategoryModification tc, BigDecimal vat, boolean vatIncluded, boolean freeOfCharge, ZoneId zoneId, int eventId) {
        final int price = evaluatePrice(tc.getPriceInCents(), vat, vatIncluded, freeOfCharge);
        final Pair<Integer, Integer> category = ticketCategoryRepository.insert(tc.getInception().toZonedDateTime(zoneId),
                tc.getExpiration().toZonedDateTime(zoneId), tc.getName(), tc.getDescription(), tc.getMaxTickets(), price, tc.isTokenGenerationRequested(), eventId, tc.isBounded());
        TicketCategory ticketCategory = ticketCategoryRepository.getById(category.getValue(), eventId);
        jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), generateTicketsForCategory(ticketCategory, eventId, new Date(), price, 0).toArray(MapSqlParameterSource[]::new));
        if(tc.isTokenGenerationRequested()) {
            insertTokens(ticketCategory);
        }
    }

    private void insertTokens(TicketCategory ticketCategory) {
        insertTokens(ticketCategory, ticketCategory.getMaxTickets());
    }

    private void insertTokens(TicketCategory ticketCategory, int requiredTokens) {
        final MapSqlParameterSource[] args = prepareTokenBulkInsertParameters(ticketCategory, requiredTokens);
        jdbc.batchUpdate(specialPriceRepository.bulkInsert(), args);
    }

    private void updateCategory(TicketCategoryModification tc, BigDecimal vat, boolean vatIncluded, boolean freeOfCharge, ZoneId zoneId, int eventId) {
        final int price = evaluatePrice(tc.getPriceInCents(), vat, vatIncluded, freeOfCharge);
        TicketCategory original = ticketCategoryRepository.getById(tc.getId(), eventId);
        ticketCategoryRepository.update(tc.getId(), tc.getName(), tc.getInception().toZonedDateTime(zoneId),
                tc.getExpiration().toZonedDateTime(zoneId), tc.getMaxTickets(), price, tc.isTokenGenerationRequested(),
                tc.getDescription());
        TicketCategory updated = ticketCategoryRepository.getById(tc.getId(), eventId);
        int addedTickets = updated.getMaxTickets() - original.getMaxTickets();
        handleTicketNumberModification(eventId, original, updated, addedTickets);
        handlePriceChange(eventId, original, updated);
        handleTokenModification(original, updated, addedTickets);
    }

    void handlePriceChange(int eventId, TicketCategory original, TicketCategory updated) {
        if(original.getPriceInCents() == updated.getPriceInCents()) {
            return;
        }
        final List<Integer> ids = ticketRepository.selectTicketInCategoryForUpdate(eventId, updated.getId(), updated.getMaxTickets());
        if(ids.size() < updated.getMaxTickets()) {
            throw new IllegalStateException("not enough tickets");
        }
        ticketRepository.updateTicketPrice(updated.getId(), eventId, updated.getPriceInCents());
    }

    void handleTokenModification(TicketCategory original, TicketCategory updated, int addedTickets) {
        if(original.isAccessRestricted() ^ updated.isAccessRestricted()) {
            if(updated.isAccessRestricted()) {
                final MapSqlParameterSource[] args = prepareTokenBulkInsertParameters(updated, updated.getMaxTickets());
                jdbc.batchUpdate(specialPriceRepository.bulkInsert(), args);
            } else {
                specialPriceRepository.cancelExpiredTokens(updated.getId());
            }
        } else if(updated.isAccessRestricted() && addedTickets != 0) {
            if(addedTickets > 0) {
                jdbc.batchUpdate(specialPriceRepository.bulkInsert(), prepareTokenBulkInsertParameters(updated, addedTickets));
            } else {
                int absDifference = Math.abs(addedTickets);
                final List<Integer> ids = specialPriceRepository.lockTokens(updated.getId(), absDifference);
                Validate.isTrue(ids.size() - absDifference == 0, "not enough tokens");
                specialPriceRepository.cancelTokens(ids);
            }
        }

    }

    void handleTicketNumberModification(int eventId, TicketCategory original, TicketCategory updated, int addedTickets) {
        if(addedTickets == 0) {
            log.debug("ticket handling not required since the number of ticket wasn't modified");
            return;
        }

        log.info("modification detected in ticket number. The difference is: {}", addedTickets);

        if(addedTickets > 0) {
            //the updated category contains more tickets than the older one
            final Stream<MapSqlParameterSource> args = generateTicketsForCategory(updated, eventId, new Date(), updated.getPriceInCents(), original.getMaxTickets());
            jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), args.toArray(MapSqlParameterSource[]::new));
        } else {
            int absDifference = Math.abs(addedTickets);
            final List<Integer> ids = ticketRepository.lockTicketsToInvalidate(eventId, updated.getId(), absDifference);
            int actualDifference = ids.size();
            if(actualDifference < absDifference) {
                throw new IllegalStateException("Cannot invalidate "+absDifference+" tickets. There are only "+actualDifference+" free tickets");
            }
            ticketRepository.invalidateTickets(ids);
        }
    }

    private MapSqlParameterSource[] prepareTokenBulkInsertParameters(TicketCategory tc, int limit) {
        return Stream.generate(MapSqlParameterSource::new)
                .limit(limit)
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

    /**
     * Calculate the price for ticket category edit page
     *
     * @param e
     * @return
     */
    private static UnaryOperator<Integer> categoryPriceCalculator(Event e) {
        return p -> {
            if(e.isFreeOfCharge()) {
                return 0;
            }
            if(e.isVatIncluded()) {
                return MonetaryUtil.addVAT(p, e.getVat());
            }
            return p;
        };
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
        return eventRepository.insert(em.getDescription(), em.getShortName(), em.getWebsiteUrl(), em.getTermsAndConditionsUrl(), em.getImageUrl(), em.getFileBlobId(), em.getLocation(),
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

	public TicketCategory getTicketCategoryById(int id, int eventId) {
		return ticketCategoryRepository.getById(id, eventId);
	}

    public boolean toggleTicketLocking(String eventName, int categoryId, int ticketId, String username) {
        Event event = getSingleEvent(eventName, username);
        checkOwnership(event, username, event.getOrganizationId());
        ticketCategoryRepository.findByEventId(event.getId()).stream().filter(tc -> tc.getId() == categoryId).findFirst().orElseThrow(IllegalArgumentException::new);
        Ticket ticket = ticketRepository.findById(ticketId, categoryId);
        Validate.isTrue(ticketRepository.toggleTicketLocking(ticketId, categoryId, !ticket.getLockedAssignment()) == 1, "unwanted result from ticket locking");
        return true;
    }

    public List<Pair<TicketReservation, OrderSummary>> getPendingPayments(String eventName, String username) {
        EventWithStatistics eventWithStatistics = getSingleEventWithStatistics(eventName, username);
        Event event = eventWithStatistics.getEvent();
        List<String> reservationIds = ticketRepository.findPendingTicketsInCategories(eventWithStatistics.getTicketCategories().stream().map(TicketCategoryWithStatistic::getId).collect(toList()))
                .stream()
                .map(Ticket::getTicketsReservationId)
                .distinct()
                .collect(toList());
        return ticketReservationManager.fetchWaitingForPayment(reservationIds, event);
    }

    public void confirmPayment(String eventName, String reservationId, String username) {
        ticketReservationManager.confirmOfflinePayment(getSingleEvent(eventName, username), reservationId);
    }

    public void confirmPayment(String eventName, String reservationId, BigDecimal paidAmount, String username) {
        Optional<Event> eventOptional = optionally(() -> getSingleEvent(eventName, username));
        Validate.isTrue(eventOptional.isPresent(), "Event not found");
        Event event = eventOptional.get();
        TicketReservation reservation = ticketReservationManager.findByPartialID(reservationId);
        Optional<OrderSummary> optionalOrderSummary = optionally(() -> ticketReservationManager.orderSummaryForReservationId(reservation.getId(), event));
        Validate.isTrue(optionalOrderSummary.isPresent(), "Reservation not found");
        OrderSummary orderSummary = optionalOrderSummary.get();
        Validate.isTrue(MonetaryUtil.centsToUnit(orderSummary.getOriginalTotalPrice().getPriceWithVAT()).compareTo(paidAmount) == 0, "paid price differs from due price");
        ticketReservationManager.confirmOfflinePayment(event, reservation.getId());
    }

    public void deletePendingOfflinePayment(String eventName, String reservationId, String username) {
        ticketReservationManager.deleteOfflinePayment(getSingleEvent(eventName, username), reservationId);
    }
    
    
    public void addPromoCode(String promoCode, int eventId, ZonedDateTime start, ZonedDateTime end, int discountAmount, DiscountType discountType) {
    	promoCodeRepository.addPromoCode(promoCode, eventId, start, end, discountAmount, discountType.toString());
    }
    
    public void deletePromoCode(int promoCodeId) {
    	promoCodeRepository.deletePromoCode(promoCodeId);
    }
    
    public List<PromoCodeDiscountWithFormattedTime> findPromoCodesInEvent(int eventId) {
    	ZoneId zoneId = eventRepository.findById(eventId).getZoneId();
    	return promoCodeRepository.findAllInEvent(eventId).stream().map((p) -> new PromoCodeDiscountWithFormattedTime(p, zoneId)).collect(toList());
    }

    public String getEventUrl(Event event) {
        return StringUtils.removeEnd(configurationManager.getRequiredValue(ConfigurationKeys.BASE_URL), "/") + "/event/" + event.getShortName() + "/";
    }

    public List<Ticket> findAllConfirmedTickets(String eventName, String username) {
        Event event = getSingleEvent(eventName, username);
        checkOwnership(event, username, event.getOrganizationId());
        return ticketRepository.findAllConfirmed(event.getId());
    }

    public List<Event> getActiveEvents() {
        return eventRepository.findAll().stream()
                .filter(e -> e.getEnd().truncatedTo(ChronoUnit.DAYS).plusDays(1).isAfter(ZonedDateTime.now(e.getZoneId()).truncatedTo(ChronoUnit.DAYS)))
                .collect(toList());
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
