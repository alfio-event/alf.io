{{#i18n}}email.hello [{{ticket.fullName}}]{{/i18n}}

{{#i18n}}email-ticket.attached [{{event.displayName}}]{{/i18n}}

{{#i18n}}email-confirmation.reservationURL [{{ticketUrl}}]{{/i18n}}

#### {{#i18n}}email.event-info{{/i18n}} ####

{{#i18n}}email.event-reminder-location{{/i18n}}

 {{event.location}}

{{#i18n}}email.event-reminder-date{{/i18n}}

{{#event.sameDay}}{{#i18n}}event-days.same-day [{{#format-date}}{{event.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{event.begin}} HH:mm{{/format-date}}] [{{#format-date}}{{event.end}} HH:mm (z){{/format-date}}] {{/i18n}}{{/event.sameDay}}
{{^event.sameDay}}{{#i18n}}event-days.not-same-day [{{#format-date}}{{event.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{event.begin}} HH:mm{{/format-date}}]{{/i18n}} - {{#i18n}}event-days.not-same-day [{{#format-date}}{{event.end}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{event.end}} HH:mm (z){{/format-date}}]{{/i18n}} {{/event.sameDay}}

{{#i18n}}email-ticket.add-to-calendar [{{event.googleCalendarUrl}}]{{/i18n}}

{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>

{{#i18n}}alfio.credits{{/i18n}}