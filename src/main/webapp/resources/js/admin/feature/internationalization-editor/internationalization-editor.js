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
        ctrl.displayValueWithFallback = displayValueWithFallback;

        ctrl.bundle = {};
        ctrl.bundleKeys = {};

        ctrl.$onInit = function() {
            loadAllLanguages();
            loadOverride();
            loadForLocale('en');
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
            });
        }

        function loadForLocale(locale) {
            $http.get('/api/v2/public/i18n/bundle/'+locale, {params: {withSystemOverride: false}}).then(function(res) {

                var keys = Object.keys(res.data).sort();
                ctrl.bundleKeys[locale] = keys;


                for(var i = 0; i < keys.length; i++) {
                    res.data[keys[i]] = res.data[keys[i]].replace(/\{\{/g, '{').replace(/}}/g, '}');
                }

                ctrl.bundle[locale] = res.data;


            });
        }

        function displayValueWithFallback(locale, key) {
            if(ctrl.translationsData && ctrl.translationsData[locale] && ctrl.translationsData[locale][key]) {
                return ctrl.translationsData[locale][key];
            }
            return ctrl.bundle[locale][key];
        }

    }

})();