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
            remove: function(conf) {
                return $http['delete']('/admin/api/configuration/key/' + conf.configurationKey).error(HttpErrorHandler.handle);
            },
            removeOrganizationConfig: function(conf, organizationId) {
                return $http['delete']('/admin/api/configuration/organization/'+organizationId+'/key/' + conf.configurationKey).error(HttpErrorHandler.handle);
            },
            loadPlugins: function() {
                return $http.get('/admin/api/configuration/plugin/load').error(HttpErrorHandler.handle);
            },
            bulkUpdatePlugins: function(pluginConfigOptions) {
                return $http.post('/admin/api/configuration/plugin/update-bulk', pluginConfigOptions).error(HttpErrorHandler.handle);
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

        var populateModel = function(result) {
            angular.extend(systemConf, ConfigurationService.transformConfigurationObject(result));
        };

        var loadAll = function() {
            systemConf.loading = true;
            ConfigurationService.loadAll().then(function(result) {
                systemConf.loading = false;
                var settings = result.data;
                systemConf.hasResults = settings['GENERAL'].length > 0;
                systemConf.noResults = settings['GENERAL'].length === 0;
                if(settings['GENERAL'].length > 0) {
                    systemConf.settings = settings;
                    populateModel(settings);
                }
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
                    var settings = result[1].data;
                    organizationConf.hasResults = settings['GENERAL'].length > 0;
                    organizationConf.noResults = settings['GENERAL'].length === 0;
                    if(settings['GENERAL'].length > 0) {
                        organizationConf.settings = settings;
                        angular.extend(organizationConf, ConfigurationService.transformConfigurationObject(settings));
                    }
                    organizationConf.loading = false;
                }, function() {
                    organizationConf.loading = false;
                });
        };
        load();
        $rootScope.$on('ReloadSettings', function() {
            load();
        });
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

    }

    OrganizationConfigurationController.prototype.$inject = ['ConfigurationService', 'OrganizationService', '$stateParams', '$q', '$rootScope'];

    function EventConfigurationController() {

    }
})();