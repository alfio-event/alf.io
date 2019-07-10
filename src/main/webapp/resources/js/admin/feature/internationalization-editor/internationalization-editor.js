(function() {
    'use strict';

    angular.module('adminApplication').component('internationalizationEditor', {
        bindings: {
            organizationId: '<',
            eventId: '<'
        },
        controller: ['ConfigurationService', 'EventService', '$http', InternationalizationEditorCtrl],
        templateUrl: '../resources/js/admin/feature/internationalization-editor/internationalization-editor.html'
    });



    function InternationalizationEditorCtrl(ConfigurationService, EventService, $http) {
        var ctrl = this;

        ctrl.loadForLocale = loadForLocale;

        ctrl.bundle = {};

        ctrl.$onInit = function() {
            loadAllLanguages();
            loadOverride();
        }

        function loadAllLanguages() {
            EventService.getAllLanguages().then(function(res) {
                ctrl.allLanguages = res.data;
            });
        }

        function loadOverride() {
            var $promiseConf;
            if(ctrl.eventId !== undefined) {
                //event
                $promiseConf = ConfigurationService.loadEventConfig(ctrl.eventId)
            } else if (ctrl.organizationId !== undefined) {
                //organization
                $promiseConf = ConfigurationService.loadOrganizationConfig(ctrl.organizationId)
            } else {
                $promiseConf = ConfigurationService.loadAll()
                //system
            }

            $promiseConf.then(function(res) {
                var translations = _.find(res.data['TRANSLATIONS'], function(v) {return v.key === 'TRANSLATION_OVERRIDE'});
                ctrl.translationsKey = translations;
                ctrl.translationsData = JSON.parse(translations.value || '{}');
            })
        }

        function loadForLocale(locale) {
            $http.get('/api/v2/public/i18n/bundle/'+locale, {params: {withSystemOverride: false}}).then(function(res) {
                ctrl.bundle[locale] = res.data;
            });
        }

    }

})();