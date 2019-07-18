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
        ctrl.edit = edit;
        ctrl.update = update;
        ctrl.isOverride = isOverride;
        ctrl.deleteKey = deleteKey;

        ctrl.bundle = {};
        ctrl.bundleKeys = {};
        ctrl.editing = {};

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
                    // from {{0}} to {0}
                    res.data[keys[i]] = res.data[keys[i]].replace(/\{\{(\d+)\}\}/g,'{$1}');
                }

                ctrl.bundle[locale] = res.data;


            });
        }

        function displayValueWithFallback(locale, key) {
            if(ctrl.translationsData && ctrl.translationsData[locale] && ctrl.translationsData[locale][key]) {
                // from {{0}} to {0}
                return ctrl.translationsData[locale][key].replace(/\{\{(\d+)\}\}/g,'{$1}');
            }
            return ctrl.bundle[locale][key];
        }

        function isOverride(locale, key) {
            return ctrl.translationsData && ctrl.translationsData[locale] && ctrl.translationsData[locale][key] !== undefined
        }

        function edit(locale, key) {
            if(!ctrl.editing[locale]) {
                ctrl.editing[locale] = {};
            }

            ctrl.editing[locale][key] = {
                value: displayValueWithFallback(locale, key)
            };
        }

        function update(locale, key, value) {
            var copyOfTranslationsData = JSON.parse(JSON.stringify(ctrl.translationsData));

            if(!copyOfTranslationsData[locale]) {
                copyOfTranslationsData[locale] = {};
            }

            //from {0} to {{0}}
            var escapedValue = value.replace(/\{(\d+)\}/g,'{{$1}}');

            copyOfTranslationsData[locale][key] = escapedValue;

            saveTranslationData(copyOfTranslationsData).then(function() {
                ctrl.editing[locale][key] = false;
                loadOverride();
            });
        }

        function deleteKey(locale, key) {
            var copyOfTranslationsData = JSON.parse(JSON.stringify(ctrl.translationsData));
            delete copyOfTranslationsData[locale][key];
            saveTranslationData(copyOfTranslationsData).then(function() {
                loadOverride();
            });
        }

        function saveTranslationData(translationsData) {
            var url = undefined;
            if(ctrl.eventId !== undefined) {
                url = '/admin/api/configuration/organizations/'+ctrl.organizationId+'/events/'+ctrl.eventId+'/update';
            } else if (ctrl.organizationId !== undefined) {
                url = '/admin/api/configuration/organizations/'+ctrl.organizationId+'/update';
            } else {
                url = '/admin/api/configuration/update-bulk';
            }
            return $http.post(url, {TRANSLATIONS: [{key: 'TRANSLATION_OVERRIDE', value: JSON.stringify(translationsData)}]});
        }

    }

})();