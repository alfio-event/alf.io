{{>/event/page-top}}

<script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
<script src="{{request.contextPath}}/resources/js/h5f/h5f.min.js"></script>
<script src="{{request.contextPath}}/resources/bower_components/bootstrap/dist/js/bootstrap.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/bootstrap-handler.js"></script>
<script src="{{request.contextPath}}/resources/js/countdownjs/countdown.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/overview-page.js" ></script>

{{>/event/header}}

<div class="wizard wMarginBottom wMarginTop center">
    <a><span class="badge">1</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step1{{/i18n}}</span></a>
    <a><span class="badge">2</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step2{{/i18n}}</span></a>
    <a class="current"><span class="badge badge-inverse">3</span> {{#i18n}}breadcrumb.step3{{/i18n}}</a>
    <a><span class="badge">4</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step4{{/i18n}}</span></a>
</div>

{{#hasErrors}}
    {{#error.globalErrors}}
        <div class="alert alert-danger" role="alert">
            <p><strong>{{#i18n}}{{.}}{{/i18n}}</strong></p>
        </div>
    {{/error.globalErrors}}
{{/hasErrors}}

<div class="alert alert-info text-center">
    <i class="fa fa-clock-o"></i>
    <span id="validity-container"><span data-validity="{{reservation.validity.time}}"
          data-message="{{#i18n}}reservation-page.time-for-completion{{/i18n}}"
          data-message-time-elapsed="{{#i18n}}error.STEP_2_ORDER_HAS_EXPIRED{{/i18n}}"
          data-labels-singular="{{#i18n}}reservation-page.time-for-completion.labels.singular{{/i18n}}"
          data-labels-plural="{{#i18n}}reservation-page.time-for-completion.labels.plural{{/i18n}}"
          data-labels-and="{{#i18n}}reservation-page.time-for-completion.labels.and{{/i18n}}"
          id="validity"></span>
    </span>
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
            {{#orderSummary.displayVat}}
                {{^event.vatIncluded}}
                    <tr><td colspan="3">{{#i18n}}reservation-page.vat [{{event.vat}}] [{{vatTranslation}}]{{/i18n}}</td><td class="text-right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
                {{/event.vatIncluded}}
            {{/orderSummary.displayVat}}
        {{/orderSummary.free}}
        <tr><th colspan="3">{{#i18n}}reservation-page.total{{/i18n}}</th><td class="text-right"><strong>{{orderSummary.totalPrice}} {{event.currency}}</strong></td></tr>

        {{^orderSummary.free}}
            {{#orderSummary.displayVat}}
                {{#event.vatIncluded}}
                    <tr><td colspan="3">{{#i18n}}reservation-page.vat-included [{{event.vat}}] [{{vatTranslation}}]{{/i18n}}</td><td class="text-right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
                {{/event.vatIncluded}}
            {{/orderSummary.displayVat}}
            {{^orderSummary.displayVat}}
                <tr><td colspan="4">{{#i18n}}invoice.vat-voided [{{vatTranslation}}]{{/i18n}}</td></tr>
            {{/orderSummary.displayVat}}
        {{/orderSummary.free}}
    </tfoot>
</table>

<form action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservation.id}}" method="POST" id="payment-form">


    {{^orderSummary.free}}
        <div class="page-header wMarginTop">
            <h2>{{#i18n}}reservation-page.payment{{/i18n}}</h2>
        </div>
        {{#multiplePaymentMethods}}
            <div class="btn-group j-btn-group" data-toggle="buttons">
                {{#activePaymentMethods}}
                <label class="btn btn-default tooltip-handler xs-payment-method" {{#is-payment-method}}[STRIPE,{{.}}]  title="Powered by Stripe"{{/is-payment-method}}>
                    <input type="radio" required name="paymentMethod" id="option-{{.}}" data-payment-method="{{.}}" autocomplete="off" value="{{.}}">
                    {{#is-payment-method}}[STRIPE,{{.}}]<i class="fa fa-cc-stripe"></i> {{#i18n}}reservation-page.credit-card{{/i18n}}{{/is-payment-method}}
                    {{#is-payment-method}}[PAYPAL,{{.}}]<i class="fa fa-paypal"></i> {{#i18n}}reservation-page.paypal{{/i18n}}{{/is-payment-method}}
                    {{#is-payment-method}}[MOLLIE,{{.}}] {{#i18n}}reservation-page.mollie{{/i18n}}{{/is-payment-method}}
                    {{#is-payment-method}}[ON_SITE,{{.}}]<i class="fa fa-money"></i> {{#i18n}}reservation-page.on-site{{/i18n}}{{/is-payment-method}}
                    {{#is-payment-method}}[OFFLINE,{{.}}]<i class="fa fa-exchange"></i> {{#i18n}}reservation-page.offline{{/i18n}}{{/is-payment-method}}
                </label>
                {{/activePaymentMethods}}
            </div>
        {{/multiplePaymentMethods}}
        {{^multiplePaymentMethods}}
            {{#activePaymentMethods}}
                <h4 class="wMarginTop">
                {{#is-payment-method}}[STRIPE,{{.}}]<i class="fa fa-credit-card"></i> {{#i18n}}reservation-page.credit-card{{/i18n}}{{/is-payment-method}}
                {{#is-payment-method}}[PAYPAL,{{.}}]<i class="fa fa-paypal"></i> {{#i18n}}reservation-page.paypal{{/i18n}}{{/is-payment-method}}
                {{#is-payment-method}}[MOLLIE,{{.}}] {{#i18n}}reservation-page.mollie{{/i18n}}{{/is-payment-method}}
                {{#is-payment-method}}[ON_SITE,{{.}}]<i class="fa fa-money"></i> {{#i18n}}reservation-page.on-site{{/i18n}}{{/is-payment-method}}
                {{#is-payment-method}}[OFFLINE,{{.}}]<i class="fa fa-exchange"></i> {{#i18n}}reservation-page.offline{{/i18n}}{{/is-payment-method}}
                </h4>
                <input type="hidden" name="paymentMethod" value="{{.}}">
            {{/activePaymentMethods}}
        {{/multiplePaymentMethods}}
        {{#activePaymentMethods}}
            <div class="payment-method-detail" id="payment-method-{{.}}">
                {{#is-payment-method}}[STRIPE,{{.}}]{{> /event/payment/stripe }}{{/is-payment-method}}
                {{#is-payment-method}}[PAYPAL,{{.}}]{{> /event/payment/paypal }}{{/is-payment-method}}
                {{#is-payment-method}}[MOLLIE,{{.}}]{{> /event/payment/mollie }}{{/is-payment-method}}
                {{#is-payment-method}}[ON_SITE,{{.}}]{{> /event/payment/on-site }}{{/is-payment-method}}
                {{#is-payment-method}}[OFFLINE,{{.}}]{{> /event/payment/offline }}{{/is-payment-method}}
            </div>
        {{/activePaymentMethods}}

    {{/orderSummary.free}}


    {{#event.privacyPolicyLinkOrNull}}
        <div class="checkbox wMarginTop wMarginBottom">
            <label>
                <input type="checkbox" required name="privacyPolicyAccepted" value="true">
                {{#i18n}}reservation-page.privacy.prefix{{/i18n}} <a href="{{event.privacyPolicyLinkOrNull}}" target="_blank">{{#i18n}}reservation-page.privacy.link.text{{/i18n}}</a>{{#i18n}}reservation-page.privacy.suffix{{/i18n}}
            </label>
        </div>
    {{/event.privacyPolicyLinkOrNull}}

    <div class="checkbox wMarginTop wMarginBottom">
        <label>
            <input type="checkbox" required name="termAndConditionsAccepted" value="true">
            {{#i18n}}reservation-page.tc.prefix{{/i18n}} <a href="{{event.termsAndConditionsUrl}}" target="_blank">{{#i18n}}reservation-page.tc.link.text{{/i18n}}</a>{{#i18n}}reservation-page.tc.suffix{{/i18n}}
        </label>
    </div>


    {{#orderSummary.free}}
        <div class="row">
            <div class="col-md-4 col-md-push-8 col-xs-12 wMarginBottom"><button type="submit" class="btn btn-success btn-block" id="continue-button">{{#i18n}}reservation-page.continue{{/i18n}}</button></div>
            <div class="col-md-4 col-md-pull-4 col-xs-12"><button type="submit" class="btn btn-default btn-block" id="cancel-reservation">{{#i18n}}common.back{{/i18n}}</button></div>
        </div>
    {{/orderSummary.free}}
    {{^orderSummary.free}}
        <div class="row">
            <div class="col-md-4 col-md-push-8 col-xs-12 wMarginBottom">
                <button type="submit" class="btn btn-success btn-block" id="continue-button">
                {{#paypalCheckoutConfirmation}}
                    {{#i18n}}reservation-page.paypal.confirm-button{{/i18n}}
                {{/paypalCheckoutConfirmation}}
                {{^paypalCheckoutConfirmation}}
                    {{#i18n}}reservation-page.pay{{/i18n}} {{orderSummary.totalPrice}} {{event.currency}}
                {{/paypalCheckoutConfirmation}}
                </button>
            </div>
            <div class="col-md-4 col-md-pull-4 col-xs-12"><button type="submit" class="btn btn-default btn-block" id="cancel-reservation">{{#i18n}}common.back{{/i18n}}</button></div>
        </div>
    {{/orderSummary.free}}
    <input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
</form>
{{>/event/page-bottom}}