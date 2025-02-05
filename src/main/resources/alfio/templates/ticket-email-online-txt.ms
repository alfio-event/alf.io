{{#i18n}}email.hello [{{ticket.fullName}}]{{/i18n}}

{{#i18n}}email-ticket.thank-you{{/i18n}}
{{#i18n}}email-ticket.confirmation-event [{{event.displayName}}]{{/i18n}}
{{#render-markdown}}{{custom-header-text}}.text{{/render-markdown}}
*********************************************
{{#i18n}}email.event.online.important-information{{/i18n}}

{{#customCheckInUrlText}}{{.}}{{/customCheckInUrlText}}{{^customCheckInUrlText}}{{#i18n}}email.event.online.check-in{{/i18n}}{{/customCheckInUrlText}}
{{onlineCheckInUrl}}

{{#customCheckInUrlDescription}}{{.}}{{/customCheckInUrlDescription}}
{{prerequisites}}
*********************************************

{{#i18n}}email-confirmation.reservationURL [{{ticketUrl}}]{{/i18n}}
{{#render-markdown}}{{custom-body-text}}.text{{/render-markdown}}
#### {{#i18n}}email.event-info{{/i18n}} ####

{{#i18n}}event.location.online{{/i18n}}

{{#i18n}}email.event-reminder-date{{/i18n}}
{{#event.sameDay}}{{#i18n}}event-days.same-day [{{#format-date}}{{validityStart}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{validityStart}} HH:mm{{/format-date}}] [{{#format-date}}{{validityEnd}} HH:mm (z){{/format-date}}] {{/i18n}}{{/event.sameDay}}
{{^event.sameDay}}{{#i18n}}event-days.not-same-day [{{#format-date}}{{validityStart}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{validityStart}} HH:mm{{/format-date}}]{{/i18n}} - {{#i18n}}event-days.not-same-day [{{#format-date}}{{validityEnd}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{validityEnd}} HH:mm (z){{/format-date}}]{{/i18n}} {{/event.sameDay}}

{{#render-markdown}}{{custom-footer-text}}.text{{/render-markdown}}
{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}

{{#i18n}}alfio.credits{{/i18n}}