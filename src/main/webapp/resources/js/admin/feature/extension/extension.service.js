(function() {
    'use strict';

    angular.module('adminApplication').service('ExtensionService', ExtensionService);


    function ExtensionService($http, HttpErrorHandler, EventService, OrganizationService, $q) {
        return {
            loadEventConfig: function(organizationName, eventShortName) {
                return $http.get('/admin/api/extensions/setting/organization/'+encodeURIComponent(organizationName)+'/event/'+encodeURIComponent(eventShortName)).error(HttpErrorHandler.handle);
            },
            loadOrganizationConfig: function(organizationName) {
                return $http.get('/admin/api/extensions/setting/organization/'+encodeURIComponent(organizationName)).error(HttpErrorHandler.handle);
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
            },

            saveBulkSystemSetting: function(toSave) {
                return $http.post('/admin/api/extensions/setting/system/bulk-update', transformSettingPayload(toSave));
            },

            saveBulkOrganizationSetting: function(orgId, toSave) {
                return OrganizationService.getOrganization(orgId).then(function(org) {
                    var organizationName = org.data.name;
                    return $http.post('/admin/api/extensions/setting/organization/' + encodeURIComponent(organizationName) + '/bulk-update', transformSettingPayload(toSave));
                });
            },

            saveBulkEventSetting: function(orgId, eventId, toSave) {
                return $q.all([OrganizationService.getOrganization(orgId), EventService.getEventById(eventId)]).then(function(res) {
                    var organizationName = res[0].data.name;
                    var eventShortName = res[1].data.shortName;
                    return $http.post('/admin/api/extensions/setting/organization/'+encodeURIComponent(organizationName)+'/event/'+encodeURIComponent(eventShortName) + '/bulk-update', transformSettingPayload(toSave));
                });
            }
        };
    }

    function transformSettingPayload(toSave) {
        if(!toSave) {
            return [];
        }

        var res = [];
        angular.forEach(toSave, function(v, idx) {
            res = res.concat(v);
        })
        return res;
    }

    ExtensionService.$inject = ['$http', 'HttpErrorHandler', 'EventService', 'OrganizationService', '$q'];


})();