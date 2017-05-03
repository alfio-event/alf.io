(function() {
    'use strict';

    angular.module('adminApplication').component('reservationCancel', {
        controller: ['EventService', ReservationCancelCtrl],
        templateUrl: '../resources/js/admin/feature/reservation-cancel/reservation-cancel.html',
        bindings: {
            event: '<',
            reservationId: '<',
            onSuccess: '&',
            onCancel:'&'
        }
    });


    function ReservationCancelCtrl(EventService) {
        var ctrl = this;

        ctrl.confirmRemove = confirmRemove;

        ctrl.$onInit = function() {
            ctrl.refund = true;
        }

        function confirmRemove() {
            return EventService.cancelReservation(ctrl.event.shortName, ctrl.reservationId, ctrl.refund).then(function() {
                ctrl.onSuccess();
            });
        }
    }
})();