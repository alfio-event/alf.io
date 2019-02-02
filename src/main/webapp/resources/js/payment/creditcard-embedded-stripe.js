(function(w, doc) {

    'use strict';

    var style = {
        base: {
            color: '#32325d',
            lineHeight: '18px',
            fontFamily: '"Helvetica Neue", Helvetica, sans-serif',
            fontSmoothing: 'antialiased',
            fontSize: '16px',
            '::placeholder': {
                color: '#aab7c4'
            }
        },
        invalid: {
            color: '#fa755a',
            iconColor: '#fa755a'
        }
    };

    var setup = function() {
        if(w.alfio && w.alfio.registerPaymentHandler) {
            var stripeHandler;
            var card;
            var stripeEl = doc.getElementById("stripe-key");

            w.alfio.registerPaymentHandler({
                paymentMethod: 'CREDIT_CARD',
                id: 'STRIPE',
                pay: function(confirmHandler, cancelHandler) {
                    var secret = ""; //TODO retrieve secret

                    stripeHandler.handleCardPayment(secret, card, {
                        source_data: {
                            owner: {
                                name: 'Jane Doe',
                                email: stripeEl.getAttribute('data-stripe-email'),
                                address: {
                                    line1: '123 Foo St.',
                                    postal_code: '94103',
                                    country: 'US'
                                }
                            }
                        }
                    }).then(function(result) {
                        if(result.error) {
                            cancelHandler(error);
                        } else {
                            //TODO polling on server to verify that the payment has been completed and notified

                            var $form = $('#payment-form');
                            $form.append($('<input type="hidden" name="gatewayToken" />').val(result.token.id));
                            confirmHandler(true);
                        }
                    });

                },
                init: function() {
                    stripeHandler = Stripe(stripeEl.getAttribute('data-stripe-key'), {
                        betas: ['payment_intent_beta_3']
                    });

                    card = stripeHandler.elements().create('card', {style: style});
                    card.mount('#card-element');
                    card.addEventListener('change', function(event) {
                        var displayError = document.getElementById('card-errors');
                        if (event.error) {
                            displayError.textContent = event.error.message;
                        } else {
                            displayError.textContent = '';
                        }
                    });
                },
                active: function() {
                    var attr;
                    var stripe = doc.getElementById("stripe-key");
                    return stripe != null && (attr = stripe.attributes.getNamedItem("data-stripe-mode")) != null && attr.value === 'embedded';
                }
            });
        } else {
            w.setTimeout(setup, 50);
        }
    };


    setup();

})(window, document);