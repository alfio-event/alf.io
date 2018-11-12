(function(w) {

    'use strict';

    var setup = function() {
        if(w.alfio && w.alfio.registerPaymentHandler) {
            w.alfio.registerPaymentHandler({
                paymentMethod: 'PAYPAL',
                id: 'PAYPAL',
                pay: function(confirmHandler) {
                    confirmHandler(true);
                },
                init: function() {
                },
                active: function() {
                    return true;
                }
            });
        } else {
            w.setTimeout(setup, 50);
        }
    };


    setup();

})(window, document);