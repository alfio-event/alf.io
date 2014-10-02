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

<h2>Order summary</h2>
<table>
	<thead>	
		<th>category</th>
		<th>price</th>
		<th>amount</th>
		<th>subtotal</th>
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
		<tr><th colspan="3">total</th><td>{{totalPrice}} {{event.currency}}</td></tr>
	</tfoot>
</table>




<form action="" method="POST" id="payment-form">
	<h2>Billing details</h2>
	<p>full name</p>
	<p>email</p>
	<p>optional address textarea</p>

	<h2>Payment</h2>
	
	<h3>https://stripe.com/docs/testing</h3>

	<span class="payment-errors"></span>
 
	<div class="form-row">
		<label>
			<span>Card Number</span>
			<input type="text" size="20" data-stripe="number"/>
		</label>
	</div>
 
	<div class="form-row">
		<label>
			<span>CVC</span>
			<input type="text" size="4" data-stripe="cvc"/>
		</label>
	</div>
 
 
	<div class="form-row">
		<label>
			<span>Expiration (MM/YYYY)</span>
			<input type="text" size="2" data-stripe="exp-month"/>
		</label>
		<span> / </span>
		<input type="text" size="4" data-stripe="exp-year"/>
	</div>
 
 	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
 	
	<button type="submit">Submit Payment</button>
</form>