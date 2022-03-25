{{#i18n}}email.hello [{{ticketReservation.fullName}}]{{/i18n}}

{{#i18n}}reservation.credit-note-issued [{{purchaseContext.displayName}}]{{/i18n}}

{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}