(function() {
    'use strict';

    angular.module('adminApplication')
        .service('AdminReservationService', ['$http', 'HttpErrorHandler', function($http, HttpErrorHandler) {
            return {
                createReservation: function(eventName, reservation) {
                    return $http.post('/admin/api/reservation/event/'+eventName+'/new', reservation).error(HttpErrorHandler.handle);
                },
                updateReservation: function(eventName, reservationId, update) {
                    return $http.post('/admin/api/reservation/event/'+eventName+'/'+reservationId, update).error(HttpErrorHandler.handle);
                },
                confirm: function(eventName, reservationId) {
                    return $http['put']('/admin/api/reservation/event/'+eventName+'/'+reservationId+'/confirm').error(HttpErrorHandler.handle);
                },
                notify: function(eventName, reservationId, notification) {
                    return $http['put']('/admin/api/reservation/event/'+eventName+'/'+reservationId+'/notify', notification).error(HttpErrorHandler.handle);
                },
                load: function(eventName, reservationId) {
                    return $http.get('/admin/api/reservation/event/'+eventName+'/'+reservationId+'/').error(HttpErrorHandler.handle);
                },
                paymentInfo: function(eventName, reservationId) {
                    return $http.get('/admin/api/reservation/event/'+eventName+'/'+reservationId+'/payment-info').error(HttpErrorHandler.handle);
                },
                refund: function(eventName, reservationId, amount) {
                    return $http.post('/admin/api/reservation/event/'+eventName+'/'+reservationId+'/refund', {amount: amount}).error(HttpErrorHandler.handle);
                },
                getTicket: function(eventName, reservationId, ticketId) {
                    return $http.get('/admin/api/reservation/event/'+eventName+'/'+reservationId+'/ticket/'+ticketId).error(HttpErrorHandler.handle);
                },
                getAudit: function(eventName, reservationId) {
                    return $http.get('/admin/api/reservation/event/'+eventName+'/'+reservationId+'/audit').error(HttpErrorHandler.handle);
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