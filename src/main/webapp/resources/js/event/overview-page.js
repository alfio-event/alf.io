(function() {

    'use strict';
    var paymentHandlers = [];

    window.alfio = {
        registerPaymentHandler: function(handler) {
            handler.init();
            paymentHandlers.push(handler);
        }
    };

    jQuery(function() {
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

        $("#cancel-reservation").click(function(e) {
            var $form = $('#payment-form');
            $("input[type=submit], button:not([type=button])", $form ).unbind('click');
            $form.unbind('submit');
            $("input", $form).unbind('keypress');

            $form
                .attr('novalidate', 'novalidate')
                .unbind('submit', submitForm)
                .find('button').prop('disabled', true);
            $form.trigger("reset");
            $form.append($('<input type="hidden" name="backFromOverview" />').val(true))
            $form.submit();
        });


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


        var paymentMethod = $('input[name=paymentMethod]');
        if(paymentMethod.length > 1) {
            $('#payment-method-STRIPE').find('input').removeAttr('required');
            $('.payment-method-detail').hide();

            paymentMethod.change(function() {
                var method = $(this).attr('data-payment-method');
                $('.payment-method-detail').hide();
                $('#payment-method-'+method).show();
            });
        }

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

        var btn = $('#continue-button');
        btn.on('click', function(e) {
            var $form = $('#payment-form');
            if($form.length === 0 || !$form.get(0).checkValidity()) {
                return false;
            }
            var selectedPaymentMethod = $form.find('input[name=paymentMethod]');
            if(selectedPaymentMethod.length > 1) {
                selectedPaymentMethod = selectedPaymentMethod.filter(":checked");
            }
            var filteredHandlers = paymentHandlers.filter(function(ph) {return ph.id === selectedPaymentMethod.val() && ph.active(); });
            var paymentHandler = filteredHandlers ? filteredHandlers[0] : null;
            if(paymentHandler) {
                btn.hide();
                paymentHandler.pay(function(res) {
                    if(res) {
                        $form.submit();
                    }
                }, function() {
                    btn.show();
                });
            }
            e.preventDefault();
        })

    });


})();