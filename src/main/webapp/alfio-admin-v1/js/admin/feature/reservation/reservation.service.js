(function() {
    'use strict';

    angular.module('adminApplication')
        .service('AdminReservationService', ['$http', 'HttpErrorHandler', '$uibModal', function($http, HttpErrorHandler, $uibModal) {
            return {
                createReservation: function(eventName, reservation) {
                    return $http.post('/admin/api/reservation/event/'+eventName+'/new', reservation).error(HttpErrorHandler.handle);
                },
                updateReservation: function(purchaseContextType, purchaseContextId, reservationId, update) {
                    return $http.post('/admin/api/reservation/'+purchaseContextType+'/'+purchaseContextId+'/'+reservationId, update).error(HttpErrorHandler.handle);
                },
                confirm: function(purchaseContextType, publicIdentifier, reservationId) {
                    return $http['put']('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/confirm').error(HttpErrorHandler.handle);
                },
                notify: function(purchaseContextType, publicIdentifier, reservationId, notification) {
                    return $http['put']('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/notify', notification).error(HttpErrorHandler.handle);
                },
                notifyAttendees: function(purchaseContextType, publicIdentifier, reservationId, ids) {
                    return $http['put']('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/notify-attendees', ids).error(HttpErrorHandler.handle);
                },
                load: function(purchaseContextType, publicIdentifier, reservationId) {
                    return $http.get('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId).error(HttpErrorHandler.handle);
                },
                paymentInfo: function(purchaseContextType, publicIdentifier, reservationId) {
                    return $http.get('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/payment-info').error(HttpErrorHandler.handle);
                },
                refund: function(purchaseContextType, publicIdentifier, reservationId, amount) {
                    return $http.post('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/refund', {amount: amount}).error(HttpErrorHandler.handle);
                },
                getAudit: function(purchaseContextType, publicIdentifier, reservationId) {
                    return $http.get('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/audit').error(HttpErrorHandler.handle);
                },
                loadAllBillingDocuments: function(purchaseContextType, publicIdentifier, reservationId) {
                    return $http.get('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/billing-documents').error(HttpErrorHandler.handle);
                },
                invalidateDocument: function(purchaseContextType, publicIdentifier, reservationId, documentId) {
                    return $http['delete']('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/billing-document/'+documentId).error(HttpErrorHandler.handle);
                },
                restoreDocument: function(purchaseContextType, publicIdentifier, reservationId, documentId) {
                    return $http['put']('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/billing-document/'+documentId+'/restore').error(HttpErrorHandler.handle);
                },
                regenerateBillingDocument: function(purchaseContextType, publicIdentifier, reservationId) {
                    return $http.put('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/regenerate-billing-document', {}).error(HttpErrorHandler.handle);
                },
                emailList: function(purchaseContextType, publicIdentifier, reservationId) {
                    return $http.get('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/email-list', {}).error(HttpErrorHandler.handle);
                },
                getTicket: function(publicIdentifier, reservationId, ticketId) {
                    return $http.get('/admin/api/reservation/event/'+publicIdentifier+'/'+reservationId+'/ticket/'+ticketId).error(HttpErrorHandler.handle);
                },
                getTicketIdsWithAdditionalData: function(publicIdentifier, reservationId) {
                    return $http.get('/admin/api/reservation/event/'+publicIdentifier+'/'+reservationId+'/tickets-with-additional-data').error(HttpErrorHandler.handle);
                },
                openFullDataView: function(purchaseContext, reservationId, ticketUUID) {
                    return $uibModal.open({
                        size:'md',
                        template:'<ticket-full-data event="event" reservation-id="reservationId" ticket-id="ticketUUID" on-success="success(result)" on-cancel="close()"></ticket-full-data>',
                        backdrop: 'static',
                        controller: function($scope) {
                            $scope.event = purchaseContext;
                            $scope.ticketUUID = ticketUUID;
                            $scope.reservationId = reservationId;
                            $scope.close = function() {
                                $scope.$close(false);
                            };

                            $scope.success = function () {
                                $scope.$close(false);
                            }
                        }
                    }).result;
                },
                getFullTicketData: function(purchaseContext, reservationId, ticketUUID) {
                    return $http.get('/admin/api/reservation/event/' + purchaseContext.publicIdentifier + '/' + reservationId + '/ticket/'+ticketUUID+'/full-data')
                        .error(HttpErrorHandler.handle);
                }
            }
        }]).service('AdminImportService', ['$http', 'HttpErrorHandler', function($http, HttpErrorHandler) {
            return {
                importAttendees: function(eventName, descriptor, singleReservations) {
                    var url = '/admin/api/event/'+eventName+'/attendees/import';
                    if(singleReservations) {
                        url += '?oneReservationPerAttendee=true'
                    }
                    return $http.post(url, descriptor).error(HttpErrorHandler.handle);
                },
                retrieveStats: function(eventName, requestId) {
                    return $http.get('/admin/api/event/'+eventName+'/attendees/import/'+requestId+'/status').error(HttpErrorHandler.handle);
                }
            }
        }])
        ;


})();