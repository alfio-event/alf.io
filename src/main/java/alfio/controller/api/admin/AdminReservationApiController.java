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
package alfio.controller.api.admin;

import alfio.controller.api.support.BookingInfoTicket;
import alfio.controller.api.support.BookingInfoTicketLoader;
import alfio.controller.api.support.PageAndContent;
import alfio.manager.*;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.modification.AdminReservationModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.subscription.SubscriptionWithUsageDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static alfio.util.FileUtil.sendPdf;

@RequestMapping("/admin/api/reservation")
@RestController
@AllArgsConstructor
public class AdminReservationApiController {

    private final AdminReservationManager adminReservationManager;
    private final EventManager eventManager;
    private final PurchaseContextManager purchaseContextManager;
    private final PurchaseContextSearchManager purchaseContextSearchManager;
    private final TicketReservationManager ticketReservationManager;
    private final BookingInfoTicketLoader bookingInfoTicketLoader;
    private final AccessService accessService;

    @PostMapping("/{purchaseContextType}/{publicIdentifier}/new")
    public Result<String> createNew(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                    @PathVariable("publicIdentifier") String publicIdentifier,
                                    @RequestBody AdminReservationModification reservation,
                                    Principal principal) {
        if(purchaseContextType != PurchaseContextType.event) {
            return Result.error(ErrorCode.EventError.NOT_FOUND);
        }
        accessService.checkEventMembership(principal, publicIdentifier, AccessService.MEMBERSHIP_ROLES); // ADMIN, OWNER _and_ SUPERVISOR can create a new reservation
        return adminReservationManager.createReservation(reservation, publicIdentifier, principal.getName()).map(r -> r.getLeft().getId());
    }


    @GetMapping("/{purchaseContextType}/{publicIdentifier}/reservations/all-status")
    public TicketReservation.TicketReservationStatus[] getAllStatus(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType, @PathVariable("publicIdentifier") String publicIdentifier) {
        return TicketReservation.TicketReservationStatus.values();
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/reservations/list")
    public PageAndContent<List<TicketReservation>> findAll(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                                           @PathVariable("publicIdentifier") String publicIdentifier,
                                                           @RequestParam(value = "page", required = false) Integer page,
                                                           @RequestParam(value = "search", required = false) String search,
                                                           @RequestParam(value = "status", required = false) List<TicketReservation.TicketReservationStatus> status,
                                                           Principal principal) {

        return purchaseContextManager.findBy(purchaseContextType, publicIdentifier)
            .map(purchaseContext -> {
                accessService.checkOrganizationOwnership(principal, purchaseContext.getOrganizationId());
                Pair<List<TicketReservation>, Integer> res = purchaseContextSearchManager.findAllReservationsFor(purchaseContext, page, search, status);
                return new PageAndContent<>(res.getLeft(), res.getRight());
            }).orElseGet(() -> new PageAndContent<>(Collections.emptyList(), 0));
    }

    @PutMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/confirm")
    public Result<TicketReservationDescriptor> confirmReservation(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                                                  @PathVariable("publicIdentifier") String publicIdentifier,
                                                                  @PathVariable("reservationId") String reservationId,
                                                                  Principal principal) {
        accessService.checkReservationMembership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.confirmReservation(purchaseContextType, publicIdentifier, reservationId, principal.getName(), AdminReservationModification.Notification.EMPTY)
            .map(triple -> toReservationDescriptor(reservationId, triple));
    }

    @PostMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}")
    public Result<Boolean> updateReservation(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                             @PathVariable("publicIdentifier") String publicIdentifier,
                                             @PathVariable("reservationId") String reservationId,
                                             @RequestBody AdminReservationModification arm,
                                             Principal principal) {
        accessService.checkReservationMembership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.updateReservation(purchaseContextType, publicIdentifier, reservationId, arm, principal.getName());
    }

