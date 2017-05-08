(function() {
    'use strict';

    angular.module('adminApplication').component('ticketsRemove', {
        controller: ['AdminReservationService', 'EventService', TicketsRemoveCtrl],
        templateUrl: '../resources/js/admin/feature/tickets-remove/tickets-remove.html',
        bindings: {
            event: '<',
            reservationId: '<',
            ticketIds:'<',
            onSuccess: '&',
            onCancel:'&'
        }
    });


    function TicketsRemoveCtrl(AdminReservationService, EventService) {
        var ctrl = this;

        ctrl.confirmRemove = confirmRemove;

        ctrl.$onInit = function() {
            ctrl.toRefund = {};
            AdminReservationService.paymentInfo(ctrl.event.shortName, ctrl.reservationId).then(function(res) {
                ctrl.paymentInfo = res.data.data;
            });
        }

        function confirmRemove() {
            return EventService.removeTickets(ctrl.event.shortName, ctrl.reservationId, ctrl.ticketIds, ctrl.toRefund).then(function() {
                ctrl.onSuccess();
            });
        }
    }
})();