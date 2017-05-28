(function() {
    'use strict';

    angular.module('adminApplication').component('reservationsList', {
        bindings: {
            event: '<'
        },
        controller: ['EventService', '$filter', ReservationsListCtrl],
        templateUrl: '../resources/js/admin/feature/reservations-list/reservations-list.html'
    });
    
    
    
    function ReservationsListCtrl(EventService, $filter) {
        var ctrl = this;

        ctrl.currentPagePending = 1;
        ctrl.currentPage = 1;
        ctrl.itemsPerPage = 50;
        ctrl.statusFilter = '';
        ctrl.toSearch = '';
        ctrl.formatFullName = formatFullName;
        ctrl.updateFilteredData = updateFilteredData;
        ctrl.truncateReservationId = truncateReservationId;

        var filter = $filter('filter');

        this.$onInit = function() {
            EventService.findAllReservations(ctrl.event.shortName).then(function(res) {
                var statuses = {};
                ctrl.reservations = res.data;
                angular.forEach(res.data, function(r) {
                    statuses[r.status] = true;
                });
                ctrl.allStatus = Object.keys(statuses).sort().map(function(v) {return {value: v, label: v}});
                ctrl.allStatus.unshift({value: '', label: 'Show all'});
                updateFilteredData();
            })
        };

        function formatFullName(r) {
            if(r.firstName && r.lastName) {
                return r.firstName + ' ' + r.lastName;
            } else {
                return r.fullName;
            }
        }

        function truncateReservationId(id) {
            return id.substring(0,8).toUpperCase();
        }

        function updateFilteredData() {
            ctrl.filteredReservations = filter(filter(_.filter(ctrl.reservations, function(r) {return r.status !== 'PENDING'}), ctrl.toSearch), {status: ctrl.statusFilter});
            ctrl.filteredPendingReservations = filter(filter(_.filter(ctrl.reservations, function(r) {return r.status === 'PENDING'}), ctrl.toSearch), {status: ctrl.statusFilter});
        }
    }
})();