(function(w, doc) {

    'use strict';

    var setup = function() {
        if(w.alfio && w.alfio.registerPaymentHandler) {
            var stripeHandler;
            var confirmFn;
            var closeFn;
            var stripeEl = doc.getElementById("stripe-key");

            w.alfio.registerPaymentHandler({
                paymentMethod: 'CREDIT_CARD',
                id: 'STRIPE',
                pay: function(confirmHandler, cancelHandler) {
                    stripeHandler.open({
                        name: stripeEl.getAttribute('data-stripe-title'),
                        description: stripeEl.getAttribute('data-stripe-description'),
                        zipCode: false,
                        allowRememberMe: false,
                        amount: (stripeEl.getAttribute('data-price') || ''),
                        currency: stripeEl.getAttribute('data-currency'),
                        email: stripeEl.getAttribute('data-stripe-email')
                    });
                    confirmFn = confirmHandler;
                    closeFn = cancelHandler;
                },
                init: function() {
                    stripeHandler = StripeCheckout.configure({
                        key: stripeEl.getAttribute('data-stripe-key'),
                        image: doc.getElementById("event-logo").getAttribute('src'),
                        locale: $('html').attr('lang'),
                        token: function(token) {
                            var $form = $('#payment-form');
                            $form.append($('<input type="hidden" name="gatewayToken" />').val(token.id));
                            confirmFn(true);
                        },
                        closed: function() {
                            if(closeFn) {
                                closeFn();
                            }
                        }
                    });
                },
                active: function() {
                    return doc.getElementById("stripe-key") != null;
                }
            });
        } else {
            w.setTimeout(setup, 50);
        }
    };


    setup();

})(window, document);