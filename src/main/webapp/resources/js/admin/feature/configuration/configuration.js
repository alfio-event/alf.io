(function() {
    "use strict";
    angular.module('alfio-configuration', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider
                .state('configuration', {
                    url: '/configuration',
                    template: '<div class="container"><div data-ui-view></div></div>',
                    controller: 'ConfigurationController',
                    controllerAs: 'configCtrl',
                    data: {
                        view: 'CONFIGURATION'
                    }
                })
                .state('configuration.system', {
                    url: '/system',
                    templateUrl: '/resources/angular-templates/admin/partials/configuration/system.html',
                    controller: 'SystemConfigurationController',
                    controllerAs: 'systemConf'
                })
                .state('configuration.organization', {
                    url: '/organization/:organizationId',
                    templateUrl: '/resources/angular-templates/admin/partials/configuration/organization.html',
                    controller: 'OrganizationConfigurationController',
                    controllerAs: 'organizationConf'
                })
                .state('configuration.event', {
                    url: '/organization/:organizationId/event/:eventId',
                    templateUrl: '/resources/angular-templates/admin/partials/configuration/event.html',
                    controller: 'EventConfigurationController',
                    controllerAs: 'eventConf'
                })
                .state('events.single.configuration', {
                    url: '/configuration',
                    templateUrl: '/resources/angular-templates/admin/partials/configuration/event.html',
                    controller: 'EventConfigurationController',
                    controllerAs: 'eventConf'
                });
        }])
        .controller('ConfigurationController', ConfigurationController)
        .controller('SystemConfigurationController', SystemConfigurationController)
        .controller('OrganizationConfigurationController', OrganizationConfigurationController)
        .controller('EventConfigurationController', EventConfigurationController)
        .controller('CategoryConfigurationController', CategoryConfigurationController)
        .directive('ticketCategoryConfiguration', ticketCategoryConfiguration)
        .service('ConfigurationService', ConfigurationService)
        .directive('basicConfigurationNeeded', basicConfigurationNeeded);

    function ConfigurationService($http, HttpErrorHandler) {
        return {
            loadAll: function() {
                return $http.get('/admin/api/configuration/load').error(HttpErrorHandler.handle);
            },
            loadOrganizationConfig: function(organizationId) {
                return $http.get('/admin/api/configuration/organizations/'+organizationId+'/load').error(HttpErrorHandler.handle);
            },
            loadEventConfig: function(eventId) {
                return $http.get('/admin/api/configuration/events/'+eventId+'/load').error(HttpErrorHandler.handle)
            },
            loadCategory: function(eventId, categoryId) {
                return $http.get('/admin/api/configuration/events/'+eventId+'/categories/'+categoryId+'/load').error(HttpErrorHandler.handle);
            },
            loadEUCountries: function () {
                return $http.get('/admin/api/configuration/eu-countries').error(HttpErrorHandler.handle);
            },
            update: function(configuration) {
                return $http.post('/admin/api/configuration/update', configuration).error(HttpErrorHandler.handle);
            },
            bulkUpdate: function(settings) {
                return $http.post('/admin/api/configuration/update-bulk', settings).error(HttpErrorHandler.handle);
            },
            updateOrganizationConfig: function(organization, settings) {
                return $http.post('/admin/api/configuration/organizations/'+organization.id+'/update', settings).error(HttpErrorHandler.handle);
            },
            updateEventConfig: function(organizationId, eventId, settings) {
                return $http.post('/admin/api/configuration/organizations/'+organizationId+'/events/'+eventId+'/update', settings).error(HttpErrorHandler.handle);
            },
            updateCategoryConfig: function(categoryId, eventId, settings) {
                return $http.post('/admin/api/configuration/events/'+eventId+'/categories/'+categoryId+'/update', settings).error(HttpErrorHandler.handle);
            },
            removeSystemConfig: function(conf) {
                return $http['delete']('/admin/api/configuration/key/' + conf.configurationKey).error(HttpErrorHandler.handle);
            },
            removeOrganizationConfig: function(conf, organizationId) {
                return $http['delete']('/admin/api/configuration/organization/'+organizationId+'/key/' + conf.configurationKey).error(HttpErrorHandler.handle);
            },
            removeEventConfig: function(conf, eventId) {
                return $http['delete']('/admin/api/configuration/event/'+eventId+'/key/' + conf.configurationKey).error(HttpErrorHandler.handle);
            },
            removeCategoryConfig: function(conf, eventId, categoryId) {
                return $http['delete']('/admin/api/configuration/event/'+eventId+'/category/'+categoryId+'/key/' + conf.configurationKey).error(HttpErrorHandler.handle);
            },
            getPlatformModeStatus: function(orgId) {
                return $http.get('/admin/api/configuration/platform-mode/status/'+orgId).error(HttpErrorHandler.handle);
            },
            transformConfigurationObject: function(original) {
                var transformed = {};
                transformed.settings = original;
                transformed.general = {
                    settings: _.filter(original['GENERAL'], function(e) {return e.key !== 'SUPPORTED_LANGUAGES'}),
                    supportedTranslations: _.find(original['GENERAL'], function(e) {return e.key === 'SUPPORTED_LANGUAGES'})
                };
                if(angular.isDefined(original['MAIL']) && original['MAIL'].length > 0) {
                    transformed.mail = {
                        settings: _.filter(original['MAIL'], function(e) {return e.key !== 'MAILER_TYPE';}),
                        type: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAILER_TYPE';}),
                        maxEmailPerCycle: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAX_EMAIL_PER_CYCLE';}),
                        cc: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAIL_SYSTEM_NOTIFICATION_CC';}),
                        mailReplyTo: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAIL_REPLY_TO';}),
                        mailAttemptsCount: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAIL_ATTEMPTS_COUNT';})
                    };
                };

                if(angular.isDefined(original['MAP']) && original['MAP'].length > 0) {
                    transformed.map = {
                        MAPS_PROVIDER: _.find(original['MAP'], function(e) {return e.key === 'MAPS_PROVIDER';}),
                        MAPS_CLIENT_API_KEY: _.find(original['MAP'], function(e) {return e.key === 'MAPS_CLIENT_API_KEY';}),
                        MAPS_HERE_APP_ID: _.find(original['MAP'], function(e) {return e.key === 'MAPS_HERE_APP_ID';}),
                        MAPS_HERE_APP_CODE: _.find(original['MAP'], function(e) {return e.key === 'MAPS_HERE_APP_CODE';})
                    }
                }

                _.forEach(['PAYMENT', 'PAYMENT_STRIPE', 'PAYMENT_PAYPAL', /*'PAYMENT_MOLLIE',*/ 'PAYMENT_OFFLINE', 'INVOICE_EU', 'TRANSLATIONS', 'ALFIO_PI'], function(group) {
                    if(angular.isDefined(original[group]) && original[group].length > 0) {
                        transformed[_.camelCase(group)] = {
                            settings: original[group]
                        };
                    }
                });
                return transformed;
            }
        };
    }

    ConfigurationService.$inject = ['$http', 'HttpErrorHandler'];

    function ConfigurationController(OrganizationService, EventService, $q, $rootScope) {
        var configCtrl = this;
        configCtrl.loading = true;
        $q.all([OrganizationService.getAllOrganizations(), EventService.getAllEvents()]).then(function(results) {
            var organizations = results[0].data;
            var events = results[1].data;
            configCtrl.organizations = _.map(organizations, function(org) {
                org.events = _.filter(events, function(e) {return !e.expired && e.organizationId === org.id});
                return org;
            });
            $rootScope.$emit('ConfigurationMenuLoaded', configCtrl.organizations);
            configCtrl.loading = false;
        }, function(e) {
            alert(e);
            configCtrl.loading = false;
        });
    }


    function handleEuCountries(conf, euCountries) {
        if(conf.invoiceEu) {
            var euCountries = _.map(euCountries, function(o) {
                var key = Object.keys(o)[0];
                return {key: key, value: o[key]};
            });
            _.forEach(_.filter(conf.invoiceEu.settings, function(e) {return e.key === 'COUNTRY_OF_BUSINESS'}), function(cb) {
                cb.listValues = euCountries;
            });
        }
    }

    ConfigurationController.$inject = ['OrganizationService', 'EventService', '$q', '$rootScope'];

    function SystemConfigurationController(ConfigurationService, EventService, ExtensionService, NotificationHandler, $rootScope, $q) {
        var systemConf = this;
        systemConf.loading = true;

        systemConf.keys = Object.keys;

        var loadAll = function() {
            systemConf.loading = true;
            $q.all([EventService.getAllLanguages(), ConfigurationService.loadAll(), ConfigurationService.loadEUCountries(), ExtensionService.loadSystem()]).then(function(results) {
                systemConf.allLanguages = results[0].data;
                loadSettings(systemConf, results[1].data, ConfigurationService);
                if(systemConf.general) {
                    systemConf.general.selectedLanguages = _.chain(systemConf.allLanguages).map('value').filter(function(x) {return parseInt(systemConf.general.supportedTranslations.value) & x;}).value();
                    systemConf.isLanguageSelected = function(lang) {
                        return systemConf.general.selectedLanguages.indexOf(lang.value) > -1;
                    };
                    systemConf.toggleLanguageSelection = function(lang) {
                        if(systemConf.isLanguageSelected(lang)) {
                            _.remove(systemConf.general.selectedLanguages, function(l) { return l === lang.value });
                        } else {
                            systemConf.general.selectedLanguages.push(lang.value);
                        }
                        systemConf.updateLocales();
                    }
                }
                handleEuCountries(systemConf, results[2].data);
                if(systemConf.alfioPi) {
                    systemConf.alfioPiOptions = _.filter(systemConf.alfioPi.settings, function(pi) { return pi.key !== 'LABEL_LAYOUT'});
                }

                systemConf.extensionSettings = results[3].data;

            }, function() {
                systemConf.loading = false;
            });
        };
        loadAll();

        systemConf.saveSettings = function(frm) {
            if(!frm.$valid) {
                return;
            }
            systemConf.loading = true;
            $q.all([ConfigurationService.bulkUpdate(systemConf.settings), ExtensionService.saveBulkSystemSetting(systemConf.extensionSettings)]).then(function() {
                loadAll();
                NotificationHandler.showSuccess("Configurations have been saved successfully");
            }, function(e) {
                NotificationHandler.showError("Unable to save the configuration");
                alert(e.data);
                systemConf.loading = false;
            });
        };

        systemConf.updateLocales = function() {
            updateLocales(systemConf);
        };

        systemConf.delete = function(config) {
            return ConfigurationService.removeSystemConfig(config);
        };

        systemConf.deleteExtensionSetting = function(config) {
            return ExtensionService.deleteSystemSettingValue(config);
        };

        $rootScope.$on('ReloadSettings', function() {
            loadAll();
        });
    }

    SystemConfigurationController.$inject = ['ConfigurationService', 'EventService', 'ExtensionService', 'NotificationHandler', '$rootScope', '$q'];

    function OrganizationConfigurationController(ConfigurationService, OrganizationService, ExtensionService, NotificationHandler, $stateParams, $q, $rootScope) {
        var organizationConf = this;
        organizationConf.organizationId = $stateParams.organizationId;
        var load = function() {
            organizationConf.loading = true;
            $q.all([OrganizationService.getOrganization(organizationConf.organizationId),
                ConfigurationService.loadOrganizationConfig(organizationConf.organizationId),
                ConfigurationService.loadEUCountries(),
                ConfigurationService.getPlatformModeStatus(organizationConf.organizationId),
                ExtensionService.loadOrganizationConfigWithOrgId(organizationConf.organizationId)
            ]).then(function(result) {
                    organizationConf.organization = result[0].data;
                    loadSettings(organizationConf, result[1].data, ConfigurationService);
                    handleEuCountries(organizationConf, result[2].data);
                    var platformModeStatus = result[3].data;
                    organizationConf.platformModeEnabled = platformModeStatus.enabled;
                    organizationConf.stripeConnected = platformModeStatus.stripeConnected;
                    organizationConf.extensionSettings = result[4].data;
                }, function() {
                    organizationConf.loading = false;
                });
        };
        load();
        organizationConf.saveSettings = function(frm) {
            if(!frm.$valid) {
                return;
            }
            organizationConf.loading = true;
            $q.all([ConfigurationService.updateOrganizationConfig(organizationConf.organization, organizationConf.settings),
                ExtensionService.saveBulkOrganizationSetting(organizationConf.organizationId, organizationConf.extensionSettings)]).then(function() {
                load();
                NotificationHandler.showSuccess("Configurations have been saved successfully");
            }, function(e) {
                NotificationHandler.showError("Unable to save the configuration");
                alert(e.data);
                organizationConf.loading = false;
            });
        };

        organizationConf.delete = function(config) {
            return ConfigurationService.removeOrganizationConfig(config, organizationConf.organizationId);
        };

        organizationConf.deleteExtensionSetting = function(config) {
            return ExtensionService.deleteOrganizationSettingValue(organizationConf.organizationId, config);
        };

        $rootScope.$on('ReloadSettings', function() {
            load();
        });
    }

    OrganizationConfigurationController.$inject = ['ConfigurationService', 'OrganizationService', 'ExtensionService', 'NotificationHandler', '$stateParams', '$q', '$rootScope'];

    function EventConfigurationController(ConfigurationService, EventService, ExtensionService, NotificationHandler, $q, $rootScope, $stateParams) {
        var eventConf = this;
        var getData = function() {
            if(angular.isDefined($stateParams.eventName)) {
                var deferred = $q.defer();
                EventService.getEvent($stateParams.eventName).then(function(result) {
                    eventConf.organizationId = result.data.organization.id;
                    var event = result.data.event;
                    eventConf.eventName = event.shortName;
                    eventConf.eventId = event.id;
                    $q.all([ConfigurationService.loadEventConfig(eventConf.eventId), ExtensionService.loadEventConfigWithOrgIdAndEventId(eventConf.organizationId, eventConf.eventId)]).then(function(result) {
                        deferred.resolve([{data:event}].concat(result));
                    }, function(e) {
                        deferred.reject(e);
                    });
                }, function(e) {
                    deferred.reject(e);
                });
                return deferred.promise;
            } else {
                eventConf.eventId = $stateParams.eventId;
                eventConf.organizationId = $stateParams.organizationId;
                return $q.all([EventService.getEventById($stateParams.eventId), ConfigurationService.loadEventConfig($stateParams.eventId), ExtensionService.loadEventConfigWithOrgIdAndEventId(eventConf.organizationId, eventConf.eventId)])
            }
        };

        var load = function() {
            eventConf.loading = true;
            getData().then(function(result) {
                    eventConf.event = result[0].data;
                    loadSettings(eventConf, result[1].data, ConfigurationService);

                    if(eventConf.alfioPi) {
                        eventConf.alfioPiOptions = _.filter(eventConf.alfioPi.settings, function(pi) { return pi.key !== 'LABEL_LAYOUT'});
                        eventConf.labelLayout = _.find(eventConf.alfioPi.settings, function(pi) { return pi.key === 'LABEL_LAYOUT'});
                    }
                    eventConf.extensionSettings = result[2].data;
                    eventConf.loading = false;
                }, function() {
                    eventConf.loading = false;
                });
        };
        load();

        eventConf.isLabelPrintingEnabled = function() {
            return _.any(eventConf.alfioPi.settings, function(pi) { return pi.key === 'LABEL_PRINTING_ENABLED' && pi.value === "true"});
        };

        eventConf.saveSettings = function(frm) {
            if(!frm.$valid) {
                return;
            }
            eventConf.loading = true;
            $q.all([ConfigurationService.updateEventConfig(eventConf.organizationId, eventConf.eventId, eventConf.settings),
                ExtensionService.saveBulkEventSetting(eventConf.organizationId, eventConf.eventId, eventConf.extensionSettings)]).then(function() {
                load();
                NotificationHandler.showSuccess("Configurations have been saved successfully");
            }, function(e) {
                NotificationHandler.showError("Unable to save the configuration");
                alert(e.data);
                eventConf.loading = false;
            });
        };

        eventConf.delete = function(config) {
            return ConfigurationService.removeEventConfig(config, eventConf.eventId);
        };

        eventConf.deleteExtensionSetting = function(config) {
            return ExtensionService.deleteEventSettingValue(eventConf.organizationId, eventConf.eventId, config);
        };

        $rootScope.$on('ReloadSettings', function() {
            load();
        });
    }

    EventConfigurationController.$inject = ['ConfigurationService', 'EventService', 'ExtensionService', 'NotificationHandler', '$q', '$rootScope', '$stateParams'];

    function loadSettings(container, settings, ConfigurationService) {
        var general = settings['GENERAL'] || [];
        container.hasResults = general.length > 0;
        container.noResults = general.length === 0;
        if(container.hasResults) {
            container.settings = settings;
            angular.extend(container, ConfigurationService.transformConfigurationObject(settings));
        }
        container.loading = false;
    }

    function ticketCategoryConfiguration() {
        return {
            restrict: 'E',
            scope: {
                event: '=',
                category: '=',
                closeModal: '&'
            },
            bindToController: true,
            controller: 'CategoryConfigurationController',
            controllerAs: 'categoryConf',
            templateUrl: '/resources/angular-templates/admin/partials/configuration/category.html'
        };
    }

    function CategoryConfigurationController(ConfigurationService, $rootScope) {
        var categoryConf = this;

        var load = function() {
            categoryConf.loading = true;
            ConfigurationService.loadCategory(categoryConf.event.id, categoryConf.category.id).then(function(result) {
                loadSettings(categoryConf, result.data, ConfigurationService);
                categoryConf.loading = false;
            }, function() {
                categoryConf.loading = false;
            });
        };
        load();

        categoryConf.saveSettings = function(frm) {
            if(!frm.$valid) {
                return;
            }
            categoryConf.loading = true;
            ConfigurationService.updateCategoryConfig(categoryConf.category.id, categoryConf.event.id, categoryConf.settings).then(function() {
                load();
            }, function(e) {
                alert(e.data);
                categoryConf.loading = false;
            });
        };

        categoryConf.delete = function(config) {
            return ConfigurationService.removeCategoryConfig(config, categoryConf.event.id, categoryConf.category.id);
        };

        $rootScope.$on('ReloadSettings', function() {
            load();
        });
    }
    CategoryConfigurationController.$inject = ['ConfigurationService', '$rootScope'];

    function basicConfigurationNeeded($uibModal, ConfigurationService, EventService, $q, $window) {
        return {
            restrict: 'A',
            scope: true,
            link: function() {
                var m = $uibModal.open({
                    size:'lg',
                    templateUrl:'/resources/angular-templates/admin/partials/configuration/basic-settings.html',
                    backdrop: 'static',
                    controllerAs: 'ctrl',
                    controller: function($scope) {
                        var ctrl = this;
                        var onlyBasic = function(list) {
                            return _.filter(list, function(c) { return c.basic; });
                        };
                        $q.all([EventService.getAllLanguages(),ConfigurationService.loadAll()]).then(function(results) {
                            var settings = results[1].data;
                            ctrl.allLanguages = results[0].data;
                            ctrl.settings = settings;
                            var generalBasic = onlyBasic(settings['GENERAL']);
                            ctrl.general = {
                                settings: _.filter(generalBasic, function(e) {return e.key !== 'SUPPORTED_LANGUAGES'}),
                                supportedTranslations: _.find(generalBasic, function(e) {return e.key === 'SUPPORTED_LANGUAGES'})
                            };
                            ctrl.mail = {
                                settings: _.filter(settings['MAIL'], function(e) {return e.key !== 'MAILER_TYPE';}),
                                type: _.find(settings['MAIL'], function(e) {return e.configurationKey === 'MAILER_TYPE';}),
                                maxEmailPerCycle: _.find(settings['MAIL'], function(e) {return e.configurationKey === 'MAX_EMAIL_PER_CYCLE';}),
                                mailReplyTo: _.find(settings['MAIL'], function(e) {return e.configurationKey === 'MAIL_REPLY_TO';})
                            };
                            ctrl.payment = {
                                settings: onlyBasic(settings['PAYMENT'])
                            };
                            ctrl.map = {
                                MAPS_PROVIDER: _.find(settings['MAP'], function(e) {return e.key === 'MAPS_PROVIDER';}),
                                MAPS_CLIENT_API_KEY: _.find(settings['MAP'], function(e) {return e.key === 'MAPS_CLIENT_API_KEY';}),
                                MAPS_HERE_APP_ID: _.find(settings['MAP'], function(e) {return e.key === 'MAPS_HERE_APP_ID';}),
                                MAPS_HERE_APP_CODE: _.find(settings['MAP'], function(e) {return e.key === 'MAPS_HERE_APP_CODE';})
                            };
                        });
                        ctrl.saveSettings = function(frm, settings) {
                            if(!frm.$valid) {
                                return;
                            }
                            ctrl.loading = true;
                            var promises = [ConfigurationService.bulkUpdate(settings)];
                            $q.all(promises).then(function() {
                                ctrl.loading = false;
                                $scope.$close(true);
                            }, function(e) {
                                alert(e.data);
                                $scope.$close(false);
                            });
                        };
                        ctrl.updateLocales = function() {
                            updateLocales(ctrl);
                        };
                    }
                });
                m.result.then(function(){ $window.location.reload(); });
            }
        };
    }

    basicConfigurationNeeded.$inject = ['$uibModal', 'ConfigurationService', 'EventService', '$q', '$window'];

    function updateLocales(controller) {
        var locales = 0;
        angular.forEach(controller.general.selectedLanguages, function(val) {
            locales |= val;
        });
        controller.general.supportedTranslations.value = locales;
    }
})();