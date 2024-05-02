(function() {
    'use strict';

    angular.module('adminApplication').component('extension', {
        controller: ['$http', '$q', 'OrganizationService', 'EventService', ExtensionCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/extension/extension.html'
    });

    function ExtensionCtrl($http, $q, OrganizationService, EventService) {
        var ctrl = this;

        this.$onInit = function() {
            load();
        };

        this.deleteExtension = deleteExtension;


        function load() {
            $q.all([$http.get('/admin/api/extensions'), OrganizationService.getAllOrganizations(), EventService.getAllEvents()]).then(function(results) {
                var organizations = results[1].data;
                var events = results[2].data;
                ctrl.extensions = results[0].data.map(function(ext) {
                    var splitPath = ext.path.split('-').filter(function(x) { return x.length > 0 });
                    var translatedPath = splitPath;
                    if(splitPath.length >= 1) {
                        var org = organizations.filter(function(o) { return o.id === parseInt(splitPath[0])});
                        if (org.length > 0) {
                            translatedPath.splice(0, 1, org[0].name);
                        }
                    }

                    if(splitPath.length >=2) {
                        var service = events.filter(function(e) {return e.id === parseInt(splitPath[1])});
                        if (service.length>0) {
                            translatedPath.splice(1,1, service[0].displayName);
                        }
                    }


                    return angular.extend({}, ext, {translatedPath: translatedPath})
                });
            });
        }

        function deleteExtension(extension) {
            if(window.confirm('Delete ' + extension.name+'?')) {
                $http.delete('/admin/api/extensions/'+ encodeURIComponent(extension.path) + '/' + encodeURIComponent(extension.name)).then(function () {
                    load();
                });
            }
        }
    }

})();