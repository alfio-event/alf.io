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

import alfio.config.Initializer;
import alfio.manager.support.CategoryEvaluator;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.model.Ticket.TicketStatus;
import alfio.model.TicketFieldConfiguration.Context;
import alfio.model.modification.EventModification;
import alfio.model.modification.EventModification.AdditionalField;
import alfio.model.modification.PromoCodeDiscountWithFormattedTime;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketFieldDescriptionModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.flywaydb.core.Flyway;
import org.springframework.core.env.Environment;
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

import static alfio.model.modification.DateTimeModification.toZonedDateTime;
import static alfio.util.EventUtil.*;
import static alfio.util.OptionalWrapper.optionally;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Component
@Transactional
@Log4j2
@AllArgsConstructor
public class EventManager {

    private static final Predicate<TicketCategory> IS_CATEGORY_BOUNDED = TicketCategory::isBounded;
    private final UserManager userManager;
    private final EventRepository eventRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final TicketRepository ticketRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ConfigurationManager configurationManager;
    private final TicketFieldRepository ticketFieldRepository;
    private final EventDeleterRepository eventDeleterRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final AdditionalServiceTextRepository additionalServiceTextRepository;
    private final Flyway flyway;
    private final Environment environment;
    private final OrganizationRepository organizationRepository;
    private final AuditingRepository auditingRepository;
    private final ExtensionManager extensionManager;


    public Event getSingleEvent(String eventName, String username) {
        return getOptionalByName(eventName, username).orElseThrow(IllegalStateException::new);
    }

    public Optional<Event> getOptionalByName(String eventName, String username) {
        return eventRepository.findOptionalByShortName(eventName)
            .filter(checkOwnership(username, organizationRepository));
    }

    public Event getSingleEventById(int eventId, String username) {
        return optionally(() -> eventRepository.findById(eventId))
            .filter(checkOwnership(username, organizationRepository))
            .orElseThrow(IllegalStateException::new);
    }

    public void checkOwnership(Event event, String username, int organizationId) {
        Validate.isTrue(organizationId == event.getOrganizationId(), "invalid organizationId");
        Validate.isTrue(checkOwnership(username, organizationRepository).test(event), "User is not authorized");
    }

    public static Predicate<Event> checkOwnership(String username, OrganizationRepository organizationRepository) {
        return event -> organizationRepository.findOrganizationForUser(username, event.getOrganizationId()).isPresent();
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
        createAllAdditionalServices(eventId, em.getAdditionalServices(), event.getZoneId());
        createAdditionalFields(event, em);
        createCategoriesForEvent(em, event);
        createAllTicketsForEvent(event, em);
        extensionManager.handleEventCreation(event);
    }

    public void toggleActiveFlag(int id, String username, boolean activate) {
        Event event = eventRepository.findById(id);
        checkOwnership(event, username, event.getOrganizationId());

        if(environment.acceptsProfiles(Initializer.PROFILE_DEMO)) {
            throw new IllegalStateException("demo mode");
        }
        Event.Status status = activate ? Event.Status.PUBLIC : Event.Status.DRAFT;
        eventRepository.updateEventStatus(id, status);
        extensionManager.handleEventStatusChange(event, status);
    }

    private void createAllAdditionalServices(int eventId, List<EventModification.AdditionalService> additionalServices, ZoneId zoneId) {
        Optional.ofNullable(additionalServices)
            .ifPresent(list -> list.forEach(as -> {
                AffectedRowCountAndKey<Integer> service = additionalServiceRepository.insert(eventId,
                    Optional.ofNullable(as.getPrice()).map(MonetaryUtil::unitToCents).orElse(0),
                    as.isFixPrice(),
                    as.getOrdinal(),
                    as.getAvailableQuantity(),
                    as.getMaxQtyPerOrder(),
                    as.getInception().toZonedDateTime(zoneId),
                    as.getExpiration().toZonedDateTime(zoneId),
                    as.getVat(),
                    as.getVatType(),
                    as.getType(),
                    as.getSupplementPolicy());
                as.getTitle().forEach(insertAdditionalServiceDescription(service.getKey()));
                as.getDescription().forEach(insertAdditionalServiceDescription(service.getKey()));
            }));
    }

