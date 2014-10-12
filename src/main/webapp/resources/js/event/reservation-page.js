(function() {
	
	'use strict';
	
	
	function stripeResponseHandler(status, response) {
		var $form = $('#payment-form');
	 
		if (response.error) {
			// Show the errors on the form
			// TODO: see http://stackoverflow.com/questions/23437439/non-english-texts-in-stripe-possible 
			// use the code for handle the localization
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
	
	var hasStripe = document.getElementById("stripe-key") != null;
	
	if(hasStripe) {
		// This identifies your website in the createToken call below
		Stripe.setPublishableKey(document.getElementById("stripe-key").getAttribute('data-stripe-key'));
	}
	
	
	 
	jQuery(function($) {
		
		function submitForm(e) {
			var $form = $(this);
			 
			// Disable the submit button to prevent repeated clicks
			$form.find('button').prop('disabled', true);
		 
			Stripe.card.createToken($form, stripeResponseHandler);
	 
			// Prevent the form from submitting with the default action
			return false;
		}
		
		
		
		$("#cancel-reservation").click(function(e) {
			var $form = $('#payment-form');
			$form
				.attr('novalidate', 'novalidate')
				.unbind('submit', submitForm)
				.find('button').prop('disabled', true);
			$form.append($('<input type="hidden" name="cancelReservation" />').val(true))
			return true;
		});
		
		if(hasStripe) {
			$('#payment-form').submit(submitForm);
		}
	});
})();