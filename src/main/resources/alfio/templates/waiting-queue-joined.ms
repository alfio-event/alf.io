{{#i18n}}email.hello [{{fullName}}]{{/i18n}}

{{#i18n}}email-waiting-queue.subscribed.text [{{eventName}}]{{/i18n}}

{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}