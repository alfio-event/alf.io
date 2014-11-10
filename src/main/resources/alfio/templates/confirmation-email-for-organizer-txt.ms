{{ticketReservation.fullName}}<{{ticketReservation.email}}> has completed the reservation {{ticketReservation.id}} for event {{event.shortName}}

{{#orderSummary.summary}}
Category: {{name}}, Amount: {{amount}}, Subtotal: {{subTotal}} {{event.currency}}{{/orderSummary.summary}}

{{^orderSummary.free}}{{^event.vatIncluded}}VAT {{event.vat}}%: {{orderSummary.totalVAT}} {{event.currency}}{{/event.vatIncluded}}{{/orderSummary.free}}

Total: {{orderSummary.totalPrice}} {{event.currency}}

{{^orderSummary.free}}{{#event.vatIncluded}}VAT INCL {{event.vat}}%: {{orderSummary.totalVAT}} {{event.currency}}{{/event.vatIncluded}}{{/orderSummary.free}}

Reservation id: {{ticketReservation.id}}.

Ticket identifier:
{{#tickets}} - {{uuid}}
{{/tickets}}