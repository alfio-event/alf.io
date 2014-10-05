<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <link rel="stylesheet" href="/resources/bower_components/bootstrap/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="/resources/bower_components/components-font-awesome/css/font-awesome.min.css">
    <link rel="stylesheet" href="/resources/css/application.css">
</head>
<body>


<div class="container">
<div class="row">
<div class="col-md-8 col-md-offset-2">


{{^free}}
 <script type="text/javascript" src="https://js.stripe.com/v2/"></script>
 <script type="text/javascript" src="https://ajax.googleapis.com/ajax/libs/jquery/1.9.1/jquery.min.js"></script>
 
<script type="text/javascript">
// This identifies your website in the createToken call below
Stripe.setPublishableKey('{{stripe_p_key}}');
 
var stripeResponseHandler = function(status, response) {
	var $form = $('#payment-form');
 
	if (response.error) {
		// Show the errors on the form
		$form.find('.payment-errors').text(response.error.message);
		$form.find('button').prop('disabled', false);
	} else {
		// token contains id, last4, and card type
		var token = response.id;
		// Insert the token into the form so it gets submitted to the server
		$form.append($('<input type="hidden" name="stripeToken" />').val(token));
		// and re-submit
		$form.get(0).submit();
	}
};
 
jQuery(function($) {
	$('#payment-form').submit(function(e) {
		var $form = $(this);
 
		// Disable the submit button to prevent repeated clicks
		$form.find('button').prop('disabled', true);
	 
		Stripe.card.createToken($form, stripeResponseHandler);
 
		// Prevent the form from submitting with the default action
		return false;
	});
});
</script>
{{/free}}

<h2>Order summary (TODO: you have X minutes to complete this order)</h2>
<table class="table">
	<thead>	
		<th>Category</th>
		<th>Price</th>
		<th>Amount</th>
		<th>Subtotal</th>
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
		<tr><th colspan="3">Total</th><td>{{totalPrice}} {{event.currency}}</td></tr>
	</tfoot>
</table>




<form action="" method="POST" id="payment-form">
	<h2>Your details</h2>
	<div class="form-group">
		<label for="full-name">Full name</label>
		<input type="text" name="fullName" id="full-name" required class="form-control">
	</div>
	<div class="form-group">
		<label for="email">Email</label>
		<input type="email" name="email" id="email" required class="form-control">
	</div>
	{{#free}}
	<button type="submit" class="btn btn-primary pull-right">Continue</button>
	{{/free}}
	{{^free}}
	
	<div class="form-group">
		<label for="billing-address">Billing address</label>
		<textarea name="billingAddress" id="billing-address" class="form-control"></textarea>
	</div>
	
	<h2>Payment (https://stripe.com/docs/testing)</h2>

	<span class="payment-errors"></span>
 
	<div class="form-group">
		<label for="card-number">Card Number</label>
		<input type="text" size="20" data-stripe="number" required class="form-control" id="card-number">
	</div>
 
	<div class="form-group">
		<div><label for="card-cvc">CVC</label></div>
		<div class="row">
			<div class="col-xs-2">
				<input type="text" size="4" data-stripe="cvc" required class="form-control" id="card-cvc">
			</div>
		</div>
	</div>
 
 
	<div class="form-group">
		<div>
			<label>Expiration (MM/YYYY)
		</div>
		<div class="row">
			<div class="col-xs-2">
				<input type="text" size="2" data-stripe="exp-month" required class="form-control" maxlength="2"/>
			</div>
			<div class="col-xs-1">/</div>
			<div class="col-xs-2">
				<input type="text" size="4" data-stripe="exp-year" required class="form-control" maxlength="4"/>
			</div>
		</div>
	</div>
	
	<div class="form-group">
		<button type="submit"  class="btn btn-primary pull-right">Submit payment and continue</button>
	</div>
	{{/free}}
 
 	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
 	
	
</form>

</div>
</div>
</div>

</body>
</html>