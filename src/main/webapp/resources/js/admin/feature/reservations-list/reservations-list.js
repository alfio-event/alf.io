(function() {
    'use strict';

    angular.module('adminApplication').component('reservationsList', {
        bindings: {
            event: '<'
        },
        controller: ['EventService', ReservationsListCtrl],
        templateUrl: '../resources/js/admin/feature/reservations-list/reservations-list.html'
    });
    
    
    
    function ReservationsListCtrl(EventService) {
        var ctrl = this;

        this.$onInit = function() {
            EventService.findAllReservations(ctrl.event.shortName).then(function(res) {
                ctrl.reservations = res.data;
            })
        }
    }
})();