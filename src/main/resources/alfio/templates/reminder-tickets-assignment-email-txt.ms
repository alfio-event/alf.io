{{#i18n}}email.hello [{{ticketReservation.fullName}}]{{/i18n}}

{{#i18n}}reminder.ticket-not-assigned.text [{{event.displayName}}] [{{reservationShortID}}] [{{reservationUrl}}]{{/i18n}}

{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}