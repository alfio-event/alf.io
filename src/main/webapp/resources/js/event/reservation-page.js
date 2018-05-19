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

        var hiddenClasses = 'hidden-xs hidden-sm hidden-md hidden-lg';

        $(document).ready(function() {
            $(":input:not(input[type=button],input[type=submit],button):visible:first").focus();
        });
        
        H5F.setup(document.getElementById("payment-form"));
        
        
        //validity
        //ready for ECMAScript6?
        var parser = Number && Number.parseInt ? Number : window;
        var validity = new Date(parser.parseInt($("#validity").attr('data-validity')));
        
        var displayMessage = function() {

            var validityElem = $("#validity");
            var template = validityElem.attr('data-message');

            countdown.setLabels(
                validityElem.attr('data-labels-singular'),
                validityElem.attr('data-labels-plural'),
                ' '+validityElem.attr('data-labels-and')+' ',
                ', ');

            var timerId = countdown(validity, function(ts) {
                        if(ts.value < 0) {
                            validityElem.html(template.replace('##time##', ts.toHTML("strong")));
                        } else {
                            clearInterval(timerId);
                            $('#validity-container').html('<strong>'+validityElem.attr('data-message-time-elapsed')+'</strong>');
                            $('#continue-button').addClass('hidden');
                        }
                    }, countdown.MONTHS|countdown.WEEKS|countdown.DAYS|countdown.HOURS|countdown.MINUTES|countdown.SECONDS);

        };
        
        displayMessage();
        

        function submitForm(e) {
            var $form = $(this);
            
            if(!this.checkValidity()) {
                return false;
            }

            var vatCountry = $('#vatCountry');
            if(vatCountry.length && vatCountry.val() !== '') {
                var vatNr = $('#vatNr');
                markFieldAsError(vatNr);
                $('#validation-result-container').removeClass(hiddenClasses);
                var validationResult = $('#validation-result');
                validationResult.html(validationResult.attr('data-validation-required-msg'));
                vatNr.focus();
                return false;
            }
             
            // Disable the submit button to prevent repeated clicks
            $form.find('button').prop('disabled', true);

            var selectedPaymentMethod = $form.find('input[name=paymentMethod]');
            if(hasStripe && (selectedPaymentMethod.length === 0 ||
                (selectedPaymentMethod.length === 1 && selectedPaymentMethod.val() === 'STRIPE') ||
                selectedPaymentMethod.filter(':checked').val() === 'STRIPE')) {
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
        
        $('#payment-form').submit(submitForm);

        function markFieldAsError(node) {
            $(node).parent().addClass('has-error');
            if($(node).parent().parent().parent().hasClass('form-group')) {
                $(node).parent().parent().parent().addClass('has-error');
            }
        }

        
        // based on http://tjvantoll.com/2012/08/05/html5-form-validation-showing-all-error-messages/
        // http://stackoverflow.com/questions/13798313/set-custom-html5-required-field-validation-message
        var createAllErrors = function() {
            var form = $(this);

            var showAllErrorMessages = function() {
                $(form).find('.has-error').removeClass('has-error');
                // Find all invalid fields within the form.
                var invalidFields = form.find("input,select,textarea").filter(function(i,v) {return !v.validity.valid;}).each( function( index, node ) {
                    markFieldAsError(node);
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
                    var fullName = $.trim($.trim($('#first-name').val()) + ' ' + $.trim($('#last-name').val()));
                    if(fullName === '') {
                        fullName = $.trim($('#full-name').val());
                    }
                    $('#card-name').val(fullName);
                    inputFields.first().focus();

                } else {
                    $('#payment-method-STRIPE').find('input').val('').removeAttr('required');
                    methodSelected(method);
                }
            });
        }

        $('#first-name, #last-name').change(function() {
            fillAttendeeData($('#first-name').val(), $('#last-name').val());
        });
        $('#full-name').change(function() {
            fillAttendeeData($(this).val());
        });
        $('#email').change(function() {
            updateIfNotTouched($('#attendeesData').find('.attendee-email').first(), $(this).val());
        });

        $('#attendeesData').find('.attendee-full-name,.attendee-first-name,.attendee-last-name,.attendee-email').first()
            .change(function() {
                $(this).removeClass('untouched');
            });

        var postponeAssignment = $('#postpone-assignment');

        postponeAssignment.change(function() {
            var element = $('#attendeesData');
            if($(this).is(':checked')) {
                element.find('.field-required').attr('required', false);
                element.addClass(hiddenClasses)
            } else {
                element.find('.field-required').attr('required', true);
                element.removeClass(hiddenClasses)
            }
        });

        if(postponeAssignment.is(':checked')) {
            $('#attendeesData').find('.field-required').attr('required', false);
        }

        function fillAttendeeData(firstOrFullName, lastName) {
            var useFullName = (typeof lastName == "undefined");
            var element = $('#attendeesData');
            if(useFullName) {
                updateIfNotTouched(element.find('.attendee-full-name').first(), firstOrFullName);
            } else {
                updateIfNotTouched(element.find('.attendee-first-name').first(), firstOrFullName);
                updateIfNotTouched(element.find('.attendee-last-name').first(), lastName);
            }
        }

        function updateIfNotTouched(element, newValue) {
            if(element.hasClass('untouched')) {
                element.val(newValue);
            }
        }


        function disableBillingFields() {
            $('#vatNr,#vatCountryCode').attr('required', false).attr('disabled', '');
        }

        disableBillingFields();



        $('#invoice-requested').change(function() {
            var element = $('#invoice');
            if($(this).is(':checked')) {
                element.find('.field-required').attr('required', true);
                element.removeClass('hidden');
                euBillingCountry.change();
                if(euBillingCountry.length === 0) {
                    $('#billing-address-container').removeClass(hiddenClasses);
                    $('#billing-address').attr('required', true).removeAttr('disabled');
                }
            } else {
                element.find('.field-required').attr('required', false);
                $('#billing-address').attr('required', false).attr('disabled');
                element.addClass('hidden');
                disableBillingFields();
            }
        });




        var euBillingCountry = $('#vatCountry');
        euBillingCountry.change(function() {
            if($(this).val() === '') {
                $('#billing-address-container').removeClass(hiddenClasses);
                $('#billing-address').attr('required', true).removeAttr('disabled');
                $('#validation-result-container, #vat-number-container, #validateVAT').addClass(hiddenClasses);
                $('#vatNr').attr('required', false).attr('disabled', '');
                $("#vatCountryCode").attr('required', false).attr('disabled', '');
            } else {
                $('#billing-address-container').addClass(hiddenClasses);
                $('#validation-result-container, #vat-number-container, #validateVAT').removeClass(hiddenClasses);
                $('#billing-address').attr('required', false).attr('disabled');
                $('#vatNr').attr('required', true).removeAttr('disabled');
                $("#vatCountryCode").attr('required', true).removeAttr('disabled', '');
            }
        });

        $('#validateVAT').click(function() {
            var frm = $(this.form);
            var action = $(this).attr('data-validation-url');
            var vatInput = $('#vatNr');
            vatInput.removeClass('has-error');
            vatInput.parent('div').removeClass('has-error');
            var vatNr = vatInput.val();
            var country = euBillingCountry.val();
            var resultContainer = $('#validation-result');
            if(vatNr !== '' && country !== '') {
                var btn = $(this);
                btn.html('<i class="fa fa-circle-o-notch fa-spin"></i>');
                $('#continue-button').attr('disabled', true);
                jQuery.ajax({
                    url: action,
                    type: 'POST',
                    data: frm.serialize(),
                    success: function() {
                        window.location.reload();
                    },
                    error: function(xhr, textStatus, errorThrown) {
                        vatInput.addClass('has-error');
                        vatInput.parent('div').addClass('has-error');
                        if(xhr.status === 400) {
                            resultContainer.html(resultContainer.attr('data-validation-error-msg'));
                        } else {
                            resultContainer.html(resultContainer.attr('data-generic-error-msg'));
                        }
                    },
                    complete: function(xhr) {
                        btn.html(btn.attr('data-text'));
                        $('#continue-button').attr('disabled', false);
                    }

                });
            }
        })
    });

    window.recaptchaLoadCallback = function() {
        window.recaptchaReady = true;
        var methods = $('input[name=paymentMethod]');
        if(methods.length === 1) {
            methodSelected(methods.val());
        } else if(methods.length === 0) {
            $('#captcha-FREE').each(function(e) {
                methodSelected('FREE');
            });
        }
    };

    var methodSelected = function(method) {
        if((method === 'FREE' || method === 'OFFLINE' || method === 'ON_SITE') && window.recaptchaReady) {
            $('.g-recaptcha').each(function(i, e) {
                try {
                    grecaptcha.reset(e.id);
                } catch(x) {}
            });
            try {
                grecaptcha.render('captcha-'+method, {
                    'sitekey': $('#captcha-'+method).attr('data-sitekey'),
                    'hl': $('html').attr('lang')
                });
            } catch(x) {}
        }
    };


})();