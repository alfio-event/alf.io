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
                });
        }])
        .controller('ConfigurationController', ConfigurationController)
        .service('ConfigurationService', ConfigurationService);

    function ConfigurationService($http, HttpErrorHandler) {
        return {
            loadAll: function() {
                return $http.get('/admin/api/configuration/load').error(HttpErrorHandler.handle);
            },
            loadOrganizations: function() {
                return $http.get('/admin/api/configuration/organizations/load').error(HttpErrorHandler.handle);
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
            remove: function(key) {
                return $http['delete']('/admin/api/configuration/key/' + key).error(HttpErrorHandler.handle);
            },
            loadPlugins: function() {
                return $http.get('/admin/api/configuration/plugin/load').error(HttpErrorHandler.handle);
            },
            bulkUpdatePlugins: function(pluginConfigOptions) {
                return $http.post('/admin/api/configuration/plugin/update-bulk', pluginConfigOptions).error(HttpErrorHandler.handle);
            }
        };
    }

    ConfigurationService.prototype.$inject = ['$http', 'HttpErrorHandler'];

    function ConfigurationController(ConfigurationService, $rootScope, $q) {
        var configCtrl = this;
        configCtrl.loading = true;

        var populateModel = function(result) {
            configCtrl.settings = result;
            configCtrl.general = {
                settings: result['GENERAL']
            };
            configCtrl.mail = {
                settings: _.filter(result['MAIL'], function(e) {return e.key !== 'MAILER_TYPE';}),
                type: _.find(result['MAIL'], function(e) {return e.configurationKey === 'MAILER_TYPE';}),
                maxEmailPerCycle: _.find(result['MAIL'], function(e) {return e.configurationKey === 'MAX_EMAIL_PER_CYCLE';}),
                mailReplyTo: _.find(result['MAIL'], function(e) {return e.configurationKey === 'MAIL_REPLY_TO';})
            };
            if(angular.isDefined(result['PAYMENT']) && result['PAYMENT'].length > 0) {
                configCtrl.payment = {
                    settings: result['PAYMENT']
                };
            }
        };

        var populatePluginModel = function(result) {
            configCtrl.pluginSettings = result;
            configCtrl.pluginSettingsByPluginId = _.groupBy(result, 'pluginId');
        };

        var loadAll = function() {
            configCtrl.loading = true;
            $q.all([ConfigurationService.loadAll(), ConfigurationService.loadPlugins(), ConfigurationService.loadOrganizations()]).then(function(results) {
                populateModel(results[0].data);
                configCtrl.globalSettings = results[0].data;
                populatePluginModel(results[1].data);
                configCtrl.organizationsConfig = results[2].data;
                configCtrl.loading = false;
            }, function(e) {
                alert(e.data);
                configCtrl.loading = false;
            });
        };
        configCtrl.organization = undefined;
        configCtrl.viewGlobalSettings = function() {
            configCtrl.organization = undefined;
            populateModel(configCtrl.globalSettings);
        };
        configCtrl.viewOrganizationSettings = function(org, config) {
            configCtrl.organization = org;
            populateModel(config);
        };
        loadAll();

        var updateGlobalSettings = function() {
            return $q.all([ConfigurationService.bulkUpdate(configCtrl.settings), ConfigurationService.bulkUpdatePlugins(configCtrl.pluginSettings)]);
        };

        var updateOrganizationSettings = function() {
            return $q.all([ConfigurationService.updateOrganizationConfig(configCtrl.organization, configCtrl.settings), ConfigurationService.bulkUpdatePlugins(configCtrl.pluginSettings)]);
        };
        configCtrl.saveSettings = function(frm) {
            if(!frm.$valid) {
                return;
            }
            configCtrl.loading = true;
            var promise = angular.isDefined(configCtrl.organization) ? updateOrganizationSettings() : updateGlobalSettings();
            promise.then(function(results) {
                populateModel(results[0].data);
                populatePluginModel(results[1].data);
                configCtrl.loading = false;
            }, function(e) {
                alert(e.data);
                configCtrl.loading = false;
            });
        };

        configCtrl.configurationChange = function(conf) {
            if(!conf.value) {
                return;
            }
            configCtrl.loading = true;
            ConfigurationService.update(conf).success(function(result) {
                configCtrl.settings = result;
                configCtrl.loading = false;
            });
        };

        $rootScope.$on('ReloadSettings', function() {
            loadAll();
        });
    }

    ConfigurationController.prototype.$inject = ['ConfigurationService', '$rootScope', '$q'];
})();