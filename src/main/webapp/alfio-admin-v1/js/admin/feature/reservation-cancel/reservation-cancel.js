(function() {
    'use strict';

    angular.module('adminApplication')
        .component('reservationCancel', {
            controller: ['AdminReservationService', 'ReservationCancelService', 'NotificationHandler', ReservationCancelCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/reservation-cancel/reservation-cancel.html',
            bindings: {
                purchaseContext: '<',
                purchaseContextType: '<',
                reservationId: '<',
                canGenerateCreditNote: '<',
            credit: '<',
            onSuccess: '&',
            onCancel:'&'
        }
    }).service('ReservationCancelService', ['$q', '$uibModal', '$http', function($q, $uibModal, $http) {
            return {
                cancelReservation: function(purchaseContextType, publicIdentifier, reservationId, refund, notify, credit, issueCreditNote) {
                    var operation = credit ? 'credit' : 'cancel';
                    return $http.post('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/'+operation, null, {
                        params: {
                            refund: refund,
                            notify: notify,
                            issueCreditNote: issueCreditNote
                        }
                    });
                },

                cancelReservationModal: function(purchaseContextType, purchaseContext, reservation, credit) {
                    var modal = $uibModal.open({
                        size:'lg',
                        template:'<reservation-cancel purchase-context-type="purchaseContextType" purchase-context="purchaseContext" reservation-id="reservationId" on-success="success()" on-cancel="close()" credit="credit" can-generate-credit-note="invoiceRequested"></reservation-cancel>',
                        backdrop: 'static',
                        controller: function($scope) {
                            $scope.purchaseContext = purchaseContext;
                            $scope.purchaseContextType = purchaseContextType;
                            $scope.reservationId = reservation.id;
                            $scope.invoiceRequested = reservation.customerData.invoiceRequested && reservation.status !== 'OFFLINE_PAYMENT';
                            $scope.credit = credit;
                            $scope.close = function() {
                                $scope.$dismiss(false);
                            };

                            $scope.success = function () {
                                $scope.$close(false);
                            }
                        }
                    });
                    return modal.result;
                }
            }
    }]);


    function ReservationCancelCtrl(AdminReservationService, ReservationCancelService, NotificationHandler) {
        var ctrl = this;

        ctrl.confirmRemove = confirmRemove;
        ctrl.operation = ctrl.credit ? "Credit" : "Cancel";

        ctrl.$onInit = function() {
            ctrl.refund = true;
            ctrl.notify = false;
            ctrl.issueCreditNote = ctrl.canGenerateCreditNote;
            AdminReservationService.paymentInfo(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservationId).then(function(res) {
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
            return ReservationCancelService.cancelReservation(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservationId, ctrl.refund, ctrl.notify, ctrl.credit, ctrl.issueCreditNote).then(function(response) {
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