    private Consumer<EventModification.AdditionalServiceText> insertAdditionalServiceDescription(int serviceId) {
        return t -> additionalServiceTextRepository.insert(serviceId, t.getLocale(), t.getType(), t.getValue());
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

    private void createAdditionalFields(Event event, EventModification em) {
        if (!CollectionUtils.isEmpty(em.getTicketFields())) {
            em.getTicketFields().forEach(f -> {
                insertAdditionalField(event, f, f.getOrder());
            });
        }
    }

    private static String toSerializedRestrictedValues(EventModification.WithRestrictedValues f) {
        List<String> restrictedValues = Optional.ofNullable(f.getRestrictedValuesAsString()).orElseGet(Collections::emptyList);
        return "select".equals(f.getType()) ? Json.GSON.toJson(restrictedValues) : null;
    }

	private void insertAdditionalField(Event event, AdditionalField f, int order) {
        String serializedRestrictedValues = toSerializedRestrictedValues(f);
        Optional<EventModification.AdditionalService> linkedAdditionalService = Optional.ofNullable(f.getLinkedAdditionalService());
        Integer additionalServiceId = linkedAdditionalService.map(as -> Optional.ofNullable(as.getId()).orElseGet(() -> findAdditionalService(event, as))).orElse(-1);
        Context context = linkedAdditionalService.isPresent() ? Context.ADDITIONAL_SERVICE : Context.ATTENDEE;
		int configurationId = ticketFieldRepository.insertConfiguration(event.getId(), f.getName(), order, f.getType(), serializedRestrictedValues, f.getMaxLength(), f.getMinLength(), f.isRequired(), context, additionalServiceId).getKey();
		f.getDescription().forEach((locale, value) -> ticketFieldRepository.insertDescription(configurationId, locale, Json.GSON.toJson(value)));
	}

    public void updateAdditionalField(int id, EventModification.UpdateAdditionalField f) {
        String serializedRestrictedValues = toSerializedRestrictedValues(f);
        ticketFieldRepository.updateRequiredAndRestrictedValues(id, f.isRequired(), serializedRestrictedValues);
        f.getDescription().forEach((locale, value) -> {
            String val = Json.GSON.toJson(value.getDescription());
            if(0 == ticketFieldRepository.updateDescription(id, locale, val)) {
                ticketFieldRepository.insertDescription(id, locale, val);
            }
        });
    }

    private Integer findAdditionalService(Event event, EventModification.AdditionalService as) {
        ZoneId utc = ZoneId.of("UTC");
        int eventId = event.getId();
        String checksum = new AdditionalService(0, eventId, as.isFixPrice(), as.getOrdinal(), as.getAvailableQuantity(),
            as.getMaxQtyPerOrder(),
            as.getInception().toZonedDateTime(event.getZoneId()).withZoneSameInstant(utc),
            as.getExpiration().toZonedDateTime(event.getZoneId()).withZoneSameInstant(utc),
            as.getVat(),
            as.getVatType(),
            Optional.ofNullable(as.getPrice()).map(MonetaryUtil::unitToCents).orElse(0),
            as.getType(),
            as.getSupplementPolicy()).getChecksum();
        return additionalServiceRepository.loadAllForEvent(eventId).stream().filter(as1 -> as1.getChecksum().equals(checksum)).findFirst().map(AdditionalService::getId).orElse(null);
    }

    public void updateEventHeader(Event original, EventModification em, String username) {
        checkOwnership(original, username, em.getOrganizationId());
        int eventId = original.getId();

        final ZoneId zoneId = ZoneId.of(em.getZoneId());
        final ZonedDateTime begin = em.getBegin().toZonedDateTime(zoneId);
        final ZonedDateTime end = em.getEnd().toZonedDateTime(zoneId);
        eventRepository.updateHeader(eventId, em.getDisplayName(), em.getWebsiteUrl(), em.getExternalUrl(), em.getTermsAndConditionsUrl(),
            em.getPrivacyPolicyUrl(), em.getImageUrl(), em.getFileBlobId(), em.getLocation(), em.getLatitude(), em.getLongitude(),
            begin, end, em.getZoneId(), em.getOrganizationId(), em.getLocales());

        createOrUpdateEventDescription(eventId, em);


        if(!original.getBegin().equals(begin) || !original.getEnd().equals(end)) {
            fixOutOfRangeCategories(em, username, zoneId, end);
        }
    }

    public void updateEventPrices(Event original, EventModification em, String username) {
        Validate.notNull(em.getAvailableSeats(), "Available Seats cannot be null");
        checkOwnership(original, username, em.getOrganizationId());
        int eventId = original.getId();
        int seatsDifference = em.getAvailableSeats() - eventRepository.countExistingTickets(original.getId());
        if(seatsDifference < 0) {
            int allocatedSeats = ticketCategoryRepository.findByEventId(original.getId()).stream()
                    .filter(TicketCategory::isBounded)
                    .mapToInt(TicketCategory::getMaxTickets)
                    .sum();
            if(em.getAvailableSeats() < allocatedSeats) {
                throw new IllegalArgumentException(format("cannot reduce max tickets to %d. There are already %d tickets allocated. Try updating categories first.", em.getAvailableSeats(), allocatedSeats));
            }
        }

        String paymentProxies = collectPaymentProxies(em);
        BigDecimal vat = em.isFreeOfCharge() ? BigDecimal.ZERO : em.getVatPercentage();
        eventRepository.updatePrices(em.getCurrency(), em.getAvailableSeats(), em.isVatIncluded(), vat, paymentProxies, eventId, em.getVatStatus(), em.getPriceInCents());
        if(seatsDifference != 0) {
            Event modified = eventRepository.findById(eventId);
            if(seatsDifference > 0) {
                final MapSqlParameterSource[] params = generateEmptyTickets(modified, Date.from(ZonedDateTime.now(modified.getZoneId()).toInstant()), seatsDifference, TicketStatus.RELEASED).toArray(MapSqlParameterSource[]::new);
                jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);
            } else {
                List<Integer> ids = ticketRepository.selectNotAllocatedTicketsForUpdate(eventId, Math.abs(seatsDifference), singletonList(TicketStatus.FREE.name()));
                Validate.isTrue(ids.size() == Math.abs(seatsDifference), "cannot lock enough tickets for deletion.");
                int invalidatedTickets = ticketRepository.invalidateTickets(ids);
                Validate.isTrue(ids.size() == invalidatedTickets, String.format("error during ticket invalidation: expected %d, got %d", ids.size(), invalidatedTickets));
            }
        }
    }

