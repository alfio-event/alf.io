{{purchaseContextTitle}}

{{#i18n}}email.hello [{{fullName}}]{{/i18n}}
{{#render-markdown}}{{custom-header-text}}.text{{/render-markdown}}
{{^orderSummary.waitingForPayment}}{{#i18n}}email-confirmation.subscription.completed{{/i18n}}{{/orderSummary.waitingForPayment}}
{{#orderSummary.waitingForPayment}}{{#i18n}}email-confirmation.subscription.waiting-for-payment [{{reservationUrl}}]{{/i18n}}{{/orderSummary.waitingForPayment}}

{{^orderSummary.waitingForPayment}}
{{#includePin}}
#### {{#i18n}}reservation-page-complete.subscription{{/i18n}} ####

{{#i18n}}reservation-page-complete.subscription.pin-description{{/i18n}}
{{pin}}

{{#i18n}}reservation-page-complete.subscription.id-description{{/i18n}}
{{subscriptionId}}

{{#i18n}}reservation-page-complete.buy-tickets{{/i18n}}: {{baseUrl}}/events-all?subscription={{subscriptionId}}
{{/includePin}}
{{/orderSummary.waitingForPayment}}

#### {{#i18n}}email-confirmation.reservation-summary{{/i18n}} ####

{{#orderSummary.summary}}
{{#i18n}}email-confirmation.summary.category{{/i18n}}: {{name}}, {{#i18n}}email-confirmation.summary.quantity{{/i18n}}: {{amount}}, {{#i18n}}email-confirmation.summary.subtotal{{/i18n}}: {{subTotal}} {{event.currency}}
{{/orderSummary.summary}}
{{^orderSummary.free}}{{^ticketReservation.vatIncluded}}
{{#i18n}}reservation-page.vat [{{ticketReservation.usedVatPercent}}] [{{vatTranslation}}]{{/i18n}}: {{orderSummary.totalVAT}} {{event.currency}}{{/ticketReservation.vatIncluded}}{{/orderSummary.free}}
{{#i18n}}reservation-page.total{{/i18n}} {{orderSummary.totalPrice}} {{event.currency}}
{{^orderSummary.free}}{{#ticketReservation.vatIncluded}}
{{#i18n}}reservation-page.vat-included [{{ticketReservation.usedVatPercent}}] [{{vatTranslation}}]{{/i18n}}: {{orderSummary.totalVAT}} {{event.currency}}{{/ticketReservation.vatIncluded}}{{/orderSummary.free}}

{{#orderSummary.notYetPaid}}{{#i18n}}reservation.not-yet-paid [{{orderSummary.totalPrice}} {{event.currency}}]{{/i18n}}{{/orderSummary.notYetPaid}}

{{^orderSummary.free}}{{#hasVat}}{{#i18n}}email-confirmation.vatNr [{{vatTranslation}}]{{/i18n}}: {{vatNr}}{{/hasVat}}{{/orderSummary.free}}

{{#i18n}}email-confirmation.order-information{{/i18n}}: {{ticketReservation.id}}
{{#render-markdown}}{{custom-body-text}}.text{{/render-markdown}}

{{#render-markdown}}{{custom-footer-text}}.text{{/render-markdown}}
{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}