    @PutMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/notify")
    public Result<Boolean> notifyReservation(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                             @PathVariable("publicIdentifier") String publicIdentifier,
                                             @PathVariable("reservationId") String reservationId,
                                             @RequestBody AdminReservationModification arm,
                                             Principal principal) {
        accessService.checkReservationMembership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.notify(purchaseContextType, publicIdentifier, reservationId, arm, principal.getName());
    }

    @PutMapping("/event/{publicIdentifier}/{reservationId}/notify-attendees")
    public Result<Boolean> notifyAttendees(@PathVariable("publicIdentifier") String publicIdentifier,
                                           @PathVariable("reservationId") String reservationId,
                                           @RequestBody List<Integer> ids,
                                           Principal principal) {
        accessService.checkReservationMembership(principal, PurchaseContextType.event, publicIdentifier, reservationId);
        return adminReservationManager.notifyAttendees(publicIdentifier, reservationId, ids, principal.getName());
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/audit")
    public Result<List<Audit>> getAudit(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                        @PathVariable("publicIdentifier") String publicIdentifier,
                                        @PathVariable("reservationId") String reservationId, Principal principal) {
        accessService.checkReservationMembership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.getAudit(purchaseContextType, publicIdentifier, reservationId, principal.getName());
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/billing-documents")
    public Result<List<BillingDocument>> getBillingDocuments(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType, @PathVariable("publicIdentifier") String publicIdentifier, @PathVariable("reservationId") String reservationId, Principal principal) {
        accessService.checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.getBillingDocuments(publicIdentifier, reservationId, principal.getName());
    }

    @DeleteMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/billing-document/{documentId}")
    public ResponseEntity<Boolean> invalidateBillingDocument(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                                             @PathVariable("publicIdentifier") String publicIdentifier,
                                                             @PathVariable("reservationId") String reservationId,
                                                             @PathVariable("documentId") long documentId,
                                                             Principal principal) {
        accessService.checkBillingDocumentOwnership(principal, purchaseContextType, publicIdentifier, reservationId, documentId);
        Result<Boolean> invalidateResult = adminReservationManager.invalidateBillingDocument(reservationId, documentId, principal.getName());
        if(invalidateResult.isSuccess()) {
            return ResponseEntity.ok(invalidateResult.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/billing-document/{documentId}/restore")
    public ResponseEntity<Boolean> restoreBillingDocument(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                                          @PathVariable("publicIdentifier") String publicIdentifier,
                                                          @PathVariable("reservationId") String reservationId,
                                                          @PathVariable("documentId") long documentId,
                                                          Principal principal) {
        accessService.checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        Result<Boolean> restoreResult = adminReservationManager.restoreBillingDocument(reservationId, documentId, principal.getName());
        if(restoreResult.isSuccess()) {
            return ResponseEntity.ok(restoreResult.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/billing-document/{documentId}")
    public ResponseEntity<Void> getBillingDocument(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                                   @PathVariable("publicIdentifier") String publicIdentifier,
                                                   @PathVariable("reservationId") String reservationId,
                                                   @PathVariable("documentId") long documentId,
                                                   Principal principal,
                                                   HttpServletResponse response) {
        accessService.checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        Result<Boolean> result = adminReservationManager.getSingleBillingDocumentAsPdf(purchaseContextType, publicIdentifier, reservationId, documentId, principal.getName())
            .map(res -> sendPdf(res.getRight(), response, publicIdentifier, reservationId, res.getLeft()));
        if(result.isSuccess()) {
            return ResponseEntity.ok(null);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}")
    public Result<TicketReservationDescriptor> loadReservation(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType, @PathVariable("publicIdentifier") String publicIdentifier, @PathVariable("reservationId") String reservationId, Principal principal) {
        accessService.checkReservationMembership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.loadReservation(purchaseContextType, publicIdentifier, reservationId, principal.getName())
            .map(triple -> toReservationDescriptor(reservationId, triple));
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/ticket/{ticketId}")
    public Result<Ticket> loadTicket(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType, @PathVariable("publicIdentifier") String publicIdentifier, @PathVariable("reservationId") String reservationId, @PathVariable("ticketId") int ticketId, Principal principal) {
        accessService.checkTicketMembership(principal, publicIdentifier, reservationId, ticketId);
        return adminReservationManager.loadReservation(purchaseContextType, publicIdentifier, reservationId, principal.getName()).flatMap(triple ->
            //not optimal
            triple.getMiddle().stream()
                .filter(t -> t.getId() == ticketId)
                .findFirst()
                .map(Result::success)
                .orElse(Result.error(ErrorCode.custom("not_found", "not found")))
        );
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/tickets-with-additional-data")
    public List<Integer> ticketsWithAdditionalData(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                                   @PathVariable("publicIdentifier") String publicIdentifier,
                                                   @PathVariable("reservationId") String reservationId,
                                                   Principal principal) {
        if(purchaseContextType != PurchaseContextType.event) {
            return List.of();
        }
        accessService.checkReservationMembership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.getTicketIdsWithAdditionalData(purchaseContextType, publicIdentifier, reservationId);
    }


    @PostMapping("/event/{publicIdentifier}/{reservationId}/remove-tickets")
    public Result<RemoveResult> removeTickets(@PathVariable("publicIdentifier") String publicIdentifier,
                                         @PathVariable("reservationId") String reservationId,
                                         @RequestBody RemoveTicketsModification toRemove,
                                         Principal principal) {
        accessService.checkReservationMembership(principal, PurchaseContextType.event, publicIdentifier, reservationId);
        List<Integer> toRefund = toRemove.getRefundTo().entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        boolean issueCreditNote = adminReservationManager.removeTickets(publicIdentifier,
            reservationId,
            toRemove.getTicketIds(),
            toRefund,
            toRemove.getNotify(),
            toRemove.issueCreditNote,
            principal.getName()).getData();
        return Result.success(new RemoveResult(true, issueCreditNote));
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/payment-info")
    public Result<TransactionAndPaymentInfo> getPaymentInfo(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType, @PathVariable("publicIdentifier") String publicIdentifier, @PathVariable("reservationId") String reservationId, Principal principal) {
        accessService.checkReservationMembership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.getPaymentInfo(reservationId);
    }

    @PostMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/cancel")
    public Result<Boolean> removeReservation(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType, @PathVariable("publicIdentifier") String publicIdentifier,
                                             @PathVariable("reservationId") String reservationId,
                                             @RequestParam("refund") boolean refund,
                                             @RequestParam(value = "notify", defaultValue = "false") boolean notify,
                                             @RequestParam(value = "issueCreditNote", defaultValue = "false") boolean issueCreditNote,
                                             Principal principal) {
        accessService.checkReservationMembership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.removeReservation(purchaseContextType, publicIdentifier, reservationId, refund, notify, issueCreditNote, principal.getName());
    }

    @PostMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/credit")
    public Result<Boolean> creditReservation(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType, @PathVariable("publicIdentifier") String publicIdentifier, @PathVariable("reservationId") String reservationId, @RequestParam("refund") boolean refund,
                                             @RequestParam(value = "notify", defaultValue = "false") boolean notify,
                                             Principal principal) {
        accessService.checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        adminReservationManager.creditReservation(purchaseContextType, publicIdentifier, reservationId, refund, notify, principal.getName());
        return Result.success(true);
    }

    @PutMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/regenerate-billing-document")
    public Result<Boolean> regenerateBillingDocument(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType, @PathVariable("publicIdentifier") String publicIdentifier, @PathVariable("reservationId") String reservationId, Principal principal) {
        accessService.checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.regenerateBillingDocument(purchaseContextType, publicIdentifier, reservationId, principal.getName());
    }

    @PostMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/refund")
    public Result<Boolean> refund(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType, @PathVariable("publicIdentifier") String publicIdentifier, @PathVariable("reservationId") String reservationId, @RequestBody RefundAmount amount, Principal principal) {
        accessService.checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.refund(purchaseContextType, publicIdentifier, reservationId, new BigDecimal(amount.amount), principal.getName());
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/email-list")
    public Result<List<LightweightMailMessage>> getEmailList(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType, @PathVariable("publicIdentifier") String publicIdentifier, @PathVariable("reservationId") String reservationId, Principal principal) {
        accessService.checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        return adminReservationManager.getEmailsForReservation(purchaseContextType, publicIdentifier, reservationId, principal.getName());
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/{reservationId}/ticket/{ticketId}/full-data")
    public ResponseEntity<BookingInfoTicket> loadFullTicketData(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                                                @PathVariable("publicIdentifier") String publicIdentifier,
                                                                @PathVariable("reservationId") String reservationId,
                                                                @PathVariable("ticketId") String ticketUUID,
                                                                Principal principal) {
        if(purchaseContextType != PurchaseContextType.event) {
            return ResponseEntity.notFound().build();
        }
        accessService.checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        return ResponseEntity.of(
            adminReservationManager.loadFullTicketInfo(reservationId, publicIdentifier, ticketUUID)
                .map(eventAndTicket -> bookingInfoTicketLoader.toBookingInfoTicket(eventAndTicket.getRight(), eventAndTicket.getLeft(), PurchaseContextFieldConfiguration.EVENT_RELATED_CONTEXTS))
        );
    }

    private TicketReservationDescriptor toReservationDescriptor(String reservationId, Triple<TicketReservation, List<Ticket>, PurchaseContext> triple) {
        List<SerializablePair<TicketCategory, List<Ticket>>> tickets = triple.getMiddle().stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).entrySet().stream()
            .map(entry -> SerializablePair.of(eventManager.getTicketCategoryById(entry.getKey(), triple.getRight().event().orElseThrow().getId()), entry.getValue()))
            .collect(Collectors.toList());
        TicketReservation reservation = triple.getLeft();
        return new TicketReservationDescriptor(reservation,
            ticketReservationManager.loadAdditionalInfo(reservationId),
            ticketReservationManager.orderSummaryForReservationId(reservationId, triple.getRight()),
            tickets,
            buildSubscriptionDetails(triple.getRight(), reservation));
    }

    private SubscriptionWithUsageDetails buildSubscriptionDetails(PurchaseContext purchaseContext, TicketReservation reservation) {
        if(purchaseContext.ofType(PurchaseContextType.subscription)) {
            // load statistics
            return ticketReservationManager.findSubscriptionDetails(reservation.getId()).orElse(null);
        }
        return null;
    }

    @RequiredArgsConstructor
    @Getter
    public static class TicketReservationDescriptor {
        private final TicketReservation reservation;
        private final TicketReservationAdditionalInfo additionalInfo;
        private final OrderSummary orderSummary;
        private final List<SerializablePair<TicketCategory, List<Ticket>>> ticketsByCategory;
        private final SubscriptionWithUsageDetails subscriptionDetails;
    }

    @RequiredArgsConstructor
    @Getter
    public static class RemoveTicketsModification {
        private final List<Integer> ticketIds;
        private final Map<Integer, Boolean> refundTo;
        private final Boolean notify;
        private final Boolean issueCreditNote;

        public Boolean getNotify() {
            return Boolean.TRUE.equals(notify);
        }

        public Boolean getIssueCreditNote() {
            return Boolean.TRUE.equals(issueCreditNote);
        }
    }

    @RequiredArgsConstructor
    @Getter
    public static class RefundAmount {
        private final String amount;
    }

    public static class RemoveResult {

        private final boolean success;
        private final boolean creditNoteGenerated;

        public RemoveResult(boolean success, boolean creditNoteGenerated) {
            this.success = success;
            this.creditNoteGenerated = creditNoteGenerated;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isCreditNoteGenerated() {
            return creditNoteGenerated;
        }
    }
}