    /**
     * This method has been modified to use the new Result<T> mechanism.
     * It will be replaced by {@link #insertCategory(Event, TicketCategoryModification, String)} in the next releases
     */
    public void insertCategory(int eventId, TicketCategoryModification tcm, String username) {
        final Event event = eventRepository.findById(eventId);
        Result<Integer> result = insertCategory(event, tcm, username);
        failIfError(result);
    }

    public Result<Integer> insertCategory(Event event, TicketCategoryModification tcm, String username) {
        return optionally(() -> {
            checkOwnership(event, username, event.getOrganizationId());
            return true;
        }).map(b -> {
            int eventId = event.getId();
            int sum = ticketCategoryRepository.getTicketAllocation(eventId);
            int notAllocated = ticketRepository.countNotAllocatedFreeAndReleasedTicket(eventId);
            int requestedTickets = tcm.isBounded() ? tcm.getMaxTickets() : 1;
            return new Result.Builder<Integer>()
                .checkPrecondition(() -> sum + requestedTickets <= eventRepository.countExistingTickets(eventId), ErrorCode.CategoryError.NOT_ENOUGH_SEATS)
                .checkPrecondition(() -> requestedTickets <= notAllocated, ErrorCode.CategoryError.ALL_TICKETS_ASSIGNED)
                .checkPrecondition(() -> tcm.getExpiration().toZonedDateTime(event.getZoneId()).isBefore(event.getEnd()), ErrorCode.CategoryError.EXPIRATION_AFTER_EVENT_END)
                .build(() -> insertCategory(tcm, event));
        }).orElseGet(() -> Result.error(ErrorCode.EventError.ACCESS_DENIED));
    }

    /**
     * This method has been modified to use the new Result<T> mechanism.
     * It will be replaced by {@link #updateCategory(int, Event, TicketCategoryModification, String)} in the next releases
     */
    public void updateCategory(int categoryId, int eventId, TicketCategoryModification tcm, String username) {
        final Event event = eventRepository.findById(eventId);
        checkOwnership(event, username, event.getOrganizationId());
        Result<TicketCategory> result = updateCategory(categoryId, event, tcm, username);
        failIfError(result);
    }

    private <T> void failIfError(Result<T> result) {
        if(!result.isSuccess()) {
            Optional<ErrorCode> firstError = result.getErrors().stream().findFirst();
            if(firstError.isPresent()) {
                throw new IllegalArgumentException(firstError.get().getDescription());
            }
            throw new IllegalArgumentException("unknown error");
        }
    }

    Result<TicketCategory> updateCategory(int categoryId, Event event, TicketCategoryModification tcm,
                                          String username, boolean resetTicketsToFree) {
        checkOwnership(event, username, event.getOrganizationId());
        int eventId = event.getId();
        return Optional.of(ticketCategoryRepository.getById(categoryId)).filter(tc -> tc.getId() == categoryId)
            .map(existing -> new Result.Builder<TicketCategory>()
                    .checkPrecondition(() -> tcm.getExpiration().toZonedDateTime(event.getZoneId()).isBefore(event.getEnd()), ErrorCode.CategoryError.EXPIRATION_AFTER_EVENT_END)
                    .checkPrecondition(() -> tcm.getMaxTickets() - existing.getMaxTickets() + ticketRepository.countAllocatedTicketsForEvent(eventId) <= eventRepository.countExistingTickets(eventId), ErrorCode.CategoryError.NOT_ENOUGH_SEATS)
                    .checkPrecondition(() -> tcm.isTokenGenerationRequested() == existing.isAccessRestricted() || ticketRepository.countConfirmedAndPendingTickets(eventId, categoryId) == 0, ErrorCode.custom("", "cannot update category: there are tickets already sold."))
                    .checkPrecondition(() -> tcm.isBounded() == existing.isBounded() || ticketRepository.countPendingOrReleasedForCategory(eventId, existing.getId()) == 0, ErrorCode.custom("", "It is not safe to change allocation strategy right now because there are pending reservations."))
                    .checkPrecondition(() -> !existing.isAccessRestricted() || tcm.isBounded() == existing.isAccessRestricted(), ErrorCode.custom("", "Dynamic allocation is not compatible with restricted access"))
                    .checkPrecondition(() -> {
                        // see https://github.com/exteso/alf.io/issues/335
                        // handle the case when the user try to shrink a category with tokens that are already sent
                        // we should fail if there are not enough free token left
                        int addedTicket = tcm.getMaxTickets() - existing.getMaxTickets();
                        if(addedTicket < 0 &&
                            existing.isAccessRestricted() &&
                            specialPriceRepository.countNotSentToken(categoryId) < Math.abs(addedTicket)) {
                            return false;
                        } else {
                            return true;
                        }
                    }, ErrorCode.CategoryError.NOT_ENOUGH_FREE_TOKEN_FOR_SHRINK)
                    .checkPrecondition(() -> {
                        if(tcm.isBounded() && !existing.isBounded()) {
                            int newSize = tcm.getMaxTickets();
                            int confirmed = ticketRepository.countConfirmedForCategory(eventId, existing.getId());
                            return newSize >= confirmed;
                        } else {
                            return true;
                        }
                    }, ErrorCode.custom("", "Not enough tickets"))
                    .build(() -> {
                        updateCategory(tcm, event.isFreeOfCharge(), event.getZoneId(), event, resetTicketsToFree);
                        return ticketCategoryRepository.getByIdAndActive(categoryId, eventId);
                    })
            )
            .orElseGet(() -> Result.error(ErrorCode.CategoryError.NOT_FOUND));
    }

