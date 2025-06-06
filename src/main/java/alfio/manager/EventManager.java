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
import alfio.controller.form.SearchOptions;
import alfio.manager.support.CategoryEvaluator;
import alfio.manager.support.extension.ExtensionCapability;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.Event.EventFormat;
import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.model.Ticket.TicketStatus;
import alfio.model.TicketCategory.TicketAccessType;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.CategoryOrdinalModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.PromoCodeDiscountWithFormattedTimeAndAmount;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.subscription.EventSubscriptionLink;
import alfio.model.subscription.LinkSubscriptionsToEventRequest;
import alfio.model.support.CheckInOutputColorConfiguration;
import alfio.model.support.CheckInOutputColorConfiguration.ColorConfiguration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ClockProvider;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import alfio.util.RequestUtils;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Triple;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static alfio.model.TicketCategory.TicketCheckInStrategy.ONCE_PER_EVENT;
import static alfio.model.modification.DateTimeModification.atZone;
import static alfio.model.system.ConfigurationKeys.CHECK_IN_COLOR_CONFIGURATION;
import static alfio.util.EventUtil.*;
import static alfio.util.MiscUtils.removeTabsAndNewlines;
import static alfio.util.Wrappers.optionally;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.stream.Collectors.*;

@Component
@Transactional
@AllArgsConstructor
public class EventManager {

    private static final Logger log = LoggerFactory.getLogger(EventManager.class);
    private static final Predicate<TicketCategory> IS_CATEGORY_BOUNDED = TicketCategory::isBounded;
    static final String ERROR_ONLINE_ON_SITE_NOT_COMPATIBLE = "Cannot switch to Online. Please remove On-Site payment method first.";
    private final UserManager userManager;
    private final EventRepository eventRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final TicketRepository ticketRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final ConfigurationManager configurationManager;
    private final EventDeleterRepository eventDeleterRepository;
    private final PurchaseContextFieldManager purchaseContextFieldManager;
    private final Flyway flyway;
    private final Environment environment;
    private final OrganizationRepository organizationRepository;
    private final AuditingRepository auditingRepository;
    private final ExtensionManager extensionManager;
    private final GroupRepository groupRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ConfigurationRepository configurationRepository;
    private final PaymentManager paymentManager;
    private final ClockProvider clockProvider;
    private final SubscriptionRepository subscriptionRepository;
    private final AdditionalServiceManager additionalServiceManager;


    public Event getSingleEvent(String eventName, String username) {
        return getOptionalByName(eventName, username).orElseThrow(IllegalStateException::new);
    }

    public EventAndOrganizationId getEventAndOrganizationId(String eventName, String username) {
        return getOptionalEventAndOrganizationIdByName(eventName, username).orElseThrow(IllegalStateException::new);
    }

    public List<Integer> getEventIdsBySlug(List<String> eventSlugs, int organizationId) {
        return eventRepository.findIdsByShortNames(eventSlugs, organizationId);
    }

    public Optional<Event> getOptionalByName(String eventName, String username) {
        return eventRepository.findOptionalByShortName(eventName)
            .filter(checkOwnership(username, organizationRepository));
    }

