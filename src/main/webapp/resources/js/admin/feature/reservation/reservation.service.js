(function() {
    'use strict';

    angular.module('adminApplication').service('AdminReservationService', ['$http', 'HttpErrorHandler', function($http, HttpErrorHandler) {
        return {
            createReservation: function(eventName, reservation) {
                return $http.post('/admin/api/reservation/event/'+eventName+'/new', reservation).error(HttpErrorHandler.handle);
            }
        }
    }]);


})();