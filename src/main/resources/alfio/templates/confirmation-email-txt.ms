{{#i18n}}email.hello [{{ticketReservation.fullName}}]{{/i18n}}

{{^orderSummary.waitingForPayment}}{{#i18n}}email-confirmation.completed [{{event.displayName}}] [{{reservationUrl}}]{{/i18n}}{{/orderSummary.waitingForPayment}}
{{#orderSummary.waitingForPayment}}{{#i18n}}email-confirmation.waiting-for-payment [{{event.displayName}}] [{{reservationUrl}}]{{/i18n}}{{/orderSummary.waitingForPayment}}

{{#render-markdown}}{{custom-header-text}}.text{{/render-markdown}}

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
#### {{#i18n}}email.event-info{{/i18n}} ####

{{#i18n}}email.event-reminder-location{{/i18n}}

 {{event.location}} 

{{#i18n}}email.event-reminder-date{{/i18n}}

{{#event.sameDay}}{{#i18n}}event-days.same-day [{{#format-date}}{{event.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{event.begin}} HH:mm{{/format-date}}] [{{#format-date}}{{event.end}} HH:mm (z){{/format-date}}] {{/i18n}}{{/event.sameDay}}
{{^event.sameDay}}{{#i18n}}event-days.not-same-day [{{#format-date}}{{event.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{event.begin}} HH:mm{{/format-date}}]{{/i18n}} - {{#i18n}}event-days.not-same-day [{{#format-date}}{{event.end}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{event.end}} HH:mm (z){{/format-date}}]{{/i18n}} {{/event.sameDay}}
{{#displaySubscriptionUsage}}

########################
{{#i18n}}reservation.complete.email.subscription.used{{/i18n}}
{{#subscriptionUsageDetails}}
{{#i18n}}reservation-page-complete.subscription.usage.available [{{subscriptionUsageDetails.available}}]{{/i18n}}
{{#i18n}}reservation.confirmation.email.subscription.button{{/i18n}}: {{subscriptionUrl}}
{{/subscriptionUsageDetails}}

########################

{{/displaySubscriptionUsage}}
{{#render-markdown}}{{custom-footer-text}}.text{{/render-markdown}}
{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}