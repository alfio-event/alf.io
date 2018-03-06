(function() {
    'use strict';

    angular.module('adminApplication').service('ExtensionService', ExtensionService);


    function ExtensionService($http, HttpErrorHandler, EventService, OrganizationService, $q) {
        return {
            loadEventConfig: function(organizationName, eventShortName) {
                return $http.get('/admin/api/extensions/setting/organization/'+organizationName+'/event/'+eventShortName).error(HttpErrorHandler.handle);
            },
            loadOrganizationConfig: function(organizationName) {
                return $http.get('/admin/api/extensions/setting/organization/'+organizationName).error(HttpErrorHandler.handle);
            },
            loadSystem: function() {
                return $http.get('/admin/api/extensions/setting/system').error(HttpErrorHandler.handle);
            },
            loadEventConfigWithOrgIdAndEventId: function(orgId, eventId) {
                var service = this;
                return $q.all([OrganizationService.getOrganization(orgId), EventService.getEventById(eventId)]).then(function(res) {
                    return service.loadEventConfig(res[0].data.name, res[1].data.shortName);
                })
            },
            loadOrganizationConfigWithOrgId: function (orgId) {
                var service = this;
                return OrganizationService.getOrganization(orgId).then(function(org) {
                    return service.loadOrganizationConfig(org.data.name);
                });
            }
        };
    }

    ExtensionService.$inject = ['$http', 'HttpErrorHandler', 'EventService', 'OrganizationService', '$q'];


})();