    Result<TicketCategory> updateCategory(int categoryId, Event event, TicketCategoryModification tcm, String username) {
        return updateCategory(categoryId, event, tcm, username, false);
    }

    void fixOutOfRangeCategories(EventModification em, String username, ZoneId zoneId, ZonedDateTime end) {
        Event event = getSingleEvent(em.getShortName(), username);
        ticketCategoryRepository.findAllTicketCategories(event.getId()).stream()
            .map(tc -> Triple.of(tc, tc.getInception(zoneId), tc.getExpiration(zoneId)))
            .filter(t -> t.getRight().isAfter(end))
            .forEach(t -> fixTicketCategoryDates(end, t.getLeft(), t.getMiddle(), t.getRight()));
    }

    private void fixTicketCategoryDates(ZonedDateTime end, TicketCategory tc, ZonedDateTime inception, ZonedDateTime expiration) {
        final ZonedDateTime newExpiration = ObjectUtils.min(end, expiration);
        Objects.requireNonNull(newExpiration);
        Validate.isTrue(inception.isBefore(newExpiration), format("Cannot fix dates for category \"%s\" (id: %d), try updating that category first.", tc.getName(), tc.getId()));
        ticketCategoryRepository.fixDates(tc.getId(), inception, newExpiration);
    }

    public void reallocateTickets(int srcCategoryId, int targetCategoryId, int eventId) {
        Event event = eventRepository.findById(eventId);
        reallocateTickets(ticketCategoryRepository.findStatisticWithId(srcCategoryId, eventId), Optional.of(ticketCategoryRepository.getByIdAndActive(targetCategoryId, event.getId())), event);
    }

    void reallocateTickets(TicketCategoryStatisticView src, Optional<TicketCategory> target, Event event) {
        int notSoldTickets = src.getNotSoldTicketsCount();
        if(notSoldTickets == 0) {
            log.debug("since all the ticket have been sold, ticket moving is not needed anymore.");
            return;
        }
        List<Integer> lockedTickets = ticketRepository.selectTicketInCategoryForUpdate(event.getId(), src.getId(), notSoldTickets, singletonList(TicketStatus.FREE.name()));
        int locked = lockedTickets.size();
        if(locked != notSoldTickets) {
            throw new IllegalStateException(String.format("Expected %d free tickets, got %d.", notSoldTickets, locked));
        }
        ticketCategoryRepository.updateSeatsAvailability(src.getId(), src.getSoldTicketsCount());
        if(target.isPresent()) {
            TicketCategory targetCategory = target.get();
            ticketCategoryRepository.updateSeatsAvailability(targetCategory.getId(), targetCategory.getMaxTickets() + locked);
            ticketRepository.moveToAnotherCategory(lockedTickets, targetCategory.getId(), targetCategory.getSrcPriceCts());
            if(targetCategory.isAccessRestricted()) {
                insertTokens(targetCategory, locked);
            } else {
                ticketRepository.resetTickets(lockedTickets);
            }
        } else {
            int result = ticketRepository.unbindTicketsFromCategory(event.getId(), src.getId(), lockedTickets);
            Validate.isTrue(result == locked, String.format("Expected %d modified tickets, got %d.", locked, result));
            ticketRepository.resetTickets(lockedTickets);
        }
        specialPriceRepository.cancelExpiredTokens(src.getId());
    }

    public void unbindTickets(String eventName, int categoryId, String username) {
        Event event = getSingleEvent(eventName, username);
        Validate.isTrue(ticketCategoryRepository.countUnboundedCategoriesByEventId(event.getId()) > 0, "cannot unbind tickets: there aren't any unbounded categories");
        TicketCategoryStatisticView ticketCategory = ticketCategoryRepository.findStatisticWithId(categoryId, event.getId());
        Validate.isTrue(ticketCategory.isBounded(), "cannot unbind tickets from an unbounded category!");
        reallocateTickets(ticketCategory, Optional.empty(), event);
    }

