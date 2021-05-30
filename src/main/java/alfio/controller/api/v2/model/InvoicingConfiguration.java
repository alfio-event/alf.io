package alfio.controller.api.v2.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class InvoicingConfiguration {
    private final boolean userCanDownloadReceiptOrInvoice;
    private final boolean euVatCheckingEnabled;
    private final boolean invoiceAllowed;
    private final boolean onlyInvoice;
    private final boolean customerReferenceEnabled;
    private final boolean enabledItalyEInvoicing;
    private final boolean vatNumberStrictlyRequired;
}
