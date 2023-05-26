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
import alfio.manager.system.ConfigurationManager;
import alfio.model.PurchaseContext;
import alfio.model.ReservationPaymentDetail;
import alfio.model.modification.TransactionMetadataModification;
import alfio.model.system.ConfigurationKeys;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static alfio.util.ExportUtils.exportExcel;
import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@RestController
@RequestMapping("/admin/api/payments")
public class AdminPaymentsApiController {

    private static final String[] EXPORT_COLUMNS = new String[] {
        "ID",
        "Name",
        "Email",
        "Type",
        "Amount",
        "Currency",
        "Payment Date/Time",
        "Notes"
    };
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PurchaseContextSearchManager purchaseContextSearchManager;
    private final PurchaseContextManager purchaseContextManager;
    private final PaymentManager paymentManager;
    private final ConfigurationManager configurationManager;

    public AdminPaymentsApiController(PurchaseContextSearchManager purchaseContextSearchManager,
                                      PurchaseContextManager purchaseContextManager,
                                      PaymentManager paymentManager,
                                      ConfigurationManager configurationManager) {
        this.purchaseContextSearchManager = purchaseContextSearchManager;
        this.purchaseContextManager = purchaseContextManager;
        this.paymentManager = paymentManager;
        this.configurationManager = configurationManager;
    }

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/list")
    PageAndContent<List<ReservationPaymentDetail>> getPaymentsForPurchaseContext(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                                                                 @PathVariable("publicIdentifier") String publicIdentifier,
                                                                                 @RequestParam(value = "page", required = false) Integer page,
                                                                                 @RequestParam(value = "search", required = false) String search,
                                                                                 Principal principal) {
        return purchaseContextManager.findBy(purchaseContextType, publicIdentifier)
            .filter(purchaseContext -> purchaseContextManager.validateAccess(purchaseContext, principal))
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
                .filter(purchaseContext -> purchaseContextManager.validateAccess(purchaseContext, principal))
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

    @GetMapping("/{purchaseContextType}/{publicIdentifier}/download")
    void exportPayments(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                        @PathVariable("publicIdentifier") String publicIdentifier,
                        @RequestParam(value = "search", required = false) String search,
                        Principal principal,
                        HttpServletResponse response) throws IOException {
        var purchaseContextOptional = purchaseContextManager.findBy(purchaseContextType, publicIdentifier);
        if (purchaseContextOptional.isPresent() && purchaseContextManager.validateAccess(purchaseContextOptional.get(), principal)) {
            var purchaseContext = purchaseContextOptional.get();
            boolean useInvoiceNumber = configurationManager.getFor(ConfigurationKeys.USE_INVOICE_NUMBER_AS_ID, purchaseContext.getConfigurationLevel())
                .getValueAsBooleanOrDefault();
            var data = purchaseContextSearchManager.findAllPaymentsForExport(purchaseContext, search)
                .stream()
                .map(d -> new String[] {
                    useInvoiceNumber ? requireNonNullElse(trimToNull(d.getInvoiceNumber()), "N/A") : d.getId(),
                    d.getFirstName() + " " + d.getLastName(),
                    d.getEmail(),
                    d.getPaymentMethod(),
                    d.getPaidAmount(),
                    d.getCurrencyCode(),
                    formatTimestamp(purchaseContext, d.getTransactionTimestamp()),
                    d.getTransactionNotes()
                });
            exportExcel(purchaseContext.getDisplayName()+" payments.xlsx", "Payments", EXPORT_COLUMNS, data, response);
        } else {
            response.setContentType("text/plain");
            response.setStatus(HttpStatus.PRECONDITION_REQUIRED.value());
            response.getWriter().write("No payments found");
        }
    }

    private static String formatTimestamp(PurchaseContext purchaseContext, String timestamp) {
        return ZonedDateTime.parse(timestamp)
            .withZoneSameInstant(purchaseContext.getZoneId())
            .format(DATE_TIME_FORMATTER);
    }
}
