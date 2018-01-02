(function() {
    'use strict';

    angular.module('adminApplication').component('scriptingAddUpdate', {
        controller: ['$http', ScriptingAddUpdateCtrl],
        templateUrl: '../resources/js/admin/feature/scripting/add-update/scripting-add-update.html',
        bindings: {
            dismiss:'&',
            close:'&',
            toUpdate:'<'
        }
    });

    function ScriptingAddUpdateCtrl($http) {
        var ctrl = this;

        ctrl.$onInit = function() {
            if(ctrl.toUpdate) {
                //deep cloning
                ctrl.script = JSON.parse(JSON.stringify(ctrl.toUpdate));
            }
        }


        ctrl.save = function(script) {
            $http.post('/admin/api/scripting', {
                path: script.path,
                name: script.name,
                enabled: script.enabled,
                script: script.script
                }).then(function() {
                ctrl.close({$script: script});
            });
        }
    }
})();