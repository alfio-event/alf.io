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
import alfio.manager.PaymentManager;
import alfio.manager.PurchaseContextManager;
import alfio.manager.PurchaseContextSearchManager;
import alfio.model.PurchaseContext;
import alfio.model.ReservationPaymentDetail;
import alfio.model.modification.TransactionMetadataModification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/admin/api/payments")
public class AdminPaymentsApiController {

    private final PurchaseContextSearchManager purchaseContextSearchManager;
    private final PurchaseContextManager purchaseContextManager;
    private final PaymentManager paymentManager;

    public AdminPaymentsApiController(PurchaseContextSearchManager purchaseContextSearchManager,
                                      PurchaseContextManager purchaseContextManager,
                                      PaymentManager paymentManager) {
        this.purchaseContextSearchManager = purchaseContextSearchManager;
        this.purchaseContextManager = purchaseContextManager;
        this.paymentManager = paymentManager;
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/list")
    PageAndContent<List<ReservationPaymentDetail>> getPaymentsForPurchaseContext(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                                                                 @PathVariable("publicIdentifier") String publicIdentifier,
                                                                                 @RequestParam(value = "page", required = false) Integer page,
                                                                                 @RequestParam(value = "search", required = false) String search,
                                                                                 Principal principal) {
        return purchaseContextManager.findBy(purchaseContextType, publicIdentifier)
            .map(purchaseContext -> {
                var res = purchaseContextSearchManager.findAllPaymentsFor(purchaseContext, page, search);
                return new PageAndContent<>(res.getLeft(), res.getRight());
            }).orElseGet(() -> new PageAndContent<>(List.of(), 0));
    }

    @PutMapping("/{purchaseContextType}/{publicIdentifier}/reservation/{reservationId}")
    ResponseEntity<String> updateTransactionData(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                                  @PathVariable("publicIdentifier") String publicIdentifier,
                                                  @PathVariable("reservationId") String reservationId,
                                                  @RequestBody TransactionMetadataModification transactionMetadataModification,
                                                  Principal principal) {
        try {
            return ResponseEntity.of(purchaseContextManager.findBy(purchaseContextType, publicIdentifier)
                .map(purchaseContext -> {
                    var timestampModification = transactionMetadataModification.getTimestamp();
                    var timestamp = timestampModification != null ? timestampModification.toZonedDateTime(purchaseContext.getZoneId()) : null;
                    paymentManager.updateTransactionDetails(reservationId, transactionMetadataModification.getNotes(), timestamp, principal);
                    return "OK";
                }));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }
}
