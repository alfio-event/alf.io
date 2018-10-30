<div class="row wMarginTop wMarginBottom">
    <div class="col-md-3 col-sm-3 col-xs-9"><a href="https://stripe.com/" target="_blank" rel="noreferrer noopener"><img src="{{request.contextPath}}/resources/images/outline.png"></a></div>
    <div class="col-md-9 col-sm-9 col-xs-12 text-muted">{{#i18n}}reservation-page.credit-card.description{{/i18n}}</div>
    {{#demoModeEnabled}}
        <div class="col-md-offset-3 col-sm-offset-3 col-md-9 col-sm-9 col-xs-12 text-muted">{{#i18n}}reservation-page.credit-card.description.demo{{/i18n}}</div>
    {{/demoModeEnabled}}
</div>

<div class="alert alert-danger payment-errors hide" role="alert">
</div>