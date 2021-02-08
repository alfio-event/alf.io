(function() {
    'use strict';

    angular.module('adminApplication').component('reservationCancel', {
        controller: ['AdminReservationService', 'EventService', 'NotificationHandler', ReservationCancelCtrl],
        templateUrl: '../resources/js/admin/feature/reservation-cancel/reservation-cancel.html',
        bindings: {
            event: '<',
            reservationId: '<',
            canGenerateCreditNote: '<',
            credit: '<',
            onSuccess: '&',
            onCancel:'&'
        }
    });


    function ReservationCancelCtrl(AdminReservationService, EventService, NotificationHandler) {
        var ctrl = this;

        ctrl.confirmRemove = confirmRemove;
        ctrl.operation = ctrl.credit ? "Credit" : "Cancel";

        ctrl.$onInit = function() {
            ctrl.refund = true;
            ctrl.notify = false;
            ctrl.issueCreditNote = ctrl.canGenerateCreditNote;
            AdminReservationService.paymentInfo(ctrl.event.shortName, ctrl.reservationId).then(function(res) {
                ctrl.paymentInfo = res.data.data;
            }).catch(function() {
                ctrl.paymentInfo = {};
            });
        };

        function confirmRemove() {
            ctrl.submitted = true;
            if(ctrl.paymentInfo.supportRefund && !ctrl.refund) {
                // don't issue a credit note if there's nothing to credit
                ctrl.issueCreditNote = false;
            }
            return EventService.cancelReservation(ctrl.event.shortName, ctrl.reservationId, ctrl.refund, ctrl.notify, ctrl.credit, ctrl.issueCreditNote).then(function(response) {
                if(response.data.success) {
                    ctrl.onSuccess();
                } else {
                    NotificationHandler.showError(response.data.firstErrorOrNull.description);
                }
            }).finally(function() {
                ctrl.submitted = false;
            });
        }
    }
})();