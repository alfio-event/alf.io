{{ticketReservation.fullName}}<{{ticketReservation.email}}> has completed the reservation {{reservationShortID}} for {{purchaseContext.displayName}}

{{#orderSummary.summary}}
Category: {{name}}, Quantity: {{amount}}, Subtotal: {{subTotal}} {{purchaseContext.currency}}, Payment Method: {{ticketReservation.paymentMethod}}{{/orderSummary.summary}}

{{^orderSummary.free}}{{^ticketReservation.vatIncluded}}VAT {{ticketReservation.usedVatPercent}}%: {{orderSummary.totalVAT}} {{purchaseContext.currency}}{{/ticketReservation.vatIncluded}}{{/orderSummary.free}}

Total: {{orderSummary.totalPrice}} {{purchaseContext.currency}} {{^orderSummary.displayVat}}(Vat Exempt){{/orderSummary.displayVat}}

{{^orderSummary.free}}{{#ticketReservation.vatIncluded}}VAT INCL {{ticketReservation.usedVatPercent}}%: {{orderSummary.totalVAT}} {{purchaseContext.currency}}{{/ticketReservation.vatIncluded}}{{/orderSummary.free}}

Reservation id: {{publicId}}.

{{#ticketReservation.invoicingAdditionalInfo}}
    {{#italianEInvoicing}}
VAT Nr.: {{ticketReservation.vatNr}}
Fiscal Code: {{fiscalCode}}
Reference Type: {{referenceType}}
Addressee Code: {{addresseeCode}}
PEC: {{pec}}
    {{/italianEInvoicing}}
{{/ticketReservation.invoicingAdditionalInfo}}

Ticket identifier:
{{#tickets}} - {{uuid}}
{{/tickets}}