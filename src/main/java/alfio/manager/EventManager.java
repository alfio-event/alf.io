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
import alfio.manager.plugin.PluginManager;
import alfio.manager.support.CategoryEvaluator;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.model.Ticket.TicketStatus;
import alfio.model.modification.*;
import alfio.model.modification.EventModification.AdditionalField;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.util.EventUtil.*;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Component
@Transactional
@Log4j2
public class EventManager {

    private static final Predicate<TicketCategory> IS_CATEGORY_BOUNDED = TicketCategory::isBounded;
    private final UserManager userManager;
    private final EventRepository eventRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final EventStatisticsManager eventStatisticsManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final TicketRepository ticketRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final LocationManager locationManager;
    private final NamedParameterJdbcTemplate jdbc;
    private final ConfigurationManager configurationManager;
    private final PluginManager pluginManager;
    private final TicketFieldRepository ticketFieldRepository;
    private final EventDeleterRepository eventDeleterRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final AdditionalServiceTextRepository additionalServiceTextRepository;

    @Autowired
    public EventManager(UserManager userManager,
                        EventRepository eventRepository,
                        EventDescriptionRepository eventDescriptionRepository,
                        EventStatisticsManager eventStatisticsManager,
                        TicketCategoryRepository ticketCategoryRepository,
                        TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository,
                        TicketRepository ticketRepository,
                        SpecialPriceRepository specialPriceRepository,
                        PromoCodeDiscountRepository promoCodeRepository,
                        LocationManager locationManager,
                        NamedParameterJdbcTemplate jdbc,
                        ConfigurationManager configurationManager,
                        PluginManager pluginManager,
                        TicketFieldRepository ticketFieldRepository,
                        EventDeleterRepository eventDeleterRepository,
                        AdditionalServiceRepository additionalServiceRepository, AdditionalServiceTextRepository additionalServiceTextRepository) {
        this.userManager = userManager;
        this.eventRepository = eventRepository;
        this.eventDescriptionRepository = eventDescriptionRepository;
        this.eventStatisticsManager = eventStatisticsManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketCategoryDescriptionRepository = ticketCategoryDescriptionRepository;
        this.ticketRepository = ticketRepository;
        this.specialPriceRepository = specialPriceRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.locationManager = locationManager;
        this.jdbc = jdbc;
        this.configurationManager = configurationManager;
        this.pluginManager = pluginManager;
        this.ticketFieldRepository = ticketFieldRepository;
        this.eventDeleterRepository = eventDeleterRepository;
        this.additionalServiceRepository = additionalServiceRepository;
        this.additionalServiceTextRepository = additionalServiceTextRepository;
    }

    public Event getSingleEvent(String eventName, String username) {
        final Event event = eventRepository.findByShortName(eventName);
        checkOwnership(event, username, event.getOrganizationId());
        return event;
    }

