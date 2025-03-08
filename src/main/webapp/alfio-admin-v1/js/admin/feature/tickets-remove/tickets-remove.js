(function() {
    'use strict';

    angular.module('adminApplication').component('ticketsRemove', {
        controller: ['AdminReservationService', 'EventService', TicketsRemoveCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/tickets-remove/tickets-remove.html',
        bindings: {
            event: '<',
            reservationId: '<',
            ticketId:'<',
            canGenerateCreditNote: '<',
            onSuccess: '&',
            onCancel:'&'
        }
    });


    function TicketsRemoveCtrl(AdminReservationService, EventService) {
        var ctrl = this;

        ctrl.confirmRemove = confirmRemove;

        ctrl.$onInit = function() {
            ctrl.toRefund = {};
            ctrl.notify = false;
            ctrl.issueCreditNote = ctrl.canGenerateCreditNote;
            AdminReservationService.paymentInfo('event', ctrl.event.shortName, ctrl.reservationId).then(function(res) {
                ctrl.paymentInfo = res.data.data;
            });

            AdminReservationService.getTicket(ctrl.event.shortName, ctrl.reservationId, ctrl.ticketId).then(function(res) {
                ctrl.ticket = res.data.data;
            })
        };

        function confirmRemove() {
            ctrl.submitted = true;
            if (!ctrl.paymentInfo.supportRefund && ctrl.canGenerateCreditNote && ctrl.issueCreditNote) {
                // if payment provider does not support refund, but credit note has been requested,
                // we simulate a refund anyway, otherwise credit note does
                ctrl.toRefund[ctrl.ticketId] = true;
            }
            return EventService.removeTickets(ctrl.event.shortName, ctrl.reservationId, [ctrl.ticketId], ctrl.toRefund, ctrl.notify, ctrl.issueCreditNote).then(function(result) {
                ctrl.onSuccess({ result: result.data.data.creditNoteGenerated });
            }).finally(function() {
                ctrl.submitted = false;
            });
        }
    }
})();