(function() {
	
	'use strict';
	
	
	function stripeResponseHandler(status, response) {
		var $form = $('#payment-form');
	 
		if (response.error) {
			$(".payment-errors").show().empty();
			// Show the errors on the form
			// TODO: see http://stackoverflow.com/questions/23437439/non-english-texts-in-stripe-possible 
			// use the code for handle the localization
			$form.find('.payment-errors').append("<p><strong>"+response.error.message+"</strong></p>");
			$form.find('button').prop('disabled', false);
			
		} else {
			$(".payment-errors").hide();
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
		
		$(".payment-errors").hide();
		
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
			
			$("input[type=submit], button:not([type=button])", $form ).unbind('click');
			$form.unbind('submit');
			$("input", $form).unbind('keypress');
			
			$form
				.attr('novalidate', 'novalidate')
				.unbind('submit', submitForm)
				.find('button').prop('disabled', true);
			$form.append($('<input type="hidden" name="cancelReservation" />').val(true))
			$form.submit();
		});
		
		if(hasStripe) {
			$('#payment-form').submit(submitForm);
		}
		
		
		
		
		
		
		// based on http://tjvantoll.com/2012/08/05/html5-form-validation-showing-all-error-messages/
		// http://stackoverflow.com/questions/13798313/set-custom-html5-required-field-validation-message
		var createAllErrors = function() {
			var form = $(this);

			var showAllErrorMessages = function() {
				$(form).find('.has-error').removeClass('has-error');
				// Find all invalid fields within the form.
				var invalidFields = form.find( ":invalid" ).each( function( index, node ) {
					$(node).parent().addClass('has-error');
				});
			};

			// Support Safari
			form.on("submit", function( event ) {
				if (this.checkValidity && !this.checkValidity() ) {
					$(this).find( ":invalid" ).first().focus();
					event.preventDefault();
				}
			});

			$("input[type=submit], button:not([type=button])", form ).on("click", showAllErrorMessages);

			$("input", form).on("keypress", function(event) {
				var type = $(this).attr("type");
				if ( /date|email|month|number|search|tel|text|time|url|week/.test(type) && event.keyCode == 13 ) {
					showAllErrorMessages();
				}
			});
		};
		
		$("form").each(createAllErrors);
		$("input,select,textarea").change(function() {
			if($(this).is(":invalid")) {
				$(this).parent().addClass('has-error');
			} else {
				$(this).parent().removeClass('has-error');
			}
		})
	});
	
	
	
})();