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

import alfio.controller.api.support.PageAndContent;
import alfio.manager.AdminReservationManager;
import alfio.manager.EventManager;
import alfio.manager.TicketReservationManager;
import alfio.model.*;
import alfio.model.modification.AdminReservationModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
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
    private final TicketReservationManager ticketReservationManager;

    @PostMapping("/event/{eventName}/new")
    public Result<String> createNew(@PathVariable("eventName") String eventName, @RequestBody AdminReservationModification reservation, Principal principal) {
        return adminReservationManager.createReservation(reservation, eventName, principal.getName()).map(r -> r.getLeft().getId());
    }


    @GetMapping("/event/{eventName}/reservations/all-status")
    public TicketReservation.TicketReservationStatus[] getAllStatus(@PathVariable("eventName") String eventName) {
        return TicketReservation.TicketReservationStatus.values();
    }

    @GetMapping("/event/{eventName}/reservations/list")
    public PageAndContent<List<TicketReservation>> findAll(@PathVariable("eventName") String eventName,
                                                          @RequestParam(value = "page", required = false) Integer page,
                                                          @RequestParam(value = "search", required = false) String search,
                                                          @RequestParam(value = "status", required = false) List<TicketReservation.TicketReservationStatus> status,
                                                          Principal principal) {
        return eventManager.getOptionalEventAndOrganizationIdByName(eventName, principal.getName())
            .map(event -> {
                Pair<List<TicketReservation>, Integer> res = ticketReservationManager.findAllReservationsInEvent(event.getId(), page, search, status);
                return new PageAndContent<>(res.getLeft(), res.getRight());
            }).orElseGet(() -> new PageAndContent<>(Collections.emptyList(), 0));
    }

    @PutMapping("/event/{eventName}/{reservationId}/confirm")
    public Result<TicketReservationDescriptor> confirmReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        return adminReservationManager.confirmReservation(eventName, reservationId, principal.getName(), AdminReservationModification.Notification.EMPTY)
            .map(triple -> toReservationDescriptor(reservationId, triple));
    }

    @PostMapping("/event/{eventName}/{reservationId}")
    public Result<Boolean> updateReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
                                             @RequestBody AdminReservationModification arm, Principal principal) {
        return adminReservationManager.updateReservation(eventName, reservationId, arm, principal.getName());
    }

    @PutMapping("/event/{eventName}/{reservationId}/notify")
    public Result<Boolean> notifyReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
                                             @RequestBody AdminReservationModification arm, Principal principal) {
        return adminReservationManager.notify(eventName, reservationId, arm, principal.getName());
    }

    @PutMapping("/event/{eventName}/{reservationId}/notify-attendees")
    public Result<Boolean> notifyAttendees(@PathVariable("eventName") String eventName,
                                           @PathVariable("reservationId") String reservationId,
                                           @RequestBody List<Integer> ids,
                                           Principal principal) {
        return adminReservationManager.notifyAttendees(eventName, reservationId, ids, principal.getName());
    }

    @GetMapping("/event/{eventName}/{reservationId}/audit")
    public Result<List<Audit>> getAudit(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        return adminReservationManager.getAudit(eventName, reservationId, principal.getName());
    }

    @GetMapping("/event/{eventName}/{reservationId}/billing-documents")
    public Result<List<BillingDocument>> getBillingDocuments(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        return adminReservationManager.getBillingDocuments(eventName, reservationId, principal.getName());
    }

    @DeleteMapping("/event/{eventName}/{reservationId}/billing-document/{documentId}")
    public ResponseEntity<Boolean> invalidateBillingDocument(@PathVariable("eventName") String eventName,
                                                             @PathVariable("reservationId") String reservationId,
                                                             @PathVariable("documentId") long documentId,
                                                             Principal principal) {
        Result<Boolean> invalidateResult = adminReservationManager.invalidateBillingDocument(eventName, reservationId, documentId, principal.getName());
        if(invalidateResult.isSuccess()) {
            return ResponseEntity.ok(invalidateResult.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/event/{eventName}/{reservationId}/billing-document/{documentId}/restore")
    public ResponseEntity<Boolean> restoreBillingDocument(@PathVariable("eventName") String eventName,
                                                             @PathVariable("reservationId") String reservationId,
                                                             @PathVariable("documentId") long documentId,
                                                             Principal principal) {
        Result<Boolean> restoreResult = adminReservationManager.restoreBillingDocument(eventName, reservationId, documentId, principal.getName());
        if(restoreResult.isSuccess()) {
            return ResponseEntity.ok(restoreResult.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/event/{eventName}/{reservationId}/billing-document/{documentId}")
    public ResponseEntity<Void> getBillingDocument(@PathVariable("eventName") String eventName,
                                                   @PathVariable("reservationId") String reservationId,
                                                   @PathVariable("documentId") long documentId,
                                                   Principal principal,
                                                   HttpServletResponse response) {
        Result<Boolean> result = adminReservationManager.getSingleBillingDocumentAsPdf(eventName, reservationId, documentId, principal.getName())
            .map(res -> sendPdf(res.getRight(), response, eventName, reservationId, res.getLeft()));
        if(result.isSuccess()) {
            return ResponseEntity.ok(null);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/event/{eventName}/{reservationId}")
    public Result<TicketReservationDescriptor> loadReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        return adminReservationManager.loadReservation(eventName, reservationId, principal.getName())
            .map(triple -> toReservationDescriptor(reservationId, triple));
    }

    @GetMapping("/event/{eventName}/{reservationId}/ticket/{ticketId}")
    public Result<Ticket> loadTicket(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, @PathVariable("ticketId") int ticketId, Principal principal) {
        return adminReservationManager.loadReservation(eventName, reservationId, principal.getName()).flatMap(triple ->
            //not optimal
            triple.getMiddle().stream()
                .filter(t -> t.getId() == ticketId)
                .findFirst()
                .map(Result::success)
                .orElse(Result.error(ErrorCode.custom("not_found", "not found")))
        );
    }


    @PostMapping("/event/{eventName}/{reservationId}/remove-tickets")
    public Result<Boolean> removeTickets(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
                                             @RequestBody RemoveTicketsModification toRemove, Principal principal) {

        List<Integer> toRefund = toRemove.getRefundTo().entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        adminReservationManager.removeTickets(eventName, reservationId, toRemove.getTicketIds(), toRefund, toRemove.getNotify(), toRemove.getForceInvoiceUpdate(), principal.getName());
        return Result.success(true);
    }

    @GetMapping("/event/{eventName}/{reservationId}/payment-info")
    public Result<TransactionAndPaymentInfo> getPaymentInfo(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        return adminReservationManager.getPaymentInfo(eventName, reservationId, principal.getName());
    }

    @PostMapping("/event/{eventName}/{reservationId}/cancel")
    public Result<Boolean> removeReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, @RequestParam("refund") boolean refund,
                                             @RequestParam(value = "notify", defaultValue = "false") boolean notify,
                                             Principal principal) {
        adminReservationManager.removeReservation(eventName, reservationId, refund, notify, principal.getName());
        return Result.success(true);
    }

    @PostMapping("/event/{eventName}/{reservationId}/credit")
    public Result<Boolean> creditReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, @RequestParam("refund") boolean refund,
                                             @RequestParam(value = "notify", defaultValue = "false") boolean notify,
                                             Principal principal) {
        adminReservationManager.creditReservation(eventName, reservationId, refund, notify, principal.getName());
        return Result.success(true);
    }

    @PutMapping("/event/{eventName}/{reservationId}/regenerate-billing-document")
    public Result<Boolean> regenerateBillingDocument(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        return adminReservationManager.regenerateBillingDocument(eventName, reservationId, principal.getName());
    }

    @PostMapping("/event/{eventName}/{reservationId}/refund")
    public Result<Boolean> refund(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, @RequestBody RefundAmount amount, Principal principal) {
        return adminReservationManager.refund(eventName, reservationId, new BigDecimal(amount.amount), principal.getName());
    }

    @GetMapping("/event/{eventName}/{reservationId}/email-list")
    public Result<List<LightweightMailMessage>> getEmailList(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        return adminReservationManager.getEmailsForReservation(eventName, reservationId, principal.getName());
    }

    private TicketReservationDescriptor toReservationDescriptor(String reservationId, Triple<TicketReservation, List<Ticket>, Event> triple) {
        List<SerializablePair<TicketCategory, List<Ticket>>> tickets = triple.getMiddle().stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).entrySet().stream()
            .map(entry -> SerializablePair.of(eventManager.getTicketCategoryById(entry.getKey(), triple.getRight().getId()), entry.getValue()))
            .collect(Collectors.toList());
        TicketReservation reservation = triple.getLeft();
        return new TicketReservationDescriptor(reservation, ticketReservationManager.orderSummaryForReservationId(reservationId, triple.getRight()), tickets);
    }

    @RequiredArgsConstructor
    @Getter
    public static class TicketReservationDescriptor {
        private final TicketReservation reservation;
        private final OrderSummary orderSummary;
        private final List<SerializablePair<TicketCategory, List<Ticket>>> ticketsByCategory;
    }

    @RequiredArgsConstructor
    @Getter
    public static class RemoveTicketsModification {
        private final List<Integer> ticketIds;
        private Map<Integer, Boolean> refundTo;
        private final Boolean notify;
        private final Boolean forceInvoiceUpdate;

        public Boolean getNotify() {
            return ObjectUtils.firstNonNull(notify, Boolean.FALSE);
        }

        public Boolean getForceInvoiceUpdate() {
            return ObjectUtils.firstNonNull(forceInvoiceUpdate, Boolean.FALSE);
        }
    }

    @RequiredArgsConstructor
    @Getter
    public static class RefundAmount {
        private final String amount;
    }
}
