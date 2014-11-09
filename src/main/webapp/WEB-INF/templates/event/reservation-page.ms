{{>/event/page-top}}

{{#orderSummary.free}}
 <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
 <script src="{{request.contextPath}}/resources/js/event/reservation-page.js"></script>
{{/orderSummary.free}}
{{^orderSummary.free}}
 <script src="https://js.stripe.com/v2/"></script>
 <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
 <script src="{{request.contextPath}}/resources/js/event/reservation-page.js" 
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
 ></script>
{{/orderSummary.free}}
<div class="center">
		{{#event.imageIsPresent}}
            <img src="{{event.imageUrl}}" alt="{{event.shortName}}">
		{{/event.imageIsPresent}}
</div>
<ol class="breadcrumb wMarginBottom wMarginTop">
    <li class="active">{{#i18n}}breadcrumb.step1{{/i18n}}</li>
    <li><i class="fa fa-credit-card"></i> {{#i18n}}breadcrumb.step2{{/i18n}}</li>
    <li class="active">{{#i18n}}breadcrumb.step3{{/i18n}}</li>
</ol>
<div class="page-header">
	<h2>{{#i18n}}reservation-page.title{{/i18n}} {{event.shortName}}</h2>
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
	
	<h2>{{#i18n}}reservation-page.payment{{/i18n}}
	
		<a href="https://stripe.com/" target="_blank"><img src="{{request.contextPath}}/resources/images/outline.png"></a>
	</h2>

	<div class="alert alert-danger payment-errors hide" role="alert">
	</div>

	<div class="form-group">
		<label for="card-number" class="control-label">{{#i18n}}reservation-page.card-number{{/i18n}}</label>
		<input type="text" size="20" data-stripe="number" required class="form-control" id="card-number" autocomplete="off">
	</div>
 
	<div class="row">
		<div class="col-md-4">
			<div class="form-group">
				<label for="card-mm">{{#i18n}}reservation-page.expiration-month{{/i18n}}</label>
				<input type="text" class="form-control" placeholder="MM" size="2" data-stripe="exp-month" required class="form-control" maxlength="2" autocomplete="off" id="card-mm" pattern="\d+">
			</div>
		</div>
		<div class="col-md-4">
			<div class="form-group">
				<label for="card-yyyy">{{#i18n}}reservation-page.expiration-year{{/i18n}}</label>
				<input type="text" class="form-control" placeholder="YYYY" size="4" data-stripe="exp-year" required class="form-control" maxlength="4" autocomplete="off" id="card-yyyy" pattern="\d+">
			</div>
		</div>
		<div class="col-md-4">
			<div class="form-group">
				<label for="card-cvc">{{#i18n}}reservation-page.cvc{{/i18n}}</label>
				<input type="text" class="form-control" placeholder="123" size="4" data-stripe="cvc" required class="form-control" id="card-cvc" autocomplete="off">
			</div>
		</div>
	</div>

	{{/orderSummary.free}}

    <div class="checkbox">
        <label>
            <input type="checkbox" required name="termAndConditionsAccepted">
			{{#i18n}}reservation-page.tc.prefix{{/i18n}} <a href="{{event.termAndConditionsUrl}}" target="_blank">{{#i18n}}reservation-page.tc.link.text{{/i18n}}</a>{{#i18n}}reservation-page.tc.suffix{{/i18n}}
        </label>
    </div>

	<hr />

	{{#orderSummary.free}}
        <button type="submit" class="btn btn-success pull-right" id="continue-button">{{#i18n}}reservation-page.continue{{/i18n}}</button>
        <button type="submit" class="btn btn-default pull-left" id="cancel-reservation">{{#i18n}}reservation-page.cancel{{/i18n}}</button>
	{{/orderSummary.free}}
	{{^orderSummary.free}}
	<div class="form-group">
        <button type="submit" class="btn btn-success pull-right" id="continue-button">{{#i18n}}reservation-page.pay{{/i18n}} {{orderSummary.totalPrice}} {{event.currency}}</button>
        <button type="submit" class="btn btn-default pull-left" id="cancel-reservation">{{#i18n}}reservation-page.cancel{{/i18n}}</button>
    </div>
	{{/orderSummary.free}}
 
 	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
 	
 	<div class="clearfix"></div>
	
</form>

{{>/event/page-bottom}}