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
package alfio.controller.api.v1.admin;

import alfio.extension.ExtensionService;
import alfio.job.executor.AssignTicketToSubscriberJobExecutor;
import alfio.manager.*;
import alfio.manager.system.AdminJobManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.ExtensionSupport.ExtensionMetadataValue;
import alfio.model.api.v1.admin.*;
import alfio.model.group.Group;
import alfio.model.modification.*;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.result.ValidationResult;
import alfio.model.subscription.EventSubscriptionLink;
import alfio.model.subscription.LinkSubscriptionsToEventRequest;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.ExtensionRepository;
import alfio.util.Json;
import alfio.util.JsonViews;
import alfio.util.Validator;
import com.fasterxml.jackson.annotation.JsonView;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static alfio.controller.api.admin.EventApiController.validateEvent;
import static alfio.manager.system.AdminJobExecutor.JobName.ASSIGN_TICKETS_TO_SUBSCRIBERS;
import static alfio.model.api.v1.admin.EventCreationRequest.findExistingCategory;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


@RestController
@RequestMapping("/api/v1/admin/event")
public class EventApiV1Controller {

    private static final Logger log = LoggerFactory.getLogger(EventApiV1Controller.class);
    private final EventManager eventManager;
    private final EventNameManager eventNameManager;
    private final FileUploadManager fileUploadManager;
    private final FileDownloadManager fileDownloadManager;
    private final UserManager userManager;
    private final EventStatisticsManager eventStatisticsManager;
    private final GroupManager groupManager;
    private final ExtensionService extensionService;
    private final ExtensionRepository extensionRepository;
    private final ConfigurationManager configurationManager;
    private final AdminJobManager adminJobManager;
    private final CheckInManager checkInManager;
    private final AccessService accessService;
    private final PurchaseContextFieldManager purchaseContextFieldManager;

    public EventApiV1Controller(EventManager eventManager,
                                EventNameManager eventNameManager,
                                FileUploadManager fileUploadManager,
                                FileDownloadManager fileDownloadManager,
                                UserManager userManager,
                                EventStatisticsManager eventStatisticsManager,
                                GroupManager groupManager,
                                ExtensionService extensionService,
                                ExtensionRepository extensionRepository,
                                ConfigurationManager configurationManager,
                                AdminJobManager adminJobManager,
                                CheckInManager checkInManager,
                                AccessService accessService,
                                PurchaseContextFieldManager purchaseContextFieldManager) {
        this.eventManager = eventManager;
        this.eventNameManager = eventNameManager;
        this.fileUploadManager = fileUploadManager;
        this.fileDownloadManager = fileDownloadManager;
        this.userManager = userManager;
        this.eventStatisticsManager = eventStatisticsManager;
        this.groupManager = groupManager;
        this.extensionService = extensionService;
        this.extensionRepository = extensionRepository;
        this.configurationManager = configurationManager;
        this.adminJobManager = adminJobManager;
        this.checkInManager = checkInManager;
        this.accessService = accessService;
        this.purchaseContextFieldManager = purchaseContextFieldManager;
    }

    @PostMapping("/create")
    @Transactional
    public ResponseEntity<String> create(@RequestBody EventCreationRequest request, Principal user) {
        String imageRef = Optional.ofNullable(request.getImageUrl()).map(this::fetchImage).orElse(null);
        Organization organization = userManager.findUserOrganizations(user.getName()).get(0);
        AtomicReference<Errors> errorsContainer = new AtomicReference<>();
        Result<String> result =  new Result.Builder<String>()
            .checkPrecondition(() -> isNotBlank(request.getTitle()), ErrorCode.custom("invalid.title", "Invalid title"))
            .checkPrecondition(() -> isBlank(request.getSlug()) || eventNameManager.isUnique(request.getSlug()), ErrorCode.custom("invalid.slug", "Invalid slug"))
            .checkPrecondition(() -> isNotBlank(request.getWebsiteUrl()), ErrorCode.custom("invalid.websiteUrl", "Invalid Website URL"))
            .checkPrecondition(() -> isNotBlank(request.getTermsAndConditionsUrl()), ErrorCode.custom("invalid.tc", "Invalid Terms and Conditions"))
            .checkPrecondition(() -> isNotBlank(request.getImageUrl()), ErrorCode.custom("invalid.imageUrl", "Invalid Image URL"))
            .checkPrecondition(() -> isNotBlank(request.getTimezone()), ErrorCode.custom("invalid.timezone", "Invalid Timezone"))
            .checkPrecondition(() -> isNotBlank(imageRef), ErrorCode.custom("invalid.image", "Image is either missing or too big (max 200kb)"))
            .checkPrecondition(() -> validateCategoriesSalesPeriod(request), ErrorCode.custom("invalid.categories", "Ticket categories: sales period not compatible with event dates"))
            .checkPrecondition(() -> validateAdditionalFields(request), ErrorCode.custom("invalid.additionalInfo", "Additional info not valid"))
            .checkPrecondition(() -> {
                EventModification eventModification = request.toEventModification(organization, eventNameManager::generateShortName, imageRef);
                errorsContainer.set(new BeanPropertyBindingResult(eventModification, "event"));
                int descriptionMaxLength = configurationManager.getFor(ConfigurationKeys.DESCRIPTION_MAXLENGTH, ConfigurationLevel.system()).getValueAsIntOrDefault(4096);
                ValidationResult validationResult = validateEvent(eventModification, errorsContainer.get(), descriptionMaxLength);
                if(!validationResult.isSuccess()) {
                    log.warn("validation failed {}", validationResult.getValidationErrors());
                }
                return validationResult.isSuccess();
            }, ErrorCode.lazy(() -> toErrorCode(errorsContainer.get())))
            //TODO all location validation
            //TODO language validation, for all the description the same languages
            .build(() -> insertEvent(request, user, imageRef).orElseThrow(IllegalStateException::new));

        if(result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        } else {
            return ResponseEntity.badRequest().body(Json.toJson(result.getErrors()));
        }

    }

