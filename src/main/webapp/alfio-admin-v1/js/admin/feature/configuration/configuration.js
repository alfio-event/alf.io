(function() {
    "use strict";
    angular.module('alfio-configuration', ['adminServices', 'group'])
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
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/configuration/system.html',
                    controller: 'SystemConfigurationController',
                    controllerAs: 'systemConf'
                })
                .state('configuration.organization', {
                    url: '/organization/:organizationId',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/configuration/organization.html',
                    controller: 'OrganizationConfigurationController',
                    controllerAs: 'organizationConf'
                })
                .state('configuration.event', {
                    url: '/organization/:organizationId/event/:eventId',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/configuration/event.html',
                    controller: 'EventConfigurationController',
                    controllerAs: 'eventConf'
                })
                .state('events.single.configuration', {
                    url: '/configuration',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/configuration/event.html',
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
        .directive('basicConfigurationNeeded', basicConfigurationNeeded)
        .directive('paymentMethodBlacklist', paymentMethodBlacklist)
        .filter('mollieConnect', function() {
            return function(options, connected) {
                return options.filter(function(o) {
                    var index = o.key.indexOf('_CONNECT');
                    return connected ? index > -1 : index === -1;
                })
            }
        }).component('regenerateInvoices', {
            controller: ['$scope', 'ConfigurationService', 'NotificationHandler', RegenerateInvoicesController],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/configuration/regenerate-invoices.html',
            bindings: {
                event: '<'
            }
        });

    function ConfigurationService($http, HttpErrorHandler, $q, $timeout, $window) {
        var configurationCache = null;
        var service = {
            loadSettingCategories: function() {
                return $http.get('/admin/api/configuration/setting-categories').error(HttpErrorHandler.handle);
            },
            loadCurrentConfigurationContext: function(OrganizationService, EventService) {
                if(configurationCache == null) {
                    configurationCache = $q(function(resolve, reject) {
                        $q.all([OrganizationService.getAllOrganizations(), EventService.getAllEvents()]).then(function(results) {
                            var organizations = results[0].data;
                            var events = results[1].data;
                            resolve({
                                organizations: _.map(organizations, function(org) {
                                    org.events = _.filter(events, function(e) {return !e.expired && e.organizationId === org.id});
                                    return org;
                                })
                            });
                        }, function(e) {
                            reject(e);
                        });
                    })
                }
                $timeout(function() {
                    configurationCache = null;
                }, 10000);
                return configurationCache;

            },
            loadAll: function() {
                return $http.get('/admin/api/configuration/load').error(function(body, status) {
                    if (status !== 403) {
                        HttpErrorHandler.handle(body, status);
                    }
                });
            },
            loadOrganizationConfig: function(organizationId) {
                return $http.get('/admin/api/configuration/organizations/'+organizationId+'/load').error(HttpErrorHandler.handle);
            },
            loadEventConfig: function(eventId) {
                return $http.get('/admin/api/configuration/events/'+eventId+'/load').error(HttpErrorHandler.handle)
            },
            loadTranslationsOverrideForEvent: function(eventId) {
                return $http.get('/admin/api/configuration/events/'+eventId+'/translations-override').error(HttpErrorHandler.handle)
            },
            loadTranslationsOverrideForOrganization: function(orgId) {
                return $http.get('/admin/api/configuration/organizations/'+orgId+'/translations-override').error(HttpErrorHandler.handle)
            },
            loadSystemTranslationsOverride: function() {
                return $http.get('/admin/api/configuration/global-translations-override').error(HttpErrorHandler.handle)
            },
            loadSingleConfigForEvent: function(eventName, key) {
                if (!window.USER_IS_OWNER) {
                    return $q.reject('not authorized');
                }
                return $http.get('/admin/api/configuration/events/'+eventName+'/single/'+key).error(HttpErrorHandler.handle)
            },
            loadSingleConfigForOrganization: function(organizationId, key) {
                if (!window.USER_IS_OWNER) {
                    return $q.reject('not authorized');
                }
                return $http.get('/admin/api/configuration/organizations/'+organizationId+'/single/'+key).error(HttpErrorHandler.handle)
            },
            loadInstanceSettings: function() {
                return $http.get('/admin/api/configuration/instance-settings').error(HttpErrorHandler.handle)
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
            transformConfigurationObject: function(original, availableCategories) {
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
                        mailAttemptsCount: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAIL_ATTEMPTS_COUNT';}),
                        enableHtmlEmails: _.find(original['MAIL'], function(e) {return e.configurationKey === 'ENABLE_HTML_EMAILS';}),
                        mailFooter: _.find(original['MAIL'], function(e) {return e.configurationKey === 'MAIL_FOOTER';})
                    };
                }

                if(angular.isDefined(original['MAP']) && original['MAP'].length > 0) {
                    transformed.map = {
                        MAPS_PROVIDER: _.find(original['MAP'], function(e) {return e.key === 'MAPS_PROVIDER';}),
                        MAPS_CLIENT_API_KEY: _.find(original['MAP'], function(e) {return e.key === 'MAPS_CLIENT_API_KEY';}),
                        MAPS_HERE_API_KEY: _.find(original['MAP'], function(e) {return e.key === 'MAPS_HERE_API_KEY';})
                    }
                }

                if(angular.isDefined(original['PAYMENT_OFFLINE']) && original['PAYMENT_OFFLINE'].length > 0) {
                    var offlineCfg = service.sortConfigOptions(original['PAYMENT_OFFLINE']);
                    var deferredOfflineConfig = service.findSingleConfig(offlineCfg, 'DEFERRED_BANK_TRANSFER_ENABLED');
                    transformed.paymentOffline = {
                        enabled: service.findSingleConfig(offlineCfg, 'BANK_TRANSFER_ENABLED'),
                        generalSettings: offlineCfg.filter(function (s) {
                            return !s.key.startsWith('REVOLUT')
                                && !s.key.startsWith('DEFERRED_BANK_TRANSFER')
                                && s.key !== 'BANK_TRANSFER_ENABLED';
                        }),
                        deferredSetting: deferredOfflineConfig,
                        deferredConfigOptions: offlineCfg.filter(function(s) {
                            return s.key === 'DEFERRED_BANK_TRANSFER_SEND_CONFIRMATION_EMAIL';
                        }),
                        revolutSettings: offlineCfg.filter(function (s) {
                            return s.key.startsWith('REVOLUT');
                        })
                    }
                }

                var filterList = ['GENERAL', 'MAIL', 'MAP', 'PAYMENT_OFFLINE'];
                _.forEach(availableCategories.filter(function(x) { return filterList.indexOf(x) === -1; }), function(group) {
                    if(angular.isDefined(original[group]) && original[group].length > 0) {
                        transformed[_.camelCase(group)] = {
                            settings: service.sortConfigOptions(original[group])
                        };
                    }
                });
                return transformed;
            },
            findSingleConfig: function(configs, key) {
                var filtered = configs.filter(function(cfg) {
                    return cfg.key === key;
                });
                return filtered.length > 0 ? filtered[0] : null;
            },
            /**
             * sort options by type. Boolean options take precedence over text-based options.
             * @param options
             * @returns {*}
             */
            sortConfigOptions: function(options) {
                return _.sortBy(options, function(s) {
                    return s.componentType === 'BOOLEAN' ? 0 : 10;
                })
            },
            findMatchingInvoiceIds: function(event, from, to) {
                return $http.get('/admin/api/configuration/event/'+event.id+'/matching-invoices?from='+from+'&to='+to).error(HttpErrorHandler.handle);
            },
            regenerateInvoices: function(event, ids) {
                return $http.post('/admin/api/configuration/event/'+event.id+'/regenerate-invoices', ids).error(HttpErrorHandler.handle);
            },
            loadFirstInvoiceDate: function(event) {
                return $http.get('/admin/api/configuration/event/'+event.id+'/invoice-first-date').error(HttpErrorHandler.handle);
            },
            generateTicketsForSubscribers: function(organizationId, eventId) {
                return $http.put('/admin/api/configuration/generate-tickets-for-subscriptions', {}, {
                    params: {
                        eventId,
                        organizationId
                    }
                })
            }
        };
        return service;
    }

    ConfigurationService.$inject = ['$http', 'HttpErrorHandler', '$q', '$timeout', '$window'];

    function ConfigurationController(OrganizationService, EventService, $q, ConfigurationService) {
        var configCtrl = this;
        configCtrl.loading = true;
        ConfigurationService.loadCurrentConfigurationContext(OrganizationService, EventService).then(function(res) {
            configCtrl.organizations = res.organizations;
            configCtrl.loading = false;
        }, function(e) {
            alert(e);
            configCtrl.loading = false;
        });
    }


    function handleEuCountries(conf, euCountries) {
        if(conf.invoiceEu) {
            var eu = _.map(euCountries, function(o) {
                var key = Object.keys(o)[0];
                return {key: key, value: o[key]};
            });
            _.forEach(_.filter(conf.invoiceEu.settings, function(e) {return e.key === 'COUNTRY_OF_BUSINESS'}), function(cb) {
                cb.listValues = eu;
            });
        }
    }

    ConfigurationController.$inject = ['OrganizationService', 'EventService', '$q', 'ConfigurationService'];

    function SystemConfigurationController(ConfigurationService, EventService, ExtensionService, NotificationHandler, $rootScope, $q) {
        var systemConf = this;
        systemConf.loading = true;

        systemConf.keys = Object.keys;

        var loadAll = function() {
            systemConf.loading = true;
            $q.all([
                EventService.getAllLanguages(),
                ConfigurationService.loadAll(),
                ConfigurationService.loadEUCountries(),
                ExtensionService.loadSystem(),
                ConfigurationService.loadSettingCategories()]).then(function(results) {
                systemConf.allLanguages = results[0].data;
                var settingCategories = results[4].data;
                systemConf.settingCategories = settingCategories;
                loadSettings(systemConf, results[1].data, ConfigurationService, settingCategories);
                handleEuCountries(systemConf, results[2].data);
                if(systemConf.alfioPi) {
                    systemConf.alfioPiOptions = _.filter(systemConf.alfioPi.settings, function(pi) { return pi.key !== 'LABEL_LAYOUT' && pi.key !== 'CHECK_IN_COLOR_CONFIGURATION'});
                }

                systemConf.extensionSettings = results[3].data;
                systemConf.cancel = function() {
                    loadAll();
                };

            }, function() {
                systemConf.loading = false;
                if (!systemConf.hasResults) {
                    systemConf.noResults = true;
                }
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

        systemConf.generateTicketsForSubscribers = function() {
            ConfigurationService.generateTicketsForSubscribers().then(function() {
                NotificationHandler.showSuccess("Generation has been scheduled. Will run in 1 minute");
            }, function() {
                NotificationHandler.showError("Error while scheduling generation");
            })
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
                ExtensionService.loadOrganizationConfigWithOrgId(organizationConf.organizationId),
                ConfigurationService.loadSettingCategories()
            ]).then(function(result) {
                    organizationConf.organization = result[0].data;
                    loadSettings(organizationConf, result[1].data, ConfigurationService, result[5].data);
                    handleEuCountries(organizationConf, result[2].data);
                    var platformModeStatus = result[3].data;
                    organizationConf.platformModeEnabled = platformModeStatus.enabled;
                    organizationConf.stripeConnected = platformModeStatus.stripeConnected;
                    organizationConf.mollieConnected = platformModeStatus.mollieConnected;
                    organizationConf.extensionSettings = result[4].data;
                    organizationConf.cancel = function() {
                        load();
                    };
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

        organizationConf.generateTicketsForSubscribers = function() {
            ConfigurationService.generateTicketsForSubscribers(organizationConf.organizationId).then(function() {
                NotificationHandler.showSuccess("Generation has been scheduled. Will run in 1 minute");
            }, function() {
                NotificationHandler.showError("Error while scheduling generation");
            })
        };

        organizationConf.deleteExtensionSetting = function(config) {
            return ExtensionService.deleteOrganizationSettingValue(organizationConf.organizationId, config);
        };

        $rootScope.$on('ReloadSettings', function() {
            load();
        });
    }

    OrganizationConfigurationController.$inject = ['ConfigurationService', 'OrganizationService', 'ExtensionService', 'NotificationHandler', '$stateParams', '$q', '$rootScope'];

    var groupTypes = {
        'ONCE_PER_VALUE': 'Limit to one ticket per email address',
        'LIMITED_QUANTITY': 'Limit to a specific number of tickets per email address',
        'UNLIMITED': 'Unlimited'
    };

    var groupMatchTypes = {
        'FULL': 'Full match',
        'EMAIL_DOMAIN': 'Match full email address, fallback on domain'
    };

    function selectGroup(conf) {
        return function(list) {
            conf.group = {
                groupId: list.id,
                eventId: conf.event.id,
                ticketCategoryId: conf.category ? conf.category.id : null,
                matchType: 'FULL',
                type: 'ONCE_PER_VALUE'
            };
        }
    }

    function EventConfigurationController(ConfigurationService,
                                          EventService,
                                          ExtensionService,
                                          NotificationHandler,
                                          $q,
                                          $rootScope,
                                          $stateParams,
                                          GroupService,
                                          $state,
                                          $uibModal) {
        var eventConf = this;
        var getData = function() {
            if(angular.isDefined($stateParams.eventName)) {
                var deferred = $q.defer();
                EventService.getEvent($stateParams.eventName).then(function(result) {
                    eventConf.organizationId = result.data.organization.id;
                    var event = result.data.event;
                    eventConf.eventName = event.shortName;
                    eventConf.eventId = event.id;
                    $q.all([
                        ConfigurationService.loadEventConfig(eventConf.eventId),
                        ExtensionService.loadEventConfigWithOrgIdAndEventId(eventConf.organizationId, eventConf.eventId),
                        ConfigurationService.loadSettingCategories()
                    ]).then(function(result) {
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
                return $q.all([
                    EventService.getEventById($stateParams.eventId),
                    ConfigurationService.loadEventConfig($stateParams.eventId),
                    ExtensionService.loadEventConfigWithOrgIdAndEventId(eventConf.organizationId, eventConf.eventId),
                    ConfigurationService.loadSettingCategories()
                ])
            }
        };

        var load = function() {
            eventConf.loading = true;
            getData().then(function(result) {
                    eventConf.event = result[0].data;
                    loadGroups();
                    loadSettings(eventConf, result[1].data, ConfigurationService, result[3].data);


                    if(eventConf.alfioPi) {
                        eventConf.alfioPiOptions = _.filter(eventConf.alfioPi.settings, function(pi) { return pi.key !== 'LABEL_LAYOUT' && pi.key !== 'CHECK_IN_COLOR_CONFIGURATION'});
                        eventConf.labelLayout = _.find(eventConf.alfioPi.settings, function(pi) { return pi.key === 'LABEL_LAYOUT'});
                        eventConf.colorConfiguration = _.find(eventConf.alfioPi.settings, function(pi) { return pi.key === 'CHECK_IN_COLOR_CONFIGURATION'});
                    }
                    eventConf.extensionSettings = result[2].data;
                    eventConf.cancel = function() {
                        if(eventConf.eventName) {
                            $state.go('events.single.detail', {eventName: eventConf.eventName});
                        } else {
                            load();
                        }
                    };
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
                ExtensionService.saveBulkEventSetting(eventConf.organizationId, eventConf.eventId, eventConf.extensionSettings),
                GroupService.linkTo(eventConf.group)
            ]).then(function() {
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

        eventConf.generateTicketsForSubscribers = function() {
            ConfigurationService.generateTicketsForSubscribers(eventConf.organizationId, eventConf.eventId).then(function() {
                NotificationHandler.showSuccess("Generation has been scheduled. Will run in 1 minute");
            }, function() {
                NotificationHandler.showError("Error while scheduling generation");
            })
        };

        eventConf.deleteExtensionSetting = function(config) {
            return ExtensionService.deleteEventSettingValue(eventConf.organizationId, eventConf.eventId, config);
        };

        $rootScope.$on('ReloadSettings', function() {
            load();
        });

        function loadGroups() {
            $q.all([
                GroupService.loadGroups(eventConf.event.organizationId),
                GroupService.loadActiveGroup(eventConf.event.shortName)
            ]).then(function(results) {
                eventConf.groups = results[0].data;
                eventConf.group = results[1].status === 200 ? results[1].data : null;
                eventConf.selectGroup = selectGroup(eventConf);
                eventConf.groupTypes = groupTypes;
                eventConf.groupMatchTypes = groupMatchTypes;
                eventConf.removeGroupLink = unlinkGroup(eventConf, GroupService, load);
            });
        }
    }

    EventConfigurationController.$inject = ['ConfigurationService', 'EventService', 'ExtensionService', 'NotificationHandler', '$q', '$rootScope', '$stateParams', 'GroupService', '$state', '$uibModal'];

    function RegenerateInvoicesController($scope, ConfigurationService, NotificationHandler) {
        var ctrl = this;

        var previewSearch = function() {
            if(moment(ctrl.searchTo).isAfter(moment(ctrl.searchFrom))) {
                ConfigurationService.findMatchingInvoiceIds(ctrl.event, moment(ctrl.searchFrom).format('x'), moment(ctrl.searchTo).format('x')).then(function(resp) {
                    ctrl.matchingInvoices = resp.data;
                    ctrl.showMatching = resp.data.length > 0;
                    ctrl.showError = resp.data.length === 0;
                    ctrl.errorMessage = resp.data.length === 0 ? 'No matching invoices found in the given period' : null;
                });
            } else {
                ctrl.showMatching = false;
                ctrl.showError = true;
                ctrl.errorMessage = 'Start date cannot be after end date';
                ctrl.matchingInvoices = [];
            }
        };
        ctrl.submitRequest = function() {
            ConfigurationService.regenerateInvoices(ctrl.event, ctrl.matchingInvoices)
                .then(function(result) {
                    var success = result.data;
                    if(success) {
                        NotificationHandler.showSuccess('Request has been scheduled for execution. You will receive an email once the process is complete');
                    } else {
                        NotificationHandler.showError('Unable to schedule request for execution. Please try again in a few minutes.');
                    }
                }, function() {
                    NotificationHandler.showError('Unable to schedule request for execution. Please try again in a few minutes.');
                });
        };

        ctrl.$onInit = function() {
            ctrl.loading = true;
            ConfigurationService.loadFirstInvoiceDate(ctrl.event).then(function(res) {
                ctrl.minDate = moment(res.data).subtract(1, 'day').startOf('day');
                ctrl.maxDate = moment().endOf('day');
                ctrl.searchFrom = null;
                ctrl.searchTo = null;
                ctrl.showError = false;
                ctrl.errorMessage = null;
                ctrl.showMatching = false;
                ctrl.loadingMatching = false;
                ctrl.matchingInvoices = [];
                ctrl.loading = false;

                $scope.$watch('$ctrl.searchFrom', function(newValue) {
                    if(newValue && ctrl.searchTo) {
                        previewSearch();
                    }
                });
                $scope.$watch('$ctrl.searchTo', function(newValue) {
                    if(newValue && ctrl.searchFrom) {
                        previewSearch();
                    }
                });
            });
        };
    }

    function unlinkGroup(conf, GroupService, loadFn) {
        return function(organizationId, groupLink) {
            if(groupLink && angular.isDefined(groupLink.id)) {
                GroupService.unlinkFrom(organizationId, groupLink.id, conf).then(function() {
                    loadFn();
                });
            } else {
                conf.group = undefined;
            }
        };
    }

    function loadSettings(container, settings, ConfigurationService, settingCategories) {
        var general = settings['GENERAL'] || [];
        if(general.length > 0) {
            container.settings = settings;
            angular.extend(container, ConfigurationService.transformConfigurationObject(settings, settingCategories));
        }
        container.hasResults = general.length > 0;
        container.noResults = general.length === 0;
        container.loading = false;
    }

    function ticketCategoryConfiguration() {
        return {
            restrict: 'E',
            scope: {
                event: '=',
                category: '=',
                closeModal: '&',
                onSave: '&'
            },
            bindToController: true,
            controller: ['ConfigurationService', '$rootScope', 'GroupService', '$q', CategoryConfigurationController],
            controllerAs: 'categoryConf',
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/configuration/category.html'
        };
    }

    function CategoryConfigurationController(ConfigurationService, $rootScope, GroupService, $q) {
        var categoryConf = this;

        var load = function() {
            categoryConf.loading = true;
            $q.all([ConfigurationService.loadCategory(categoryConf.event.id, categoryConf.category.id),
                GroupService.loadGroups(categoryConf.event.organizationId),
                GroupService.loadActiveGroup(categoryConf.event.shortName, categoryConf.category.id),
                ConfigurationService.loadSettingCategories()])
                .then(function(results) {
                    loadSettings(categoryConf, results[0].data, ConfigurationService, results[3].data);
                    categoryConf.groups = results[1].data;
                    categoryConf.group = results[2].status === 200 ? results[2].data : null;
                    categoryConf.removeGroupLink = unlinkGroup(categoryConf, GroupService, load);
                    categoryConf.loading = false;
                }, function() {
                    categoryConf.loading = false;
                });
        };
        load();

        categoryConf.selectGroup = selectGroup(categoryConf);
        categoryConf.groupTypes = groupTypes;
        categoryConf.groupMatchTypes = groupMatchTypes;


        categoryConf.saveSettings = function(frm) {
            if(!frm.$valid) {
                return;
            }
            categoryConf.loading = true;
            var onSaveComplete = categoryConf.onSave ? categoryConf.onSave : load;

            ConfigurationService.updateCategoryConfig(categoryConf.category.id, categoryConf.event.id, categoryConf.settings).then(function() {
                if(categoryConf.group) {
                    GroupService.linkTo(categoryConf.group).then(function() {
                        onSaveComplete();
                    });
                } else {
                    onSaveComplete();
                }
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
    CategoryConfigurationController.$inject = ['ConfigurationService', '$rootScope', 'GroupService', '$q'];

    function basicConfigurationNeeded($uibModal, ConfigurationService, EventService, $q, $window) {
        return {
            restrict: 'A',
            scope: true,
            link: function() {
                var m = $uibModal.open({
                    size:'lg',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/configuration/basic-settings.html',
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
                                MAPS_HERE_API_KEY: _.find(settings['MAP'], function(e) {return e.key === 'MAPS_HERE_API_KEY';})
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

    function paymentMethodBlacklist() {
        return {
            scope: {
                currentSelection: '='
            },
            bindToController: true,
            controllerAs: '$ctrl',
            controller: ['PAYMENT_PROXY_DESCRIPTIONS', function (paymentMethods) {
                var ctrl = this;
                ctrl.isOptionSelected = function(key) {
                    return ctrl.currentSelection != null && ctrl.currentSelection.indexOf(key) > -1;
                };
                ctrl.toggleSelected = function(key) {
                    if(ctrl.isOptionSelected(key)) {
                        ctrl.currentSelection = ctrl.currentSelection.split(',').filter(function(k) { return k !== key}).join(',');
                    } else {
                        var array = ctrl.currentSelection ? ctrl.currentSelection.split(',') : [];
                        array.push(key);
                        ctrl.currentSelection = array.join(',');
                    }
                };
                ctrl.paymentMethods = paymentMethods;
            }],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/configuration/payment-method-blacklist.html'
        }
    }
})();