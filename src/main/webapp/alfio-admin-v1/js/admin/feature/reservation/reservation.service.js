(function() {
    'use strict';

    angular.module('adminApplication')
        .service('AdminReservationService', ['$http', 'HttpErrorHandler', function($http, HttpErrorHandler) {
            return {
                createReservation: function(eventName, reservation) {
                    return $http.post('/admin/api/reservation/event/'+eventName+'/new', reservation).error(HttpErrorHandler.handle);
                },
                confirm: function(purchaseContextType, publicIdentifier, reservationId) {
                    return $http['put']('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/confirm').error(HttpErrorHandler.handle);
                },
                paymentInfo: function(purchaseContextType, publicIdentifier, reservationId) {
                    return $http.get('/admin/api/reservation/'+purchaseContextType+'/'+publicIdentifier+'/'+reservationId+'/payment-info').error(HttpErrorHandler.handle);
                },
                getTicket: function(publicIdentifier, reservationId, ticketId) {
                    return $http.get('/admin/api/reservation/event/'+publicIdentifier+'/'+reservationId+'/ticket/'+ticketId).error(HttpErrorHandler.handle);
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