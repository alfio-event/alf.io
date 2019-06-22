(function () {
    "use strict";

    angular.module('adminApplication')
        .service('ReservationIdentifierConfiguration', ['ConfigurationService', '$window', ReservationIdentifierConfiguration])
        .component('reservationIdentifier', {
            bindings: {
                reservation: '<',
                eventId: '<',
                displayFullReservationId: '<'
            },
            controller: ['ReservationIdentifierConfiguration', ReservationIdentifierController],
            template: '<span>{{$ctrl.reservationIdentifier}}</span>'
        });

    function ReservationIdentifierController(ReservationIdentifierConfiguration) {
        var ctrl = this;
        ctrl.$onInit = function() {
            ReservationIdentifierConfiguration.getReservationIdentifier(ctrl.eventId, ctrl.reservation, ctrl.displayFullReservationId)
                .then(function(result) {
                    ctrl.reservationIdentifier = result;
                });
        };
    }


    function ReservationIdentifierConfiguration(ConfigurationService, $window) {
        var config = {};
        return {
            getReservationIdentifier: function(eventId, reservation, displayFullId) {
                if(config[eventId] == null) {
                    config[eventId] = ConfigurationService.loadSingleConfigForEvent(eventId, 'USE_INVOICE_NUMBER_AS_ID').then(function(result) {
                        return result.data;
                    });
                    $window.setTimeout(function() {
                        config[eventId] = null;
                    }, 30000);
                }
                return config[eventId].then(function(result) {
                    var useInvoiceNumber = (result === 'true'); // default is false
                    if(useInvoiceNumber) {
                        return reservation.invoiceNumber || 'N/A';
                    }
                    return displayFullId ? reservation.id : reservation.id.substring(0,8).toUpperCase();
                });
            }
        }

    }

})();