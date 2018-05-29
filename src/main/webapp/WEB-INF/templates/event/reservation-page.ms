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
<script src="{{request.contextPath}}/resources/js/event/attendee-form.js"></script>
<script src="{{request.contextPath}}/resources/js/countdownjs/countdown.min.js"></script>
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

<form action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservation.id}}" method="POST" id="payment-form">
    {{#paypalCheckoutConfirmation}}
        {{#useFirstAndLastName}}
        <input type="hidden" name="firstName" value="{{firstName}}">
        <input type="hidden" name="lastName" value="{{lastName}}">
        {{/useFirstAndLastName}}
        {{^useFirstAndLastName}}
        <input type="hidden" name="fullName" value="{{fullName}}">
        {{/useFirstAndLastName}}
        <input type="hidden" name="email" value="{{email}}">
        <input type="hidden" name="billingAddress" value="{{billingAddress}}">
        <input type="hidden" name="hmac" value="{{hmac}}">
        <input type="hidden" name="paymentMethod" value="PAYPAL">
        <input type="hidden" name="paypalPaymentId" value="{{paypalPaymentId}}">
        <input type="hidden" name="paypalPayerID" value="{{paypalPayerID}}">
        <input type="hidden" name="termAndConditionsAccepted" value="true">
        <input type="hidden" name="invoiceRequested" value="{{invoiceRequested}}">
        <input type="hidden" name="customerReference" value="{{customerReference}}">
    <div class="alert alert-success">
        <h3><i class="fa fa-check"></i> {{#i18n}}reservation-page.paypal.confirm{{/i18n}}</h3>
    </div>

    {{/paypalCheckoutConfirmation}}
    {{^paypalCheckoutConfirmation}}

    <div class="page-header">
        <h2>{{#i18n}}reservation-page.your-details{{/i18n}}</h2>
    </div>
        {{#useFirstAndLastName}}
        <div class="row">
            <div class="col-xs-12 col-sm-6">
                <div class="form-group {{#field-has-error}}[firstName] has-error{{/field-has-error}}">
                    <label for="first-name" class="control-label">{{#i18n}}common.first-name{{/i18n}}</label>
                    <input type="text" name="firstName" id="first-name" required class="form-control" autocomplete="fname" maxlength="255" value="{{paymentForm.firstName}}">
                    {{#field-has-error}}[firstName]<span class="help-block text-danger">{{#i18n}}{{#field-error}}firstName{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
                </div>
            </div>
            <div class="col-xs-12 col-sm-6">
                <div class="form-group {{#field-has-error}}[lastName] has-error{{/field-has-error}}">
                    <label for="last-name" class="control-label">{{#i18n}}common.last-name{{/i18n}}</label>
                    <input type="text" name="lastName" id="last-name" required class="form-control" autocomplete="lname" maxlength="255" value="{{paymentForm.lastName}}">
                    {{#field-has-error}}[lastName]<span class="help-block text-danger">{{#i18n}}{{#field-error}}lastName{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
                </div>
            </div>
        </div>
        {{/useFirstAndLastName}}
        {{^useFirstAndLastName}}
        <div class="form-group {{#field-has-error}}[fullName] has-error{{/field-has-error}}">
            <label for="full-name" class="control-label">{{#i18n}}common.full-name{{/i18n}}</label>
            <input type="text" name="fullName" id="full-name" required class="form-control" autocomplete="name" maxlength="255" value="{{paymentForm.fullName}}">
            {{#field-has-error}}[fullName]<span class="help-block text-danger">{{#i18n}}{{#field-error}}fullName{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
        </div>
        {{/useFirstAndLastName}}


        <div class="row">
            <div class="col-xs-12 {{#captchaRequestedFreeOfCharge}}col-md-6{{/captchaRequestedFreeOfCharge}}">
                <div class="form-group {{#field-has-error}}[email] has-error{{/field-has-error}}">
                    <label for="email" class="control-label">{{#i18n}}common.email{{/i18n}}</label>
                    <input type="email" name="email" id="email" required class="form-control" autocomplete="email" maxlength="255" value="{{paymentForm.email}}">
                    {{#field-has-error}}[email]<span class="help-block text-danger">{{#i18n}}{{#field-error}}email{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
                </div>
            </div>
            {{#captchaRequestedFreeOfCharge}}
                <div class="col-xs-12 col-md-6">
                    <div id="captcha-FREE" class="g-recaptcha" data-sitekey="{{recaptchaApiKey}}"></div>
                </div>
            {{/captchaRequestedFreeOfCharge}}
        </div>

    {{#customerReferenceEnabled}}
        <div class="form-group {{#field-has-error}}[customerReference] has-error{{/field-has-error}}">
            <label for="customerReference" class="control-label">{{#i18n}}common.customer-reference{{/i18n}}</label>
            <input type="text" name="customerReference" id="customerReference" class="form-control" maxlength="255" value="{{paymentForm.customerReference}}">
            {{#field-has-error}}[customerReference]<span class="help-block text-danger">{{#i18n}}{{#field-error}}customerReference{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
        </div>
    {{/customerReferenceEnabled}}

    <div class="page-header">
        <h2>{{#i18n}}reservation-page.attendees{{/i18n}}</h2>
    </div>

    {{#showPostpone}}
        <div class="checkbox">
            <label>
                <input type="checkbox" name="postponeAssignment" id="postpone-assignment" value="true" {{#postponeAssignment}}checked="checked"{{/postponeAssignment}}>
                {{#i18n}}reservation-page.postpone-assignment{{/i18n}}
            </label>
        </div>
    {{/showPostpone}}

    <div id="attendeesData" {{#postponeAssignment}}class="hidden-xs hidden-sm hidden-md hidden-lg"{{/postponeAssignment}}>
        <ul class="list-group">
            {{#ticketsByCategory}}
                {{#value}}
                    <li class="list-group-item">
                        <h3><i class="fa fa-ticket"></i> {{#i18n}}reservation-page-complete.ticket-nr{{/i18n}}<span class="ticket-counter"></span> - <small>{{key.name}}</small> {{#-first}}<button type="button" id="copy-from-contact-data" class="btn btn-xs btn-default"><i class="fa fa-clone"></i> {{#i18n}}reservation-page.copy-attendee{{/i18n}}</button>{{/-first}}</h3>
                        <div class="form-horizontal">
                            <div data-collapse-enabled="true" class="collapsible-container">
                                {{> /event/attendee-fields }}
                            </div>
                        </div>
                    </li>
                {{/value}}
            {{/ticketsByCategory}}
        </ul>
    </div>
    <div class="clearfix"></div>


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

    {{#invoiceIsAllowed}}
        {{^vatNrIsLinked}}
            {{#onlyInvoice}}
                <input type="hidden" name="invoiceRequested" id="invoice-requested" value="true">
                {{#euVatCheckingEnabled}}
                    <div class="checkbox wMarginTop">
                        <label>
                            <input type="checkbox" name="addCompanyBillingDetails" id="add-company-billing-details" value="true">
                            {{#i18n}}reservation.add-company-billing-details [{{vatTranslation}}]{{/i18n}}
                        </label>
                    </div>
                {{/euVatCheckingEnabled}}
            {{/onlyInvoice}}
            {{^onlyInvoice}}
                <div class="checkbox wMarginTop">
                    <label>
                        <input type="checkbox" name="invoiceRequested" id="invoice-requested" value="true" {{#paymentForm.invoiceRequested}}checked{{/paymentForm.invoiceRequested}}>
                        {{#i18n}}reservation-page.i-need-an-invoice{{/i18n}}
                    </label>
                </div>
            {{/onlyInvoice}}
        {{/vatNrIsLinked}}
        {{#vatNrIsLinked}}
            <div class="page-header wMarginTop">
                <h2>{{#i18n}}invoice.title{{/i18n}}</h2>
            </div>
        {{/vatNrIsLinked}}
    {{/invoiceIsAllowed}}

    <div id="invoice" class="wMarginTop {{#invoiceIsAllowed}}{{^paymentForm.invoiceRequested}}hidden{{/paymentForm.invoiceRequested}}{{/invoiceIsAllowed}}" {{#euVatCheckingEnabled}}data-eu-vat-checking-enabled="true"{{/euVatCheckingEnabled}}>
        {{#euVatCheckingEnabled}}
            {{^vatNrIsLinked}}
                <div class="row" id="eu-vat-check-countries">
                    <div class="col-xs-12 col-sm-4">
                        <div class="form-group">
                            <label for="vatCountry">{{#i18n}}reservation-page-complete.country{{/i18n}}</label>
                            <select name="vatCountryCode" id="vatCountry" value="{{paymentForm.vatCountryCode}}" class="form-control field-required">
                                <option value="">{{#i18n}}reservation-page.country.select{{/i18n}}</option>
                                <optgroup label="{{#i18n}}reservation-page.eu-countries{{/i18n}}" id="optgroup-eu-countries-list">
                                {{#euCountriesForVat}}
                                    <option value="{{left}}">{{left}} - {{right}}</option>
                                {{/euCountriesForVat}}
                                </optgroup>
                                <optgroup label="{{#i18n}}reservation-page.all-countries{{/i18n}}">
                                {{#countriesForVat}}
                                    <option value="{{left}}">{{left}} - {{right}}</option>
                                {{/countriesForVat}}
                                </optgroup>
                            </select>
                        </div>
                    </div>

                    <div id="vat-number-container" class="{{^paymentForm.hasVatCountryCode}}hidden-xs hidden-sm hidden-md hidden-lg{{/paymentForm.hasVatCountryCode}}">
                            <div class="col-xs-6 col-sm-8">
                                <div class="form-group">
                                    <label for="vatNr">{{#i18n}}invoice.vat [{{vatTranslation}}]{{/i18n}}</label>
                                    <div class="input-group">
                                        <input id="vatNr" type="text" required name="vatNr" class="form-control" value="{{paymentForm.vatNr}}"/>
                                        <div class="input-group-btn">
                                            <button type="button" class="btn btn-warning btn-block" data-validation-url="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservation.id}}/vat-validation" id="validateVAT"
                                                data-text="{{#i18n}}invoice.validate.vat [{{vatTranslation}}]{{/i18n}}"
                                                data-text-non-eu="{{#i18n}}common.apply{{/i18n}}">{{#i18n}}invoice.validate.vat [{{vatTranslation}}]{{/i18n}}</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                    </div>
                </div>
                <div id="validation-result-container" class="row {{^paymentForm.hasVatCountryCode}}hidden-xs hidden-sm hidden-md hidden-lg{{/paymentForm.hasVatCountryCode}}">
                    <div class="col-xs-10 text-right">
                        <span id="validation-result" class="text-danger" data-validation-error-msg="{{#i18n}}reservation-page.vat-validation-error [{{vatTranslation}}]{{/i18n}}"
                              data-generic-error-msg="{{#i18n}}reservation-page.vat-error [{{vatTranslation}}]{{/i18n}}"
                              data-validation-required-msg="{{#i18n}}reservation-page.vat-validation-required [{{vatTranslation}}]{{/i18n}}"></span>
                    </div>
                </div>
            {{/vatNrIsLinked}}
            <div class="clearfix"></div>
        {{/euVatCheckingEnabled}}

        <div class="row">
            {{#vatNrIsLinked}}
                <input type="hidden" name="vatCountryCode" value="{{paymentForm.vatCountryCode}}">
                <input type="hidden" name="vatNr" value="{{paymentForm.vatNr}}"/>
                <input type="hidden" name="invoiceRequested" value="true">
                <div class="col-xs-6 col-md-2">
                    <div class="form-group">
                        <label class="control-label">{{#i18n}}reservation-page-complete.country{{/i18n}}</label>
                        <p class="form-control-static">{{paymentForm.vatCountryCode}}</p>
                    </div>
                </div>
                <div class="col-xs-6 col-md-2">
                    <div class="form-group">
                        <label class="control-label">{{#i18n}}invoice.vat [{{vatTranslation}}]{{/i18n}}</label>
                        <p class="form-control-static">{{paymentForm.vatNr}}</p>
                    </div>
                </div>
            {{/vatNrIsLinked}}
            <div class="col-xs-12 {{#vatNrIsLinked}}col-md-8{{/vatNrIsLinked}}">
                <div class="form-group {{#field-has-error}}[billingAddress] has-error{{/field-has-error}}" id="billing-address-container">
                    <label for="billing-address" class="control-label">{{#i18n}}{{billingAddressLabel}}{{/i18n}}</label>
                    <textarea name="billingAddress" id="billing-address" class="form-control" maxlength="450" {{#invoiceIsAllowed}}data-invoice-enabled="true"{{/invoiceIsAllowed}}>{{paymentForm.billingAddress}}</textarea>
                    {{#field-has-error}}[billingAddress]<span class="help-block text-danger">{{#i18n}}{{#field-error}}billingAddress{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
                </div>
            </div>
            {{#euVatCheckingEnabled}}{{#vatNrIsLinked}}
            <div class="col-md-6 col-md-push-6 col-xs-12">
                <div class="form-group">
                    <button type="button" id="reset-billing-information" class="btn btn-warning btn-block"
                        data-reset-billing-information-url="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservation.id}}/reset-billing-info">
                        {{#i18n}}reservation-page.reset-billing-informations{{/i18n}}
                    </button>
                </div>
            </div>
            {{/vatNrIsLinked}}{{/euVatCheckingEnabled}}

        </div>
    </div>
    {{/orderSummary.free}}
    <hr/>

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


    {{/paypalCheckoutConfirmation}}

    {{#orderSummary.free}}
        <div class="row">
            <div class="col-md-4 col-md-push-8 col-xs-12 wMarginBottom"><button type="submit" class="btn btn-success btn-block" id="continue-button">{{#i18n}}reservation-page.continue{{/i18n}}</button></div>
            <div class="col-md-4 col-md-pull-4 col-xs-12"><button type="submit" class="btn btn-default btn-block" id="cancel-reservation">{{#i18n}}reservation-page.cancel{{/i18n}}</button></div>
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
            <div class="col-md-4 col-md-pull-4 col-xs-12"><button type="submit" class="btn btn-default btn-block" id="cancel-reservation">{{#i18n}}reservation-page.cancel{{/i18n}}</button></div>
        </div>
    {{/orderSummary.free}}

 
    <input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
    
    <div class="clearfix"></div>

</form>
{{#captchaRequestedForOffline}}
<script src="https://www.google.com/recaptcha/api.js?onload=recaptchaLoadCallback&render=explicit" async defer></script>
{{/captchaRequestedForOffline}}

{{>/event/page-bottom}}