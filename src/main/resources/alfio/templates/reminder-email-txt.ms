{{#i18n}}email.hello [{{ticketReservation.fullName}}]{{/i18n}}

{{#i18n}}reservation.reminder.mail [{{event.displayName}}] [{{ticketReservation.id}}] [{{#format-date}}{{expirationDate}} dd MMMM yyyy HH:mm locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]] [{{reservationUrl}}]{{/i18n}}

{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}