    MapSqlParameterSource[] prepareTicketsBulkInsertParameters(ZonedDateTime creation,
                                                               Event event, int requestedTickets, TicketStatus ticketStatus) {

        //FIXME: the date should be inserted as ZonedDateTime !
        Date creationDate = Date.from(creation.toInstant());

        List<TicketCategory> categories = ticketCategoryRepository.findByEventId(event.getId());
        Stream<MapSqlParameterSource> boundedTickets = categories.stream()
                .filter(IS_CATEGORY_BOUNDED)
                .flatMap(tc -> generateTicketsForCategory(tc, event, creationDate, 0));
        int generatedTickets = categories.stream()
                .filter(IS_CATEGORY_BOUNDED)
                .mapToInt(TicketCategory::getMaxTickets)
                .sum();
        if(generatedTickets >= requestedTickets) {
            return boundedTickets.toArray(MapSqlParameterSource[]::new);
        }

        return Stream.concat(boundedTickets, generateEmptyTickets(event, creationDate, requestedTickets - generatedTickets, ticketStatus)).toArray(MapSqlParameterSource[]::new);
    }

    private Stream<MapSqlParameterSource> generateTicketsForCategory(TicketCategory tc,
                                                                     Event event,
                                                                     Date creationDate,
                                                                     int existing) {
        Optional<TicketCategory> filteredTC = Optional.of(tc).filter(TicketCategory::isBounded);
        int missingTickets = filteredTC.map(c -> Math.abs(c.getMaxTickets() - existing)).orElseGet(() -> eventRepository.countExistingTickets(event.getId()) - existing);
        return generateStreamForTicketCreation(missingTickets)
                    .map(ps -> buildTicketParams(event.getId(), creationDate, filteredTC, tc.getSrcPriceCts(), ps));
    }

    private void createCategoriesForEvent(EventModification em, Event event) {
        boolean freeOfCharge = em.isFreeOfCharge();
        ZoneId zoneId = TimeZone.getTimeZone(event.getTimeZone()).toZoneId();
        int eventId = event.getId();

        int requestedSeats = em.getTicketCategories().stream()
                .filter(TicketCategoryModification::isBounded)
                .mapToInt(TicketCategoryModification::getMaxTickets)
                .sum();
        Validate.notNull(em.getAvailableSeats(), "Available Seats cannot be null");
        int notAssignedTickets = em.getAvailableSeats() - requestedSeats;
        Validate.isTrue(notAssignedTickets >= 0, "Total categories' seats cannot be more than the actual event seats");
        Validate.isTrue(notAssignedTickets > 0 || em.getTicketCategories().stream().allMatch(TicketCategoryModification::isBounded), "Cannot add an unbounded category if there aren't any free tickets");

        em.getTicketCategories().forEach(tc -> {
            final int price = evaluatePrice(tc.getPriceInCents(), freeOfCharge);
            final int maxTickets = tc.isBounded() ? tc.getMaxTickets() : 0;
            final AffectedRowCountAndKey<Integer> category = ticketCategoryRepository.insert(tc.getInception().toZonedDateTime(zoneId),
                tc.getExpiration().toZonedDateTime(zoneId), tc.getName(), maxTickets, tc.isTokenGenerationRequested(), eventId, tc.isBounded(), price, StringUtils.trimToNull(tc.getCode()),
                toZonedDateTime(tc.getValidCheckInFrom(), zoneId), toZonedDateTime(tc.getValidCheckInTo(), zoneId),
                toZonedDateTime(tc.getTicketValidityStart(), zoneId), toZonedDateTime(tc.getTicketValidityEnd(), zoneId));

            insertOrUpdateTicketCategoryDescription(category.getKey(), tc, event);

            if (tc.isTokenGenerationRequested()) {
                final TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(category.getKey(), event.getId());
                final MapSqlParameterSource[] args = prepareTokenBulkInsertParameters(ticketCategory, ticketCategory.getMaxTickets());
                jdbc.batchUpdate(specialPriceRepository.bulkInsert(), args);
            }
        });
    }

