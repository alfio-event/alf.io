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
                });
        }])
        .controller('ConfigurationController', ConfigurationController)
        .controller('SystemConfigurationController', SystemConfigurationController)
        .controller('OrganizationConfigurationController', OrganizationConfigurationController)
        .controller('EventConfigurationController', EventConfigurationController)
        .service('ConfigurationService', ConfigurationService);

    function ConfigurationService($http, HttpErrorHandler) {
        return {
            loadAll: function() {
                return $http.get('/admin/api/configuration/load').error(HttpErrorHandler.handle);
            },
            loadOrganization: function(organizationId) {
                return $http.get('/admin/api/configuration/organizations/'+organizationId+'/load').error(HttpErrorHandler.handle);
            },
            loadEvent: function(eventId) {
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
            remove: function(conf) {
                return $http['delete']('/admin/api/configuration/key/' + conf.configurationKey).error(HttpErrorHandler.handle);
            },
            removeOrganizationConfig: function(conf, organizationId) {
                return $http['delete']('/admin/api/configuration/organization/'+organizationId+'/key/' + conf.configurationKey).error(HttpErrorHandler.handle);
            },
            removeEventConfig: function(conf, eventId) {
                return $http['delete']('/admin/api/configuration/event/'+eventId+'/key/' + conf.configurationKey).error(HttpErrorHandler.handle);
            },
            loadPlugins: function(eventId) {
                return $http.get('/admin/api/configuration/events/'+eventId+'/plugin/load').error(HttpErrorHandler.handle);
            },
            bulkUpdatePlugins: function(eventId, pluginConfigOptions) {
                return $http.post('/admin/api/configuration/events/'+eventId+'/plugin/update-bulk', pluginConfigOptions).error(HttpErrorHandler.handle);
            },
            transformConfigurationObject: function(original) {
                var transformed = {};
                transformed.settings = original;
                transformed.general = {
                    settings: original['GENERAL']
                };
                transformed.mail = {
                    settings: _.filter(original['MAIL'], function(e) {return e.key !== 'MAILER_TYPE';}),
                    type: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAILER_TYPE';}),
                    maxEmailPerCycle: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAX_EMAIL_PER_CYCLE';}),
                    mailReplyTo: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAIL_REPLY_TO';})
                };
                if(angular.isDefined(original['PAYMENT']) && original['PAYMENT'].length > 0) {
                    transformed.payment = {
                        settings: original['PAYMENT']
                    };
                }
                return transformed;
            }
        };
    }

    ConfigurationService.prototype.$inject = ['$http', 'HttpErrorHandler'];

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

    ConfigurationController.prototype.$inject = ['OrganizationService', 'EventService', '$q'];

    function SystemConfigurationController(ConfigurationService, $rootScope) {
        var systemConf = this;
        systemConf.loading = true;

        var loadAll = function() {
            systemConf.loading = true;
            ConfigurationService.loadAll().then(function(result) {
                loadSettings(systemConf, result.data, ConfigurationService);
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

        systemConf.delete = function(config) {
            return ConfigurationService.remove(config);
        };

        $rootScope.$on('ReloadSettings', function() {
            loadAll();
        });
    }

    SystemConfigurationController.prototype.$inject = ['ConfigurationService', '$rootScope', '$q'];

    function OrganizationConfigurationController(ConfigurationService, OrganizationService, $stateParams, $q, $rootScope) {
        var organizationConf = this;
        organizationConf.organizationId = $stateParams.organizationId;
        organizationConf.delete = function(config) {
            return ConfigurationService.removeOrganizationConfig(config, organizationConf.organizationId);
        };
        var load = function() {
            organizationConf.loading = true;
            $q.all([OrganizationService.getOrganization(organizationConf.organizationId), ConfigurationService.loadOrganization(organizationConf.organizationId)])
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

    OrganizationConfigurationController.prototype.$inject = ['ConfigurationService', 'OrganizationService', '$stateParams', '$q', '$rootScope'];

    function EventConfigurationController(ConfigurationService, EventService, $stateParams, $q, $rootScope) {
        var eventConf = this;
        eventConf.organizationId = $stateParams.organizationId;
        eventConf.eventId = $stateParams.eventId;
        var load = function() {
            eventConf.loading = true;
            $q.all([EventService.getEventById(eventConf.eventId), ConfigurationService.loadEvent(eventConf.eventId), ConfigurationService.loadPlugins(eventConf.eventId)])
                .then(function(result) {
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
            ConfigurationService.updateEventConfig(eventConf.organizationId, eventConf.eventId, eventConf.settings).then(function() {
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

    EventConfigurationController.prototype.$inject = ['ConfigurationService', 'OrganizationService', 'EventService', '$stateParams', '$q', '$rootScope'];

    function loadSettings(container, settings, ConfigurationService) {
        container.hasResults = settings['GENERAL'].length > 0;
        container.noResults = settings['GENERAL'].length === 0;
        if(settings['GENERAL'].length > 0) {
            container.settings = settings;
            angular.extend(container, ConfigurationService.transformConfigurationObject(settings));
        }
        container.loading = false;
    }
})();