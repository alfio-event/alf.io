{{>/event/page-top}}

{{#includeStripe}}
 <script src="https://js.stripe.com/v2/"></script>
{{/includeStripe}}
 <script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
 <script src="{{request.contextPath}}/resources/js/h5f/h5f.min.js"></script>
 <script src="{{request.contextPath}}/resources/bower_components/bootstrap/dist/js/bootstrap.min.js"></script>
 <script src="{{request.contextPath}}/resources/js/event/bootstrap-handler.js"></script>
 <script src="{{request.contextPath}}/resources/js/event/reservation-page.js"
{{#includeStripe}}
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
{{/includeStripe}}
 ></script>
{{>/event/header}}

<div class="wizard wMarginBottom wMarginTop center">
    <a><span class="badge">1</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step1{{/i18n}}</span></a>
    <a class="current"><span class="badge badge-inverse">2</span> {{#i18n}}breadcrumb.step2{{/i18n}}</a>
    <a><span class="badge">3</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step3{{/i18n}}</span></a>
</div>

<div class="page-header">
    <h2>{{#i18n}}reservation-page.title{{/i18n}}</h2>
</div>

<table class="table">
    <thead>
        <tr>
            <th>{{#i18n}}reservation-page.category{{/i18n}}</th>
            <th class="text-center">{{#i18n}}reservation-page.amount{{/i18n}}</th>
            <th class="text-right">{{#i18n}}reservation-page.price{{/i18n}}</th>
            <th class="text-right">{{#i18n}}reservation-page.subtotal{{/i18n}}</th>
        </tr>
    </thead>
    <tbody>
{{#orderSummary.summary}}
        <tr>
            <td>{{name}}</td>
            <td class="text-center">{{amount}}</td>
            <td class="text-right">{{price}}</td>
            <td class="text-right">{{subTotal}} {{event.currency}}</td>
        </tr>
{{/orderSummary.summary}}
    </tbody>
    <tfoot>
        {{^orderSummary.free}}
        {{^event.vatIncluded}}
            <tr><td colspan="3">{{#i18n}}reservation-page.vat [{{event.vat}}]{{/i18n}}</td><td class="text-right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
        {{/event.vatIncluded}}
        {{/orderSummary.free}}
        <tr><th colspan="3">{{#i18n}}reservation-page.total{{/i18n}}</th><td class="text-right"><strong>{{orderSummary.totalPrice}} {{event.currency}}</strong></td></tr>
        
        {{^orderSummary.free}}
        {{#event.vatIncluded}}
            <tr><td colspan="3">{{#i18n}}reservation-page.vat-included [{{event.vat}}]{{/i18n}}</td><td class="text-right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
        {{/event.vatIncluded}}
        {{/orderSummary.free}}
    </tfoot>
</table>

{{#hasErrors}}
    {{#error.globalErrors}}
        <div class="alert alert-danger" role="alert">
            <p><strong>{{#i18n}}{{.}}{{/i18n}}</strong></p>
        </div>
    {{/error.globalErrors}}
{{/hasErrors}}

<div class="alert alert-info" data-validity="{{reservation.validity.time}}" data-message="{{#i18n}}reservation-page.time-for-completition{{/i18n}}" data-message-time-elapsed="{{#i18n}}error.STEP_2_ORDER_HAS_EXPIRED{{/i18n}}" id="validity">
    <p><strong></strong></p>
</div>

<form action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservation.id}}" method="POST" id="payment-form">
    {{#paypalCheckoutConfirmation}}

        <input type="hidden" name="fullName" value="{{fullName}}">
        <input type="hidden" name="email" value="{{email}}">
        <input type="hidden" name="billingAddress" value="{{billingAddress}}">
        <input type="hidden" name="hmac" value="{{hmac}}">
        <input type="hidden" name="paymentMethod" value="PAYPAL">
        <input type="hidden" name="paypalPaymentId" value="{{paypalPaymentId}}">
        <input type="hidden" name="paypalPayerID" value="{{paypalPayerID}}">
        <input type="hidden" name="termAndConditionsAccepted" value="true">
        {{#i18n}}reservation-page.paypal.confirm{{/i18n}}
    {{/paypalCheckoutConfirmation}}
    {{^paypalCheckoutConfirmation}}


    <h2>{{#i18n}}reservation-page.your-details{{/i18n}}</h2>
    <div class="form-group {{#field-has-error}}[fullName] has-error{{/field-has-error}}">
        <label for="full-name" class="control-label">{{#i18n}}common.full-name{{/i18n}}</label>
        <input type="text" name="fullName" id="full-name" required class="form-control" autocomplete="name" maxlength="255" value="{{paymentForm.fullName}}">
        {{#field-has-error}}[fullName]<span class="help-block text-danger">{{#i18n}}{{#field-error}}fullName{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
    </div>
    <div class="form-group {{#field-has-error}}[email] has-error{{/field-has-error}}">
        <label for="email" class="control-label">{{#i18n}}common.email{{/i18n}}</label>
        <input type="email" name="email" id="email" required class="form-control" autocomplete="email" maxlength="255" value="{{paymentForm.email}}">
        {{#field-has-error}}[email]<span class="help-block text-danger">{{#i18n}}{{#field-error}}email{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
    </div>

    {{^orderSummary.free}}
    
    <div class="form-group {{#field-has-error}}[billingAddress] has-error{{/field-has-error}}">
        <label for="billing-address" class="control-label">{{#i18n}}reservation-page.billing-address{{/i18n}}</label>
        <textarea name="billingAddress" id="billing-address" class="form-control" maxlength="450">{{paymentForm.billingAddress}}</textarea>
        {{#field-has-error}}[billingAddress]<span class="help-block text-danger">{{#i18n}}{{#field-error}}billingAddress{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
    </div>



    <h2>{{#i18n}}reservation-page.payment{{/i18n}}</h2>

        {{#event.multiplePaymentMethods}}
            <div class="btn-group j-btn-group" data-toggle="buttons">
                {{#event.allowedPaymentProxies}}
                <label class="btn btn-default tooltip-handler xs-payment-method" {{#is-payment-method}}[STRIPE,{{.}}]  title="Powered by Stripe"{{/is-payment-method}}>
                    <input type="radio" required name="paymentMethod" id="option-{{.}}" data-payment-method="{{.}}" autocomplete="off" value="{{.}}">
                    {{#is-payment-method}}[STRIPE,{{.}}]<i class="fa fa-credit-card"></i> {{#i18n}}reservation-page.credit-card{{/i18n}}{{/is-payment-method}}
                    {{#is-payment-method}}[PAYPAL,{{.}}]<i class="fa fa-paypal"></i> {{#i18n}}reservation-page.paypal{{/i18n}}{{/is-payment-method}}
                    {{#is-payment-method}}[ON_SITE,{{.}}]<i class="fa fa-money"></i> {{#i18n}}reservation-page.on-site{{/i18n}}{{/is-payment-method}}
                    {{#is-payment-method}}[OFFLINE,{{.}}]<i class="fa fa-exchange"></i> {{#i18n}}reservation-page.offline{{/i18n}}{{/is-payment-method}}
                </label>
                {{/event.allowedPaymentProxies}}
            </div>
        {{/event.multiplePaymentMethods}}
        {{^event.multiplePaymentMethods}}
            {{#event.firstPaymentMethod}}
                <h4 class="wMarginTop">
                {{#is-payment-method}}[STRIPE,{{.}}]<i class="fa fa-credit-card"></i> {{#i18n}}reservation-page.credit-card{{/i18n}}{{/is-payment-method}}
                {{#is-payment-method}}[PAYPAL,{{.}}]<i class="fa fa-paypal"></i> {{#i18n}}reservation-page.paypal{{/i18n}}{{/is-payment-method}}
                {{#is-payment-method}}[ON_SITE,{{.}}]<i class="fa fa-money"></i> {{#i18n}}reservation-page.on-site{{/i18n}}{{/is-payment-method}}
                {{#is-payment-method}}[OFFLINE,{{.}}]<i class="fa fa-exchange"></i> {{#i18n}}reservation-page.offline{{/i18n}}{{/is-payment-method}}
                </h4>
                <input type="hidden" name="paymentMethod" value="{{.}}">
            {{/event.firstPaymentMethod}}
        {{/event.multiplePaymentMethods}}
        {{#event.allowedPaymentProxies}}
            <div class="payment-method-detail" id="payment-method-{{.}}">
                {{#is-payment-method}}[STRIPE,{{.}}]{{> /event/payment/stripe }}{{/is-payment-method}}
                {{#is-payment-method}}[PAYPAL,{{.}}]{{> /event/payment/paypal }}{{/is-payment-method}}
                {{#is-payment-method}}[ON_SITE,{{.}}]{{> /event/payment/on-site }}{{/is-payment-method}}
                {{#is-payment-method}}[OFFLINE,{{.}}]{{> /event/payment/offline }}{{/is-payment-method}}
            </div>
        {{/event.allowedPaymentProxies}}
    {{/orderSummary.free}}

    {{#expressCheckoutEnabled}}
    <div class="checkbox">
        <label>
            <input type="checkbox" name="expressCheckoutRequested" value="true">
            {{#i18n}}reservation-page.express-checkout{{/i18n}}
        </label>
    </div>
    {{/expressCheckoutEnabled}}

    <div class="checkbox">
        <label>
            <input type="checkbox" required name="termAndConditionsAccepted" value="true">
            {{#i18n}}reservation-page.tc.prefix{{/i18n}} <a href="{{event.termsAndConditionsUrl}}" target="_blank">{{#i18n}}reservation-page.tc.link.text{{/i18n}}</a>{{#i18n}}reservation-page.tc.suffix{{/i18n}}
        </label>
    </div>


    {{/paypalCheckoutConfirmation}}

    <hr />



    {{#orderSummary.free}}
        <div class="row">
            <div class="col-md-4 col-md-push-8 col-xs-12 wMarginBottom"><button type="submit" class="btn btn-success btn-block" id="continue-button">{{#i18n}}reservation-page.continue{{/i18n}}</button></div>
            <div class="col-md-4 col-md-pull-4 col-xs-12"><button type="submit" class="btn btn-default btn-block" id="cancel-reservation">{{#i18n}}reservation-page.cancel{{/i18n}}</button></div>
        </div>
    {{/orderSummary.free}}
    {{^orderSummary.free}}
        <div class="row">
            <div class="col-md-4 col-md-push-8 col-xs-12 wMarginBottom"><button type="submit" class="btn btn-success btn-block" id="continue-button">
                {{#paypalCheckoutConfirmation}}
                    {{#i18n}}reservation-page.paypal.confirm-button{{/i18n}}
                {{/paypalCheckoutConfirmation}}
                {{^paypalCheckoutConfirmation}}
                    {{#i18n}}reservation-page.pay{{/i18n}} {{orderSummary.totalPrice}} {{event.currency}}
                {{/paypalCheckoutConfirmation}}
            </button></div>
            <div class="col-md-4 col-md-pull-4 col-xs-12"><button type="submit" class="btn btn-default btn-block" id="cancel-reservation">{{#i18n}}reservation-page.cancel{{/i18n}}</button></div>
        </div>
    {{/orderSummary.free}}




 
    <input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
    
    <div class="clearfix"></div>
    
</form>

{{>/event/page-bottom}}