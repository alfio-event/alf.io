{{ticketReservation.fullName}}<{{ticketReservation.email}}> has completed the reservation {{reservationShortID}} for event {{event.displayName}}

{{#orderSummary.summary}}
Category: {{name}}, Quantity: {{amount}}, Subtotal: {{subTotal}} {{event.currency}}, Payment Method: {{ticketReservation.paymentMethod}}{{/orderSummary.summary}}

{{^orderSummary.free}}{{^ticketReservation.vatIncluded}}VAT {{ticketReservation.usedVatPercent}}%: {{orderSummary.totalVAT}} {{event.currency}}{{/ticketReservation.vatIncluded}}{{/orderSummary.free}}

Total: {{orderSummary.totalPrice}} {{event.currency}} {{^orderSummary.displayVat}}(Vat Exempt){{/orderSummary.displayVat}}

{{^orderSummary.free}}{{#ticketReservation.vatIncluded}}VAT INCL {{ticketReservation.usedVatPercent}}%: {{orderSummary.totalVAT}} {{event.currency}}{{/ticketReservation.vatIncluded}}{{/orderSummary.free}}

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