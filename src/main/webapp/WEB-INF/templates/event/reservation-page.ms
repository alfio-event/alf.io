{{>/event/page-top}}
<script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
<script src="{{request.contextPath}}/resources/js/h5f/h5f.min.js"></script>
<script src="{{request.contextPath}}/resources/bower_components/bootstrap/dist/js/bootstrap.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/bootstrap-handler.js"></script>
<script src="{{request.contextPath}}/resources/js/event/reservation-page.js"
<script src="{{request.contextPath}}/resources/js/event/attendee-form.js"></script>
<script src="{{request.contextPath}}/resources/js/countdownjs/countdown.min.js"></script>
{{>/event/header}}

<div class="wizard wMarginBottom wMarginTop center">
    <a><span class="badge">1</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step1{{/i18n}}</span></a>
    <a class="current"><span class="badge badge-inverse">2</span> {{#i18n}}breadcrumb.step2{{/i18n}}</a>
    <a><span class="badge">3</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step3{{/i18n}}</span></a>
    <a><span class="badge">4</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step4{{/i18n}}</span></a>
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

<form action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservation.id}}/validate-to-overview" method="POST" id="payment-form">
    <div class="page-header">
        <h2>{{#i18n}}reservation-page.your-details{{/i18n}}</h2>
    </div>
        {{#useFirstAndLastName}}
        <div class="row">
            <div class="col-xs-12 col-sm-6">
                <div class="form-group {{#field-has-error}}[firstName] has-error{{/field-has-error}}">
                    <label for="first-name" class="control-label">{{#i18n}}common.first-name{{/i18n}}</label>
                    <input type="text" name="firstName" id="first-name" required class="form-control {{#attendeeAutocompleteEnabled}}autocomplete-src{{/attendeeAutocompleteEnabled}}" autocomplete="fname" maxlength="255" value="{{paymentForm.firstName}}">
                    {{#field-has-error}}[firstName]<span class="help-block text-danger">{{#i18n}}{{#field-error}}firstName{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
                </div>
            </div>
            <div class="col-xs-12 col-sm-6">
                <div class="form-group {{#field-has-error}}[lastName] has-error{{/field-has-error}}">
                    <label for="last-name" class="control-label">{{#i18n}}common.last-name{{/i18n}}</label>
                    <input type="text" name="lastName" id="last-name" required class="form-control {{#attendeeAutocompleteEnabled}}autocomplete-src{{/attendeeAutocompleteEnabled}}" autocomplete="lname" maxlength="255" value="{{paymentForm.lastName}}">
                    {{#field-has-error}}[lastName]<span class="help-block text-danger">{{#i18n}}{{#field-error}}lastName{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
                </div>
            </div>
        </div>
        {{/useFirstAndLastName}}
        {{^useFirstAndLastName}}
        <div class="form-group {{#field-has-error}}[fullName] has-error{{/field-has-error}}">
            <label for="full-name" class="control-label">{{#i18n}}common.full-name{{/i18n}}</label>
            <input type="text" name="fullName" id="full-name" required class="form-control {{#attendeeAutocompleteEnabled}}autocomplete-src{{/attendeeAutocompleteEnabled}}" autocomplete="name" maxlength="255" value="{{paymentForm.fullName}}">
            {{#field-has-error}}[fullName]<span class="help-block text-danger">{{#i18n}}{{#field-error}}fullName{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
        </div>
        {{/useFirstAndLastName}}


        <div class="row">
            <div class="col-xs-12 {{#captchaRequestedFreeOfCharge}}col-md-6{{/captchaRequestedFreeOfCharge}}">
                <div class="form-group {{#field-has-error}}[email] has-error{{/field-has-error}}">
                    <label for="email" class="control-label">{{#i18n}}common.email{{/i18n}}</label>
                    <input type="email" name="email" id="email" required class="form-control {{#attendeeAutocompleteEnabled}}autocomplete-src{{/attendeeAutocompleteEnabled}}" autocomplete="email" maxlength="255" value="{{paymentForm.email}}">
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
                        <h3><i class="fa fa-ticket"></i> {{#i18n}}reservation-page-complete.ticket-nr{{/i18n}}<span class="ticket-counter"></span> <span class="hidden-xs hidden-xs">- <small>{{key.name}}</small></span>{{^attendeeAutocompleteEnabled}}{{#-first}}<button type="button" id="copy-from-contact-data" class="btn btn-xs btn-default tooltip-handler pull-right" title="{{#i18n}}reservation-page.copy-attendee{{/i18n}}"><i class="fa fa-clone"></i><span class="sr-only">{{#i18n}}reservation-page.copy-attendee{{/i18n}}</span></button>{{/-first}}{{/attendeeAutocompleteEnabled}}</h3>
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
                    {{#i18n}}reservation-page.pay{{/i18n}} {{orderSummary.totalPrice}} {{event.currency}}
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