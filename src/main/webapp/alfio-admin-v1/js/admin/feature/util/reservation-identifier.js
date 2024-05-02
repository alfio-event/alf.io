(function () {
    "use strict";

    angular.module('adminApplication')
        .service('ReservationIdentifierConfiguration', ['ConfigurationService', '$window', '$q', ReservationIdentifierConfiguration])
        .component('reservationIdentifier', {
            bindings: {
                reservation: '<',
                purchaseContext: '<',
                purchaseContextType: '<',
                displayFullReservationId: '<'
            },
            controller: ['ReservationIdentifierConfiguration', ReservationIdentifierController],
            template: '<span>{{$ctrl.reservationIdentifier}}</span>'
        });

    function ReservationIdentifierController(ReservationIdentifierConfiguration) {
        var ctrl = this;
        ctrl.$onInit = function() {
            ReservationIdentifierConfiguration.getReservationIdentifier(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservation, ctrl.displayFullReservationId)
                .then(function(result) {
                    ctrl.reservationIdentifier = result;
                });
        };
    }


    function ReservationIdentifierConfiguration(ConfigurationService, $window, $q) {
        var config = {};
        return {
            getReservationIdentifier: function(purchaseContextType, purchaseContextId, reservation, displayFullId) {
                var key = purchaseContextType + '__' + purchaseContextId;
                if(config[key] == null) {
                    if(purchaseContextType === 'event') {
                        config[key] = ConfigurationService.loadSingleConfigForEvent(purchaseContextId, 'USE_INVOICE_NUMBER_AS_ID').then(function(result) {
                            return result.data;
                        });
                    } else {
                        config[key] = $q.resolve(false);
                    }
                    $window.setTimeout(function() {
                        config[key] = null;
                    }, 30000);
                }
                var deferred = $q.defer();
                config[key].then(function(result) {
                    var useInvoiceNumber = (result === 'true'); // default is false
                    if(useInvoiceNumber) {
                        deferred.resolve(reservation.invoiceNumber || 'N/A');
                    }
                    deferred.resolve(displayFullId ? reservation.id : reservation.id.substring(0,8).toUpperCase());
                }, function() {
                    deferred.resolve(displayFullId ? reservation.id : reservation.id.substring(0,8).toUpperCase());
                });
                return deferred.promise;
            }
        }

    }

})();