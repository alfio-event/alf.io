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

import alfio.manager.*;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.api.v1.admin.*;
import alfio.model.api.v1.admin.ReservationConfirmationResponse.HolderDetail;
import alfio.model.api.v1.admin.subscription.Owner;
import alfio.model.metadata.SubscriptionMetadata;
import alfio.model.modification.AdminReservationModification.Notification;
import alfio.model.modification.AttendeeData;
import alfio.model.modification.AttendeeResources;
import alfio.model.result.ErrorCode;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.user.Role;
import alfio.util.ClockProvider;
import alfio.util.ReservationUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.ReservationUtil.handleReservationCreationErrors;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.stream.Collectors.*;

@RestController
@RequestMapping("/api/v1/admin")
public class ReservationApiV1Controller {

    private final TicketReservationManager ticketReservationManager;
    private final PurchaseContextManager purchaseContextManager;
    private final EventManager eventManager;
    private final PromoCodeRequestManager promoCodeRequestManager;
    private final AdminReservationManager adminReservationManager;
    private final AdditionalServiceManager additionalServiceManager;
    private final AccessService accessService;
    private final PurchaseContextFieldManager purchaseContextFieldManager;
    private final ConfigurationManager configurationManager;
    private final AdminReservationRequestManager requestManager;

    public ReservationApiV1Controller(TicketReservationManager ticketReservationManager,
                                      PurchaseContextManager purchaseContextManager,
                                      PromoCodeRequestManager promoCodeRequestManager,
                                      EventManager eventManager,
                                      AccessService accessService,
                                      AdditionalServiceManager additionalServiceManager,
                                      AdminReservationManager adminReservationManager,
                                      PurchaseContextFieldManager purchaseContextFieldManager,
                                      ConfigurationManager configurationManager,
                                      AdminReservationRequestManager requestManager) {
        this.ticketReservationManager = ticketReservationManager;
        this.purchaseContextManager = purchaseContextManager;
        this.promoCodeRequestManager = promoCodeRequestManager;
        this.eventManager = eventManager;
        this.accessService = accessService;
        this.additionalServiceManager = additionalServiceManager;
        this.adminReservationManager = adminReservationManager;
        this.purchaseContextFieldManager = purchaseContextFieldManager;
        this.configurationManager = configurationManager;
        this.requestManager = requestManager;
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/reservation/{id}")
    public ResponseEntity<ReservationDetail> retrieveDetail(@PathVariable PurchaseContextType purchaseContextType,
                                                            @PathVariable String publicIdentifier,
                                                            @PathVariable("id") String reservationId,
                                                            Principal principal) {
        accessService.checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        // it is guaranteed by the check above, that reservation and purchaseContext exists
        var reservation = ticketReservationManager.findById(reservationId).orElseThrow();
        var purchaseContext = purchaseContextManager.findBy(purchaseContextType, publicIdentifier).orElseThrow();
        List<AttendeesByCategory> attendeesByCategories = List.of();
        List<Owner> subscriptionOwners = List.of();
        if (purchaseContext.ofType(PurchaseContextType.event)) {
            var conf = configurationManager.getFor(EnumSet.of(ENABLE_WALLET, ENABLE_PASS, BASE_URL), purchaseContext.getConfigurationLevel());
            var tickets = adminReservationManager.findTicketsWithMetadata(reservationId);
            var valuesByTicketId = purchaseContextFieldManager.findAllValuesByTicketIds(tickets.stream().map(t -> t.getTicket().getId())
                .toList());
            var ticketsByCategories = tickets.stream().collect(groupingBy(t -> t.getTicket().getCategoryId()));
            attendeesByCategories = ticketsByCategories.keySet().stream().map(categoryId -> {
                var ticketsForCategory = ticketsByCategories.get(categoryId);
                var attendeesData = ticketsForCategory.stream().map(tfc -> {
                    var ticket = tfc.getTicket();
                    var additional = valuesByTicketId.getOrDefault(ticket.getId(), List.of()).stream()
                        .collect(groupingBy(PurchaseContextFieldValue::getName, mapping(PurchaseContextFieldValue::getValue, toList())));
                    return new AttendeeData(
                        ticket.getFirstName(),
                        ticket.getLastName(),
                        ticket.getEmail(),
                        ticket.getExtReference(),
                        tfc.getAttributes(),
                        additional,
                        AttendeeResources.fromTicket(ticket, purchaseContext, conf));
                }).toList();
                return new AttendeesByCategory(categoryId, ticketsForCategory.size(), attendeesData, List.of());
            }).toList();
        } else {
            var subscriptionDetails = ticketReservationManager.findSubscriptionDetails(reservationId).orElseThrow();
            var subscription = subscriptionDetails.getSubscription();
            var subscriptionId = subscription.getId();
            var metadata = requireNonNullElseGet(adminReservationManager.findSubscriptionMetadata(subscriptionId), SubscriptionMetadata::empty);
            var fields = purchaseContextFieldManager.findAllValuesBySubscriptionIds(List.of(subscriptionId))
                .getOrDefault(subscriptionId, List.of())
                .stream()
                .collect(groupingBy(PurchaseContextFieldValue::getName, mapping(PurchaseContextFieldValue::getValue, toList())));
            subscriptionOwners = List.of(
                new Owner(fields, subscriptionId, subscription.getFirstName(), subscription.getLastName(), subscription.getEmail(), metadata.getProperties())
            );
        }
        return ResponseEntity.ok(
            new ReservationDetail(reservationId, reservation.getStatus(), new ReservationUser(null, reservation.getFirstName(), reservation.getLastName(), reservation.getEmail(), null), attendeesByCategories, subscriptionOwners)
        );
    }

    @PostMapping("/event/{slug}/reservation")
    @Transactional
    public ResponseEntity<CreationResponse> createTicketsReservation(@PathVariable("slug") String eventSlug,
                                                                     @RequestBody TicketReservationCreationRequest reservationCreationRequest,
                                                                     Principal principal) {
        accessService.checkEventReservationCreationRequest(principal, eventSlug, reservationCreationRequest);
        var bindingResult = new BeanPropertyBindingResult(reservationCreationRequest, "reservation");

        var optionalEvent = purchaseContextManager.findBy(PurchaseContextType.event, eventSlug);
        if (optionalEvent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var event = (Event) optionalEvent.get();
        Optional<String> promoCodeDiscount = ReservationUtil.checkPromoCode(reservationCreationRequest, event, promoCodeRequestManager, bindingResult);
        var locale = Locale.forLanguageTag(requireNonNullElseGet(reservationCreationRequest.getLanguage(), () -> event.getContentLanguages().get(0).getLanguage()));
        var selected = ReservationUtil.validateCreateRequest(reservationCreationRequest, bindingResult, ticketReservationManager, eventManager, additionalServiceManager, "", event);
        if(selected.isPresent() && !bindingResult.hasErrors()) {
            var pair = selected.get();
            return handleReservationCreationErrors(() -> ticketReservationManager.createTicketReservation(event, pair.getLeft(), pair.getRight(), promoCodeDiscount, locale, principal), bindingResult, event.getType())
                .map(id -> ResponseEntity.ok(postCreate(reservationCreationRequest, id, event, locale)))
                .orElseGet(() -> ResponseEntity.badRequest().build());
        } else {
            return ResponseEntity.badRequest()
                .body(CreationResponse.error(bindingResult.getAllErrors().stream().map(err -> ErrorCode.custom("invalid."+err.getObjectName(), err.getCode())).collect(toList())));
        }
    }

    @PostMapping("/event/{slug}/reservation/import")
    public ResponseEntity<BulkCreationResponse> bulkCreateEventReservation(@PathVariable String slug,
                                                                           @RequestBody List<TicketReservationCreationRequest> reservations,
                                                                           @RequestParam String language,
                                                                           Principal principal) {
        accessService.checkEventReservationCreationRequest(principal, slug, reservations);
        var result = requestManager.scheduleReservations(slug, language, reservations, principal.getName());
        if (result.isSuccess()) {
            return ResponseEntity.ok(new BulkCreationResponse(result.getData(), null));
        }
        return ResponseEntity.badRequest().body(new BulkCreationResponse(null, result.getErrors()));
    }

    @GetMapping("/event/{slug}/reservation/bulk/{requestId}/status")
    public ResponseEntity<AdminReservationRequestStats> getRequestStatus(@PathVariable String slug,
                                                                         @PathVariable String requestId,
                                                                         Principal principal) {
        var result = requestManager.getRequestStatus(requestId, slug, principal.getName());
        if (result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        }
        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/subscription/{id}/reservation")
    @Transactional
    public ResponseEntity<CreationResponse> createSubscriptionReservation(@PathVariable("id") String subscriptionId,
                                                                          @RequestBody SubscriptionReservationCreationRequest creationRequest,
                                                                          Principal principal) {
        accessService.checkSubscriptionDescriptorOwnership(principal, subscriptionId);
        var bindingResult = new BeanPropertyBindingResult(creationRequest, "reservation");

        var optionalDescriptor = purchaseContextManager.findBy(PurchaseContextType.subscription, subscriptionId);

        if (optionalDescriptor.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var subscriptionDescriptor = (SubscriptionDescriptor) optionalDescriptor.get();
        var locale = Locale.forLanguageTag(requireNonNullElseGet(creationRequest.getLanguage(), () -> subscriptionDescriptor.getContentLanguages().get(0).getLanguage()));
        return handleReservationCreationErrors(() -> ticketReservationManager.createSubscriptionReservation(subscriptionDescriptor, locale, principal, creationRequest.getMetadataOrNull()), bindingResult, subscriptionDescriptor.getType())
            .map(id -> ResponseEntity.ok(postCreate(creationRequest, id, subscriptionDescriptor, locale)))
            .orElseGet(() -> {
                if (bindingResult.hasErrors()) {
                    return ResponseEntity.badRequest()
                        .body(
                            CreationResponse.error(bindingResult.getAllErrors().stream().map(err -> ErrorCode.custom("invalid."+err.getObjectName(), err.getCode())).collect(toList()))
                        );
                }
                return ResponseEntity.badRequest().build();
            });
    }

    @PutMapping("/reservation/{reservationId}/confirm")
    public ResponseEntity<ReservationConfirmationResponse> confirmReservation(@PathVariable String reservationId,
                                                                              @RequestBody ReservationConfirmationRequest reservationConfirmationRequest,
                                                                              Principal principal) {

        if (!reservationConfirmationRequest.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        var result = adminReservationManager.confirmReservation(reservationId,
            principal,
            reservationConfirmationRequest.transaction(),
            Notification.orEmpty(reservationConfirmationRequest.notification()),
            reservationConfirmationRequest.reservationBillingData());

        if (result.isSuccess()) {
            var data = result.getData();
            var purchaseContext = data.getRight();
            if (purchaseContext.ofType(PurchaseContextType.event)) {
                return ResponseEntity.ok(new ReservationConfirmationResponse(
                    data.getMiddle().stream().map(t -> new HolderDetail(t.getUuid(), t.getFirstName(), t.getLastName(), t.getEmail())).collect(toList())
                ));
            } else {
                var subscriptions = ticketReservationManager.findSubscriptionDetails(data.getLeft().getId())
                    .map(List::of)
                    .orElseGet(List::of);
                return ResponseEntity.ok(new ReservationConfirmationResponse(
                    subscriptions.stream().map(s -> {
                        var subscription = s.getSubscription();
                        return new HolderDetail(subscription.getId().toString(), subscription.getFirstName(), subscription.getLastName(), subscription.getEmail());
                    }).collect(toList()))
                );
            }
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    // delete ticket from event
    @DeleteMapping("/event/{slug}/reservation/delete-ticket/{ticketUUID}")
    public ResponseEntity<Boolean> deleteTicket(@PathVariable String slug,
                                                @PathVariable String ticketUUID,
                                                Principal user) {
        accessService.checkEventTicketIdentifierMembership(user, slug, ticketUUID, EnumSet.of(Role.OWNER, Role.API_CONSUMER));
        return ResponseEntity.of(adminReservationManager.findTicketWithReservationId(ticketUUID, slug, user.getName())
            .map(ticket -> {
                // make sure that ticket belongs to the same event
                var result = adminReservationManager.removeTickets(slug,
                    ticket.getTicketsReservationId(),
                    List.of(ticket.getId()),
                    List.of(),
                    false,
                    false,
                    user.getName());
                return result.isSuccess();
            })
        );
    }

    // delete subscription
    @DeleteMapping("/subscription/{id}/reservation/delete-subscription/{subscriptionToDelete}")
    public ResponseEntity<Boolean> deleteSubscription(@PathVariable("id") String descriptorId,
                                                      @PathVariable UUID subscriptionToDelete,
                                                      Principal user) {
        accessService.checkSubscriptionDescriptorOwnership(user, descriptorId);
        return ResponseEntity.of(adminReservationManager.findReservationIdForSubscription(descriptorId, subscriptionToDelete, user)
            .map(descriptorAndReservationId -> {
                var result = adminReservationManager.removeSubscription(descriptorAndReservationId.getKey(), descriptorAndReservationId.getValue(), subscriptionToDelete, user.getName());
                return result.isSuccess();
            })
        );
    }

    private CreationResponse postCreate(ReservationAPICreationRequest creationRequest,
                                        String id,
                                        PurchaseContext purchaseContext,
                                        Locale locale) {
        var user = creationRequest.getUser();
        if(user != null) {
            ticketReservationManager.setReservationOwner(id, user.getUsername(), user.getEmail(), user.getFirstName(), user.getLastName(), locale.getLanguage());
        }
        var reservationConfiguration = creationRequest.getReservationConfiguration();
        if(reservationConfiguration != null) {
            var reservationMetadata = new ReservationMetadata(reservationConfiguration.isHideContactData(),
                false,
                false,
                reservationConfiguration.isHideConfirmationButtons(),
                reservationConfiguration.isLockEmailEdit());
            ticketReservationManager.setReservationMetadata(id, reservationMetadata);
        }
        var descriptorId = creationRequest instanceof TicketReservationCreationRequest trcr ? trcr.getSubscriptionId() : null;
        if (creationRequest instanceof SubscriptionReservationCreationRequest request && request.hasAdditionalInfo()) {
            var subscriptionId = ticketReservationManager.findSubscriptionDetails(id).orElseThrow().getSubscription().getId();
            purchaseContextFieldManager.updateFieldsForReservation(request.getSubscriptionOwner(), purchaseContext,
                null, subscriptionId);
        }
        return CreationResponse.success(id, ticketReservationManager.reservationUrlForExternalClients(id, purchaseContext, locale.getLanguage(), user != null, descriptorId));
    }

    public record CreationResponse(String id, String href, List<ErrorCode> errors) {

        public boolean isSuccess() {
            return CollectionUtils.isEmpty(errors) && StringUtils.isNotEmpty(id);
        }

        static CreationResponse success(String id, String href) {
            return new CreationResponse(id, href, null);
        }

        static CreationResponse error(List<ErrorCode> errors) {
            return new CreationResponse(null, null, errors);
        }
    }

    public record BulkCreationResponse(String batchId, List<ErrorCode> errors) {}
}
