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


function ResourcesEditCtrl(ResourceService, EventService) {
    var ctrl = this;

    ctrl.saveFor = saveFor;

    ctrl.$onInit = function() {
        ctrl.templateBodies = {};

        ctrl.resources = {};

        loadAll()
    }


    function saveFor(locale) {
        var newText  = ctrl.resources[locale];
        ResourceService.uploadFile(ctrl.event.organizationId, ctrl.event.id, {fileAsString: newText, name: getFileName(locale), type: 'text/plain'}).then(loadAll);
    }

    function loadAll() {
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
                    ResourceService.getEventResource(ctrl.event.organizationId, ctrl.event.id, getFileName(locale)).then(function(res) {
                        console.log(res);
                        ctrl.resources[locale] = res.data;
                    })
                }, function() {
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