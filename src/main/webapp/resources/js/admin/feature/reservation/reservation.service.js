(function() {
    'use strict';

    angular.module('adminApplication').service('AdminReservationService', ['$http', 'HttpErrorHandler', function($http, HttpErrorHandler) {
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
            }
        }
    }]);


})();