    private boolean validateAdditionalFields(EventCreationRequest request) {
        return CollectionUtils.isEmpty(request.getAdditionalInfo()) || request.getAdditionalInfo()
            .stream()
            .allMatch(f -> Validator.validateAdditionalInfoName(f.getName()));
    }

    private boolean validateCategoriesSalesPeriod(EventCreationRequest request) {
        var eventEnd = request.getEndDate();
        return request.getTickets().getCategories().stream()
            .allMatch(tc -> tc.getStartSellingDate().isBefore(tc.getEndSellingDate()) && tc.getEndSellingDate().isBefore(eventEnd));
    }

    private ErrorCode toErrorCode(Errors errors) {
        return errors.getFieldErrors().stream()
            .map(e -> ErrorCode.custom(e.getField(), e.getCode()))
            .findFirst()
            .orElse(ErrorCode.EventError.NOT_FOUND);
    }

    @GetMapping("/list")
    public ResponseEntity<List<EventWithAdditionalInfo>> listEvents(Principal user) {
        return ResponseEntity.ok(eventStatisticsManager.getAllEventsWithAdditionalInfo(user.getName()));
    }

    @GetMapping("/{slug}/stats")
    @JsonView(JsonViews.AdminPublicApi.class)
    public ResponseEntity<EventWithAdditionalInfo> stats(@PathVariable String slug, Principal user) {
        accessService.checkEventOwnership(user, slug);
        Result<EventWithAdditionalInfo> result = new Result.Builder<EventWithAdditionalInfo>()
            .build(() -> eventStatisticsManager.getEventWithAdditionalInfo(slug,user.getName()));

        if(result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{slug}/download-attendees")
    public ResponseEntity<List<DownloadedAttendeesByCategory>> downloadAttendees(@PathVariable String slug, Principal user) {
        accessService.checkEventOwnership(user, slug);
        var event = eventManager.getSingleEvent(slug,user.getName());
        var ticketCategories = eventManager.loadTicketCategories(event);
        var ticketsByCategoryId = eventManager.findAllConfirmedTicketsForCSV(slug, user.getName()).stream()
            .filter(t -> t.getTicket().getCategoryId() != null && t.getTicket().getAssigned())
            .collect(Collectors.groupingBy(t -> t.getTicket().getCategoryId()));
        var additionalInfoByTicketId = purchaseContextFieldManager.findAllConfirmedTicketValues(event.getId());
        return ResponseEntity.ok(ticketCategories.stream().filter(category -> ticketsByCategoryId.containsKey(category.getId()))
            .map(category -> {
                var ticketsInCategory = ticketsByCategoryId.get(category.getId());
                var downloadedAttendeesData = ticketsInCategory.stream()
                    .map(t -> {
                        var ticket = t.getTicket();
                        var additional = Objects.requireNonNullElse(additionalInfoByTicketId.get(ticket.getId()), List.<PurchaseContextFieldValue>of()).stream()
                            .collect(Collectors.groupingBy(PurchaseContextFieldValue::getName, Collectors.mapping(PurchaseContextFieldValue::getValue, Collectors.toList())));
                        return new DownloadedAttendeeData(ticket.getFirstName(),
                            ticket.getLastName(),
                            ticket.getEmail(),
                            Map.of(),
                            additional,
                            ticket.getExtReference(),
                            ticket.getStatus(),
                            t.getTicketReservation().getConfirmationTimestamp());
                    })
                    .toList();
                return new DownloadedAttendeesByCategory(category.getId(), downloadedAttendeesData);
            }).toList());
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<String> delete(@PathVariable String slug, Principal user) {
        accessService.checkEventOwnership(user, slug);
        Result<String> result =  new Result.Builder<String>()
            .build(() -> {
                eventManager.getOptionalEventAndOrganizationIdByName(slug,user.getName()).ifPresent( e -> eventManager.deleteEvent(e.getId(),user.getName()));
                return "Ok";
            });
        if(result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }


    @PostMapping("/update/{slug}")
    @Transactional
    public ResponseEntity<String> update(@PathVariable String slug, @RequestBody EventCreationRequest request, Principal user) {
        accessService.checkEventOwnership(user, slug);
        String imageRef = fetchImage(request.getImageUrl());

        Result<String> result =  new Result.Builder<String>()
            .build(() -> updateEvent(slug, request, user, imageRef).map(Event::getShortName).orElseThrow());

        if(result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{slug}/subscriptions")
    public ResponseEntity<LinkedSubscription> getLinkedSubscriptions(@PathVariable String slug, Principal user) {
        var event = accessService.checkEventOwnership(user, slug);
        return ResponseEntity.ok(retrieveLinkedSubscriptionsForEvent(slug, event.getId(), event.getOrganizationId()));
    }

    @PutMapping("/{slug}/subscriptions")
    public ResponseEntity<LinkedSubscription> updateLinkedSubscriptions(@PathVariable String slug,
                                                                        @RequestBody List<LinkSubscriptionsToEventRequest> subscriptions,
                                                                        Principal user) {
        var eventAndOrgId = accessService.checkDescriptorsLinkRequest(user, slug, subscriptions);
        eventManager.updateLinkedSubscriptions(subscriptions, eventAndOrgId.getId(), eventAndOrgId.getOrganizationId());
        return ResponseEntity.ok(retrieveLinkedSubscriptionsForEvent(slug, eventAndOrgId.getId(), eventAndOrgId.getOrganizationId()));
    }

    @PostMapping("/{slug}/generate-subscribers-tickets")
    public ResponseEntity<Boolean> generateTicketsForSubscribers(@PathVariable String slug,
                                                                 Principal user) {
        var eventAndOrganizationId = accessService.checkEventOwnership(user, slug);
        Map<String, Object> params = Map.of(
            AssignTicketToSubscriberJobExecutor.EVENT_ID, eventAndOrganizationId.getId(),
            AssignTicketToSubscriberJobExecutor.ORGANIZATION_ID, eventAndOrganizationId.getOrganizationId(),
            AssignTicketToSubscriberJobExecutor.FORCE_GENERATION, true
        );
        return ResponseEntity.ok(adminJobManager.scheduleExecution(ASSIGN_TICKETS_TO_SUBSCRIBERS, params));
    }

    @GetMapping("/{slug}/check-in-log")
    public ResponseEntity<List<CheckInLogEntry>> checkInLog(@PathVariable String slug,
                                                      Principal user) {
        accessService.checkEventOwnership(user, slug);
        try {
            return ResponseEntity.ok(checkInManager.retrieveLogEntries(slug, user.getName()));
        } catch (Exception ex) {
            log.error("Error while loading check-in log entries", ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    private LinkedSubscription retrieveLinkedSubscriptionsForEvent(String slug, int id, int organizationId) {
        var subscriptions = eventManager.getLinkedSubscriptions(id, organizationId);
        return new LinkedSubscription(slug,
            subscriptions.stream().collect(Collectors.toMap(EventSubscriptionLink::getSubscriptionDescriptorId, EventSubscriptionLink::getCompatibleCategories))
        );
    }

    private Optional<Event> updateEvent(String slug, EventCreationRequest request, Principal user, String imageRef) {
        Organization organization = userManager.findUserOrganizations(user.getName()).get(0);
        EventWithAdditionalInfo original = eventStatisticsManager.getEventWithAdditionalInfo(slug,user.getName());

        Event event = original.getEvent();
        int originalSeats = original.getAvailableSeats();

        EventModification em = request.toEventModificationUpdate(original, organization, imageRef);

        eventManager.updateEventHeader(event, em, user.getName());


        if (originalSeats > em.getAvailableSeats()) {
            // if the caller wants to decrease the number of seats, we have to shrink categories first
            handleCategoriesUpdate(user, em, original, event);
            eventManager.updateEventSeatsAndPrices(event, em, user.getName(), false);
        } else {
            // in all other cases, we need to modify the event first
            eventManager.updateEventSeatsAndPrices(event, em, user.getName(), false);
            handleCategoriesUpdate(user, em, original, event);
        }

        return eventManager.getOptionalByName(slug,user.getName());
    }

    private void handleCategoriesUpdate(Principal user, EventModification em, EventWithAdditionalInfo original, Event event) {
        if (em.getTicketCategories() != null && !em.getTicketCategories().isEmpty()) {
            var existingCategories = original.getTicketCategories();
            em.getTicketCategories().stream().sorted(Comparator.comparing(TicketCategoryModification::getMaxTickets)).forEach(c -> {
                var existingCategory = findExistingCategory(existingCategories, c.getName(), c.getId());
                if (existingCategory.isPresent()) {
                    eventManager.updateCategory(existingCategory.get().getId(), event.getId(), c, user.getName());
                } else {
                    eventManager.insertCategory(event.getId(), c, user.getName());
                }
            });
        }
    }

    private Optional<String> insertEvent(EventCreationRequest request, Principal user, String imageRef) {
        try {
            Organization organization = userManager.findUserOrganizations(user.getName()).get(0);
            EventModification em = request.toEventModification(organization,eventNameManager::generateShortName,imageRef);
            eventManager.createEvent(em, user.getName());
            var eventWithStatistics = eventStatisticsManager.getEventWithAdditionalInfo(em.getShortName(),user.getName());
            var event = eventWithStatistics.getEvent();
            Optional.ofNullable(request.getTickets().getPromoCodes()).ifPresent(promoCodes ->
                promoCodes.forEach(pc -> //TODO add ref to categories
                    eventManager.addPromoCode(
                        pc.getName(),
                        event.getId(),
                        organization.getId(),
                        ZonedDateTime.of(pc.getValidFrom(),event.getZoneId()),
                        ZonedDateTime.of(pc.getValidTo(),event.getZoneId()),
                        pc.getDiscount(),
                        pc.getDiscountType(),
                        Collections.emptyList(),
                        null,
                        null,
                        null,
                        PromoCodeDiscount.CodeType.DISCOUNT,
                        null,
                        pc.getDiscountType() != PromoCodeDiscount.DiscountType.PERCENTAGE ? event.getCurrency() : null
                    )
                )
            );
            //link categories to groups, if any
            request.getTickets().getCategories().stream()
                .filter(cr -> cr.getGroupLink() != null && cr.getGroupLink().getGroupId() != null)
                .map(cr -> Pair.of(cr, groupManager.findById(cr.getGroupLink().getGroupId(), organization.getId())))
                .forEach(link -> {
                    if(link.getRight().isPresent()) {
                        Group group = link.getRight().get();
                        EventCreationRequest.CategoryRequest categoryRequest = link.getLeft();
                        findExistingCategory(eventWithStatistics.getTicketCategories(), categoryRequest.getName(), categoryRequest.getId()).ifPresent(category -> {
                            EventCreationRequest.GroupLinkRequest groupLinkRequest = categoryRequest.getGroupLink();
                            LinkedGroupModification modification = new LinkedGroupModification(null,
                                group.getId(),
                                event.getId(),
                                category.getId(),
                                groupLinkRequest.getType(),
                                groupLinkRequest.getMatchType(),
                                groupLinkRequest.getMaxAllocation());
                            groupManager.createLink(group.getId(), event.getId(), modification);
                        });
                    }
                });
            if(!CollectionUtils.isEmpty(request.getExtensionSettings())) {
                request.getExtensionSettings().stream()
                    .collect(Collectors.groupingBy(EventCreationRequest.ExtensionSetting::getExtensionId))
                    .forEach((id,settings) -> {
                        List<ExtensionSupport.ExtensionMetadataIdAndName> metadata = extensionService.getSingle(organization, event, id)
                            .map(es -> extensionRepository.findAllParametersForExtension(es.getId()))
                            .orElseGet(Collections::emptyList);

                        List<ExtensionMetadataValue> values = settings.stream()
                            .map(es -> Pair.of(es, metadata.stream().filter(mm -> mm.getName().equals(es.getKey())).findFirst()))
                            .filter(pair -> {
                                if (pair.getRight().isEmpty()) {
                                    log.warn("ignoring non-existent extension setting key {}", pair.getLeft().getKey());
                                }
                                return pair.getRight().isPresent();
                            })
                            .map(pair -> new ExtensionMetadataValue(pair.getRight().get().getId(), pair.getLeft().getValue()))
                            .toList();
                        extensionService.bulkUpdateEventSettings(organization, event, values);
                    });

            }
            return Optional.of(event.getShortName());
        } catch (Exception ex) {
            log.error("Error while inserting event", ex);
            return Optional.empty();
        }
    }

    private String fetchImage(String url) {
        if(url != null) {
            FileDownloadManager.DownloadedFile file = fileDownloadManager.downloadFile(url);
            return file != null ? fileUploadManager.insertFile(file.toUploadBase64FileModification()) : null;
        } else {
            return null;
        }
    }
}
