(function() {
    'use strict';

    angular.module('adminApplication').service('ExtensionService', ExtensionService);


    function ExtensionService($http, HttpErrorHandler, EventService, OrganizationService, $q) {
        return {
            loadEventConfig: function(orgId, eventShortName) {
                return $http.get('/admin/api/extensions/setting/organization/'+orgId+'/event/'+encodeURIComponent(eventShortName)).error(HttpErrorHandler.handle);
            },
            loadOrganizationConfig: function(orgId) {
                return $http.get('/admin/api/extensions/setting/organization/'+orgId).error(HttpErrorHandler.handle);
            },
            loadSystem: function() {
                return $http.get('/admin/api/extensions/setting/system').error(HttpErrorHandler.handle);
            },
            loadEventConfigWithOrgIdAndEventId: function(orgId, eventId) {
                var service = this;
                return $q.all([EventService.getEventById(eventId)]).then(function(res) {
                    return service.loadEventConfig(orgId, res[0].data.shortName);
                })
            },
            loadOrganizationConfigWithOrgId: function (orgId) {
                return this.loadOrganizationConfig(orgId);
            },

            saveBulkSystemSetting: function(toSave) {
                return $http.post('/admin/api/extensions/setting/system/bulk-update', transformSettingPayload(toSave));
            },

            saveBulkOrganizationSetting: function(orgId, toSave) {
                return $http.post('/admin/api/extensions/setting/organization/' + orgId + '/bulk-update', transformSettingPayload(toSave));
            },

            saveBulkEventSetting: function(orgId, eventId, toSave) {
                return $q.all([EventService.getEventById(eventId)]).then(function(res) {
                    var eventShortName = res[0].data.shortName;
                    return $http.post('/admin/api/extensions/setting/organization/'+orgId +'/event/'+encodeURIComponent(eventShortName) + '/bulk-update', transformSettingPayload(toSave));
                });
            },
            deleteSystemSettingValue: function(toDelete) {
                return $http.delete('/admin/api/extensions/setting/system/'+toDelete.id);
            },
            deleteOrganizationSettingValue: function(orgId, toDelete) {
                return $http.delete('/admin/api/extensions/setting/organization/' + orgId + '/'+toDelete.id);
            },
            deleteEventSettingValue: function(orgId, eventId, toDelete) {
                return $q.all([EventService.getEventById(eventId)]).then(function(res) {
                    var eventShortName = res[0].data.shortName;
                    return $http.delete('/admin/api/extensions/setting/organization/'+orgId+'/event/'+encodeURIComponent(eventShortName) + '/'+toDelete.id);
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