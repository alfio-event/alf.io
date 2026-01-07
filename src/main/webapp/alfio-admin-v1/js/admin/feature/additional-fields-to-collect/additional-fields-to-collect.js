(function() {
    'use strict';

    angular.module('adminApplication')
        .service('AdditionalFieldsService', ['$http', 'HttpErrorHandler', AdditionalFieldsService]);

    // temporary until we migrate the "create event" page
    function AdditionalFieldsService($http, HttpErrorHandler) {
        return {
            getAdditionalFields: function(purchaseContextType, publicIdentifier) {
                return $http.get('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field').error(HttpErrorHandler.handle);
            },
            addField: function(purchaseContextType, publicIdentifier, field) {
                return $http.post('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field/new', field).error(HttpErrorHandler.handle);
            },
        }
    }
})();