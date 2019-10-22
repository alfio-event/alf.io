(function(w, doc) {

    'use strict';

    var style = {
        base: {
            color: '#000000',
            lineHeight: '18px',
            fontFamily: '"Helvetica Neue", Helvetica, sans-serif',
            fontSmoothing: 'antialiased',
            fontSize: '14px',
            '::placeholder': {
                color: '#aab7c4'
            }
        },
        invalid: {
            color: '#a94442',
            iconColor: '#a94442'
        }
    };

    var setup = function() {
        if(w.alfio && w.alfio.registerPaymentHandler) {
            var stripeHandler;
            var card;
            var stripeEl = doc.getElementById("stripe-key");
            var eventName = stripeEl.getAttribute("data-stripe-event-name");
            var reservationId = stripeEl.getAttribute("data-stripe-reservation-id");
            var readyToGo = false;

            w.alfio.registerPaymentHandler({
                paymentMethod: 'CREDIT_CARD',
                id: 'STRIPE',
                pay: function(confirmHandler, cancelHandler) {
                    retrieveSecretKey(eventName, reservationId, function(secret) {
                        stripeHandler.handleCardPayment(secret, card, getMetadata(stripeEl)).then(function(result) {
                            if(result.error) {
                                cancelHandler(result.error.message);
                            } else {
                                var checkIfPaid = function() {
                                    var url = "/api/events/"+eventName+"/reservation/"+reservationId+"/payment/CREDIT_CARD/status";
                                    jQuery.ajax({
                                        url: url,
                                        type: 'GET',
                                        success: function(result) {
                                            if(result.successful) {
                                                var $form = $('#payment-form');
                                                $form.append($('<input type="hidden" name="gatewayToken" />').val(result.gatewayIdOrNull));
                                                clearInterval(handle);
                                                confirmHandler(true);
                                            }
                                            if(result.failed) {
                                                confirmHandler(false);
                                            }
                                        },
                                        error: function(xhr, textStatus, errorThrown) {
                                            errorCallback(textStatus);
                                        }
                                    });
                                };
                                var handle = setInterval(checkIfPaid, 1000);
                            }
                        });
                    }, cancelHandler);
                },
                init: function() {
                    var options = {};
                    var connectedAccount = stripeEl.getAttribute('data-stripe-on-behalf-of');
                    if(connectedAccount && connectedAccount !== '') {
                        options.stripeAccount = connectedAccount;
                    }
                    stripeHandler = Stripe(stripeEl.getAttribute('data-stripe-key'), options);

                    card = stripeHandler.elements({locale: $('html').attr('lang')}).create('card', {style: style});
                    card.mount('#card-element');
                    card.addEventListener('change', function(event) {
                        readyToGo = false;
                        var displayError = $('#card-errors');
                        var cardContainer = $('#card-element-container');
                        if (event.error) {
                            cardContainer.addClass('has-error');
                            displayError.removeClass('hide');
                            displayError.find('#error-message').text(event.error.message);
                        } else {
                            cardContainer.removeClass('has-error');
                            displayError.addClass('hide');
                            displayError.find('#error-message').text('');
                        }
                        if(event.complete) {
                            readyToGo = true;
                        }
                    });
                },
                valid: function() {
                    return readyToGo;
                },
                active: function() {
                    var attr;
                    var stripe = doc.getElementById("stripe-key");
                    return stripe != null && (attr = stripe.attributes.getNamedItem("data-stripe-embedded")) != null && attr.value === 'true';
                }
            });
        } else {
            w.setTimeout(setup, 50);
        }
    };


    setup();

    var getMetadata = function(stripeEl) {
        var nullIfEmpty = function(val) {
            if(val == null || val === '') {
                return null;
            }
            return val;
        };
        return {
            payment_method_data: {
                billing_details: {
                    name: stripeEl.getAttribute('data-stripe-contact-name'),
                    email: stripeEl.getAttribute('data-stripe-email'),
                    address: {
                        line1: nullIfEmpty(stripeEl.getAttribute('data-stripe-contact-address')),
                        postal_code: nullIfEmpty(stripeEl.getAttribute('data-stripe-contact-zip')),
                        country: nullIfEmpty(stripeEl.getAttribute('data-stripe-contact-country').toLowerCase())
                    }
                }
            }
        };
    };

    var retrieveSecretKey = function(eventName, reservationId, successCallback, errorCallback) {
        var url = "/api/events/"+eventName+"/reservation/"+reservationId+"/payment/CREDIT_CARD/init";
        jQuery.ajax({
            url: url,
            type: 'POST',
            data: {
                '_csrf': document.forms[0].elements['_csrf'].value
            },
            success: function(result) {
                if(result.errorMessage || !result.clientSecret) {
                    errorCallback(result.errorMessage);
                } else {
                    successCallback(result.clientSecret);
                }
            },
            error: function(xhr, textStatus, errorThrown) {
                errorCallback(textStatus);
            }
        });
    }

})(window, document);