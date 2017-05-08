(function() {
    'use strict';

    angular.module('adminApplication').component('reservationCancel', {
        controller: ['AdminReservationService', 'EventService', ReservationCancelCtrl],
        templateUrl: '../resources/js/admin/feature/reservation-cancel/reservation-cancel.html',
        bindings: {
            event: '<',
            reservationId: '<',
            onSuccess: '&',
            onCancel:'&'
        }
    });


    function ReservationCancelCtrl(AdminReservationService, EventService) {
        var ctrl = this;

        ctrl.confirmRemove = confirmRemove;

        ctrl.$onInit = function() {
            ctrl.refund = true;
            AdminReservationService.paymentInfo(ctrl.event.shortName, ctrl.reservationId).then(function(res) {
                ctrl.paymentInfo = res.data.data;
            }).finally(function() {
                ctrl.paymentInfo = {};
            });
        };

        function confirmRemove() {
            ctrl.submitted = true;
            return EventService.cancelReservation(ctrl.event.shortName, ctrl.reservationId, ctrl.refund).then(function() {
                ctrl.onSuccess();
            }).finally(function() {
                ctrl.submitted = false;
            });
        }
    }
})();