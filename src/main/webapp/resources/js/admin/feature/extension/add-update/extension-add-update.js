(function() {
    'use strict';

    angular.module('adminApplication').component('extensionAddUpdate', {
        controller: ['$http', '$q', '$state', 'OrganizationService', 'EventService', ExtensionAddUpdateCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/extension/add-update/extension-add-update.html',
        bindings: {
            dismiss:'&',
            close:'&',
            toUpdate:'<'
        }
    }).filter('belongsToOrganization', function() {
        return function(events, orgId) {
            return events.filter(function(ev) {
                return ev.organizationId === orgId;
            })
        }
    });

    function ExtensionAddUpdateCtrl($http, $q, $state, OrganizationService, EventService) {
        var ctrl = this;
        ctrl.extension = null;
        ctrl.dataLoader = null;

        ctrl.$onInit = function() {
            ctrl.edit = ctrl.toUpdate;
            if(ctrl.toUpdate) {
                ctrl.extensionLoader = $http.get('/admin/api/extensions/' + encodeURIComponent(ctrl.toUpdate.path) + '/' + encodeURIComponent(ctrl.toUpdate.name));
            } else {
                ctrl.extensionLoader = $http.get('/admin/api/extensions/sample');
            }
            ctrl.dataLoader = $q.all([ctrl.extensionLoader, OrganizationService.getAllOrganizations(), EventService.getAllEvents()]).then(function(results) {
                ctrl.organizations = results[1].data;
                ctrl.allEvents = results[2].data;
                var extension = results[0].data;
                var splitPath = extension.path.split('-').filter(function(x) { return x.length > 0 });
                var path = {
                    organization: undefined,
                    event: undefined
                };
                if(splitPath.length >= 1) {
                    path.organization = parseInt(splitPath[0]);
                    if (splitPath.length === 2) {
                        path.event = parseInt(splitPath[1]);
                    }
                }

                ctrl.extension = angular.extend({}, extension, {translatedPath: path});

                if(ctrl.toUpdate) {
                    ctrl.previousPath = extension.path;
                    ctrl.previousName = extension.name;
                }

            });
        };

        ctrl.updatePath = function() {
            if(ctrl.extension.translatedPath.organization === undefined) {
                delete ctrl.extension.translatedPath.event;
            }
            if(ctrl.extension.translatedPath.organization === null) {
                delete ctrl.extension.translatedPath.organization;
                delete ctrl.extension.translatedPath.event;
            }
            if(ctrl.extension.translatedPath.event === null) {
                delete ctrl.extension.translatedPath.event;
            }
            ctrl.extension.path = generatePath(ctrl.extension.translatedPath);
        };

        function generatePath(translated) {
            var path = "-";
            if(translated.organization !== undefined) {
                path += translated.organization + (translated.event !== undefined ? ("-" + translated.event) : "");
            }
            return path;
        }

        ctrl.save = function(extension) {

            var url = ctrl.toUpdate ? ('/admin/api/extensions/'+encodeURIComponent(ctrl.previousPath)+'/'+encodeURIComponent(ctrl.previousName)) : '/admin/api/extensions';

            $http.post(url, {
                path: extension.path,
                name: extension.name,
                enabled: extension.enabled,
                script: extension.script
            }).then(function() {
                $state.go('extension.list');
            }, function(res) {
                ctrl.errorMessage = res.data.value;
                window.scrollTo(0,0);
            });
        };

        ctrl.cancel = function() {
            $state.go('extension.list');
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

                ctrl.dataLoader.then(function() {
                    editor.setValue(ctrl.extension.script, 0);
                    editor.clearSelection();
                });

            }
        };
    }
})();