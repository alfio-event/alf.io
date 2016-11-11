(function() {
    'use strict';

    angular.module('adminApplication').component('resourcesEdit', {
        bindings: {
            event:'<',
            resourceName: '<'
        },
        controller: ResourcesEditCtrl,
        templateUrl: '../resources/js/admin/feature/resources/edit/resources-edit.html'
    })


function ResourcesEditCtrl(ResourceService, EventService, $q) {
    var ctrl = this;

    ctrl.saveFor = saveFor;
    ctrl.deleteFor = deleteFor;
    ctrl.resetFor = resetFor;
    ctrl.previewFor = previewFor;

    ctrl.$onInit = function() {
        loadAll()
    };

    ctrl.initLoadListener = function(locale) {
        var key = locale.locale;
        return function(editor) {
            var session = editor.getSession();

            // Options
            session.setUndoManager(new ace.UndoManager());

            session.on("change", function(event, editor) {
                var newVal = editor.getValue();
                var currVal = ctrl.resources[key];
                if(newVal != currVal) {
                    ctrl.resources[key] = newVal;
                }
            });
            editor.setValue(ctrl.resources[key], 0);
        }
    };

    var errorHandler = function(err) {
        var reader = new FileReader();
        var promise = $q(function(resolve, reject) {
            reader.onloadend = function() {
                resolve({download: false, text: reader.result})
            }
        });
        reader.readAsText(err.data);
        promise.then(function(res) {
            try {
                ctrl.error = JSON.parse(res.text);
            } catch(e) {
                ctrl.error = res.text;
            }
        });
    };

    function previewFor(locale) {
        delete ctrl.error;
        var newText  = ctrl.resources[locale];
        ResourceService.preview(ctrl.event.organizationId, ctrl.event.id, ctrl.resourceName, locale, {fileAsString: newText}).then(function(res) {
            if(!res.download) {
                ctrl.previewedText = res.text;
                ctrl.previewMode = true;
            }
        }, errorHandler);
    }

    function saveFor(locale) {
        delete ctrl.error;
        ctrl.previewMode = false;
        var newText  = ctrl.resources[locale];
        ResourceService.uploadFile(ctrl.event.organizationId, ctrl.event.id, {fileAsString: newText, name: getFileName(locale), type: 'text/plain'}).then(loadAll, errorHandler);
    }

    function deleteFor(locale) {
        delete ctrl.error;
        ctrl.previewMode = false
        ResourceService.deleteFile(ctrl.event.organizationId, ctrl.event.id, getFileName(locale)).then(loadAll, errorHandler);
    }

    function resetFor(locale) {
        ctrl.previewMode = false;
        ctrl.resources[locale] = ctrl.originalResources[locale] || ctrl.templateBodies[locale];
    }

    function loadAll() {
        ctrl.templateBodies = {};
        ctrl.resources = {};
        ctrl.resourcesMetadata = {};
        ctrl.originalResources = {};

        EventService.getSelectedLanguages(ctrl.event.shortName).then(function(lang) {
            ctrl.locales = lang.data;
            return lang.data;
        }).then(function(selectedLang) {

            angular.forEach(selectedLang, function(lang) {
                var locale = lang.locale;

                var p = ResourceService.getTemplateBody(ctrl.resourceName, locale).then(function(res) {
                    ctrl.templateBodies[locale] = res.data;
                    return res.data;
                });

                ResourceService.getMetadataForEventResource(ctrl.event.organizationId, ctrl.event.id, getFileName(locale)).then(function(res) {
                    ctrl.resourcesMetadata[locale] = res.data;
                    ResourceService.getEventResource(ctrl.event.organizationId, ctrl.event.id, getFileName(locale)).then(function(resource) {
                        ctrl.resources[locale] = resource.data;
                        ctrl.originalResources[locale] = resource.data;
                    })
                }, function() {
                    //if there is no file for the given locale, use the template instead
                    p.then(function(data) {
                        ctrl.resources[locale] = data;
                    })
                });
            });
        });
    }

    function getFileName(locale) {
        return ctrl.resourceName+'_'+locale+'.ms';
    }
}

})();