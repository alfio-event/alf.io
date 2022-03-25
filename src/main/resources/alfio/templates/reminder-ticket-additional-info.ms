{{#i18n}}email.hello [{{fullName}}]{{/i18n}}

{{#i18n}}reminder.ticket-additional-info.text [{{event.displayName}}] [{{ticketURL}}]{{/i18n}}

{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}