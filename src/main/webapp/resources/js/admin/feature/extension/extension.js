(function() {
    'use strict';

    angular.module('adminApplication').component('extension', {
        controller: ['$http', '$q', 'OrganizationService', ExtensionCtrl],
        templateUrl: '../resources/js/admin/feature/extension/extension.html'
    });

    function ExtensionCtrl($http, $q, OrganizationService) {
        var ctrl = this;

        this.$onInit = function() {
            load();
        };

        this.deleteExtension = deleteExtension;


        function load() {
            $q.all([$http.get('/admin/api/extensions'), OrganizationService.getAllOrganizations()]).then(function(results) {
                var organizations = results[1].data;
                ctrl.extensions = results[0].data.map(function(ext) {
                    var splitPath = ext.path.split('.').filter(function(x) { return x.length > 0 });
                    var translatedPath = splitPath;
                    if(splitPath.length > 1) {
                        var org = organizations.filter(function(o) { return o.id === parseInt(splitPath[0])});
                        if(org.length > 0) {
                            translatedPath.splice(0, 1, org[0].name);
                        }
                    }
                    return angular.extend({}, ext, {translatedPath: translatedPath})
                });
            });
        }

        function deleteExtension(extension) {
            if(window.confirm('Delete ' + extension.name+'?')) {
                $http.delete('/admin/api/extensions/'+ extension.name, {params: {path: extension.path}}).then(function () {
                    load();
                });
            }
        }
    }

})();