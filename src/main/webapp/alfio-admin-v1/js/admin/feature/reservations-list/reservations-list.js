(function() {
    'use strict';

    angular.module('adminApplication').component('reservationsList', {
        bindings: {
            purchaseContext: '<',
            purchaseContextType: '<'
        },
        controller: ['PurchaseContextService', '$filter', '$location', '$stateParams', ReservationsListCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/reservations-list/reservations-list.html'
    }).component('reservationsListTable', {
        bindings: {
            purchaseContext: '<',
            purchaseContextType: '<',
            targetContextType: '<',
            reservations: '<'
        },
        controller: function() {

            var ctrl = this;
            ctrl.$onInit = function() {
                ctrl.formatFullName = function(r) {
                    if(r.firstName && r.lastName) {
                        return r.firstName + ' ' + r.lastName;
                    } else {
                        return r.fullName;
                    }
                };
                if(!ctrl.targetContextType) {
                    ctrl.targetContextType = ctrl.purchaseContextType;
                }
            }

        },
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/reservations-list/reservations-list-table.html'
    });
    
    
    
    function ReservationsListCtrl(PurchaseContextService, $filter, $location, $stateParams) {
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
                PurchaseContextService.findAllReservations(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.currentPage - 1, ctrl.toSearch, ['COMPLETE']).then(function (res) {
                    ctrl.reservations = res.data.left;
                    ctrl.foundReservations = res.data.right;
                });
            }

            if(loadPartially.paymentPending) {
                PurchaseContextService.findAllReservations(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.currentPagePendingPayment - 1, ctrl.toSearch, ['IN_PAYMENT', 'EXTERNAL_PROCESSING_PAYMENT', 'WAITING_EXTERNAL_CONFIRMATION', 'OFFLINE_PAYMENT', 'DEFERRED_OFFLINE_PAYMENT']).then(function (res) {
                    ctrl.paymentPendingReservations = res.data.left;
                    ctrl.paymentPendingFoundReservations = res.data.right;
                });
            }

            if(loadPartially.pending) {
                PurchaseContextService.findAllReservations(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.currentPagePending -1, ctrl.toSearch, ['PENDING']).then(function(res) {
                    ctrl.pendingReservations = res.data.left;
                    ctrl.foundPendingReservations = res.data.right;
                });
            }

            if(loadPartially.cancelled) {
                PurchaseContextService.findAllReservations(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.currentPageCancelled -1, ctrl.toSearch, ['CANCELLED']).then(function(res) {
                    ctrl.cancelledReservations = res.data.left;
                    ctrl.foundCancelledReservations = res.data.right;
                });
            }

            if(loadPartially.stuck) {
                PurchaseContextService.findAllReservations(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, 0, ctrl.toSearch, ['STUCK']).then(function(res) {
                    ctrl.stuckReservations = res.data.left;
                    ctrl.foundStuckReservations = res.data.right;
                });
            }

            if(loadPartially.credited) {
                PurchaseContextService.findAllReservations(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, 0, ctrl.toSearch, ['CREDIT_NOTE_ISSUED']).then(function(res) {
                    ctrl.creditedReservations = res.data.left;
                    ctrl.foundCreditedReservations = res.data.right;
                });
            }

            var keys = Object.keys(ctrl.purchaseContext.title);
            ctrl.purchaseContextTitle = ctrl.purchaseContext.title[keys[0]];
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