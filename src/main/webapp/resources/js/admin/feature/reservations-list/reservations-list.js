(function() {
    'use strict';

    angular.module('adminApplication').component('reservationsList', {
        bindings: {
            event: '<'
        },
        controller: ['EventService', '$filter', '$location', ReservationsListCtrl],
        templateUrl: '../resources/js/admin/feature/reservations-list/reservations-list.html'
    });
    
    
    
    function ReservationsListCtrl(EventService, $filter, $location) {
        var ctrl = this;

        var currentSearch = $location.search();

        ctrl.currentPagePending = currentSearch.pendingPage || 1;
        ctrl.currentPage = currentSearch.page || 1;
        ctrl.toSearch = currentSearch.search || '';

        ctrl.itemsPerPage = 50;
        ctrl.formatFullName = formatFullName;
        ctrl.updateFilteredData = loadData;
        ctrl.truncateReservationId = truncateReservationId;

        this.$onInit = function() {
            EventService.getAllReservationStatus(ctrl.event.shortName).then(function(res) {
                ctrl.otherStatuses = $filter('filter')(res.data, '!PENDING');
                loadData()
            });
        };

        function loadData(loadPartially) {

            loadPartially = loadPartially || {pending: true, completed: true};

            $location.search({pendingPage: ctrl.currentPagePending, page: ctrl.currentPage, search: ctrl.toSearch});

            if(loadPartially.completed) {
                EventService.findAllReservations(ctrl.event.shortName, ctrl.currentPage - 1, ctrl.toSearch, ctrl.otherStatuses).then(function (res) {
                    ctrl.reservations = res.data.left;
                    ctrl.foundReservations = res.data.right;
                });
            }

            if(loadPartially.pending) {
                EventService.findAllReservations(ctrl.event.shortName, ctrl.currentPagePending -1, ctrl.toSearch, ['PENDING']).then(function(res) {
                    ctrl.pendingReservations = res.data.left;
                    ctrl.foundPendingReservations = res.data.right;
                });
            }
        }

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
    }
})();