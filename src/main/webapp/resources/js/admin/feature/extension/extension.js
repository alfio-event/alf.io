(function() {
    'use strict';

    angular.module('adminApplication').component('extension', {
        controller: ['$http', ExtensionCtrl],
        templateUrl: '../resources/js/admin/feature/extension/extension.html'
    });

    function ExtensionCtrl($http) {
        var ctrl = this;

        this.$onInit = function() {
            load();
        };

        this.deleteExtension = deleteExtension;


        function load() {
            $http.get('/admin/api/extensions').then(function(res) {
                ctrl.scripts = res.data;
            })
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