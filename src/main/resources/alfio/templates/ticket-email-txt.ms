{{#i18n}}email.hello [{{ticket.fullName}}]{{/i18n}}
{{#render-markdown}}{{custom-header-text}}.text{{/render-markdown}}
{{#i18n}}email-ticket.attached [{{event.displayName}}]{{/i18n}}

{{#i18n}}email-confirmation.reservationURL [{{ticketUrl}}]{{/i18n}}
{{#render-markdown}}{{custom-body-text}}.text{{/render-markdown}}
#### {{#i18n}}email.event-info{{/i18n}} ####

{{#i18n}}email.event-reminder-location{{/i18n}}

 {{event.location}}

{{#i18n}}email.event-reminder-date{{/i18n}}

{{#event.sameDay}}{{#i18n}}event-days.same-day [{{#format-date}}{{validityStart}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{validityStart}} HH:mm{{/format-date}}] [{{#format-date}}{{validityEnd}} HH:mm (z){{/format-date}}] {{/i18n}}{{/event.sameDay}}
{{^event.sameDay}}{{#i18n}}event-days.not-same-day [{{#format-date}}{{validityStart}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{validityStart}} HH:mm{{/format-date}}]{{/i18n}} - {{#i18n}}event-days.not-same-day [{{#format-date}}{{validityEnd}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{validityEnd}} HH:mm (z){{/format-date}}]{{/i18n}} {{/event.sameDay}}

{{#i18n}}email-ticket.add-to-calendar [{{googleCalendarUrl}}]{{/i18n}}
{{#render-markdown}}{{custom-footer-text}}.text{{/render-markdown}}
{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}

{{#i18n}}alfio.credits{{/i18n}}