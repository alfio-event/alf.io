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
                        name: stripeEl.getAttribute('data-stripe-contact-name'),
                        description: stripeEl.getAttribute('data-stripe-description'),
                        zipCode: false,
                        allowRememberMe: false,
                        amount: parseInt(stripeEl.getAttribute('data-price') || '0', 10),
                        currency: stripeEl.getAttribute('data-currency'),
                        email: stripeEl.getAttribute('data-stripe-email')
                    });
                    confirmFn = confirmHandler;
                    closeFn = cancelHandler;
                },
                init: function() {
                    var confirmCalled = false;
                    stripeHandler = StripeCheckout.configure({
                        key: stripeEl.getAttribute('data-stripe-key'),
                        image: doc.getElementById("event-logo").getAttribute('src'),
                        locale: $('html').attr('lang'),
                        token: function(token) {
                            var $form = $('#payment-form');
                            $form.append($('<input type="hidden" name="gatewayToken" />').val(token.id));
                            confirmFn(true);
                            confirmCalled = true;
                        },
                        closed: function() {
                            if(closeFn && !confirmCalled) {
                                closeFn();
                            }
                        }
                    });
                },
                valid: function() {
                    return true;
                },
                active: function() {
                    var attr;
                    var stripe = doc.getElementById("stripe-key");
                    //checkout is the default
                    return stripe != null && (attr = stripe.attributes.getNamedItem("data-stripe-embedded")) == null || attr.value === 'false';
                }
            });
        } else {
            w.setTimeout(setup, 50);
        }
    };


    setup();

})(window, document);