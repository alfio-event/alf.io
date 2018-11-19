<div class="wMarginBottom wMarginTop text-muted">
    {{#i18n}}reservation-page.paypal.description{{/i18n}}
    {{#demoModeEnabled}}
        <p>{{#i18n}}reservation-page.paypal.demo [{{paypalTestUsername}}] [{{paypalTestPassword}}]{{/i18n}}</p>
    {{/demoModeEnabled}}
</div>
<script src="{{request.contextPath}}/resources/js/payment/paypal.js" defer async></script>

{{#tokenAcquired}}
    <div class="alert alert-success">
        <h3><i class="fa fa-check"></i> {{#i18n}}reservation-page.paypal.confirm{{/i18n}}</h3>
    </div>
{{/tokenAcquired}}