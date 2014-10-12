{{>/event/page-top}}

{{#free}}
 <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
 <script src="/resources/js/event/reservation-page.js"></script>
{{/free}}
{{^free}}
 <script src="https://js.stripe.com/v2/"></script>
 <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
 <script src="/resources/js/event/reservation-page.js" data-stripe-key="{{stripe_p_key}}" id="stripe-key"></script>
{{/free}}

<h1>{{#i18n}}reservation-page.title{{/i18n}} {{event.shortName}}</h1>
<table class="table">
	<thead>
		<tr>
			<th>{{#i18n}}reservation-page.category{{/i18n}}</th>
			<th>{{#i18n}}reservation-page.price{{/i18n}}</th>
			<th>{{#i18n}}reservation-page.amount{{/i18n}}</th>
			<th>{{#i18n}}reservation-page.subtotal{{/i18n}}</th>
		</tr>
	</thead>
	<tbody>
{{#summary}}
		<tr>
			<td>{{name}}</td>
			<td>{{price}}</td>
			<td>{{amount}}</td>
			<td>{{subTotal}}</td>
		</tr>
{{/summary}}
	</tbody>
	<tfoot>
		<tr><th colspan="3">{{#i18n}}reservation-page.total{{/i18n}}</th><td><strong>{{totalPrice}} {{event.currency}}</strong></td></tr>
	</tfoot>
</table>

{{#hasErrors}}
<ul>
	{{#error.globalErrors}}
		<li>global error: {{code}}</li>
	{{/error.globalErrors}}
	{{#error.fieldErrors}}
		<li>field error : {{field}} : {{code}}</li>
	{{/error.fieldErrors}}
</ul>
{{/hasErrors}}


<form action="" method="POST" id="payment-form">
	<h2>{{#i18n}}reservation-page.your-details{{/i18n}}</h2>
	<div class="form-group">
		<label for="full-name">{{#i18n}}common.full-name{{/i18n}}</label>
		<input type="text" name="fullName" id="full-name" required class="form-control" autocomplete="off">
	</div>
	<div class="form-group">
		<label for="email">{{#i18n}}common.email{{/i18n}}</label>
		<input type="email" name="email" id="email" required class="form-control" autocomplete="off">
	</div>
	{{#free}}
	<button type="submit" class="btn btn-primary pull-right">{{#i18n}}reservation-page.continue{{/i18n}}</button>
	<button type="submit" class="btn btn-info pull-left" id="cancel-reservation">{{#i18n}}reservation-page.cancel{{/i18n}}</button>
	{{/free}}
	{{^free}}
	
	<div class="form-group">
		<label for="billing-address">{{#i18n}}reservation-page.billing-address{{/i18n}}</label>
		<textarea name="billingAddress" id="billing-address" class="form-control"></textarea>
	</div>
	
	<h2>{{#i18n}}reservation-page.payment{{/i18n}}</h2>

	<span class="payment-errors"></span>

	<div class="form-group">
		<label for="card-number">{{#i18n}}reservation-page.card-number{{/i18n}}</label>
		<input type="text" size="20" data-stripe="number" required class="form-control" id="card-number" autocomplete="off">
	</div>
 
	<div class="form-group">
		<div><label for="card-cvc"> {{#i18n}}reservation-page.cvc{{/i18n}}</label></div>
		<div class="row">
			<div class="col-xs-2">
				<input type="text" size="4" data-stripe="cvc" required class="form-control" id="card-cvc" autocomplete="off">
			</div>
		</div>
	</div>
 
 
	<div class="form-group">
		<div>
			<label> {{#i18n}}reservation-page.expiration{{/i18n}}</label>
		</div>
		<div class="row">
			<div class="col-xs-2">
				<input type="text" size="2" data-stripe="exp-month" required class="form-control" maxlength="2" autocomplete="off"/>
			</div>
			<div class="col-xs-1">/</div>
			<div class="col-xs-2">
				<input type="text" size="4" data-stripe="exp-year" required class="form-control" maxlength="4" autocomplete="off"/>
			</div>
		</div>
	</div>
	
	<div class="form-group">
		<button type="submit" class="btn btn-success pull-right">{{#i18n}}reservation-page.pay{{/i18n}} {{totalPrice}} {{event.currency}}</button>
		<button type="submit" class="btn btn-info pull-left" id="cancel-reservation">{{#i18n}}reservation-page.cancel{{/i18n}}</button>
	</div>
	{{/free}}
 
 	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
 	
 	<div class="clearfix"></div>
	
</form>

{{>/event/page-bottom}}