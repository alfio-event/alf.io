{{ticketReservation.fullName}}<{{ticketReservation.email}}> has completed the reservation {{ticketReservation.id}} for event {{event.displayName}}

{{#orderSummary.summary}}
Category: {{name}}, Quantity: {{amount}}, Subtotal: {{subTotal}} {{event.currency}}, Payment Method: {{ticketReservation.paymentMethod}}{{/orderSummary.summary}}

{{^orderSummary.free}}{{^event.vatIncluded}}VAT {{event.vat}}%: {{orderSummary.totalVAT}} {{event.currency}}{{/event.vatIncluded}}{{/orderSummary.free}}

Total: {{orderSummary.totalPrice}} {{event.currency}}

{{^orderSummary.free}}{{#event.vatIncluded}}VAT INCL {{event.vat}}%: {{orderSummary.totalVAT}} {{event.currency}}{{/event.vatIncluded}}{{/orderSummary.free}}

Reservation id: {{ticketReservation.id}}.

Ticket identifier:
{{#tickets}} - {{uuid}}
{{/tickets}}