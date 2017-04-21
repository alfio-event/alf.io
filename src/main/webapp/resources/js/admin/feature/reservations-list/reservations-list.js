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

        ctrl.statusFilter = '';
        ctrl.formatFullName = formatFullName;

        this.$onInit = function() {

            EventService.findAllReservations(ctrl.event.shortName).then(function(res) {
                var statuses = {};
                ctrl.reservations = res.data;
                angular.forEach(res.data, function(r) {
                    statuses[r.status] = true;
                });
                ctrl.allStatus = Object.keys(statuses).sort().map(function(v) {return {value: v, label: v}});
                ctrl.allStatus.unshift({value: '', label: 'Show all'});
            })
        }

        function formatFullName(r) {
            if(r.firstName && r.lastName) {
                return r.firstName + ' ' + r.lastName;
            } else {
                return r.fullName;
            }
        }
    }
})();