{{#i18n}}email.hello [{{reservation.fullName}}]{{/i18n}}

{{#i18n}}email-transaction-failed.text [{{reservationId}}]{{/i18n}}
{{#reservationCancelled}}
{{#i18n}}email-transaction-failed.cancelled{{/i18n}}
{{/reservationCancelled}}{{^reservationCancelled}}
{{#i18n}}email-transaction-failed.retry [{{reservationUrl}}]{{/i18n}}
{{/reservationCancelled}}

{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
{{#hasMailFooter}}{{#render-markdown}}{{mailFooter}}.text{{/render-markdown}}{{/hasMailFooter}}