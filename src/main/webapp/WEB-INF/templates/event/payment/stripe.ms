<div class="row wMarginTop wMarginBottom">
    <div class="col-md-3 col-sm-3 col-xs-9"><a href="https://stripe.com/" target="_blank" rel="noreferrer noopener"><img src="{{request.contextPath}}/resources/images/outline.png"></a></div>
    <div class="col-md-9 col-sm-9 col-xs-12 text-muted">{{#i18n}}reservation-page.credit-card.description{{/i18n}}</div>
    {{#demoModeEnabled}}
        <div class="col-md-offset-3 col-sm-offset-3 col-md-9 col-sm-9 col-xs-12 text-muted">{{#i18n}}reservation-page.credit-card.description.demo{{/i18n}}</div>
    {{/demoModeEnabled}}
</div>

<div class="alert alert-danger payment-errors hide" role="alert">
</div>

<script src="https://checkout.stripe.com/checkout.js"></script>
<script src="{{request.contextPath}}/resources/js/payment/creditcard-stripe.js"
        id="stripe-key"
        data-stripe-key="{{stripe_p_key}}"

        data-stripe-message-incorrect_number="{{#i18n}}error.STEP2_STRIPE_incorrect_number{{/i18n}}"
        data-stripe-message-invalid_number="{{#i18n}}error.STEP2_STRIPE_invalid_number{{/i18n}}"
        data-stripe-message-invalid_expiry_month="{{#i18n}}error.STEP2_STRIPE_invalid_expiry_month{{/i18n}}"
        data-stripe-message-invalid_expiry_year="{{#i18n}}error.STEP2_STRIPE_invalid_expiry_year{{/i18n}}"
        data-stripe-message-invalid_cvc="{{#i18n}}error.STEP2_STRIPE_invalid_cvc{{/i18n}}"
        data-stripe-message-expired_card="{{#i18n}}error.STEP2_STRIPE_expired_card{{/i18n}}"
        data-stripe-message-incorrect_cvc="{{#i18n}}error.STEP2_STRIPE_incorrect_cvc{{/i18n}}"
        data-stripe-message-incorrect_zip="{{#i18n}}error.STEP2_STRIPE_incorrect_zip{{/i18n}}"
        data-stripe-message-card_declined="{{#i18n}}error.STEP2_STRIPE_card_declined{{/i18n}}"
        data-stripe-message-missing="{{#i18n}}error.STEP2_STRIPE_missing{{/i18n}}"
        data-stripe-message-processing_error="{{#i18n}}error.STEP2_STRIPE_processing_error{{/i18n}}"
        data-stripe-message-abort="{{#i18n}}error.STEP2_STRIPE_abort{{/i18n}}"
        data-stripe-message-rate_limit="{{#i18n}}error.STEP2_STRIPE_rate_limit{{/i18n}}"
        data-stripe-title="{{event.displayName}}"
        data-stripe-email="{{reservation.email}}"
        data-price="{{orderSummary.priceInCents}}"
        data-currency="{{event.currency}}"
        data-stripe-description="{{orderSummary.descriptionForPayment}}"
        defer async
></script>
