(function() {
    'use strict';

    angular.module('adminApplication').component('paymentsList', {
        bindings: {
            purchaseContext: '<',
            purchaseContextType: '<'
        },
        controller: ['PurchaseContextService', '$filter', '$location', '$stateParams', 'AdminReservationService', PaymentsListCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/payments-list/payments-list.html'
    });
    
    
    
    function PaymentsListCtrl(PurchaseContextService, $filter, $location, $stateParams, AdminReservationService) {
        var ctrl = this;

        var currentSearch = $location.search();

        ctrl.currentPage = currentSearch.page || 1;
        ctrl.toSearch = currentSearch.search || $stateParams.search || '';

        if(!ctrl.targetContextType) {
            ctrl.targetContextType = ctrl.purchaseContextType;
        }

        ctrl.itemsPerPage = 50;
        ctrl.formatFullName = formatFullName;
        ctrl.updateFilteredData = loadData;
        ctrl.editPaymentDetails = editPaymentDetails;

        this.$onInit = function() {
            loadData();
        };

        function loadData() {

            $location.search({
                page: ctrl.currentPage,
                search: ctrl.toSearch
            });

            PurchaseContextService.findAllPayments(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.currentPage - 1, ctrl.toSearch)
                .then(function (res) {
                    ctrl.reservations = res.data.left;
                    ctrl.foundReservations = res.data.right;
                    setTimeout(function() {
                        document.getElementById('filter-reservations').focus();
                    });
                });

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

        function editPaymentDetails(reservationId) {
            PurchaseContextService.editPaymentDetails(reservationId, ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier)
                .then(function(){
                    loadData();
                }, function(err) {
                    console.error('error', err);
                });
        }
    }
})();