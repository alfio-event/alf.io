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
 	data-stripe-message-rate_limit="{{#i18n}}error.STEP2_STRIPE_rate_limit{{/i18n}}"
 ></script>
{{/orderSummary.free}}

<h1>{{#i18n}}reservation-page.title{{/i18n}} {{event.shortName}}</h1>
<table class="table">
	<thead>
		<tr>
			<th>{{#i18n}}reservation-page.category{{/i18n}}</th>
			<th style="text-align:right">{{#i18n}}reservation-page.price{{/i18n}}</th>
			<th style="text-align:center">{{#i18n}}reservation-page.amount{{/i18n}}</th>
			<th style="text-align:right">{{#i18n}}reservation-page.subtotal{{/i18n}}</th>
		</tr>
	</thead>
	<tbody>
{{#orderSummary.summary}}
		<tr>
			<td>{{name}}</td>
			<td style="text-align:right">{{price}}</td>
			<td style="text-align:center">{{amount}}</td>
			<td style="text-align:right">{{subTotal}} {{event.currency}}</td>
		</tr>
{{/orderSummary.summary}}
	</tbody>
	<tfoot>
		{{^orderSummary.free}}
		{{^event.vatIncluded}}
			<tr><td colspan="3">{{#i18n}}reservation-page.vat [{{event.vat}}]{{/i18n}}</td><td style="text-align:right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
		{{/event.vatIncluded}}
		{{/orderSummary.free}}
		<tr><th colspan="3">{{#i18n}}reservation-page.total{{/i18n}}</th><td style="text-align:right"><strong>{{orderSummary.totalPrice}} {{event.currency}}</strong></td></tr>
		
		{{^orderSummary.free}}
		{{#event.vatIncluded}}
			<tr><td colspan="3">{{#i18n}}reservation-page.vat-included [{{event.vat}}]{{/i18n}}</td><td style="text-align:right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
		{{/event.vatIncluded}}
		{{/orderSummary.free}}
	</tfoot>
</table>

{{#hasErrors}}
<div class="alert alert-danger" role="alert">
	{{#error.globalErrors}}
		<p><strong>{{#i18n}}{{.}}{{/i18n}}</strong></p>
	{{/error.globalErrors}}
</div>
{{/hasErrors}}

<form action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservation.id}}" method="POST" id="payment-form">
	<h2>{{#i18n}}reservation-page.your-details{{/i18n}}</h2>
	<div class="form-group">
		<label for="full-name">{{#i18n}}common.full-name{{/i18n}}</label>
		<input type="text" name="fullName" id="full-name" required class="form-control" autocomplete="name" maxlength="255" value="{{paymentForm.fullName}}">
	</div>
	<div class="form-group">
		<label for="email">{{#i18n}}common.email{{/i18n}}</label>
		<input type="email" name="email" id="email" required class="form-control" autocomplete="email" maxlength="255" value="{{paymentForm.email}}">
	</div>
	{{#orderSummary.free}}
	<button type="submit" class="btn btn-primary pull-right">{{#i18n}}reservation-page.continue{{/i18n}}</button>
	<button type="submit" class="btn btn-info pull-left" id="cancel-reservation">{{#i18n}}reservation-page.cancel{{/i18n}}</button>
	{{/orderSummary.free}}
	{{^orderSummary.free}}
	
	<div class="form-group">
		<label for="billing-address">{{#i18n}}reservation-page.billing-address{{/i18n}}</label>
		<textarea name="billingAddress" id="billing-address" class="form-control" maxlength="450">{{paymentForm.billingAddress}}</textarea>
	</div>
	
	<h2>{{#i18n}}reservation-page.payment{{/i18n}}</h2>

	<div class="alert alert-danger payment-errors" role="alert">
	</div>

	<div class="form-group">
		<label for="card-number">{{#i18n}}reservation-page.card-number{{/i18n}}</label>
		<input type="text" size="20" data-stripe="number" required class="form-control" id="card-number" autocomplete="off">
	</div>
 
	<div class="form-group">
		<div><label for="card-cvc"> {{#i18n}}reservation-page.cvc{{/i18n}}</label></div>
		<div class="row">
			<div class="col-sm-2">
				<input type="text" size="4" data-stripe="cvc" required class="form-control" id="card-cvc" autocomplete="off">
			</div>
		</div>
	</div>
 
 
	<div class="form-group">
		<div>
			<label>
				<span class="hidden-xs">{{#i18n}}reservation-page.expiration{{/i18n}}</span>
				<span class="visible-xs">{{#i18n}}reservation-page.expiration-month{{/i18n}}</span>
			</label>
		</div>
		<div class="row">
			<div class="col-sm-2">
				<input type="text" size="2" data-stripe="exp-month" required class="form-control" maxlength="2" autocomplete="off" id="card-mm" pattern="\d+"/>
			</div>
			<div class="col-sm-1 hidden-xs">/</div>
			<div class="col-sm-2 visible-xs" style="margin-top:15px">
				<label>{{#i18n}}reservation-page.expiration-year{{/i18n}}</label>
			</div>
			<div class="col-sm-2">
				<input type="text" size="4" data-stripe="exp-year" required class="form-control" maxlength="4" autocomplete="off" id="card-yyyy" pattern="\d+"/>
			</div>
		</div>
	</div>
	
	<div class="form-group">
		<button type="submit" class="btn btn-success pull-right">{{#i18n}}reservation-page.pay{{/i18n}} {{orderSummary.totalPrice}} {{event.currency}}</button>
		<button type="submit" class="btn btn-info pull-left" id="cancel-reservation">{{#i18n}}reservation-page.cancel{{/i18n}}</button>
	</div>
	{{/orderSummary.free}}
 
 	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
 	
 	<div class="clearfix"></div>
	
</form>

{{>/event/page-bottom}}