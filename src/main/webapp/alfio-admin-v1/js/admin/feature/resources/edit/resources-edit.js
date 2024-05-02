(function() {
    'use strict';

    angular.module('adminApplication').component('resourcesEdit', {
        bindings: {
            system: '<',
            event:'<',
            forOrganization: '<',
            organizationId: '<',
            resourceName: '<'
        },
        controller: ResourcesEditCtrl,
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/resources/edit/resources-edit.html'
    })


function ResourcesEditCtrl(ResourceService, EventService, $q) {
    var ctrl = this;

    ctrl.saveFor = saveFor;
    ctrl.deleteFor = deleteFor;
    ctrl.resetFor = resetFor;
    ctrl.previewFor = previewFor;
    ctrl.showDeleteButton = showDeleteButton;

    ctrl.$onInit = function() {
        loadAll()
    };

    function getOrgId() {
        if (ctrl.system) {
            return undefined;
        }
        return ctrl.forOrganization ? ctrl.organizationId : ctrl.event.organizationId;
    }

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
            ctrl.editors[key] = editor;
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
        var orgId = ctrl.system ? undefined : getOrgId();
        var eventId = ctrl.system || ctrl.forOrganization ? undefined : ctrl.event.id
        ResourceService.preview(orgId, eventId, ctrl.resourceName, locale, {fileAsString: newText}).then(function(res) {
            if(!res.download) {
                ctrl.previewedText = res.text;
                ctrl.previewMode = true;
            }
            ctrl.binaryPreview = res.download;
        }, errorHandler);
    }

    function saveFor(locale) {
        delete ctrl.error;
        ctrl.previewMode = false;
        var newText  = ctrl.resources[locale];
        var saver;
        if (ctrl.system) {
            saver = ResourceService.uploadSystemFile({fileAsString: newText, name: getFileName(locale), type: 'text/plain'});
        } else if(ctrl.forOrganization) {
            saver = ResourceService.uploadOrganizationFile(getOrgId(), {fileAsString: newText, name: getFileName(locale), type: 'text/plain'});
        } else {
            saver = ResourceService.uploadFile(ctrl.event.organizationId, ctrl.event.id, {fileAsString: newText, name: getFileName(locale), type: 'text/plain'});
        }
        saver.then(loadAll, errorHandler);
    }

    function deleteFor(locale) {
        delete ctrl.error;
        ctrl.previewMode = false;
        var deleter;
        if (ctrl.system) {
            deleter = ResourceService.deleteSystemFile(getFileName(locale));
        } else if(ctrl.forOrganization) {
            deleter = ResourceService.deleteOrganizationFile(getOrgId(), getFileName(locale));
        } else {
            deleter = ResourceService.deleteFile(ctrl.event.organizationId, ctrl.event.id, getFileName(locale));
        }
        deleter.then(loadAll, errorHandler);
    }

    function resetFor(locale) {
        ctrl.previewMode = false;
        ctrl.resources[locale] = ctrl.originalResources[locale] || ctrl.templateBodies[locale];
        ctrl.editors[locale].setValue(ctrl.resources[locale], 0);
    }

    function loadAll() {
        ctrl.templateBodies = {};
        ctrl.resources = {};
        ctrl.resourcesMetadata = {};
        ctrl.originalResources = {};
        ctrl.editors = {};

        var languageLoader;
        if(ctrl.system || ctrl.forOrganization) {
            languageLoader = EventService.getAllLanguages();
        } else {
            languageLoader = EventService.getSelectedLanguages(ctrl.event.shortName);
        }

        languageLoader.then(function(lang) {
            ctrl.locales = lang.data;
            return lang.data;
        }).then(function(selectedLang) {

            angular.forEach(selectedLang, function(lang) {
                var locale = lang.locale;

                var p = ResourceService.getTemplateBody(ctrl.resourceName, locale).then(function(res) {
                    ctrl.templateBodies[locale] = res.data;
                    return res.data;
                });

                var metadataLoader;
                if (ctrl.system) {
                    metadataLoader = ResourceService.getMetadataForSystemResource(getFileName(locale));
                } else if(ctrl.forOrganization) {
                    metadataLoader = ResourceService.getMetadataForOrganizationResource(getOrgId(), getFileName(locale));
                } else {
                    metadataLoader = ResourceService.getMetadataForEventResource(getOrgId(), ctrl.event.id, getFileName(locale))
                       .then(
                        function(res) {return res},
                        function() {return ResourceService.getMetadataForOrganizationResource(getOrgId(), getFileName(locale));}
                    );
                }

                metadataLoader.then(function(res) {
                    ctrl.resourcesMetadata[locale] = res.data;
                    var resourceLoader;
                    if (ctrl.system) {
                        resourceLoader = ResourceService.getSystemResource(getFileName(locale));
                    } else if(ctrl.forOrganization) {
                        resourceLoader = ResourceService.getOrganizationResource(getOrgId(), getFileName(locale));
                    } else {
                        resourceLoader = ResourceService.getEventResource(getOrgId(), ctrl.event.id, getFileName(locale)).then(
                            function(res) {return res},
                            function() {return ResourceService.getOrganizationResource(getOrgId(), getFileName(locale));}
                        );
                    }
                    resourceLoader.then(function(resource) {
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

    function showDeleteButton(locale) {
        if(ctrl.system || ctrl.forOrganization) {
            return ctrl.resourcesMetadata[locale];
        } else {
            return ctrl.resourcesMetadata[locale] && ctrl.resourcesMetadata[locale].eventId === ctrl.event.id;
        }
    }

    function getFileName(locale) {
        return ctrl.resourceName+'_'+locale+'.ms';
    }
}

})();