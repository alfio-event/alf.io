{{#render-markdown}}{{message}}.text{{/render-markdown}}

{{#walletEnabled}}
*******************************
Add to Google Wallet
{{baseUrl}}/api/wallet/event/{{event.shortName}}/v1/version/passes/{{ticket.uuid}}
*******************************
{{/walletEnabled}}
--
{{#i18n}}alfio.credits{{/i18n}}