{{ticketReservation.fullName}}<{{ticketReservation.email}}> has completed the reservation {{ticketReservation.id}} for event {{event.displayName}}

{{#orderSummary.summary}}
Category: {{name}}, Quantity: {{amount}}, Subtotal: {{subTotal}} {{event.currency}}, Payment Method: {{ticketReservation.paymentMethod}}{{/orderSummary.summary}}

{{^orderSummary.free}}{{^ticketReservation.vatIncluded}}VAT {{ticketReservation.usedVatPercent}}%: {{orderSummary.totalVAT}} {{event.currency}}{{/ticketReservation.vatIncluded}}{{/orderSummary.free}}

Total: {{orderSummary.totalPrice}} {{event.currency}} {{^orderSummary.displayVat}}(Vat Exempt){{/orderSummary.displayVat}}

{{^orderSummary.free}}{{#ticketReservation.vatIncluded}}VAT INCL {{ticketReservation.usedVatPercent}}%: {{orderSummary.totalVAT}} {{event.currency}}{{/ticketReservation.vatIncluded}}{{/orderSummary.free}}

Reservation id: {{ticketReservation.id}}.

Ticket identifier:
{{#tickets}} - {{uuid}}
{{/tickets}}