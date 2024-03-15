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
import alfio.model.Event;
import alfio.model.PurchaseContext;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.PurchaseContextFieldValue;
import alfio.model.ReservationMetadata;
import alfio.model.api.v1.admin.*;
import alfio.model.api.v1.admin.ReservationConfirmationResponse.HolderDetail;
import alfio.model.api.v1.admin.subscription.Owner;
import alfio.model.metadata.SubscriptionMetadata;
import alfio.model.modification.AdminReservationModification.Notification;
import alfio.model.modification.AttendeeData;
import alfio.model.result.ErrorCode;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.user.Role;
import alfio.util.ReservationUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

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

    @Autowired
    public ReservationApiV1Controller(TicketReservationManager ticketReservationManager,
                                      PurchaseContextManager purchaseContextManager,
                                      PromoCodeRequestManager promoCodeRequestManager,
                                      EventManager eventManager,
                                      AccessService accessService,
                                      AdditionalServiceManager additionalServiceManager,
                                      AdminReservationManager adminReservationManager,
                                      PurchaseContextFieldManager purchaseContextFieldManager) {
        this.ticketReservationManager = ticketReservationManager;
        this.purchaseContextManager = purchaseContextManager;
        this.promoCodeRequestManager = promoCodeRequestManager;
        this.eventManager = eventManager;
        this.accessService = accessService;
        this.additionalServiceManager = additionalServiceManager;
        this.adminReservationManager = adminReservationManager;
        this.purchaseContextFieldManager = purchaseContextFieldManager;
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/reservation/{id}")
    public ResponseEntity<ReservationDetail> retrieveDetail(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                                            @PathVariable("publicIdentifier") String publicIdentifier,
                                                            @PathVariable("id") String reservationId,
                                                            Principal principal) {
        accessService.checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        // it is guaranteed by the check above, that reservation and purchaseContext exists
        var reservation = ticketReservationManager.findById(reservationId).orElseThrow();
        var purchaseContext = purchaseContextManager.findBy(purchaseContextType, publicIdentifier).orElseThrow();
        List<AttendeesByCategory> attendeesByCategories = List.of();
        List<Owner> subscriptionOwners = List.of();
        if (purchaseContext.ofType(PurchaseContextType.event)) {
            var tickets = adminReservationManager.findTicketsWithMetadata(reservationId);
            var valuesByTicketId = purchaseContextFieldManager.findAllValuesByTicketIds(tickets.stream().map(t -> t.getTicket().getId())
                .collect(toList()));
            var ticketsByCategories = tickets.stream().collect(groupingBy(t -> t.getTicket().getCategoryId()));
            attendeesByCategories = ticketsByCategories.keySet().stream().map(categoryId -> {
                var ticketsForCategory = ticketsByCategories.get(categoryId);
                var attendeesData = ticketsForCategory.stream().map(tfc -> {
                    var ticket = tfc.getTicket();
                    var additional = valuesByTicketId.getOrDefault(ticket.getId(), List.of()).stream()
                        .collect(groupingBy(PurchaseContextFieldValue::getName, mapping(PurchaseContextFieldValue::getValue, toList())));
                    return new AttendeeData(ticket.getFirstName(), ticket.getLastName(), ticket.getEmail(), tfc.getAttributes(), additional);
                }).collect(toList());
                return new AttendeesByCategory(categoryId, ticketsForCategory.size(), attendeesData, List.of());
            }).collect(toList());
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
            new ReservationDetail(reservationId, new ReservationUser(null, reservation.getFirstName(), reservation.getLastName(), reservation.getEmail(), null), attendeesByCategories, subscriptionOwners)
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
            return ticketReservationManager.createTicketReservation(event, pair.getLeft(), pair.getRight(), promoCodeDiscount, locale, bindingResult, principal)
                .map(id -> ResponseEntity.ok(postCreate(reservationCreationRequest, id, event, locale)))
                .orElseGet(() -> ResponseEntity.badRequest().build());
        } else {
            return ResponseEntity.badRequest()
                .body(CreationResponse.error(bindingResult.getAllErrors().stream().map(err -> ErrorCode.custom("invalid."+err.getObjectName(), err.getCode())).collect(toList())));
        }
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
        return ticketReservationManager.createSubscriptionReservation(subscriptionDescriptor, locale, bindingResult, principal, creationRequest.getMetadataOrNull())
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
    public ResponseEntity<ReservationConfirmationResponse> confirmReservation(@PathVariable("reservationId") String reservationId,
                                                                              @RequestBody ReservationConfirmationRequest reservationConfirmationRequest,
                                                                              Principal principal) {

        if (!reservationConfirmationRequest.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        var result = adminReservationManager.confirmReservation(reservationId,
            principal,
            reservationConfirmationRequest.getTransaction(),
            Notification.orEmpty(reservationConfirmationRequest.getNotification()));

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
    public ResponseEntity<Boolean> deleteTicket(@PathVariable("slug") String slug,
                                                @PathVariable("ticketUUID") String ticketUUID,
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
                                                      @PathVariable("subscriptionToDelete") UUID subscriptionToDelete,
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
        var descriptorId = creationRequest instanceof TicketReservationCreationRequest ? ((TicketReservationCreationRequest) creationRequest).getSubscriptionId() : null;
        if (creationRequest instanceof SubscriptionReservationCreationRequest && ((SubscriptionReservationCreationRequest)creationRequest).hasAdditionalInfo()) {
            var subscriptionId = ticketReservationManager.findSubscriptionDetails(id).orElseThrow().getSubscription().getId();
            purchaseContextFieldManager.updateFieldsForReservation(((SubscriptionReservationCreationRequest) creationRequest).getSubscriptionOwner(), purchaseContext,
                null, subscriptionId);
        }
        return CreationResponse.success(id, ticketReservationManager.reservationUrlForExternalClients(id, purchaseContext, locale.getLanguage(), user != null, descriptorId));
    }

    public static class CreationResponse {
        private final String id;
        private final String href;
        private final List<ErrorCode> errors;

        private CreationResponse(String id, String href, List<ErrorCode> errors) {
            this.id = id;
            this.href = href;
            this.errors = errors;
        }

        public String getId() {
            return id;
        }

        public String getHref() {
            return href;
        }

        public List<ErrorCode> getErrors() {
            return errors;
        }

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
}
