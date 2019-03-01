(function() {

    'use strict';

    var element = $('#config-element');
    var url = "/api/events/"+element.attr('data-event-name')+"/reservation/"+element.attr('data-reservation-id')+"/payment/"+element.attr('data-payment-method')+"/status";
    var checkIfPaid = function() {
        jQuery.ajax({
            url: url,
            type: 'GET',
            success: function(result) {
                if(result.successful || result.failed) {
                    clearInterval(handle);
                    window.location.reload(true);
                }
            },
            error: function(xhr, textStatus, errorThrown) {
                if(console && console.log) {
                    console.log("error while checking", textStatus);
                }
                if(xhr.status === 404) {
                    window.location.reload(true);
                }
            }
        });
    };

    var handle = setInterval(checkIfPaid, 5000);

})();