    public Event getSingleEventById(int eventId, String username) {
        final Event event = eventRepository.findById(eventId);
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

    public List<TicketCategory> loadTicketCategories(Event event) {
        return ticketCategoryRepository.findByEventId(event.getId());
    }

    public Organization loadOrganizer(Event event, String username) {
        return userManager.findOrganizationById(event.getOrganizationId(), username);
    }

    /**
     * Internal method used by automated jobs
     * @return
     */
    Organization loadOrganizerUsingSystemPrincipal(Event event) {
        return loadOrganizer(event, UserManager.ADMIN_USERNAME);
    }

    public Event findEventByTicketCategory(TicketCategory ticketCategory) {
        return eventRepository.findById(ticketCategory.getEventId());
    }

    public Event findEventByAdditionalService(AdditionalService additionalService) {
        return eventRepository.findById(additionalService.getEventId());
    }

    public void createEvent(EventModification em) {
        int eventId = insertEvent(em);
        Event event = eventRepository.findById(eventId);
        createOrUpdateEventDescription(eventId, em);
        createAdditionalFields(eventId, em);
        createCategoriesForEvent(em, event);
        createAllTicketsForEvent(eventId, event);
        createAllAdditionalServices(eventId, em.getAdditionalServices(), event.getZoneId());
        initPlugins(event);
    }

    private void createAllAdditionalServices(int eventId, List<EventModification.AdditionalService> additionalServices, ZoneId zoneId) {
        Optional.ofNullable(additionalServices)
            .ifPresent(list -> list.forEach(as -> {
                AffectedRowCountAndKey<Integer> service = additionalServiceRepository.insert(eventId, Optional.ofNullable(as.getPrice()).map(MonetaryUtil::unitToCents).orElse(0), as.isFixPrice(), as.getOrdinal(), as.getAvailableQuantity(), as.getMaxQtyPerOrder(), as.getInception().toZonedDateTime(zoneId), as.getExpiration().toZonedDateTime(zoneId), as.getVat(), as.getVatType());
                as.getTitle().forEach(insertAdditionalServiceDescription(service.getKey()));
                as.getDescription().forEach(insertAdditionalServiceDescription(service.getKey()));
            }));
    }

    private Consumer<EventModification.AdditionalServiceText> insertAdditionalServiceDescription(int serviceId) {
        return t -> additionalServiceTextRepository.insert(serviceId, t.getLocale(), t.getType(), t.getValue());
    }

    private void initPlugins(Event event) {
        pluginManager.installPlugins(event);
    }

    private void createOrUpdateEventDescription(int eventId, EventModification em) {
        eventDescriptionRepository.delete(eventId, EventDescription.EventDescriptionType.DESCRIPTION);


        Set<String> validLocales = ContentLanguage.findAllFor(em.getLocales()).stream()
            .map(ContentLanguage::getLanguage)
            .collect(Collectors.toSet());

        Optional.ofNullable(em.getDescription()).ifPresent(descriptions -> descriptions.forEach((locale, description) -> {
            if (validLocales.contains(locale)) {
                eventDescriptionRepository.insert(eventId, locale, EventDescription.EventDescriptionType.DESCRIPTION, description);
            }
        }));
    }

    private void createAdditionalFields(int eventId, EventModification em) {
        if (!CollectionUtils.isEmpty(em.getTicketFields())) {
           em.getTicketFields().forEach(f -> {
               insertAdditionalField(eventId, f, f.getOrder());
           });
        }
    }

	private void insertAdditionalField(int eventId, AdditionalField f, int order) {
		List<String> restrictedValues = Optional.ofNullable(f.getRestrictedValues()).orElseGet(Collections::emptyList).stream().map(EventModification.RestrictedValue::getValue).collect(Collectors.toList());
		   String serializedRestrictedValues = "select".equals(f.getType()) ? Json.GSON.toJson(restrictedValues) : null;
		   int configurationId = ticketFieldRepository.insertConfiguration(eventId, f.getName(), order, f.getType(), serializedRestrictedValues, f.getMaxLength(), f.getMinLength(), f.isRequired(), TicketFieldConfiguration.Context.ATTENDEE).getKey();
		   f.getDescription().forEach((locale, value) -> {
		       ticketFieldRepository.insertDescription(configurationId, locale, Json.GSON.toJson(value));
		   });
	}

    public void updateEventHeader(Event original, EventModification em, String username) {
        checkOwnership(original, username, em.getOrganizationId());
        int eventId = original.getId();
        final GeolocationResult geolocation = geolocate(em.getLocation());
        final ZoneId zoneId = geolocation.getZoneId();
        final ZonedDateTime begin = em.getBegin().toZonedDateTime(zoneId);
        final ZonedDateTime end = em.getEnd().toZonedDateTime(zoneId);
        eventRepository.updateHeader(eventId, em.getDisplayName(), em.getWebsiteUrl(), em.getExternalUrl(), em.getTermsAndConditionsUrl(),
            em.getImageUrl(), em.getFileBlobId(), em.getLocation(), geolocation.getLatitude(), geolocation.getLongitude(),
            begin, end, geolocation.getTimeZone(), em.getOrganizationId(), em.getLocales());

        createOrUpdateEventDescription(eventId, em);


        if(!original.getBegin().equals(begin) || !original.getEnd().equals(end)) {
            fixOutOfRangeCategories(em, username, zoneId, end);
        }
    }

    public void updateEventPrices(Event original, EventModification em, String username) {
        checkOwnership(original, username, em.getOrganizationId());
        int eventId = original.getId();
        final EventWithStatistics eventWithStatistics = eventStatisticsManager.fillWithStatistics(original);
        int seatsDifference = em.getAvailableSeats() - original.getAvailableSeats();
        if(seatsDifference < 0) {
            int allocatedSeats = eventWithStatistics.getTicketCategories().stream()
                    .filter(TicketCategoryWithStatistic::isBounded)
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
        if(seatsDifference != 0) {
            Event modified = eventRepository.findById(eventId);
            if(seatsDifference > 0) {
                final MapSqlParameterSource[] params = generateEmptyTickets(modified, Date.from(ZonedDateTime.now(modified.getZoneId()).toInstant()), seatsDifference).toArray(MapSqlParameterSource[]::new);
                jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);
            } else {
                List<Integer> ids = ticketRepository.selectNotAllocatedTicketsForUpdate(eventId, Math.abs(seatsDifference), singletonList(TicketStatus.FREE.name()));
                Validate.isTrue(ids.size() == Math.abs(seatsDifference), "cannot lock enough tickets for deletion.");
                int invalidatedTickets = ticketRepository.invalidateTickets(ids);
                Validate.isTrue(ids.size() == invalidatedTickets, String.format("error during ticket invalidation: expected %d, got %d", ids.size(), invalidatedTickets));
            }
        }
    }

    public void insertCategory(int eventId, TicketCategoryModification tcm, String username) {
        final Event event = eventRepository.findById(eventId);
        checkOwnership(event, username, event.getOrganizationId());
        int sum = ticketCategoryRepository.findByEventId(eventId).stream()
                .filter(IS_CATEGORY_BOUNDED)
                .mapToInt(TicketCategory::getMaxTickets)
                .sum();
        int notBoundedTickets = ticketRepository.countNotSoldTicketsForUnbounded(eventId);
        int requestedTickets = tcm.isBounded() ? tcm.getMaxTickets() : 1;
        Validate.isTrue(sum + requestedTickets <= event.getAvailableSeats(), "Not enough seats");
        Validate.isTrue(requestedTickets <= notBoundedTickets, "All the tickets have already been assigned to a category. Try increasing the total seats number.");
        Validate.isTrue(tcm.getExpiration().toZonedDateTime(event.getZoneId()).isBefore(event.getEnd()), "expiration must be before the end of the event");
        insertCategory(tcm, event);
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
        if(tcm.isBounded() ^ existing.isBounded()) {
            throw new IllegalStateException("Bounded flag modification not yet implemented.");
        }
        updateCategory(tcm, event.getVat(), event.isVatIncluded(), event.isFreeOfCharge(), event.getZoneId(), event);
    }

    void fixOutOfRangeCategories(EventModification em, String username, ZoneId zoneId, ZonedDateTime end) {
        eventStatisticsManager.getSingleEventWithStatistics(em.getShortName(), username).getTicketCategories().stream()
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
        reallocateTickets(eventStatisticsManager.loadTicketCategoryWithStats(srcCategoryId, event), Optional.of(ticketCategoryRepository.getById(targetCategoryId, event.getId())), event);
    }

    void reallocateTickets(TicketCategoryWithStatistic src, Optional<TicketCategory> target, Event event) {
        int notSoldTickets = src.getNotSoldTickets();
        if(notSoldTickets == 0) {
            log.info("since all the ticket have been sold, ticket moving is not needed anymore.");
            return;
        }
        List<Integer> lockedTickets = ticketRepository.selectTicketInCategoryForUpdate(event.getId(), src.getId(), notSoldTickets, singletonList(TicketStatus.FREE.name()));
        int locked = lockedTickets.size();
        if(locked != notSoldTickets) {
            throw new IllegalStateException(String.format("Expected %d free tickets, got %d.", notSoldTickets, locked));
        }
        ticketCategoryRepository.updateSeatsAvailability(src.getId(), src.getSoldTickets());
        if(target.isPresent()) {
            TicketCategory targetCategory = target.get();
            ticketCategoryRepository.updateSeatsAvailability(targetCategory.getId(), targetCategory.getMaxTickets() + locked);
            ticketRepository.moveToAnotherCategory(lockedTickets, targetCategory.getId(), targetCategory.getPriceInCents(), targetCategory.getPriceInCents());
            insertTokens(targetCategory, locked);
        } else {
            int result = ticketRepository.unbindTicketsFromCategory(event.getId(), src.getId(), lockedTickets);
            Validate.isTrue(result == locked, String.format("Expected %d modified tickets, got %d.", locked, result));
        }
        specialPriceRepository.cancelExpiredTokens(src.getId());
    }

    public void unbindTickets(String eventName, int categoryId, String username) {
        Event event = getSingleEvent(eventName, username);
        Validate.isTrue(ticketCategoryRepository.countUnboundedCategoriesByEventId(event.getId()) > 0, "cannot unbind tickets: there aren't any unbounded categories");
        TicketCategoryWithStatistic ticketCategory = eventStatisticsManager.loadTicketCategoryWithStats(categoryId, event);
        Validate.isTrue(ticketCategory.isBounded(), "cannot unbind tickets from an unbounded category!");
        reallocateTickets(ticketCategory, Optional.<TicketCategory>empty(), event);
    }

    MapSqlParameterSource[] prepareTicketsBulkInsertParameters(int eventId,
                                                               ZonedDateTime creation,
                                                               Event event,
                                                               int regularPrice) {

        //FIXME: the date should be inserted as ZonedDateTime !
        Date creationDate = Date.from(creation.toInstant());

        List<TicketCategory> categories = ticketCategoryRepository.findByEventId(event.getId());
        Stream<MapSqlParameterSource> boundedTickets = categories.stream()
                .filter(IS_CATEGORY_BOUNDED)
                .flatMap(tc -> generateTicketsForCategory(tc, event, creationDate, regularPrice, 0));
        int existingTickets = categories.stream()
                .filter(IS_CATEGORY_BOUNDED)
                .mapToInt(TicketCategory::getMaxTickets)
                .sum();
        if(existingTickets >= event.getAvailableSeats()) {
            return boundedTickets.toArray(MapSqlParameterSource[]::new);
        }

        return Stream.concat(boundedTickets, generateEmptyTickets(event, creationDate, event.getAvailableSeats() - existingTickets)).toArray(MapSqlParameterSource[]::new);
    }

    private Stream<MapSqlParameterSource> generateTicketsForCategory(TicketCategory tc,
                                                                     Event event,
                                                                     Date creationDate,
                                                                     int regularPrice,
                                                                     int existing) {
        Optional<TicketCategory> filteredTC = Optional.of(tc).filter(TicketCategory::isBounded);
        int missingTickets = filteredTC.map(c -> Math.abs(c.getMaxTickets() - existing)).orElseGet(() -> event.getAvailableSeats() - existing);
        return generateStreamForTicketCreation(missingTickets)
                    .map(ps -> buildTicketParams(event.getId(), creationDate, filteredTC, regularPrice, tc.getPriceInCents(), ps));
    }

    private void createCategoriesForEvent(EventModification em, Event event) {
        boolean freeOfCharge = em.isFreeOfCharge();
        boolean vatIncluded = em.isVatIncluded();
        ZoneId zoneId = TimeZone.getTimeZone(event.getTimeZone()).toZoneId();
        int eventId = event.getId();

        int requestedSeats = em.getTicketCategories().stream()
                .filter(TicketCategoryModification::isBounded)
                .mapToInt(TicketCategoryModification::getMaxTickets)
                .sum();
        int notAssignedTickets = em.getAvailableSeats() - requestedSeats;
        Validate.isTrue(notAssignedTickets >= 0, "Total categories' seats cannot be more than the actual event seats");
        Validate.isTrue(notAssignedTickets > 0 || em.getTicketCategories().stream().noneMatch(tc -> !tc.isBounded()), "Cannot add an unbounded category if there aren't any free tickets");

        em.getTicketCategories().stream().forEach(tc -> {
            final int price = evaluatePrice(tc.getPriceInCents(), em.getVat(), vatIncluded, freeOfCharge);
            final int maxTickets = tc.isBounded() ? tc.getMaxTickets() : 0;
            final AffectedRowCountAndKey<Integer> category = ticketCategoryRepository.insert(tc.getInception().toZonedDateTime(zoneId),
                tc.getExpiration().toZonedDateTime(zoneId), tc.getName(), maxTickets, price, tc.isTokenGenerationRequested(), eventId, tc.isBounded());

            insertOrUpdateTicketCategoryDescription(category.getKey(), tc, event);

            if (tc.isTokenGenerationRequested()) {
                final TicketCategory ticketCategory = ticketCategoryRepository.getById(category.getKey(), event.getId());
                final MapSqlParameterSource[] args = prepareTokenBulkInsertParameters(ticketCategory, ticketCategory.getMaxTickets());
                jdbc.batchUpdate(specialPriceRepository.bulkInsert(), args);
            }
        });
    }

    private void insertCategory(TicketCategoryModification tc, Event event) {
        ZoneId zoneId = event.getZoneId();
        int eventId = event.getId();
        final int price = evaluatePrice(tc.getPriceInCents(), event.getVat(), event.isVatIncluded(), event.isFreeOfCharge());
        final AffectedRowCountAndKey<Integer> category = ticketCategoryRepository.insert(tc.getInception().toZonedDateTime(zoneId),
            tc.getExpiration().toZonedDateTime(zoneId), tc.getName(), tc.isBounded() ? tc.getMaxTickets() : 0, price, tc.isTokenGenerationRequested(), eventId, tc.isBounded());
        TicketCategory ticketCategory = ticketCategoryRepository.getById(category.getKey(), eventId);
        if(tc.isBounded()) {
            List<Integer> lockedTickets = ticketRepository.selectNotAllocatedTicketsForUpdate(eventId, ticketCategory.getMaxTickets(), singletonList(TicketStatus.FREE.name()));
            jdbc.batchUpdate(ticketRepository.bulkTicketUpdate(), lockedTickets.stream().map(id -> new MapSqlParameterSource("id", id).addValue("categoryId", ticketCategory.getId()).addValue("originalPrice", ticketCategory.getPriceInCents()).addValue("paidPrice", ticketCategory.getPriceInCents())).toArray(MapSqlParameterSource[]::new));
            if(tc.isTokenGenerationRequested()) {
                insertTokens(ticketCategory);
            }
        }

        insertOrUpdateTicketCategoryDescription(category.getKey(), tc, event);

    }

    private void insertTokens(TicketCategory ticketCategory) {
        insertTokens(ticketCategory, ticketCategory.getMaxTickets());
    }

    private void insertTokens(TicketCategory ticketCategory, int requiredTokens) {
        final MapSqlParameterSource[] args = prepareTokenBulkInsertParameters(ticketCategory, requiredTokens);
        jdbc.batchUpdate(specialPriceRepository.bulkInsert(), args);
    }

    private void insertOrUpdateTicketCategoryDescription(int tcId, TicketCategoryModification tc, Event event) {
        ticketCategoryDescriptionRepository.delete(tcId);

        Set<String> eventLang = ContentLanguage.findAllFor(event.getLocales()).stream().map(ContentLanguage::getLanguage).collect(Collectors.toSet());

        Optional.ofNullable(tc.getDescription()).ifPresent(descriptions -> descriptions.forEach((locale, desc) -> {
            if (eventLang.contains(locale)) {
                ticketCategoryDescriptionRepository.insert(tcId, locale, desc);
            }
        }));
    }

    private void updateCategory(TicketCategoryModification tc, BigDecimal vat, boolean vatIncluded, boolean freeOfCharge, ZoneId zoneId, Event event) {
        int eventId = event.getId();
        final int price = evaluatePrice(tc.getPriceInCents(), vat, vatIncluded, freeOfCharge);
        TicketCategory original = ticketCategoryRepository.getById(tc.getId(), eventId);
        ticketCategoryRepository.update(tc.getId(), tc.getName(), tc.getInception().toZonedDateTime(zoneId),
                tc.getExpiration().toZonedDateTime(zoneId), tc.getMaxTickets(), price, tc.isTokenGenerationRequested());
        TicketCategory updated = ticketCategoryRepository.getById(tc.getId(), eventId);
        int addedTickets = updated.getMaxTickets() - original.getMaxTickets();
        handleTicketNumberModification(event, original, updated, addedTickets);
        handlePriceChange(eventId, original, updated);
        handleTokenModification(original, updated, addedTickets);

        insertOrUpdateTicketCategoryDescription(tc.getId(), tc, event);
    }

    void handlePriceChange(int eventId, TicketCategory original, TicketCategory updated) {
        if(original.getPriceInCents() == updated.getPriceInCents()) {
            return;
        }
        final List<Integer> ids = ticketRepository.selectTicketInCategoryForUpdate(eventId, updated.getId(), updated.getMaxTickets(), singletonList(TicketStatus.FREE.name()));
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

    void handleTicketNumberModification(Event event, TicketCategory original, TicketCategory updated, int addedTickets) {
        if(addedTickets == 0) {
            log.debug("ticket handling not required since the number of ticket wasn't modified");
            return;
        }

        log.info("modification detected in ticket number. The difference is: {}", addedTickets);

        if(addedTickets > 0) {
            //the updated category contains more tickets than the older one
            List<Integer> lockedTickets = ticketRepository.selectNotAllocatedTicketsForUpdate(event.getId(), addedTickets, singletonList(TicketStatus.FREE.name()));
            jdbc.batchUpdate(ticketRepository.bulkTicketUpdate(), lockedTickets.stream()
                    .map(id -> new MapSqlParameterSource("id", id)
                            .addValue("categoryId", updated.getId())
                            .addValue("originalPrice", updated.getPriceInCents())
                            .addValue("paidPrice", updated.getPriceInCents()))
                    .toArray(MapSqlParameterSource[]::new));
        } else {
            int absDifference = Math.abs(addedTickets);
            final List<Integer> ids = ticketRepository.lockTicketsToInvalidate(event.getId(), updated.getId(), absDifference);
            int actualDifference = ids.size();
            if(actualDifference < absDifference) {
                throw new IllegalStateException("Cannot invalidate "+absDifference+" tickets. There are only "+actualDifference+" free tickets");
            }
            ticketRepository.invalidateTickets(ids);
            final MapSqlParameterSource[] params = generateEmptyTickets(event, Date.from(ZonedDateTime.now(event.getZoneId()).toInstant()), Math.abs(addedTickets)).toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);
        }
    }

    private MapSqlParameterSource[] prepareTokenBulkInsertParameters(TicketCategory tc, int limit) {
        return generateStreamForTicketCreation(limit)
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

    private int insertEvent(EventModification em) {
        String paymentProxies = collectPaymentProxies(em);
        int actualPrice = em.isInternal() ? evaluatePrice(em.getPriceInCents(), em.getVat(), em.isVatIncluded(), em.isFreeOfCharge()) : 0;
        BigDecimal vat = !em.isInternal() || em.isFreeOfCharge() ? BigDecimal.ZERO : em.getVat();
        String privateKey = UUID.randomUUID().toString();
        final GeolocationResult result = geolocate(em.getLocation());
        return eventRepository.insert(em.getShortName(), em.getEventType(), em.getDisplayName(), em.getWebsiteUrl(), em.getExternalUrl(), em.isInternal() ? em.getTermsAndConditionsUrl() : "",
            em.getImageUrl(), em.getFileBlobId(), em.getLocation(), result.getLatitude(), result.getLongitude(), em.getBegin().toZonedDateTime(result.getZoneId()),
            em.getEnd().toZonedDateTime(result.getZoneId()), result.getTimeZone(), actualPrice, em.getCurrency(), em.getAvailableSeats(), em.isInternal() && em.isVatIncluded(),
            vat, paymentProxies, privateKey, em.getOrganizationId(), em.getLocales()).getKey();
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

    public AdditionalService getAdditionalServiceById(int id, int eventId) {
        return additionalServiceRepository.getById(id, eventId);
    }

    public boolean toggleTicketLocking(String eventName, int categoryId, int ticketId, String username) {
        Event event = getSingleEvent(eventName, username);
        checkOwnership(event, username, event.getOrganizationId());
        ticketCategoryRepository.findByEventId(event.getId()).stream().filter(tc -> tc.getId() == categoryId).findFirst().orElseThrow(IllegalArgumentException::new);
        Ticket ticket = ticketRepository.findById(ticketId, categoryId);
        Validate.isTrue(ticketRepository.toggleTicketLocking(ticketId, categoryId, !ticket.getLockedAssignment()) == 1, "unwanted result from ticket locking");
        return true;
    }

    public void addPromoCode(String promoCode, int eventId, ZonedDateTime start, ZonedDateTime end, int discountAmount, DiscountType discountType, List<Integer> categoriesId) {
        Validate.isTrue(promoCode.length() >= 7, "min length is 7 chars");
        if(DiscountType.PERCENTAGE == discountType) {
            Validate.inclusiveBetween(0, 100, discountAmount, "percentage discount must be between 0 and 100");
        }
        if(DiscountType.FIXED_AMOUNT == discountType) {
            Validate.isTrue(discountAmount >= 0, "fixed discount amount cannot be less than zero");
        }

        promoCodeRepository.addPromoCode(promoCode, eventId, start, end, discountAmount, discountType.toString(), Json.GSON.toJson(categoriesId));
    }
    
    public void deletePromoCode(int promoCodeId) {
        promoCodeRepository.deletePromoCode(promoCodeId);
    }

    public void updatePromoCode(String promoCodeName, int eventId, ZonedDateTime start, ZonedDateTime end) {
        promoCodeRepository.update(eventId, promoCodeName, start, end);
    }
    
    public List<PromoCodeDiscountWithFormattedTime> findPromoCodesInEvent(int eventId) {
        ZoneId zoneId = eventRepository.findById(eventId).getZoneId();
        return promoCodeRepository.findAllInEvent(eventId).stream().map((p) -> new PromoCodeDiscountWithFormattedTime(p, zoneId)).collect(toList());
    }

    public String getEventUrl(Event event) {
        return StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL)), "/") + "/event/" + event.getShortName() + "/";
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

    public Function<Ticket, Boolean> checkTicketCancellationPrerequisites() {
        return CategoryEvaluator.ticketCancellationAvailabilityChecker(ticketCategoryRepository);
    }

    public void updateTicketFieldDescriptions(Map<String, TicketFieldDescriptionModification> descriptions) {
        descriptions.forEach((locale, value) -> {
            String description = Json.GSON.toJson(value.getDescription());
            if(0 == ticketFieldRepository.updateDescription(value.getTicketFieldConfigurationId(), locale, description)) {
                ticketFieldRepository.insertDescription(value.getTicketFieldConfigurationId(), locale, description);
            }
        });
    }
    
	public void addAdditionalField(int eventId, AdditionalField field) {
		Integer order = ticketFieldRepository.findMaxOrderValue(eventId);
		insertAdditionalField(eventId, field, order == null ? 0 : order + 1);
	}
	
	public void deleteAdditionalField(int ticketFieldConfigurationId) {
		ticketFieldRepository.deleteValues(ticketFieldConfigurationId);
		ticketFieldRepository.deleteDescription(ticketFieldConfigurationId);
		ticketFieldRepository.deleteField(ticketFieldConfigurationId);
	}
	
	public void swapAdditionalFieldPosition(int eventId, int id1, int id2) {
		TicketFieldConfiguration field1 = ticketFieldRepository.findById(id1);
		TicketFieldConfiguration field2 = ticketFieldRepository.findById(id2);
		Assert.isTrue(eventId == field1.getEventId());
		Assert.isTrue(eventId == field2.getEventId());
		ticketFieldRepository.updateFieldOrder(id1, field2.getOrder());
		ticketFieldRepository.updateFieldOrder(id2, field1.getOrder());
	}
	
	public void deleteEvent(int eventId, String username) {
		final Event event = eventRepository.findById(eventId);
		checkOwnership(event, username, event.getOrganizationId());
		
		eventDeleterRepository.deleteWaitingQueue(eventId);
		
		eventDeleterRepository.deletePluginLog(eventId);
		eventDeleterRepository.deletePluginConfiguration(eventId);
		
		eventDeleterRepository.deleteConfigurationEvent(eventId);
		eventDeleterRepository.deleteConfigurationTicketCategory(eventId);
		
		eventDeleterRepository.deleteEmailMessage(eventId);
		
		eventDeleterRepository.deleteTicketFieldValue(eventId);
		eventDeleterRepository.deleteFieldDescription(eventId);

        eventDeleterRepository.deleteAdditionalServiceFieldValue(eventId);
        eventDeleterRepository.deleteAdditionalServiceDescriptions(eventId);
        eventDeleterRepository.deleteAdditionalServiceItems(eventId);

		eventDeleterRepository.deleteTicketFieldConfiguration(eventId);

        eventDeleterRepository.deleteAdditionalServices(eventId);

		eventDeleterRepository.deleteEventMigration(eventId);
		eventDeleterRepository.deleteSponsorScan(eventId);
		eventDeleterRepository.deleteTicket(eventId);
		eventDeleterRepository.resetTicketReservation(eventId);
		
		eventDeleterRepository.deletePromoCode(eventId);
		eventDeleterRepository.deleteTicketCategoryText(eventId);
		eventDeleterRepository.deleteTicketCategory(eventId);
		eventDeleterRepository.deleteEventDescription(eventId);
		
		eventDeleterRepository.deleteEvent(eventId);
		
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
