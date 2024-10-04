(function() {
    'use strict';

    angular.module('adminApplication').component('internationalizationEditor', {
        bindings: {
            organizationId: '<',
            eventId: '<'
        },
        controller: ['ConfigurationService', 'EventService', '$http', '$q', InternationalizationEditorCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/internationalization-editor/internationalization-editor.html'
    });

    function hideEscaping(value) {
        return value.replace(/{+(\d+)}+/g,'{$1}')
            .replace(/''/g, "'");
    }


    function InternationalizationEditorCtrl(ConfigurationService, EventService, $http, $q) {
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
        };

        function loadAllLanguages() {
            EventService.getAllLanguages().then(function(res) {
                ctrl.allLanguages = res.data;
            });
        }

        function loadOverride() {
            loadOverrideFor(ctrl.organizationId, ctrl.eventId).then(function(res) {
                ctrl.translationsData = res;
            });
        }

        function loadOverrideForPreviousLevel() {
            if(ctrl.eventId !== undefined) {
                return $q.all([loadOverrideFor(undefined, undefined), loadOverrideFor(ctrl.organizationId, undefined)]).then(function(toBeMerged) {
                    return [null, _.merge(toBeMerged[0], toBeMerged[1])];
                });
            } else if(ctrl.organizationId !== undefined) {
                return loadOverrideFor(undefined, undefined);
            } else {
                return $q(function(resolve, reject) {resolve([])});
            }
        }

        function loadOverrideFor(organizationId, eventId) {
            var $promiseConf;
            if(eventId !== undefined) {
                //event
                $promiseConf = ConfigurationService.loadTranslationsOverrideForEvent(ctrl.eventId)
            } else if (organizationId !== undefined) {
                //organization
                $promiseConf = ConfigurationService.loadTranslationsOverrideForOrganization(ctrl.organizationId)
            } else {
                $promiseConf = ConfigurationService.loadSystemTranslationsOverride()
                //system
            }

            return $promiseConf.then(function(res) {
                return res.data;
            });
        }

        function loadForLocale(locale) {

            loadOverrideForPreviousLevel().then(function(res) {
                loadAndCustomizeBundle(locale, res);
            }, function() {
                loadAndCustomizeBundle(locale, undefined);
            });
        }

        function loadAndCustomizeBundle(locale, overrideFromPreviousLevel) {
            $http.get('/api/v2/public/i18n/bundle/'+locale, {params: {withSystemOverride: false}}).then(function(res) {
                var keys = Object.keys(res.data).sort();
                ctrl.bundleKeys[locale] = keys;
                for(var i = 0; i < keys.length; i++) {
                    var key = keys[i];
                    var value = res.data[key];
                    //check if override is present
                    if(overrideFromPreviousLevel && overrideFromPreviousLevel[locale] && overrideFromPreviousLevel[locale][key]) {
                        value = overrideFromPreviousLevel[locale][key];
                    }
                    // from {{0}} to {0}
                    res.data[keys[i]] = hideEscaping(value);
                }
                ctrl.bundle[locale] = res.data;
            });
        }

        function displayValueWithFallback(locale, key) {
            if(ctrl.translationsData && ctrl.translationsData[locale] && ctrl.translationsData[locale][key]) {
                // from {{0}} to {0}
                return hideEscaping(ctrl.translationsData[locale][key]);
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

            var escapedValue = value.replace(/{+(\d+)}+/g,'{$1}')
                .replace(/([^'])'([^'])/g,"$1''$2"); //add escape for single quotes

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

    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions#Escaping
    function escapeRegExp(string) {
      return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); // $& means the whole matched string
    }

    angular.module('adminApplication').filter('filterBundle', function() {
        return function(res, filterValue, displayValueWithFallback, locale, isOverride, displayOnlyOverride) {
            if(res && (filterValue || displayOnlyOverride)) {
                var matcher = filterValue ? new RegExp(escapeRegExp(filterValue), 'i') : /.*/g;
                var result = [];
                var resLength = res.length;
                for(var i = 0; i < resLength; i++) {
                    var key = res[i];
                    if(key.match(matcher) !== null || displayValueWithFallback(locale, key).match(matcher) !== null) {
                        if(displayOnlyOverride && isOverride(locale, key)) {
                            result.push(res[i]);
                        } else if (!displayOnlyOverride) {
                            result.push(res[i]);
                        }
                    }
                }
                return result;
            } else {
                return res;
            }

        }
    })

})();