    private Integer insertCategory(TicketCategoryModification tc, Event event) {
        ZoneId zoneId = event.getZoneId();
        int eventId = event.getId();
        final int price = evaluatePrice(tc.getPriceInCents(), event.isFreeOfCharge());
        final AffectedRowCountAndKey<Integer> category = ticketCategoryRepository.insert(tc.getInception().toZonedDateTime(zoneId),
            tc.getExpiration().toZonedDateTime(zoneId), tc.getName(), tc.isBounded() ? tc.getMaxTickets() : 0, tc.isTokenGenerationRequested(), eventId, tc.isBounded(), price, StringUtils.trimToNull(tc.getCode()),
            toZonedDateTime(tc.getValidCheckInFrom(), zoneId),
            toZonedDateTime(tc.getValidCheckInTo(), zoneId),
            toZonedDateTime(tc.getTicketValidityStart(), zoneId),
            toZonedDateTime(tc.getTicketValidityEnd(), zoneId));
        TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(category.getKey(), eventId);
        if(tc.isBounded()) {
            List<Integer> lockedTickets = ticketRepository.selectNotAllocatedTicketsForUpdate(eventId, ticketCategory.getMaxTickets(), asList(TicketStatus.FREE.name(), TicketStatus.RELEASED.name()));
            jdbc.batchUpdate(ticketRepository.bulkTicketUpdate(), lockedTickets.stream().map(id -> new MapSqlParameterSource("id", id).addValue("categoryId", ticketCategory.getId()).addValue("srcPriceCts", ticketCategory.getSrcPriceCts())).toArray(MapSqlParameterSource[]::new));
            if(tc.isTokenGenerationRequested()) {
                insertTokens(ticketCategory);
                ticketRepository.revertToFree(eventId, ticketCategory.getId(), lockedTickets);
            } else {
                ticketRepository.resetTickets(lockedTickets);//reset to RELEASED
            }
        }

        insertOrUpdateTicketCategoryDescription(category.getKey(), tc, event);
        return category.getKey();
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

    private void updateCategory(TicketCategoryModification tc,
                                boolean freeOfCharge,
                                ZoneId zoneId,
                                Event event,
                                boolean resetTicketsToFree) {

        int eventId = event.getId();
        final int price = evaluatePrice(tc.getPriceInCents(), freeOfCharge);
        TicketCategory original = ticketCategoryRepository.getByIdAndActive(tc.getId(), eventId);
        ticketCategoryRepository.update(tc.getId(), tc.getName(), tc.getInception().toZonedDateTime(zoneId),
                tc.getExpiration().toZonedDateTime(zoneId), tc.getMaxTickets(), tc.isTokenGenerationRequested(), price, StringUtils.trimToNull(tc.getCode()),
                toZonedDateTime(tc.getValidCheckInFrom(), zoneId),
                toZonedDateTime(tc.getValidCheckInTo(), (zoneId)),
                toZonedDateTime(tc.getTicketValidityStart(), zoneId),
                toZonedDateTime(tc.getTicketValidityEnd(), zoneId));
        TicketCategory updated = ticketCategoryRepository.getByIdAndActive(tc.getId(), eventId);
        int addedTickets = 0;
        if(original.isBounded() ^ tc.isBounded()) {
            handleTicketAllocationStrategyChange(event, original, tc);
        } else {
            addedTickets = updated.getMaxTickets() - original.getMaxTickets();
            handleTicketNumberModification(event, original, updated, addedTickets, resetTicketsToFree);
        }
        handleTokenModification(original, updated, addedTickets);
        handlePriceChange(event, original, updated);

        insertOrUpdateTicketCategoryDescription(tc.getId(), tc, event);

        //

        auditingRepository.insertUpdateTicketInCategoryId(tc.getId());

    }

    private void handleTicketAllocationStrategyChange(Event event, TicketCategory original, TicketCategoryModification updated) {
        if(updated.isBounded()) {
            //the ticket allocation strategy has been changed to "bounded",
            //therefore we have to link the tickets which have not yet been acquired to this category
            int eventId = event.getId();
            int newSize = updated.getMaxTickets();
            int confirmed = ticketRepository.countConfirmedForCategory(eventId, original.getId());
            int addedTickets = newSize - confirmed;
            List<Integer> ids = ticketRepository.selectNotAllocatedTicketsForUpdate(eventId, addedTickets, singletonList(TicketStatus.FREE.name()));
            Validate.isTrue(ids.size() == addedTickets, "not enough tickets");
            Validate.isTrue(ids.size() == 0 || ticketRepository.moveToAnotherCategory(ids, original.getId(), updated.getPriceInCents()) == ids.size(), "not enough tickets");
        } else {
            reallocateTickets(ticketCategoryRepository.findStatisticWithId(original.getId(), event.getId()), Optional.empty(), event);
        }
        ticketCategoryRepository.updateBoundedFlag(original.getId(), updated.isBounded());
    }

    void handlePriceChange(Event event, TicketCategory original, TicketCategory updated) {
        if(original.getSrcPriceCts() == updated.getSrcPriceCts() || !original.isBounded()) {
            return;
        }
        final List<Integer> ids = ticketRepository.selectTicketInCategoryForUpdate(event.getId(), updated.getId(), updated.getMaxTickets(), singletonList(TicketStatus.FREE.name()));
        if(ids.size() < updated.getMaxTickets()) {
            throw new IllegalStateException("Tickets have already been sold (or are in the process of being sold) for this category. Therefore price update is not allowed.");
        }
        //there's no need to calculate final price, vat etc, since these values will be updated at the time of reservation
        ticketRepository.updateTicketPrice(updated.getId(), event.getId(), updated.getSrcPriceCts(), 0, 0, 0);
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
                final List<Integer> ids = specialPriceRepository.lockNotSentTokens(updated.getId(), absDifference);
                Validate.isTrue(ids.size() - absDifference == 0, "not enough tokens");
                specialPriceRepository.cancelTokens(ids);
            }
        }

    }

    void handleTicketNumberModification(Event event, TicketCategory original, TicketCategory updated, int addedTickets, boolean resetToFree) {
        if(addedTickets == 0) {
            log.debug("ticket handling not required since the number of ticket wasn't modified");
            return;
        }

        log.debug("modification detected in ticket number. The difference is: {}", addedTickets);

        if(addedTickets > 0) {
            //the updated category contains more tickets than the older one
            List<Integer> lockedTickets = ticketRepository.selectNotAllocatedTicketsForUpdate(event.getId(), addedTickets, asList(TicketStatus.FREE.name(), TicketStatus.RELEASED.name()));
            Validate.isTrue(addedTickets == lockedTickets.size(), "Cannot add %d tickets. There are only %d free tickets.", addedTickets, lockedTickets.size());
            jdbc.batchUpdate(ticketRepository.bulkTicketUpdate(), lockedTickets.stream()
                    .map(id -> new MapSqlParameterSource("id", id).addValue("categoryId", updated.getId()).addValue("srcPriceCts", updated.getSrcPriceCts()))
                    .toArray(MapSqlParameterSource[]::new));
            if(updated.isAccessRestricted()) {
                //since the updated category is not public, the tickets shouldn't be distributed to waiting people.
                ticketRepository.revertToFree(event.getId(), updated.getId(), lockedTickets);
            } else if(!resetToFree) {
                ticketRepository.resetTickets(lockedTickets);
            }

        } else {
            int absDifference = Math.abs(addedTickets);
            final List<Integer> ids = ticketRepository.lockTicketsToInvalidate(event.getId(), updated.getId(), absDifference);
            int actualDifference = ids.size();
            if(actualDifference < absDifference) {
                throw new IllegalStateException("Cannot invalidate "+absDifference+" tickets. There are only "+actualDifference+" free tickets");
            }
            ticketRepository.invalidateTickets(ids);
            final MapSqlParameterSource[] params = generateEmptyTickets(event, Date.from(ZonedDateTime.now(event.getZoneId()).toInstant()), absDifference, TicketStatus.RELEASED).toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);
        }
    }

    private MapSqlParameterSource[] prepareTokenBulkInsertParameters(TicketCategory tc, int limit) {
        return generateStreamForTicketCreation(limit)
                .peek(ps -> {
                    ps.addValue("code", UUID.randomUUID().toString());
                    ps.addValue("priceInCents", tc.getSrcPriceCts());
                    ps.addValue("ticketCategoryId", tc.getId());
                    ps.addValue("status", SpecialPrice.Status.WAITING.name());
                })
                .toArray(MapSqlParameterSource[]::new);
    }

    private void createAllTicketsForEvent(Event event, EventModification em) {
        Validate.notNull(em.getAvailableSeats());
        final MapSqlParameterSource[] params = prepareTicketsBulkInsertParameters(ZonedDateTime.now(event.getZoneId()), event, em.getAvailableSeats(), TicketStatus.FREE);
        jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), params);
    }

    private int insertEvent(EventModification em) {
        Validate.notNull(em.getAvailableSeats());
        String paymentProxies = collectPaymentProxies(em);
        BigDecimal vat = !em.isInternal() || em.isFreeOfCharge() ? BigDecimal.ZERO : em.getVatPercentage();
        String privateKey = UUID.randomUUID().toString();
        ZoneId zoneId = ZoneId.of(em.getZoneId());
        String currentVersion = flyway.info().current().getVersion().getVersion();
        return eventRepository.insert(em.getShortName(), em.getEventType(), em.getDisplayName(), em.getWebsiteUrl(), em.getExternalUrl(), em.isInternal() ? em.getTermsAndConditionsUrl() : "",
            em.getPrivacyPolicyUrl(), em.getImageUrl(), em.getFileBlobId(), em.getLocation(), em.getLatitude(), em.getLongitude(), em.getBegin().toZonedDateTime(zoneId),
            em.getEnd().toZonedDateTime(zoneId), em.getZoneId(), em.getCurrency(), em.getAvailableSeats(), em.isInternal() && em.isVatIncluded(),
            vat, paymentProxies, privateKey, em.getOrganizationId(), em.getLocales(), em.getVatStatus(), em.getPriceInCents(), currentVersion, Event.Status.DRAFT).getKey();
    }

    private String collectPaymentProxies(EventModification em) {
        return em.getAllowedPaymentProxies()
                .stream()
                .map(PaymentProxy::name)
                .collect(joining(","));
    }

    public TicketCategory getTicketCategoryById(int id, int eventId) {
        return ticketCategoryRepository.getByIdAndActive(id, eventId);
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

    public void addPromoCode(String promoCode, Integer eventId, Integer organizationId, ZonedDateTime start, ZonedDateTime end, int discountAmount, DiscountType discountType, List<Integer> categoriesId) {
        Validate.isTrue(promoCode.length() >= 7, "min length is 7 chars");
        Validate.isTrue((eventId != null && organizationId == null) || (eventId == null && organizationId != null), "eventId or organizationId must be not null");
        if(DiscountType.PERCENTAGE == discountType) {
            Validate.inclusiveBetween(0, 100, discountAmount, "percentage discount must be between 0 and 100");
        }
        if(DiscountType.FIXED_AMOUNT == discountType) {
            Validate.isTrue(discountAmount >= 0, "fixed discount amount cannot be less than zero");
        }

        //
        categoriesId = Optional.ofNullable(categoriesId).orElse(Collections.emptyList()).stream().filter(Objects::nonNull).collect(toList());
        //

        promoCodeRepository.addPromoCode(promoCode, eventId, organizationId, start, end, discountAmount, discountType.toString(), Json.GSON.toJson(categoriesId));
    }
    
    public void deletePromoCode(int promoCodeId) {
        promoCodeRepository.deletePromoCode(promoCodeId);
    }

    public void updatePromoCode(int promoCodeId, ZonedDateTime start, ZonedDateTime end) {
        promoCodeRepository.updateEventPromoCode(promoCodeId, start, end);
    }
    
    public List<PromoCodeDiscountWithFormattedTime> findPromoCodesInEvent(int eventId) {
        ZoneId zoneId = eventRepository.findById(eventId).getZoneId();
        return promoCodeRepository.findAllInEvent(eventId).stream().map((p) -> new PromoCodeDiscountWithFormattedTime(p, zoneId)).collect(toList());
    }

    public List<PromoCodeDiscountWithFormattedTime> findPromoCodesInOrganization(int organizationId) {
        ZoneId zoneId = ZoneId.systemDefault();
        return promoCodeRepository.findAllInOrganization(organizationId).stream().map((p) -> new PromoCodeDiscountWithFormattedTime(p, zoneId)).collect(toList());
    }

    public String getEventUrl(Event event) {
        return StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL)), "/") + "/event/" + event.getShortName() + "/";
    }

    public List<TicketCSVInfo> findAllConfirmedTicketsForCSV(String eventName, String username) {
        Event event = getSingleEvent(eventName, username);
        checkOwnership(event, username, event.getOrganizationId());
        return ticketRepository.findAllConfirmedForCSV(event.getId());
    }

    public List<Event> getPublishedEvents() {
        return getActiveEventsStream().filter(e -> e.getStatus() == Event.Status.PUBLIC).collect(toList());
    }

    public List<Event> getActiveEvents() {
        return getActiveEventsStream().collect(toList());
    }

    private Stream<Event> getActiveEventsStream() {
        return eventRepository.findAll().stream()
            .filter(e -> e.getEnd().truncatedTo(ChronoUnit.DAYS).plusDays(1).isAfter(ZonedDateTime.now(e.getZoneId()).truncatedTo(ChronoUnit.DAYS)));
    }

    public Function<Ticket, Boolean> checkTicketCancellationPrerequisites() {
        return CategoryEvaluator.ticketCancellationAvailabilityChecker(ticketCategoryRepository);
    }

    void resetReleasedTickets(Event event) {
        int reverted = ticketRepository.revertToFree(event.getId());
        if(reverted > 0) {
            log.debug("Reverted {} tickets to FREE for event {}", reverted, event.getId());
        }
    }

    public void updateTicketFieldDescriptions(Map<String, TicketFieldDescriptionModification> descriptions) {
        descriptions.forEach((locale, value) -> {
            String description = Json.GSON.toJson(value.getDescription());
            if(0 == ticketFieldRepository.updateDescription(value.getTicketFieldConfigurationId(), locale, description)) {
                ticketFieldRepository.insertDescription(value.getTicketFieldConfigurationId(), locale, description);
            }
        });
    }
    
	public void addAdditionalField(Event event, AdditionalField field) {
		Integer order = ticketFieldRepository.findMaxOrderValue(event.getId());
		insertAdditionalField(event, field, order == null ? 0 : order + 1);
	}
	
	public void deleteAdditionalField(int ticketFieldConfigurationId) {
		ticketFieldRepository.deleteValues(ticketFieldConfigurationId);
		ticketFieldRepository.deleteDescription(ticketFieldConfigurationId);
		ticketFieldRepository.deleteField(ticketFieldConfigurationId);
	}
	
	public void swapAdditionalFieldPosition(int eventId, int id1, int id2) {
		TicketFieldConfiguration field1 = ticketFieldRepository.findById(id1);
		TicketFieldConfiguration field2 = ticketFieldRepository.findById(id2);
		Assert.isTrue(eventId == field1.getEventId(), "eventId does not match field1.eventId");
		Assert.isTrue(eventId == field2.getEventId(), "eventId does not match field2.eventId");
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
		eventDeleterRepository.deleteTransactions(eventId);
		eventDeleterRepository.deleteReservation(eventId);
		
		eventDeleterRepository.deletePromoCode(eventId);
		eventDeleterRepository.deleteTicketCategoryText(eventId);
		eventDeleterRepository.deleteTicketCategory(eventId);
		eventDeleterRepository.deleteEventDescription(eventId);

        eventDeleterRepository.deleteResources(eventId);
        eventDeleterRepository.deleteScanAudit(eventId);
		
		eventDeleterRepository.deleteEvent(eventId);
		
	}

    public void disableEventsFromUsers(List<Integer> userIds) {
        if(!userIds.isEmpty()) {
            eventRepository.disableEventsForUsers(userIds);
        }
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
