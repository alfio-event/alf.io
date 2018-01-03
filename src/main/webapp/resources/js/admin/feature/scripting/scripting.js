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
        this.deleteScript = deleteScript;


        function load() {
            $http.get('/admin/api/scripting').then(function(res) {
                ctrl.scripts = res.data;
            })
        }

        function deleteScript(script) {
            if(window.confirm('Delete script ' + script.name+'?')) {
                $http.delete('/admin/api/scripting/' + script.path + '/' + script.name).then(function () {
                    load();
                });
            }
        }
        
        
        function addNewOrUpdate(script) {
            var modal = $uibModal.open({
                template: '<scripting-add-update to-update="ctrl.toUpdate" close="ctrl.close($script)" dismiss="ctrl.dismiss()"></scripting-add-update>',
                controller: function() {
                    var ctrl = this;

                    ctrl.toUpdate = script;

                    ctrl.dismiss = function() {
                        modal.dismiss();
                    }

                    ctrl.close = function(script) {
                        modal.close();
                        load();
                    }
                },
                controllerAs: 'ctrl'
            });
        }
    }
})();