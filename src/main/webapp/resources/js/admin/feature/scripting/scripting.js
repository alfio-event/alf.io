(function() {
    'use strict';

    angular.module('adminApplication').component('scripting', {
        controller: ['$http', '$uibModal', ScriptingCtrl],
        templateUrl: '../resources/js/admin/feature/scripting/scripting.html'
    });

    function ScriptingCtrl($http, $uibModal) {
        var ctrl = this;

        this.$onInit = function() {
            load();
        };

        this.addNewOrUpdate = addNewOrUpdate;


        function load() {
            $http.get('/admin/api/scripting').then(function(res) {
                ctrl.scripts = res.data;
            })
        }
        
        
        function addNewOrUpdate(script) {
            $uibModal.open({
                template: '<scripting-add-update></scripting-add-update>'
            })
        }
    }
})();