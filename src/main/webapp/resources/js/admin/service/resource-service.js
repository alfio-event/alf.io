(function () {

'use strict';

angular.module('adminApplication').service('ResourceService', function($http) {
    return {

        listTemplates: function() {
            return $http.get('api/overridable-template/');
        },
        getTemplateBody: function(name, locale) {
            return $http.get('api/overridable-template/'+name+'/'+locale);
        },
        getMetadataForEventResource: function(orgId, eventId, name) {
            return $http.get('api/resource-event/'+orgId+'/'+eventId+'/'+name+'/metadata');
        },
        getEventResource: function(orgId, eventId, name) {
            return $http.get('api/resource-event/'+orgId+'/'+eventId+'/'+name);
        },
        uploadFile: function(orgId, eventId, file) {
            return $http.post('api/resource-event/'+orgId+'/'+eventId+'/', file);
        }
    };
});


})();