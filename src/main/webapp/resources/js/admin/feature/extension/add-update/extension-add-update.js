(function() {
    'use strict';

    angular.module('adminApplication').component('extensionAddUpdate', {
        controller: ['$http', '$q', '$state', ExtensionAddUpdateCtrl],
        templateUrl: '../resources/js/admin/feature/extension/add-update/extension-add-update.html',
        bindings: {
            dismiss:'&',
            close:'&',
            toUpdate:'<'
        }
    });

    function ExtensionAddUpdateCtrl($http, $q, $state) {
        var ctrl = this;
        ctrl.extension = null;
        ctrl.extensionLoader = null;

        ctrl.$onInit = function() {
            ctrl.edit = ctrl.toUpdate;
            if(ctrl.toUpdate) {
                ctrl.extensionLoader = $http.get('/admin/api/extensions/' + ctrl.toUpdate.name, {params: {path: ctrl.toUpdate.path}});
            } else {
                ctrl.extensionLoader = $http.get('/admin/api/extensions/sample');
            }
            ctrl.extensionLoader.then(function(res) {
                ctrl.extension = res.data;
            });
        };


        ctrl.save = function(extension) {
            $http.post('/admin/api/extensions', {
                path: extension.path,
                name: extension.name,
                enabled: extension.enabled,
                script: extension.script
            }).then(function() {
                $state.go('extension.list');
            });
        };

        ctrl.initLoadListener = function() {
            return function(editor) {
                var session = editor.getSession();

                // Options
                session.setUndoManager(new ace.UndoManager());

                session.on("change", function(event, editor) {
                    var newVal = editor.getValue();
                    var currVal = ctrl.extension.script;
                    if(newVal !== currVal) {
                        ctrl.extension.script = newVal;
                    }
                });

                ctrl.extensionLoader.then(function() {
                    editor.setValue(ctrl.extension.script, 0);
                    editor.clearSelection();
                });

            }
        };
    }
})();