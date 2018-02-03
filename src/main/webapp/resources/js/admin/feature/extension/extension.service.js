(function() {
    'use strict';

    angular.module('adminApplication').service('ExtensionService', ExtensionService);


    function ExtensionService($http, HttpErrorHandler) {
        return {
            loadEventConfig: function(organizationName, eventShortName) {
                return $http.get('/admin/api/extensions/setting/organization/'+organizationName+'/event/'+eventShortName).error(HttpErrorHandler.handle);
            },
            loadOrganizationConfig: function(organizationName) {
                return $http.get('/admin/api/extensions/setting/organization/'+organizationName).error(HttpErrorHandler.handle);
            },
            loadSystem: function() {
                return $http.get('/admin/api/extensions/setting/system').error(HttpErrorHandler.handle);
            }
        };
    }

    ExtensionService.$inject = ['$http', 'HttpErrorHandler'];


})();