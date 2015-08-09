(function() {
    
    'use strict';
    
    
    function stripeResponseHandler(status, response) {
        var $form = $('#payment-form');
     
        
        //https://stripe.com/docs/api#errors codes from stripes
        
        /*
         * incorrect_number         The card number is incorrect.
         * invalid_number           The card number is not a valid credit card number.
         * invalid_expiry_month     The card's expiration month is invalid.
         * invalid_expiry_year      The card's expiration year is invalid.
         * invalid_cvc              The card's security code is invalid.
         * expired_card             The card has expired.
         * incorrect_cvc            The card's security code is incorrect.
         * incorrect_zip            The card's zip code failed validation.
         * card_declined            The card was declined.
         * missing                  There is no card on a customer that is being charged.
         * processing_error         An error occurred while processing the card.
         * rate_limit               An error occurred due to requests hitting the API too quickly.
         * 
         */
        
        var errorCodeToSelectorMap = {
            incorrect_number : '[data-stripe=number]',
            invalid_number: '[data-stripe=number]',
            invalid_expiry_month : '[data-stripe=exp-month]',
            invalid_expiry_year : '[data-stripe=exp-year]',
            invalid_cvc : '[data-stripe=cvc]',
            expired_card : '[data-stripe]',
            incorrect_cvc : '[data-stripe=cvc]',
            card_declined : '[data-stripe]',
            missing : '[data-stripe]',
            processing_error : '[data-stripe]',
            rate_limit : '[data-stripe]'
        };
        
        if (response.error) {
            $(".payment-errors").removeClass('hide').empty();
            $("[data-stripe]").parent().removeClass('has-error');
            
            
            var attrValue = document.getElementById("stripe-key").getAttribute('data-stripe-message-'+response.error.code);
            
            $form.find('.payment-errors').append("<p><strong>"+(attrValue || response.error.message)+"</strong></p>");
            $form.find('button').prop('disabled', false);
            $form.find(errorCodeToSelectorMap[response.error.code]).parent().addClass('has-error');
            
        } else {
            $(".payment-errors").addClass('hide');
            // token contains id, last4, and card type
            var token = response.id;
            // Insert the token into the form so it gets submitted to the server
            $form.append($('<input type="hidden" name="stripeToken" />').val(token));
            // and re-submit
            $form.get(0).submit();
        }
    }
    
    var hasStripe = document.getElementById("stripe-key") != null;
    
    if(hasStripe) {
        // This identifies your website in the createToken call below
        Stripe.setPublishableKey(document.getElementById("stripe-key").getAttribute('data-stripe-key'));
    }
    
    
     
    jQuery(function() {
        
        H5F.setup(document.getElementById("payment-form"));
        
        
        //validity
        //ready for ECMAScript6?
        var parser = Number && Number.parseInt ? Number : window;
        var validity = new Date(parser.parseInt($("#validity").attr('data-validity')));
        
        var interval = null;
        
        var displayMessage = function() {

            var totalSec = Math.max(validity.getTime() - new Date().getTime(), 0) / 1000;
            var sec = totalSec % 60;
            var min = (totalSec - sec) / 60;
            var validityElem = $("#validity");
            if(totalSec <= 0) {
                validityElem.find("strong").html(validityElem.attr('data-message-time-elapsed'));
                $("#continue-button").addClass('hide');
                clearInterval(interval);
            } else {
                validityElem.find("strong").html(validityElem.attr('data-message').replace('#minutes#', min).replace('#seconds#',  Math.floor(sec)));
            }
        };
        
        displayMessage();
        
        var interval = setInterval(displayMessage, 1000);
        //
        
        
        
        function submitForm(e) {
            var $form = $(this);
            
            if(!this.checkValidity()) {
                return false;
            }
             
            // Disable the submit button to prevent repeated clicks
            $form.find('button').prop('disabled', true);

            var selectedPaymentMethod = $form.find('input[name=paymentMethod]');
            if(selectedPaymentMethod.length === 0 ||
                (selectedPaymentMethod.length === 1 && selectedPaymentMethod.val() === 'STRIPE') ||
                selectedPaymentMethod.filter(':checked').val() === 'STRIPE') {
                Stripe.card.createToken($form, stripeResponseHandler);
                // Prevent the form from submitting with the default action
                return false;
            }
            return true;
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
                var invalidFields = form.find("input,select,textarea").filter(function(i,v) {return !v.validity.valid;}).each( function( index, node ) {
                    $(node).parent().addClass('has-error');
                    if($(node).parent().parent().parent().hasClass('form-group')) {
                        $(node).parent().parent().parent().addClass('has-error');
                    }
                });
            };

            // Support Safari
            form.on("submit", function( event ) {
                if (this.checkValidity && !this.checkValidity() ) {
                    $(this).find("input,select,textarea").filter(function(i,v) {return !v.validity.valid;}).first().focus();
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
            if( !this.validity.valid) {
                $(this).parent().addClass('has-error');
                if($(this).parent().parent().parent().hasClass('form-group')) {
                    $(this).parent().parent().parent().addClass('has-error');
                }
            } else {
                $(this).parent().removeClass('has-error');
                if($(this).parent().parent().parent().hasClass('form-group')) {
                    $(this).parent().parent().parent().removeClass('has-error');
                }
            }
        });

        var paymentMethod = $('input[name=paymentMethod]');
        if(paymentMethod.length > 1) {
            $('#payment-method-STRIPE').find('input').removeAttr('required');
            $('.payment-method-detail').hide();

            paymentMethod.change(function() {
                var method = $(this).attr('data-payment-method');
                $('.payment-method-detail').hide();
                $('#payment-method-'+method).show();
                if(method === 'STRIPE') {
                    var inputFields = $('#payment-method-STRIPE').find('input');
                    inputFields.attr('required', true);
                    inputFields.first().focus();

                } else {
                    $('#payment-method-STRIPE').find('input').val('').removeAttr('required');
                }
            });
        }
    });
})();