(function () {

'use strict';

angular.module('adminApplication').service('ResourceService', function($http, $q) {
    return {
        listTemplates: function() {
            return $http.get('/admin/api/overridable-template');
        },
        getTemplateBody: function(name, locale) {
            return $http.get('/admin/api/overridable-template/'+name+'/'+locale);
        },
        getMetadataForSystemResource: function(name) {
            return $http.get('/admin/api/resource/'+name+'/metadata');
        },
        getMetadataForOrganizationResource: function(orgId, name) {
            return $http.get('/admin/api/resource-organization/'+orgId+'/'+name+'/metadata');
        },
        getOrganizationResource: function(orgId, name) {
            return $http.get('/admin/api/resource-organization/'+orgId+'/'+name);
        },
        getSystemResource: function(name) {
            return $http.get('/admin/api/resource/'+name);
        },
        uploadOrganizationFile: function(orgId, file) {
            return $http.post('/admin/api/resource-organization/'+orgId, file);
        },
        uploadSystemFile: function(file) {
            return $http.post('/admin/api/resource', file);
        },
        deleteOrganizationFile: function(orgId, name) {
            return $http.delete('/admin/api/resource-organization/'+orgId+'/'+name);
        },
        deleteSystemFile: function(name) {
            return $http.delete('/admin/api/resource/'+name);
        },
        /**/
        getMetadataForEventResource: function(orgId, eventId, name) {
            return $http.get('/admin/api/resource-event/'+orgId+'/'+eventId+'/'+name+'/metadata');
        },
        getEventResource: function(orgId, eventId, name) {
            return $http.get('/admin/api/resource-event/'+orgId+'/'+eventId+'/'+name);
        },
        uploadFile: function(orgId, eventId, file) {
            return $http.post('/admin/api/resource-event/'+orgId+'/'+eventId, file);
        },
        deleteFile: function(orgId, eventId, name) {
            return $http.delete('/admin/api/resource-event/'+orgId+'/'+eventId+'/'+name);
        },
        preview: function(orgId, eventId, name, locale, file) {

            return $http.post('/admin/api/overridable-template/'+name+'/'+locale+'/preview?organizationId='+orgId+(eventId !== undefined ? '&eventId='+eventId : ''), file, {responseType: 'blob'}).then(function(res) {

                var contentType = res.headers('Content-Type');
                if(contentType.startsWith('text/plain')) {
                    var reader = new FileReader();

                    var promise = $q(function(resolve, reject) {
                        reader.onloadend = function() {
                            resolve({download: false, text: reader.result})
                        }
                    });
                    reader.readAsText(res.data);
                    return promise;
                }

                var fileName = res.headers('content-disposition').match(/filename=(.*)$/)[1];
                var finalFile = new File([res.data], fileName, {type: contentType});
                var fileUrl = URL.createObjectURL(finalFile);

                var a = document.createElement('a');
                //for FF, we must append the element: https://stackoverflow.com/a/32226068
                document.body.appendChild(a);
                a.href = fileUrl;
                a.download = fileName;
                a.click();
                setTimeout(function(){
                    document.body.removeChild(a);
                    window.URL.revokeObjectURL(url);
                }, 100);

                return {download: true}
            });
        }
    };
});


})();