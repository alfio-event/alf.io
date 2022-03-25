{{#i18n}}email.hello [{{ticket.fullName}}]{{/i18n}}

{{#i18n}}ticket-has-changed-owner.notice [{{eventName}}] [{{previousEmail}}] [{{newEmail}}] [{{ticketUrl}}]{{/i18n}}


{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}