    public Optional<EventAndOrganizationId> getOptionalEventAndOrganizationIdByName(String eventName, String username) {
        return eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName)
            .filter(checkOwnership(username, organizationRepository));
    }

    public Optional<EventAndOrganizationId> getOptionalEventIdAndOrganizationIdById(int eventId, String username) {
        return eventRepository.findOptionalEventAndOrganizationIdById(eventId)
            .filter(checkOwnership(username, organizationRepository));
    }

    public Event getSingleEventById(int eventId, String username) {
        return eventRepository.findOptionalById(eventId)
            .filter(checkOwnership(username, organizationRepository))
            .orElseThrow(IllegalStateException::new);
    }

    public void checkOwnership(EventAndOrganizationId event, String username, int organizationId) {
        Validate.isTrue(organizationId == event.getOrganizationId(), "invalid organizationId");
        Validate.isTrue(checkOwnership(username, organizationRepository).test(event), "User is not authorized");
    }

    public static Predicate<EventAndOrganizationId> checkOwnership(String username, OrganizationRepository organizationRepository) {
        return event -> checkOwnership(organizationRepository, username, event.getOrganizationId());
    }

    private static IntPredicate checkOwnershipByOrgId(String username, OrganizationRepository organizationRepository) {
        return id -> checkOwnership(organizationRepository, username, id);
    }

    private static boolean checkOwnership(OrganizationRepository organizationRepository, String username, Integer orgId) {
        return orgId != null && organizationRepository.findOrganizationForUser(username, orgId).isPresent();
    }

    public List<TicketCategory> loadTicketCategories(EventAndOrganizationId event) {
        return ticketCategoryRepository.findAllTicketCategories(event.getId());
    }

    public Organization loadOrganizer(EventAndOrganizationId event, String username) {
        return userManager.findOrganizationById(event.getOrganizationId(), username);
    }

    /**
     * Internal method used by automated jobs
     * @return
     */
    Organization loadOrganizerUsingSystemPrincipal(EventAndOrganizationId event) {
        return loadOrganizer(event, UserManager.ADMIN_USERNAME);
    }

    public boolean eventExistsById(int eventId) {
        return eventRepository.existsById(eventId);
    }

    public void createEvent(EventModification em, String username) {
        Assert.isNull(em.getId(), "id must be null");
        var organization = organizationRepository.findAllForUser(username)
            .stream()
            .filter(org -> org.getId() == em.getOrganizationId())
            .findFirst()
            .orElseThrow();
        // pre-create request validation
        extensionManager.handleEventValidation(em);
        int eventId = insertEvent(em);
        Optional<EventAndOrganizationId> srcEvent = getCopiedFrom(em, username);
        Event event = eventRepository.findById(eventId);
        createOrUpdateEventDescription(eventId, em);
        additionalServiceManager.createAllAdditionalServices(event, em.getAdditionalServices());
        createAdditionalFields(event, em);
        createCategoriesForEvent(em, event, srcEvent);
        createAllTicketsForEvent(event, em);
        createSubscriptionLinks(eventId, organization.getId(), toSubscriptionLinkRequests(em.getLinkedSubscriptions()));
        srcEvent.ifPresent(eventAndOrganizationId -> copySettings(event, eventAndOrganizationId));
        extensionManager.handleEventCreation(event);
        var eventMetadata = extensionManager.handleMetadataUpdate(event, organization, AlfioMetadata.empty());
        if(eventMetadata != null) {
            eventRepository.updateMetadata(eventMetadata, eventId);
        }
    }

    private Optional<EventAndOrganizationId> getCopiedFrom(EventModification em, String username) {
        if (em.getMetadata() != null && StringUtils.isNotBlank(em.getMetadata().getCopiedFrom())) {
            return getOptionalEventAndOrganizationIdByName(em.getMetadata().getCopiedFrom(), username);
        }
        return Optional.empty();
    }

    /**
     * Copies settings from a copied event into the new one
     * Supported settings are:
     * - Event-specific configuration
     * @param event event
     * @param srcEvent source event
     */
    private void copySettings(Event event, EventAndOrganizationId srcEvent) {
        int count = configurationRepository.copyEventConfiguration(event.getId(), event.getOrganizationId(), srcEvent.getId(), srcEvent.getOrganizationId());
        log.info("copied {} settings from source event", count);
    }

    private void createSubscriptionLinks(int eventId, int organizationId, List<LinkSubscriptionsToEventRequest> linkedSubscriptions) {
        if(CollectionUtils.isNotEmpty(linkedSubscriptions)) {
            var parameters = linkedSubscriptions.stream()
                .map(s -> new MapSqlParameterSource("eventId", eventId)
                    .addValue("subscriptionId", s.getDescriptorId())
                    .addValue("pricePerTicket", 0)
                    .addValue("organizationId", organizationId)
                    .addValue("compatibleCategories", Json.toJson(s.getCategories()))
                )
                .toArray(MapSqlParameterSource[]::new);
            var result = jdbcTemplate.batchUpdate(SubscriptionRepository.INSERT_SUBSCRIPTION_LINK, parameters);
            Validate.isTrue(Arrays.stream(result).allMatch(r -> r == 1), "Cannot link subscription");
        }
    }

    public void toggleActiveFlag(int id, String username, boolean activate) {
        Event event = eventRepository.findById(id);
        checkOwnership(event, username, event.getOrganizationId());

        if(environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO))) {
            throw new IllegalStateException("demo mode");
        }
        Event.Status status = activate ? Event.Status.PUBLIC : Event.Status.DRAFT;
        eventRepository.updateEventStatus(id, status);
        extensionManager.handleEventStatusChange(event, status);
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
            em.getTicketFields().forEach(f -> purchaseContextFieldManager.insertAdditionalField(event, f, f.getOrder()));
        }
    }

    public void updateEventHeader(Event original, EventModification em, String username) {
        IntPredicate ownershipChecker = checkOwnershipByOrgId(username, organizationRepository);
        boolean sameOrganization = original.getOrganizationId() == em.getOrganizationId();
        Validate.isTrue(ownershipChecker.test(original.getOrganizationId()) && (sameOrganization || ownershipChecker.test(em.getOrganizationId())), "Invalid organizationId");
        int eventId = original.getId();
        Validate.isTrue(sameOrganization || groupRepository.countByEventId(eventId) == 0, "Cannot change organization because there is a group linked to this event.");
        Validate.isTrue(sameOrganization || !subscriptionRepository.hasLinkedSubscription(eventId), "Cannot change organization because there are one or more subscriptions linked.");

        boolean formatUpdated = em.getFormat() != original.getFormat();
        if(em.getFormat() == EventFormat.ONLINE && formatUpdated) {
            Validate.isTrue(original.getAllowedPaymentProxies().stream().allMatch(p -> p != PaymentProxy.ON_SITE), ERROR_ONLINE_ON_SITE_NOT_COMPATIBLE);
        }

        String timeZone = ObjectUtils.firstNonNull(em.getZoneId(), em.getGeolocation() != null ? em.getGeolocation().timeZone() : null);
        String latitude = ObjectUtils.firstNonNull(em.getLatitude(), em.getGeolocation() != null ? em.getGeolocation().latitude() : null);
        String longitude = ObjectUtils.firstNonNull(em.getLongitude(), em.getGeolocation() != null ?  em.getGeolocation().longitude(): null);

        final ZoneId zoneId = ZoneId.of(timeZone);
        final ZonedDateTime begin = em.getBegin().toZonedDateTime(zoneId);
        final ZonedDateTime end = em.getEnd().toZonedDateTime(zoneId);
        eventRepository.updateHeader(eventId, em.getDisplayName(), em.getWebsiteUrl(), em.getExternalUrl(), em.getTermsAndConditionsUrl(),
            em.getPrivacyPolicyUrl(), em.getImageUrl(), em.getFileBlobId(), em.getLocation(), latitude, longitude,
            begin, end, timeZone, em.getOrganizationId(), em.getLocales(), em.getFormat());

        createOrUpdateEventDescription(eventId, em);

        if(!original.getBegin().equals(begin) || !original.getEnd().equals(end)) {
            fixOutOfRangeCategories(em, username, zoneId, end);
        }

        if(formatUpdated) {
            // update ticket access type for categories if the format has been updated
            var ticketAccessType = evaluateTicketAccessType(original.getFormat(), em.getFormat());
            ticketCategoryRepository.updateTicketAccessTypeForEvent(eventId, ticketAccessType);
        }

        extensionManager.handleEventHeaderUpdate(eventRepository.findById(eventId), organizationRepository.findOrganizationForUser(username, em.getOrganizationId()).orElseThrow());
    }

    private TicketAccessType evaluateTicketAccessType(EventFormat oldFormat, EventFormat newFormat) {
        if(newFormat == EventFormat.HYBRID) {
            return oldFormat == EventFormat.ONLINE ? TicketAccessType.ONLINE : TicketAccessType.IN_PERSON;
        }
        return TicketAccessType.INHERIT;
    }

    public void updateEventSeatsAndPrices(Event original, EventModification em, String username) {
        updateEventSeatsAndPrices(original, em, username, true);
    }

    public void updateEventSeatsAndPrices(Event original, EventModification em, String username, boolean updateSubscriptions) {
        Validate.notNull(em.getAvailableSeats(), "Available Seats cannot be null");
        checkOwnership(original, username, em.getOrganizationId());
        extensionManager.handleEventSeatsPricesUpdateValidation(original, em);
        int eventId = original.getId();
        int seatsDifference = em.getAvailableSeats() - eventRepository.countExistingTickets(original.getId());
        if(seatsDifference < 0) {
            int allocatedSeats = ticketCategoryRepository.findAllTicketCategories(original.getId()).stream()
                    .filter(TicketCategory::isBounded)
                    .mapToInt(TicketCategory::getMaxTickets)
                    .sum();
            if(em.getAvailableSeats() < allocatedSeats) {
                throw new IllegalArgumentException(format("cannot reduce max tickets to %d. There are already %d tickets allocated. Try updating categories first.", em.getAvailableSeats(), allocatedSeats));
            }
        }
        validatePaymentProxies(em.getAllowedPaymentProxies(), em.getOrganizationId());
        String paymentProxies = collectPaymentProxies(em);
        BigDecimal vat = em.isFreeOfCharge() ? BigDecimal.ZERO : em.getVatPercentage();
        eventRepository.updatePrices(em.getCurrency(), em.getAvailableSeats(), em.isVatIncluded(), vat, paymentProxies, eventId, em.getVatStatus(), em.getPriceInCents());
        if(seatsDifference != 0) {
            Event modified = eventRepository.findById(eventId);
            if(seatsDifference > 0) {
                final MapSqlParameterSource[] params = generateEmptyTickets(modified, Date.from(ZonedDateTime.now(clockProvider.withZone(modified.getZoneId())).toInstant()), seatsDifference, TicketStatus.RELEASED).toArray(MapSqlParameterSource[]::new);
                ticketRepository.bulkTicketInitialization(params);
            } else {
                List<Integer> ids = ticketRepository.selectNotAllocatedTicketsForUpdate(eventId, Math.abs(seatsDifference), singletonList(TicketStatus.FREE.name()));
                Validate.isTrue(ids.size() == Math.abs(seatsDifference), "cannot lock enough tickets for deletion.");
                int invalidatedTickets = ticketRepository.invalidateTickets(ids);
                Validate.isTrue(ids.size() == invalidatedTickets, "error during ticket invalidation: expected %d, got %d".formatted(ids.size(), invalidatedTickets));
            }
        }
        if (updateSubscriptions && em.getLinkedSubscriptions() != null) {
            var requests = toSubscriptionLinkRequests(em.getLinkedSubscriptions());
            updateLinkedSubscriptions(requests, eventId, original.getOrganizationId());
        }
    }

    // temporary until we support full subscription link on the admin UI
    private static List<LinkSubscriptionsToEventRequest> toSubscriptionLinkRequests(List<UUID> subscriptionIds) {
        return requireNonNullElse(subscriptionIds, List.<UUID>of()).stream()
            .map(s -> new LinkSubscriptionsToEventRequest(s, List.of())).collect(Collectors.toList());
    }

    public void updateLinkedSubscriptions(List<LinkSubscriptionsToEventRequest> linkedSubscriptions, int eventId, int organizationId) {
        if(CollectionUtils.isNotEmpty(linkedSubscriptions)) {
            var descriptorIds = linkedSubscriptions.stream().map(LinkSubscriptionsToEventRequest::getDescriptorId).collect(Collectors.toList());
            int removed = subscriptionRepository.removeStaleSubscriptions(eventId, organizationId, descriptorIds);
            log.trace("removed {} subscription links", removed);
            createSubscriptionLinks(eventId, organizationId, linkedSubscriptions);
        } else if (linkedSubscriptions != null) {
            // the user removed all the subscriptions
            int removed = subscriptionRepository.removeAllSubscriptionsForEvent(eventId, organizationId);
            log.trace("removed all subscription links ({}) for event {}", removed, eventId);
        }
    }

    private void validatePaymentProxies(List<PaymentProxy> paymentProxies, int organizationId) {
        var conflicts = paymentManager.validateSelection(paymentProxies, organizationId);
        if(!conflicts.isEmpty()) {
            var firstConflict = IterableUtils.get(conflicts, 0);
            throw new IllegalStateException("Conflicting providers found: "+firstConflict.getValue());
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
                    .checkPrecondition(() -> !existing.isBounded() || tcm.getMaxTickets() - existing.getMaxTickets() + ticketRepository.countAllocatedTicketsForEvent(eventId) <= eventRepository.countExistingTickets(eventId), ErrorCode.CategoryError.NOT_ENOUGH_SEATS)
                    .checkPrecondition(() -> tcm.isTokenGenerationRequested() == existing.isAccessRestricted() || ticketRepository.countConfirmedAndPendingTickets(eventId, categoryId) == 0, ErrorCode.custom("", "cannot update category: there are tickets already sold."))
                    .checkPrecondition(() -> tcm.isBounded() == existing.isBounded() || ticketRepository.countPendingOrReleasedForCategory(eventId, existing.getId()) == 0, ErrorCode.custom("", "It is not safe to change allocation strategy right now because there are pending reservations."))
                    .checkPrecondition(() -> !existing.isAccessRestricted() || tcm.isBounded() == existing.isAccessRestricted(), ErrorCode.custom("", "Dynamic allocation is not compatible with restricted access"))
                    .checkPrecondition(() -> {
                        // see https://github.com/exteso/alf.io/issues/335
                        // handle the case when the user try to shrink a category with tokens that are already sent
                        // we should fail if there are not enough free token left
                        int addedTicket = tcm.getMaxTickets() - existing.getMaxTickets();
                        return addedTicket >= 0 ||
                            !existing.isAccessRestricted() ||
                            specialPriceRepository.countNotSentToken(categoryId) >= Math.abs(addedTicket);
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
        return updateCategory(categoryId, event, tcm, username, tcm.isSkipWaitingList());
    }

    void fixOutOfRangeCategories(EventModification em, String username, ZoneId zoneId, ZonedDateTime end) {
        EventAndOrganizationId event = getEventAndOrganizationId(em.getShortName(), username);
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

    public void reallocateTickets(int srcCategoryId, int targetCategoryId, EventAndOrganizationId event) {
        reallocateTickets(ticketCategoryRepository.findStatisticWithId(srcCategoryId, event.getId()), Optional.of(ticketCategoryRepository.getByIdAndActive(targetCategoryId, event.getId())), event);
    }

    void reallocateTickets(TicketCategoryStatisticView src, Optional<TicketCategory> target, EventAndOrganizationId event) {
        int notSoldTickets = src.getNotSoldTicketsCount();
        if (notSoldTickets == 0) {
            log.debug("since all the ticket have been sold, ticket moving is not needed anymore.");
            return;
        }
        List<Integer> lockedTickets = ticketRepository.selectTicketInCategoryForUpdate(event.getId(), src.getId(), notSoldTickets, singletonList(TicketStatus.FREE.name()));
        int locked = lockedTickets.size();
        if (locked != notSoldTickets) {
            throw new IllegalStateException("Expected %d free tickets, got %d.".formatted(notSoldTickets, locked));
        }
        ticketCategoryRepository.updateSeatsAvailability(src.getId(), src.getMaxTickets() - locked);
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
            Validate.isTrue(result == locked, "Expected %d modified tickets, got %d.".formatted(locked, result));
            ticketRepository.resetTickets(lockedTickets);
        }
        specialPriceRepository.cancelExpiredTokens(src.getId());
    }

    public void unbindTickets(String eventName, int categoryId, String username) {
        EventAndOrganizationId event = getEventAndOrganizationId(eventName, username);
        Validate.isTrue(ticketCategoryRepository.countUnboundedCategoriesByEventId(event.getId()) > 0, "cannot unbind tickets: there aren't any unbounded categories");
        TicketCategoryStatisticView ticketCategory = ticketCategoryRepository.findStatisticWithId(categoryId, event.getId());
        Validate.isTrue(ticketCategory.isBounded(), "cannot unbind tickets from an unbounded category!");
        reallocateTickets(ticketCategory, Optional.empty(), event);
    }

    public List<TicketCategory> findCategoriesById(Collection<Integer> categoryIds, EventAndOrganizationId event) {
        return ticketCategoryRepository.getByIdsAndActive(categoryIds, event.getId());
    }

    MapSqlParameterSource[] prepareTicketsBulkInsertParameters(ZonedDateTime creation,
                                                               Event event, int requestedTickets, TicketStatus ticketStatus) {

        //FIXME: the date should be inserted as ZonedDateTime !
        Date creationDate = Date.from(creation.toInstant());

        List<TicketCategory> categories = ticketCategoryRepository.findAllTicketCategories(event.getId());
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

    private void createCategoriesForEvent(EventModification em, Event event, Optional<EventAndOrganizationId> srcEventOptional) {
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
            final int price = evaluatePrice(tc.getPrice(), freeOfCharge, event.getCurrency());
            final int maxTickets = tc.isBounded() ? tc.getMaxTickets() : 0;
            var accessType = requireNonNullElse(tc.getTicketAccessType(), TicketAccessType.INHERIT);
            if(event.getFormat() == EventFormat.HYBRID && accessType == TicketAccessType.INHERIT) {
                // if the event is hybrid the default is IN_PERSON
                accessType = TicketAccessType.IN_PERSON;
            }
            final AffectedRowCountAndKey<Integer> category = ticketCategoryRepository.insert(tc.getInception().toZonedDateTime(zoneId),
                tc.getExpiration().toZonedDateTime(zoneId), tc.getName(), maxTickets, tc.isTokenGenerationRequested(), eventId, tc.isBounded(), price, StringUtils.trimToNull(tc.getCode()),
                atZone(tc.getValidCheckInFrom(), zoneId), atZone(tc.getValidCheckInTo(), zoneId),
                atZone(tc.getTicketValidityStart(), zoneId), atZone(tc.getTicketValidityEnd(), zoneId), tc.getOrdinal(), Optional.ofNullable(tc.getTicketCheckInStrategy()).orElse(ONCE_PER_EVENT),
                requireNonNullElseGet(tc.getMetadata(), AlfioMetadata::empty), accessType);

            insertOrUpdateTicketCategoryDescription(category.getKey(), tc, event);

            if (tc.isTokenGenerationRequested()) {
                final TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(category.getKey(), event.getId());
                specialPriceRepository.bulkInsert(ticketCategory, ticketCategory.getMaxTickets());
            }

            if (srcEventOptional.isPresent() && tc.getMetadata() != null && StringUtils.isNumeric(tc.getMetadata().getCopiedFrom())) {
                int count = configurationRepository.copyCategoryConfiguration(event.getId(),
                    event.getOrganizationId(),
                    category.getKey(),
                    srcEventOptional.get().getId(),
                    srcEventOptional.get().getOrganizationId(),
                    Integer.parseInt(tc.getMetadata().getCopiedFrom())
                );
                log.info("Copied {} settings for category {}", count, tc.getName());
            }
        });
    }

    private Integer insertCategory(TicketCategoryModification tc, Event event) {
        ZoneId zoneId = event.getZoneId();
        int eventId = event.getId();
        final int price = evaluatePrice(tc.getPrice(), event.isFreeOfCharge(), event.getCurrency());
        final AffectedRowCountAndKey<Integer> category = ticketCategoryRepository.insert(tc.getInception().toZonedDateTime(zoneId),
            tc.getExpiration().toZonedDateTime(zoneId), tc.getName(), tc.isBounded() ? tc.getMaxTickets() : 0, tc.isTokenGenerationRequested(), eventId, tc.isBounded(), price, StringUtils.trimToNull(tc.getCode()),
            atZone(tc.getValidCheckInFrom(), zoneId),
            atZone(tc.getValidCheckInTo(), zoneId),
            atZone(tc.getTicketValidityStart(), zoneId),
            atZone(tc.getTicketValidityEnd(), zoneId), tc.getOrdinal(),
            requireNonNullElse(tc.getTicketCheckInStrategy(), ONCE_PER_EVENT),
            requireNonNullElseGet(tc.getMetadata(), AlfioMetadata::empty),
            requireNonNullElse(tc.getTicketAccessType(), TicketAccessType.INHERIT));
        TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(category.getKey(), eventId);
        if(tc.isBounded()) {
            List<Integer> lockedTickets = ticketRepository.selectNotAllocatedTicketsForUpdate(eventId, ticketCategory.getMaxTickets(), asList(TicketStatus.FREE.name(), TicketStatus.RELEASED.name()));
            ticketRepository.bulkTicketUpdate(lockedTickets, ticketCategory);
            if(tc.isTokenGenerationRequested()) {
                insertTokens(ticketCategory);
                ticketRepository.revertToFree(eventId, ticketCategory.getId(), lockedTickets);
            } else {
                ticketRepository.resetTickets(lockedTickets);//reset to RELEASED
            }
        }

        insertOrUpdateTicketCategoryDescription(category.getKey(), tc, event);
        saveBadgeColorConfiguration(tc.getBadgeColor(), event, category.getKey());
        return category.getKey();
    }

    void saveBadgeColorConfiguration(String badgeColor, Event event, Integer categoryId) {
        if(StringUtils.isNotBlank(badgeColor)) {
            var chosenColor = badgeColor.toLowerCase();
            var colorConfiguration = CheckInManager.getOutputColorConfiguration(event, configurationManager);
            boolean existingConfiguration = colorConfiguration != null;
            if(!existingConfiguration) {
                colorConfiguration = new CheckInOutputColorConfiguration("success", List.of(new ColorConfiguration(chosenColor, List.of(categoryId))));
            } else {
                var configurationWithoutCategory = colorConfiguration.getConfigurations().stream()
                    .map(cc -> new ColorConfiguration(cc.getColorName(), cc.getCategories().stream().filter(c -> !c.equals(categoryId)).collect(toUnmodifiableList())))
                    .filter(cc -> !cc.getCategories().isEmpty())
                    .collect(toList());
                boolean colorExists = configurationWithoutCategory.stream().anyMatch(cc -> cc.getColorName().equals(chosenColor));
                if(colorExists) {
                    colorConfiguration = new CheckInOutputColorConfiguration(colorConfiguration.getDefaultColorName(), configurationWithoutCategory.stream().map(cc -> {
                        if(cc.getColorName().equals(chosenColor)) {
                            var newList = new ArrayList<>(cc.getCategories());
                            newList.add(categoryId);
                            return new ColorConfiguration(chosenColor, newList);
                        }
                        return cc;
                    }).collect(toUnmodifiableList()));
                } else {
                    var newList = new ArrayList<>(configurationWithoutCategory);
                    newList.add(new ColorConfiguration(chosenColor, List.of(categoryId)));
                    colorConfiguration = new CheckInOutputColorConfiguration(colorConfiguration.getDefaultColorName(), newList);
                }
            }
            if(existingConfiguration) {
                configurationRepository.updateEventLevel(event.getId(), event.getOrganizationId(), CHECK_IN_COLOR_CONFIGURATION.name(), Json.toJson(colorConfiguration));
            } else {
                configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), CHECK_IN_COLOR_CONFIGURATION.name(), Json.toJson(colorConfiguration), null);
            }

        }
    }

    private void insertTokens(TicketCategory ticketCategory) {
        insertTokens(ticketCategory, ticketCategory.getMaxTickets());
    }

    private void insertTokens(TicketCategory ticketCategory, int requiredTokens) {
        specialPriceRepository.bulkInsert(ticketCategory, requiredTokens);
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
        final int price = evaluatePrice(tc.getPrice(), freeOfCharge, event.getCurrency());
        TicketCategory original = ticketCategoryRepository.getByIdAndActive(tc.getId(), eventId);
        ticketCategoryRepository.update(tc.getId(), tc.getName(), tc.getInception().toZonedDateTime(zoneId),
                tc.getExpiration().toZonedDateTime(zoneId), tc.getMaxTickets(), tc.isTokenGenerationRequested(), price, StringUtils.trimToNull(tc.getCode()),
                atZone(tc.getValidCheckInFrom(), zoneId),
                atZone(tc.getValidCheckInTo(), zoneId),
                atZone(tc.getTicketValidityStart(), zoneId),
                atZone(tc.getTicketValidityEnd(), zoneId),
                requireNonNullElse(tc.getTicketCheckInStrategy(), ONCE_PER_EVENT), tc.getTicketAccessType());
        TicketCategory updated = ticketCategoryRepository.getByIdAndActive(tc.getId(), eventId);
        int addedTickets = 0;
        if(original.isBounded() ^ tc.isBounded()) {
            handleTicketAllocationStrategyChange(event, original, tc);
        } else {
            addedTickets = updated.getMaxTickets() - original.getMaxTickets();
            handleTicketNumberModification(event, updated, addedTickets, resetTicketsToFree);
        }
        handleTokenModification(original, updated, addedTickets);
        handlePriceChange(event, original, updated);

        insertOrUpdateTicketCategoryDescription(tc.getId(), tc, event);

        //
        saveBadgeColorConfiguration(tc.getBadgeColor(), event, tc.getId());

        auditingRepository.insertUpdateTicketInCategoryId(tc.getId());

    }

    private void handleTicketAllocationStrategyChange(EventAndOrganizationId event, TicketCategory original, TicketCategoryModification updated) {
        if(updated.isBounded()) {
            //the ticket allocation strategy has been changed to "bounded",
            //therefore we have to link the tickets which have not yet been acquired to this category
            int eventId = event.getId();
            int newSize = updated.getMaxTickets();
            int confirmed = ticketRepository.countConfirmedForCategory(eventId, original.getId());
            int addedTickets = newSize - confirmed;
            List<Integer> ids = ticketRepository.selectNotAllocatedTicketsForUpdate(eventId, addedTickets, singletonList(TicketStatus.FREE.name()));
            Validate.isTrue(addedTickets >= 0, "Cannot reduce capacity to "+newSize+". Minimum size allowed is "+confirmed);
            Validate.isTrue(ids.size() >= addedTickets, "not enough tickets");
            Validate.isTrue(ids.isEmpty() || ticketRepository.moveToAnotherCategory(ids, original.getId(), MonetaryUtil.unitToCents(updated.getPrice(), original.getCurrencyCode())) == ids.size(), "not enough tickets");
        } else {
            reallocateTickets(ticketCategoryRepository.findStatisticWithId(original.getId(), event.getId()), Optional.empty(), event);
        }
        ticketCategoryRepository.updateBoundedFlag(original.getId(), updated.isBounded());
    }

    void handlePriceChange(EventAndOrganizationId event, TicketCategory original, TicketCategory updated) {
        if(original.getSrcPriceCts() == updated.getSrcPriceCts() || !original.isBounded()) {
            return;
        }
        final List<Integer> ids = ticketRepository.selectTicketInCategoryForUpdate(event.getId(), updated.getId(), updated.getMaxTickets(), singletonList(TicketStatus.FREE.name()));
        if(ids.size() < updated.getMaxTickets()) {
            throw new IllegalStateException("Tickets have already been sold (or are in the process of being sold) for this category. Therefore price update is not allowed.");
        }
        //there's no need to calculate final price, vat etc, since these values will be updated at the time of reservation
        ticketRepository.updateTicketPrice(updated.getId(), event.getId(), updated.getSrcPriceCts(), 0, 0, 0, original.getCurrencyCode());
    }

    void handleTokenModification(TicketCategory original, TicketCategory updated, int addedTickets) {
        if(original.isAccessRestricted() ^ updated.isAccessRestricted()) {
            if(updated.isAccessRestricted()) {
                specialPriceRepository.bulkInsert(updated, updated.getMaxTickets());
            } else {
                specialPriceRepository.cancelExpiredTokens(updated.getId());
            }
        } else if(updated.isAccessRestricted() && addedTickets != 0) {
            if(addedTickets > 0) {
                specialPriceRepository.bulkInsert(updated, addedTickets);
            } else {
                int absDifference = Math.abs(addedTickets);
                final List<Integer> ids = specialPriceRepository.lockNotSentTokens(updated.getId(), absDifference);
                Validate.isTrue(ids.size() - absDifference == 0, "not enough tokens");
                specialPriceRepository.cancelTokens(ids);
            }
        }

    }

    void handleTicketNumberModification(Event event, TicketCategory updated, int addedTickets, boolean resetToFree) {
        if(addedTickets == 0) {
            log.debug("ticket handling not required since the number of ticket wasn't modified");
            return;
        }

        log.debug("modification detected in ticket number. The difference is: {}", addedTickets);

        if(addedTickets > 0) {
            //the updated category contains more tickets than the older one
            List<Integer> lockedTickets = ticketRepository.selectNotAllocatedTicketsForUpdate(event.getId(), addedTickets, asList(TicketStatus.FREE.name(), TicketStatus.RELEASED.name()));
            Validate.isTrue(addedTickets == lockedTickets.size(), "Cannot add %d tickets. There are only %d free tickets.", addedTickets, lockedTickets.size());
            ticketRepository.bulkTicketUpdate(lockedTickets, updated);
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
            final MapSqlParameterSource[] params = generateEmptyTickets(event, Date.from(event.now(clockProvider).toInstant()), absDifference, resetToFree ? TicketStatus.FREE : TicketStatus.RELEASED).toArray(MapSqlParameterSource[]::new);
            ticketRepository.bulkTicketInitialization(params);
        }
    }

    private void createAllTicketsForEvent(Event event, EventModification em) {
        Objects.requireNonNull(em.getAvailableSeats());
        final MapSqlParameterSource[] params = prepareTicketsBulkInsertParameters(event.now(clockProvider), event, em.getAvailableSeats(), TicketStatus.FREE);
        ticketRepository.bulkTicketInitialization(params);
    }

    private int insertEvent(EventModification em) {
        Objects.requireNonNull(em.getAvailableSeats());
        validatePaymentProxies(em.getAllowedPaymentProxies(), em.getOrganizationId());
        String paymentProxies = collectPaymentProxies(em);
        BigDecimal vat = em.isFreeOfCharge() ? BigDecimal.ZERO : em.getVatPercentage();
        String privateKey = UUID.randomUUID().toString();
        ZoneId zoneId = ZoneId.of(em.getZoneId());
        String currentVersion = flyway.info().current().getVersion().getVersion();
        var eventMetadata = requireNonNullElseGet(em.getMetadata(), AlfioMetadata::empty);
        return eventRepository.insert(em.getShortName(), em.getFormat(), em.getDisplayName(), em.getWebsiteUrl(), em.getExternalUrl(), em.getTermsAndConditionsUrl(),
            em.getPrivacyPolicyUrl(), em.getImageUrl(), em.getFileBlobId(), em.getLocation(), em.getLatitude(), em.getLongitude(), em.getBegin().toZonedDateTime(zoneId),
            em.getEnd().toZonedDateTime(zoneId), em.getZoneId(), em.getCurrency(), em.getAvailableSeats(), em.isVatIncluded(),
            vat, paymentProxies, privateKey, em.getOrganizationId(), em.getLocales(), em.getVatStatus(), em.getPriceInCents(), currentVersion, Event.Status.DRAFT, eventMetadata).getKey();
    }

    private String collectPaymentProxies(EventModification em) {
        return em.getAllowedPaymentProxies()
                .stream()
                .map(PaymentProxy::name)
                .distinct()
                .collect(joining(","));
    }

    public TicketCategory getTicketCategoryById(int id, int eventId) {
        return ticketCategoryRepository.getByIdAndActive(id, eventId);
    }

    public boolean toggleTicketLocking(String eventName, int categoryId, int ticketId, String username) {
        EventAndOrganizationId event = getEventAndOrganizationId(eventName, username);
        checkOwnership(event, username, event.getOrganizationId());
        // FIXME: can search directly by id
        var existingCategory = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(tc -> tc.getId() == categoryId).findFirst();
        if(existingCategory.isPresent()) {
            Ticket ticket = ticketRepository.findById(ticketId, categoryId);
            Validate.isTrue(ticketRepository.toggleTicketLocking(ticketId, categoryId, !ticket.getLockedAssignment()) == 1, "unwanted result from ticket locking");
            return true;
        }
        throw new IllegalArgumentException("Invalid category");
    }

    public void addPromoCode(String promoCode,
                             Integer eventId,
                             Integer organizationId,
                             ZonedDateTime start,
                             ZonedDateTime end,
                             int discountAmount,
                             DiscountType discountType,
                             List<Integer> categoriesId,
                             Integer maxUsage,
                             String description,
                             String emailReference,
                             PromoCodeDiscount.CodeType codeType,
                             Integer hiddenCategoryId,
                             String currencyCode) {

        Validate.isTrue(promoCode.length() >= 7, "min length is 7 chars");
        Validate.isTrue((eventId != null && organizationId == null) || (eventId == null && organizationId != null), "eventId or organizationId must be not null");
        Validate.isTrue(StringUtils.length(description) < 1025, "Description can be maximum 1024 chars");
        Validate.isTrue(StringUtils.length(emailReference) < 257, "Description can be maximum 256 chars");
        Validate.isTrue(!PromoCodeDiscount.supportsCurrencyCode(codeType, discountType) || StringUtils.length(currencyCode) == 3, "Currency code is not valid");

        if(maxUsage != null) {
            Validate.isTrue(maxUsage > 0, "Invalid max usage");
        }
        if(DiscountType.PERCENTAGE == discountType) {
            Validate.inclusiveBetween(0, 100, discountAmount, "percentage discount must be between 0 and 100");
        }
        if(DiscountType.FIXED_AMOUNT == discountType || DiscountType.FIXED_AMOUNT_RESERVATION == discountType) {
            Validate.isTrue(discountAmount >= 0, "fixed discount amount cannot be less than zero");
        }

        if(PromoCodeDiscount.CodeType.ACCESS == codeType) {
            Validate.notNull(hiddenCategoryId, "Hidden category is required");
        }

        //
        categoriesId = Optional.ofNullable(categoriesId).orElse(Collections.emptyList()).stream().filter(Objects::nonNull).collect(toList());
        //

        if (organizationId == null) {
            organizationId = eventRepository.findOrganizationIdByEventId(eventId);
        }

        if(codeType == PromoCodeDiscount.CodeType.ACCESS) {
            discountType = DiscountType.NONE;
        }

        promoCodeRepository.addPromoCode(promoCode, eventId, organizationId, start, end, discountAmount, discountType,
            Json.GSON.toJson(categoriesId), maxUsage, description, emailReference, codeType, hiddenCategoryId, currencyCode);
    }

    public void deletePromoCode(int promoCodeId) {
        promoCodeRepository.deletePromoCode(promoCodeId);
    }

    public void updatePromoCode(int promoCodeId, ZonedDateTime start, ZonedDateTime end, Integer maxUsage, List<Integer> categories, String description, String emailReference, Integer hiddenCategoryId) {
        Validate.isTrue(StringUtils.length(description) < 1025, "Description can be maximum 1024 chars");
        Validate.isTrue(StringUtils.length(emailReference) < 257, "Description can be maximum 256 chars");
        String categoriesJson = CollectionUtils.isEmpty(categories) ? null : Json.toJson(categories);

        promoCodeRepository.updateEventPromoCode(promoCodeId, start, end, maxUsage, categoriesJson, description, emailReference, hiddenCategoryId);
    }

    public List<PromoCodeDiscountWithFormattedTimeAndAmount> findPromoCodesInEvent(int eventId) {
        var event = eventRepository.findById(eventId);
        return promoCodeRepository.findAllInEvent(eventId).stream().map(p -> new PromoCodeDiscountWithFormattedTimeAndAmount(p, event.getZoneId(), event.getCurrency())).collect(toList());
    }

    public List<PromoCodeDiscountWithFormattedTimeAndAmount> findPromoCodesInOrganization(int organizationId) {
        ZoneId zoneId = ZoneId.systemDefault();
        return promoCodeRepository.findAllInOrganization(organizationId).stream().map(p -> new PromoCodeDiscountWithFormattedTimeAndAmount(p, zoneId, null)).collect(toList());
    }

    public String getEventUrl(Event event) {
        var baseUrl = configurationManager.getFor(ConfigurationKeys.BASE_URL, event.getConfigurationLevel()).getRequiredValue();
        return StringUtils.removeEnd(baseUrl, "/") + "/event/" + event.getShortName() + "/";
    }

    public List<TicketWithReservationAndTransaction> findAllConfirmedTicketsForCSV(String eventName, String username) {
        EventAndOrganizationId event = getEventAndOrganizationId(eventName, username);
        checkOwnership(event, username, event.getOrganizationId());
        return ticketRepository.findAllConfirmedForCSV(event.getId());
    }

    public List<Event> getPublishedEvents(SearchOptions searchOptions) {
        return eventRepository.findVisibleBySearchOptions(searchOptions.getSubscriptionCodeUUIDOrNull(),
            searchOptions.getOrganizer(),
            searchOptions.getOrganizerSlug(),
            searchOptions.getTags());
    }

    public List<Event> getActiveEvents() {
        return getActiveEventsStream(1).toList();
    }

    public List<Event> getEventsByDateRange(int days) {
        return getActiveEventsStream(days).toList();
    }

    private Stream<Event> getActiveEventsStream(int days) {
        return eventRepository.findAll().stream()
            .filter(e -> e.getEnd().truncatedTo(ChronoUnit.DAYS).plusDays(days).isAfter(ZonedDateTime.now(clockProvider.withZone(e.getZoneId())).truncatedTo(ChronoUnit.DAYS)));
    }

    public Function<Ticket, Boolean> checkTicketCancellationPrerequisites() {
        return CategoryEvaluator.ticketCancellationAvailabilityChecker(ticketCategoryRepository);
    }

    void resetReleasedTickets(EventAndOrganizationId event) {
        int reverted = ticketRepository.revertToFree(event.getId());
        if(reverted > 0) {
            log.debug("Reverted {} tickets to FREE for event {}", reverted, event.getId());
        }
    }

	public void deleteEvent(int eventId, String username) {
		final Event event = eventRepository.findById(eventId);
		checkOwnership(event, username, event.getOrganizationId());
        eventDeleterRepository.deleteAllForEvent(eventId);
    }

    public Optional<TicketCategory> getOptionalByIdAndActive(int ticketCategoryId, int eventId) {
        return ticketCategoryRepository.getOptionalByIdAndActive(ticketCategoryId, eventId);
    }

    public void deleteCategory(String eventName, int categoryId, String username) {
        var optionalEvent = getOptionalEventAndOrganizationIdByName(eventName, username);
        if(optionalEvent.isEmpty()) {
            throw new IllegalArgumentException("Event not found");
        }
        int eventId = optionalEvent.get().getId();
        if(ticketCategoryRepository.countActiveByEventId(eventId) < 2) {
            throw new IllegalArgumentException("At least one category is required");
        }
        var optionalCategory = getOptionalByIdAndActive(categoryId, eventId);
        if(optionalCategory.isEmpty()) {
            throw new IllegalArgumentException("Category not found");
        }
        var category = optionalCategory.get();
        int result = ticketCategoryRepository.deleteCategoryIfEmpty(category.getId());
        if(result != 1) {
            log.debug("cannot delete category. Expected result 1, got {}", result);
            throw new IllegalStateException("Cannot delete category");
        }
        if(category.isBounded()) {
            int ticketsCount = category.getMaxTickets();
            var ticketIds = ticketRepository.selectTicketInCategoryForUpdate(eventId, categoryId, ticketsCount, List.of(TicketStatus.FREE.name(), TicketStatus.RELEASED.name()));
            Validate.isTrue(ticketIds.size() == ticketsCount, "Error while deleting category. Please ensure that there is no pending reservation.");
            ticketRepository.resetTickets(ticketIds);
            Validate.isTrue(ticketsCount == ticketRepository.unbindTicketsFromCategory(eventId, categoryId, ticketIds), "Cannot remove tickets from category.");
        }
    }

    public void rearrangeCategories(String eventName, List<CategoryOrdinalModification> categories, String username) {
        var optionalEvent = getOptionalEventAndOrganizationIdByName(eventName, username);
        if(optionalEvent.isPresent()) {
            int eventId = optionalEvent.get().getId();
            var parameterSources = categories.stream()
                .map(category -> new MapSqlParameterSource("ordinal", category.getOrdinal())
                    .addValue("id", category.getId())
                    .addValue("eventId", eventId))
                .toArray(MapSqlParameterSource[]::new);
            int[] results = jdbcTemplate.batchUpdate(ticketCategoryRepository.updateOrdinal(), parameterSources);
            Validate.isTrue(IntStream.of(results).sum() == categories.size(), "Unexpected result from update.");
        } else {
            log.warn("unauthorized access to event {}", removeTabsAndNewlines(eventName));
        }
    }

    public Map<Integer, String> getEventNamesByIds(List<Integer> eventIds, Principal principal) {
        if (!RequestUtils.isAdmin(principal)) {
            throw new IllegalStateException("User must be admin");
        }
        return eventRepository.getEventNamesByIds(eventIds).stream().collect(Collectors.toMap(EventIdShortName::getId, EventIdShortName::getShortName));
    }

    public Map<Integer, String> getEventsNameInOrganization(int orgId, Principal principal) {
        if (!RequestUtils.isAdmin(principal)) {
            throw new IllegalStateException("User must be admin");
        }
        return eventRepository.getEventsNameInOrganization(orgId).stream().collect(Collectors.toMap(EventIdShortName::getId, EventIdShortName::getShortName));
    }

    public boolean updateMetadata(Event event, AlfioMetadata metadata) {
        var existing = eventRepository.getMetadataForEvent(event.getId());
        var updatedMetadata = extensionManager.handleMetadataUpdate(event, organizationRepository.getById(event.getOrganizationId()), metadata);
        eventRepository.updateMetadata(existing.merge(Objects.requireNonNullElse(updatedMetadata, metadata)), event.getId());
        return true;
    }

    public boolean updateCategoryMetadata(EventAndOrganizationId event, int categoryId, AlfioMetadata metadata) {
        var existing = ticketCategoryRepository.getMetadata(event.getId(), categoryId);
        return ticketCategoryRepository.updateMetadata(existing.merge(metadata), event.getId(), categoryId) == 1;
    }

    public AlfioMetadata getMetadataForEvent(EventAndOrganizationId event) {
        return eventRepository.getMetadataForEvent(event.getId());
    }

    public AlfioMetadata getMetadataForCategory(EventAndOrganizationId event, int categoryId) {
        return ticketCategoryRepository.getMetadata(event.getId(), categoryId);
    }

    public Optional<String> executeCapability(String eventName,
                                              String username,
                                              ExtensionCapability capability,
                                              Map<String, String> requestParams) {
        return getOptionalByName(eventName, username)
            .flatMap(event -> {
                if (capability == ExtensionCapability.GENERATE_MEETING_LINK) {
                    var organization = organizationRepository.getById(event.getOrganizationId());
                    return extensionManager.handleGenerateMeetingLinkCapability(event, organization, getMetadataForEvent(event), requestParams)
                        .map(metadata -> {
                            eventRepository.updateMetadata(requireNonNullElseGet(metadata, AlfioMetadata::empty), event.getId());
                            return "metadata updated";
                        });
                } else if (capability == ExtensionCapability.LINK_EXTERNAL_APPLICATION) {
                    return extensionManager.handleGenerateLinkCapability(event, getMetadataForEvent(event), requestParams);
                } else {
                    return extensionManager.executeCapability(capability, requestParams, event, String.class);
                }

            });
    }

    public List<UUID> getLinkedSubscriptionIds(int eventId, int organizationId) {
        return subscriptionRepository.findLinkedSubscriptionIds(eventId, organizationId);
    }

    public List<EventSubscriptionLink> getLinkedSubscriptions(int eventId, int organizationId) {
        return subscriptionRepository.findLinkedSubscriptions(eventId, organizationId);
    }

    public int getEventsCount() {
        return eventRepository.countEvents();
    }


}
