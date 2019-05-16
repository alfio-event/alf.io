<div class="row wMarginTop wMarginBottom">
    <div class="col-md-3 col-sm-3 col-xs-9"><a href="https://stripe.com/" target="_blank" rel="noreferrer noopener"><img src="{{request.contextPath}}/resources/images/outline.png"></a></div>
    <div class="col-md-9 col-sm-9 col-xs-12 text-muted">{{#i18n}}reservation-page.credit-card.description{{/i18n}}</div>
    {{#demoModeEnabled}}
        <div class="col-md-offset-3 col-sm-offset-3 col-md-9 col-sm-9 col-xs-12 text-muted">{{#i18n}}reservation-page.credit-card.description.demo{{/i18n}}</div>
    {{/demoModeEnabled}}
</div>

{{#if-config-flag}}[STRIPE_ENABLE_SCA]
    <div class="wMarginTop row">
        <div class="col-xs-12">
            <div class="form-group">
                <label for="card-name" class="control-label">{{#i18n}}reservation-page.cardholder.name{{/i18n}}</label>
                <input type="text" data-stripe="name" required class="form-control" id="card-name" autocomplete="name" value="{{reservation.fullName}}">
            </div>
        </div>
    </div>
    <div class="row">
        <div class="col-xs-12">
            <div class="form-group" id="card-element-container">
                <label for="card-element" class="control-label">{{#i18n}}reservation-page.card.details{{/i18n}}</label>
                <div>
                    <div id="card-element"></div>
                </div>
                <div class="wMarginTop text-danger payment-errors hide" role="alert" id="card-errors">
                    <strong id="error-message"></strong>
                </div>
            </div>
        </div>
    </div>

    <script src="https://js.stripe.com/v3/"></script>
    <script src="{{request.contextPath}}/resources/js/payment/creditcard-embedded-stripe.js"
            id="stripe-key"
            data-stripe-key="{{stripe_p_key}}"
            data-stripe-title="{{event.displayName}}"
            data-price="{{orderSummary.priceInCents}}"
            data-currency="{{event.currency}}"
            data-stripe-description="{{orderSummary.descriptionForPayment}}"
            data-stripe-embedded="{{#config-flag}}STRIPE_ENABLE_SCA{{/config-flag}}"
            data-stripe-event-name="{{event.shortName}}"
            data-stripe-reservation-id="{{reservation.id}}"
            data-stripe-contact-name="{{reservation.fullName}}"
            data-stripe-contact-zip="{{billingDetails.zip}}"
            data-stripe-contact-country="{{billingDetails.country}}"
            data-stripe-contact-address="{{billingDetails.addressLine1}}"
            data-stripe-email="{{reservation.email}}"
            {{#platformMode}}data-stripe-on-behalf-of="{{stripeConnectedAccount}}"{{/platformMode}}
            defer async
    ></script>
{{/if-config-flag}}
{{^if-config-flag}}[STRIPE_ENABLE_SCA]
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
        data-price="{{orderSummary.priceInCents}}"
        data-currency="{{event.currency}}"
        data-stripe-description="{{orderSummary.descriptionForPayment}}"
        data-stripe-embedded="{{#config-flag}}STRIPE_ENABLE_SCA{{/config-flag}}"
        data-stripe-event-name="{{event.shortName}}"
        data-stripe-reservation-id="{{reservation.id}}"
        data-stripe-contact-name="{{reservation.fullName}}"
        data-stripe-contact-zip="{{billingDetails.zip}}"
        data-stripe-contact-country="{{billingDetails.country}}"
        data-stripe-contact-address="{{billingDetails.addressLine1}}"
        data-stripe-email="{{reservation.email}}"
        defer async
></script>
{{/if-config-flag}}
