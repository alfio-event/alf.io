(function() {
    'use strict';

    angular.module('adminApplication').component('reservationsList', {
        bindings: {
            event: '<'
        },
        controller: ['EventService', '$filter', '$location', '$stateParams', ReservationsListCtrl],
        templateUrl: '../resources/js/admin/feature/reservations-list/reservations-list.html'
    });
    
    
    
    function ReservationsListCtrl(EventService, $filter, $location, $stateParams) {
        var ctrl = this;

        var currentSearch = $location.search();

        ctrl.currentPagePending = currentSearch.pendingPage || 1;
        ctrl.currentPage = currentSearch.page || 1;
        ctrl.currentPagePendingPayment = currentSearch.pendingPaymentPage || 1;
        ctrl.currentPageCancelled = currentSearch.cancelledPage || 1;
        ctrl.toSearch = currentSearch.search || $stateParams.search || '';
        ctrl.selectedTab = currentSearch.t || 1;

        ctrl.itemsPerPage = 50;
        ctrl.formatFullName = formatFullName;
        ctrl.updateFilteredData = loadData;
        ctrl.onTabSelected = onTabSelected;

        this.$onInit = function() {
            loadData();
        };

        function loadData(loadPartially) {

            loadPartially = loadPartially || {pending: true, completed: true, paymentPending: true, cancelled: true, stuck: true, credited: true};

            $location.search({
                pendingPage: ctrl.currentPagePending,
                pendingPaymentPage: ctrl.currentPagePendingPayment,
                cancelledPage: ctrl.currentPageCancelled,
                page: ctrl.currentPage,
                search: ctrl.toSearch,
                t: ctrl.selectedTab
            });

            if(loadPartially.completed) {
                EventService.findAllReservations(ctrl.event.shortName, ctrl.currentPage - 1, ctrl.toSearch, ['COMPLETE']).then(function (res) {
                    ctrl.reservations = res.data.left;
                    ctrl.foundReservations = res.data.right;
                });
            }

            if(loadPartially.paymentPending) {
                EventService.findAllReservations(ctrl.event.shortName, ctrl.currentPagePendingPayment - 1, ctrl.toSearch, ['IN_PAYMENT', 'EXTERNAL_PROCESSING_PAYMENT', 'WAITING_EXTERNAL_CONFIRMATION', 'OFFLINE_PAYMENT']).then(function (res) {
                    ctrl.paymentPendingReservations = res.data.left;
                    ctrl.paymentPendingFoundReservations = res.data.right;
                });
            }

            if(loadPartially.pending) {
                EventService.findAllReservations(ctrl.event.shortName, ctrl.currentPagePending -1, ctrl.toSearch, ['PENDING']).then(function(res) {
                    ctrl.pendingReservations = res.data.left;
                    ctrl.foundPendingReservations = res.data.right;
                });
            }

            if(loadPartially.cancelled) {
                EventService.findAllReservations(ctrl.event.shortName, ctrl.currentPageCancelled -1, ctrl.toSearch, ['CANCELLED']).then(function(res) {
                    ctrl.cancelledReservations = res.data.left;
                    ctrl.foundCancelledReservations = res.data.right;
                });
            }

            if(loadPartially.stuck) {
                EventService.findAllReservations(ctrl.event.shortName, 0, ctrl.toSearch, ['STUCK']).then(function(res) {
                    ctrl.stuckReservations = res.data.left;
                    ctrl.foundStuckReservations = res.data.right;
                });
            }

            if(loadPartially.credited) {
                EventService.findAllReservations(ctrl.event.shortName, 0, ctrl.toSearch, ['CREDIT_NOTE_ISSUED']).then(function(res) {
                    ctrl.creditedReservations = res.data.left;
                    ctrl.foundCreditedReservations = res.data.right;
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

        function onTabSelected(n) {
            ctrl.selectedTab = n;
        }
    }
})();