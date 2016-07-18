(function() {
    "use strict";
    angular.module('alfio-configuration', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider
                .state('configuration', {
                    url: '/configuration',
                    templateUrl: '/resources/angular-templates/admin/partials/configuration/index.html',
                    controller: 'ConfigurationController',
                    controllerAs: 'configCtrl'
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
            remove: function(conf) {
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
            loadPluginsConfig: function(eventId) {
                return $http.get('/admin/api/configuration/events/'+eventId+'/plugin/load').error(HttpErrorHandler.handle);
            },
            bulkUpdatePlugins: function(eventId, pluginConfigOptions) {
                return $http.post('/admin/api/configuration/events/'+eventId+'/plugin/update-bulk', pluginConfigOptions).error(HttpErrorHandler.handle);
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
                        mailReplyTo: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAIL_REPLY_TO';}),
                        mailAttemptsCount: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAIL_ATTEMPTS_COUNT';})
                    };
                }
                if(angular.isDefined(original['PAYMENT']) && original['PAYMENT'].length > 0) {
                    transformed.payment = {
                        settings: original['PAYMENT']
                    };
                }
                return transformed;
            }
        };
    }

    ConfigurationService.$inject = ['$http', 'HttpErrorHandler'];

    function ConfigurationController(OrganizationService, EventService, $q) {
        var configCtrl = this;
        configCtrl.loading = true;
        $q.all([OrganizationService.getAllOrganizations(), EventService.getAllEvents()]).then(function(results) {
            var organizations = results[0].data;
            var events = results[1].data;
            configCtrl.organizations = _.map(organizations, function(org) {
                org.events = _.filter(events, function(e) {return !e.expired && e.organizationId === org.id});
                return org;
            });
            configCtrl.loading = false;
        }, function(e) {
            alert(e);
            configCtrl.loading = false;
        });
    }

    ConfigurationController.$inject = ['OrganizationService', 'EventService', '$q'];

    function SystemConfigurationController(ConfigurationService, EventService, $rootScope, $q) {
        var systemConf = this;
        systemConf.loading = true;

        var loadAll = function() {
            systemConf.loading = true;
            $q.all([EventService.getAllLanguages(), ConfigurationService.loadAll()]).then(function(results) {
                systemConf.allLanguages = results[0].data;
                loadSettings(systemConf, results[1].data, ConfigurationService);
                systemConf.general.selectedLanguages = _.chain(systemConf.allLanguages).map('value').filter(function(x) {return parseInt(systemConf.general.supportedTranslations.value) & x;}).value();
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
            ConfigurationService.bulkUpdate(systemConf.settings).then(function() {
                loadAll();
            }, function(e) {
                alert(e.data);
                systemConf.loading = false;
            });
        };

        systemConf.updateLocales = function() {
            updateLocales(systemConf);
        };

        systemConf.delete = function(config) {
            return ConfigurationService.remove(config);
        };

        $rootScope.$on('ReloadSettings', function() {
            loadAll();
        });
    }

    SystemConfigurationController.$inject = ['ConfigurationService', 'EventService', '$rootScope', '$q'];

    function OrganizationConfigurationController(ConfigurationService, OrganizationService, $stateParams, $q, $rootScope) {
        var organizationConf = this;
        organizationConf.organizationId = $stateParams.organizationId;
        organizationConf.delete = function(config) {
            return ConfigurationService.removeOrganizationConfig(config, organizationConf.organizationId);
        };
        var load = function() {
            organizationConf.loading = true;
            $q.all([OrganizationService.getOrganization(organizationConf.organizationId), ConfigurationService.loadOrganizationConfig(organizationConf.organizationId)])
                .then(function(result) {
                    organizationConf.organization = result[0].data;
                    loadSettings(organizationConf, result[1].data, ConfigurationService);
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
            ConfigurationService.updateOrganizationConfig(organizationConf.organization, organizationConf.settings).then(function() {
                load();
            }, function(e) {
                alert(e.data);
                organizationConf.loading = false;
            });
        };

        organizationConf.delete = function(config) {
            return ConfigurationService.removeOrganizationConfig(config, organizationConf.organizationId);
        };

        $rootScope.$on('ReloadSettings', function() {
            load();
        });
    }

    OrganizationConfigurationController.$inject = ['ConfigurationService', 'OrganizationService', '$stateParams', '$q', '$rootScope'];

    function EventConfigurationController(ConfigurationService, EventService, $q, $rootScope, $stateParams) {
        var eventConf = this;
        var getData = function() {
            if(angular.isDefined($stateParams.eventName)) {
                var deferred = $q.defer();
                EventService.getEvent($stateParams.eventName).then(function(result) {
                    eventConf.organizationId = result.data.organization.id;
                    var event = result.data.event;
                    eventConf.eventName = event.shortName;
                    eventConf.eventId = event.id;
                    $q.all([ConfigurationService.loadEventConfig(eventConf.eventId), ConfigurationService.loadPluginsConfig(eventConf.eventId)]).then(function(result) {
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
                return $q.all([EventService.getEventById($stateParams.eventId), ConfigurationService.loadEventConfig($stateParams.eventId), ConfigurationService.loadPluginsConfig($stateParams.eventId)])
            }
        };

        var load = function() {
            eventConf.loading = true;
            getData().then(function(result) {
                    eventConf.event = result[0].data;
                    loadSettings(eventConf, result[1].data, ConfigurationService);
                    eventConf.pluginSettings = result[2].data;
                    eventConf.pluginSettingsByPluginId = _.groupBy(result[2].data, 'pluginId');
                    eventConf.loading = false;
                }, function() {
                    eventConf.loading = false;
                });
        };
        load();

        eventConf.saveSettings = function(frm) {
            if(!frm.$valid) {
                return;
            }
            eventConf.loading = true;
            $q.all([ConfigurationService.updateEventConfig(eventConf.organizationId, eventConf.eventId, eventConf.settings), ConfigurationService.bulkUpdatePlugins(eventConf.eventId, eventConf.pluginSettings)]).then(function() {
                load();
            }, function(e) {
                alert(e.data);
                eventConf.loading = false;
            });
        };

        eventConf.delete = function(config) {
            return ConfigurationService.removeEventConfig(config, eventConf.eventId);
        };

        $rootScope.$on('ReloadSettings', function() {
            load();
        });
    }

    EventConfigurationController.$inject = ['ConfigurationService', 'EventService', '$q', '$rootScope', '$stateParams'];

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
                        });
                        ctrl.saveSettings = function(frm, settings, pluginSettings) {
                            if(!frm.$valid) {
                                return;
                            }
                            ctrl.loading = true;
                            var promises = [ConfigurationService.bulkUpdate(settings)];
                            if(angular.isDefined(pluginSettings)) {
                                promises.push(ConfigurationService.bulkUpdatePlugins(pluginSettings));
                            }
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