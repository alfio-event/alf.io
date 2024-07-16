(function() {
    'use strict';

    angular.module('subscriptions', ['adminServices'])
    .config(['$stateProvider', function($stateProvider) {
        $stateProvider
        .state('subscriptions', {
            url: '/subscriptions',
            template: '<subscriptions-container organizations="ctrl.organizations"></subscriptions-container>',
            controller: ['loadOrganizations', function (loadOrganizations) {
                this.organizations = loadOrganizations.data;
            }],
            controllerAs: 'ctrl',
            resolve: {
                'loadOrganizations': function(OrganizationService) {
                    return OrganizationService.getAllOrganizations();
                }
            }
        })
        .state('subscriptions.list', {
            url: '/:organizationId/list',
            template: '<subscriptions-list organization-id="ctrl.organizationId"></subscriptions-list>',
            controllerAs: 'ctrl',
            controller: ['$stateParams', function ($stateParams) {
                this.organizationId = $stateParams.organizationId;
            }]
        })
        .state('subscriptions.single', {
            url: '/:organizationId/:subscriptionId',
            template: '<single-subscription-container organization-id="ctrl.organizationId" subscription-descriptor="ctrl.subscription"></single-subscription-container>',
            controller: ['loadSubscription', '$stateParams', function(loadSubscription, $stateParams) {
                this.subscription = loadSubscription.data;
                this.organizationId = $stateParams.organizationId;
            }],
            abstract: true,
            controllerAs: 'ctrl',
            resolve: {
                'loadSubscription': function(SubscriptionService, $stateParams) {
                    return SubscriptionService.loadDescriptor($stateParams.organizationId, $stateParams.subscriptionId);
                }
            }
        })
        .state('subscriptions.single.reservationsList', {
            url: '/reservations/?search',
            template: '<reservations-list purchase-context="ctrl.subscriptionDescriptor" purchase-context-type="ctrl.purchaseContextType"></reservations-list>',
            controller: ['loadSubscription', function(loadSubscription) {
                var ctrl = this;
                ctrl.subscriptionDescriptor = loadSubscription.data;
                ctrl.purchaseContextType = 'subscription';
            }],
            controllerAs: 'ctrl'
        })
        .state('subscriptions.single.view-reservation', {
            url:'/reservation/:reservationId?fromCreation',
            template: '<reservation-view purchase-context="ctrl.subscriptionDescriptor" purchase-context-type="ctrl.purchaseContextType" reservation-descriptor="ctrl.reservationDescriptor"></reservation-view>',
            controller: function(loadSubscription, getReservationDescriptor) {
                this.subscriptionDescriptor = loadSubscription.data;
                this.purchaseContextType = 'subscription';
                this.reservationDescriptor = getReservationDescriptor.data.data;
            },
            controllerAs: 'ctrl',
            resolve: {
                'getReservationDescriptor': function(AdminReservationService, $stateParams) {
                    return AdminReservationService.load('subscription', $stateParams.subscriptionId, $stateParams.reservationId);
                }
            }
        })
        .state('subscriptions.new', {
            url: '/:organizationId/create',
            template: '<subscriptions-edit organization-id="ctrl.organizationId"></subscriptions-edit>',
            controllerAs: 'ctrl',
            controller: ['$stateParams', function ($stateParams) {
                this.organizationId = $stateParams.organizationId;
            }]
        })
        .state('subscriptions.edit', {
            url: '/:organizationId/:subscriptionId/edit',
            template: '<subscriptions-edit organization-id="ctrl.organizationId" subscription-id="ctrl.subscriptionId"></subscriptions-edit>',
            controllerAs: 'ctrl',
            controller: ['$stateParams', function ($stateParams) {
                this.organizationId = $stateParams.organizationId;
                this.subscriptionId = $stateParams.subscriptionId;
            }]
        })
        .state('subscriptions.single.additional-fields', {
            url:'/additional-fields',
            template: '<additional-fields subscription-descriptor="ctrl.subscriptionDescriptor"></additional-fields>',
            controller: function(loadSubscription) {
                this.subscriptionDescriptor = loadSubscription.data;
            },
            controllerAs: 'ctrl'
        })
    }])
    .component('subscriptionsContainer', {
        controller: ['$stateParams', '$state', '$scope', ContainerCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/subscriptions/container.html',
        bindings: { organizations: '<'}
    })
    .component('subscriptionsList', {
        controller: ['SubscriptionService', 'ConfigurationService', '$q', 'NotificationHandler', '$uibModal', SubscriptionsListCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/subscriptions/list.html',
        bindings: {
            organizationId: '<'
        }
    })
    .component('singleSubscriptionContainer', {
        controller: ['$stateParams', '$state', '$rootScope', function($stateParams, $state, $rootScope) {
            var ctrl = this;
            ctrl.backToReservationList = $state.is('subscriptions.single.view-reservation');
            var unbind = $rootScope.$on('$stateChangeSuccess', function() {
                ctrl.backToReservationList = $state.is('subscriptions.single.view-reservation');
            });
            ctrl.subscriptionId = this.subscriptionDescriptor.id;
            ctrl.$onDestroy = function() {
                unbind();
            }
        }],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/subscriptions/detail.html',
        bindings: {
            organizationId: '<',
            subscriptionDescriptor: '<'
        }
    })
    .component('subscriptionsEdit', {
        controller: ['$state', 'SubscriptionService', 'EventService', 'UtilsService', '$q', 'ImageTransformService', '$scope', 'PaymentProxyService', 'PAYMENT_PROXY_DESCRIPTIONS', 'NotificationHandler', 'ConfigurationService', 'LocationService', SubscriptionsEditCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/subscriptions/edit.html',
        bindings: {
            organizationId: '<',
            subscriptionId: '<'
        }
    })
    .service('SubscriptionService', ['$http', 'HttpErrorHandler', '$q', 'NotificationHandler', SubscriptionService]);

    function ContainerCtrl($stateParams, $state, $scope) {
        var ctrl = this;

        $scope.$watch(function(){
            return $stateParams.organizationId;
        }, function(newVal, oldVal){
            var orgId = parseInt(newVal, 10);
            ctrl.organization = _.find(ctrl.organizations, function(org) {return org.id === orgId});
        });

        ctrl.$onInit = function() {
            if(ctrl.organizations && ctrl.organizations.length > 0) {
                var orgId = ctrl.organizations[0].id;
                if($stateParams.organizationId) {
                    orgId = parseInt($stateParams.organizationId, 10);
                }
                ctrl.organization = _.find(ctrl.organizations, function(org) {return org.id === orgId});
                if($state.current.name === 'subscriptions') {
                    $state.go('.list', {organizationId: ctrl.organization.id});
                }
            }
        };
    }

    function SubscriptionsListCtrl(SubscriptionService, ConfigurationService, $q, NotificationHandler, $uibModal) {
        var ctrl = this;

        ctrl.$onInit = function() {
            $q.all([
                SubscriptionService.loadSubscriptionsDescriptors(ctrl.organizationId),
                ConfigurationService.loadSingleConfigForOrganization(ctrl.organizationId, 'BASE_URL'),
                ConfigurationService.loadSingleConfigForOrganization(ctrl.organizationId, 'GENERATE_TICKETS_FOR_SUBSCRIPTIONS'),
            ]).then(function(res) {
                ctrl.subscriptions = res[0].data;
                ctrl.baseUrl = res[1].data;
                ctrl.ticketsGenerationJobActive = res[2].data === 'true';
            });
        }

        ctrl.firstTranslation = function(obj) {
            if(!obj) {
                return '';
            }
            var keys = Object.keys(obj);
            return keys.length > 0 ? obj[keys[0]] : '';
        }

        ctrl.showLinkedEvents = function(subscription) {
            SubscriptionService.findLinkedEvents(ctrl.organizationId, subscription.id).then(function(res) {
                var links = res.data;
                if(links.length === 0) {
                    NotificationHandler.showInfo('No linked events found...');
                    return;
                }
                $uibModal.open({
                    size: 'lg',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/subscriptions/linked-events.html',
                    controllerAs: '$ctrl',
                    backdrop: 'static',
                    controller: function($scope) {
                        var ctrl = this;
                        ctrl.links = links;
                        ctrl.dismiss = function() {
                            $scope.$dismiss('cancel');
                        }
                    }
                });
            });
        }
    }

    function SubscriptionsEditCtrl($state,
                                   SubscriptionService,
                                   EventService,
                                   UtilsService,
                                   $q,
                                   ImageTransformService,
                                   $scope,
                                   PaymentProxyService,
                                   PAYMENT_PROXY_DESCRIPTIONS,
                                   NotificationHandler,
                                   ConfigurationService,
                                   LocationService) {
        var ctrl = this;
        ctrl.existing = false;

        ctrl.subscriptionAvailabilityTypes = SubscriptionService.subscriptionAvailabilityTypes;
        ctrl.vatStatuses = SubscriptionService.vatStatus;
        ctrl.validityTimeUnits = SubscriptionService.validityTimeUnits;

        ctrl.selectedTimeUnit = function() {
            return ctrl.validityTimeUnits[ctrl.subscription.validityTimeUnit];
        };

        ctrl.selectTimeUnit = function(timeUnit, event) {
            if(event) {
                event.preventDefault();
            }
            ctrl.subscription.validityTimeUnit = timeUnit;
        }

        var presets = {
            "multipleEntries": {
                validityType: 'NOT_SET',
                usageType: 'ONCE_PER_EVENT'
            },
            "period": {
                validityType: 'STANDARD',
                usageType: 'ONCE_PER_EVENT',
                validityTimeUnit: 'MONTHS',
                validityUnits: 1
            },
            "custom": {
                validityType: 'CUSTOM',
                usageType: 'ONCE_PER_EVENT'
            }
        }

        function evaluatePresetName(subscription) {
            switch(subscription.validityType) {
                case "NOT_SET":
                    return "multipleEntries";
                case "CUSTOM":
                    return "custom";
                default:
                    return "period";
            }
        }

        ctrl.selectPreset = function(name, event) {
            if(event) {
                event.preventDefault();
            }
            ctrl.subscription = angular.extend(ctrl.subscription, presets[name]);
            ctrl.preset = name;
        }

        ctrl.$onInit = function () {
            var promises = [
                EventService.getSupportedLanguages(),
                UtilsService.getAvailableCurrencies(),
                PaymentProxyService.getAllProxies(ctrl.organizationId),
                LocationService.getTimezones(),
                ConfigurationService.loadSingleConfigForOrganization(ctrl.organizationId, 'GENERATE_TICKETS_FOR_SUBSCRIPTIONS'),
                ConfigurationService.loadInstanceSettings()
            ];
            if(ctrl.subscriptionId) {
                ctrl.existing = true;
                promises.unshift(SubscriptionService.loadDescriptor(ctrl.organizationId, ctrl.subscriptionId));
            } else {
                promises.unshift($q.resolve({
                    data: {
                        title: {},
                        description: {},
                        validityFromModel: {},
                        validityToModel: {},
                        onSaleFromModel: {},
                        onSaleToModel: {},
                        organizationId: ctrl.organizationId,
                        supportsTicketsGeneration: false
                    }
                }));
            }
            $q.all(promises).then(function(res) {
                ctrl.subscription = res[0].data;
                ctrl.languages = res[1].data;
                ctrl.currencies = res[2].data;
                ctrl.paymentMethods = getPaymentMethods(res[3].data);
                ctrl.timeZones = res[4].data;
                ctrl.ticketsGenerationJobActive = res[5].data === 'true';
                ctrl.descriptionMaxLength = res[6].data.descriptionMaxLength;

                if(ctrl.existing) {
                    initExistingSubscription();
                } else {
                    ctrl.selectedLanguages = [ctrl.languages[0]]
                }
                refreshAvailableLanguages();
                refreshTimeZone(ctrl);
                if(ctrl.subscription.maxAvailable === -1) {
                    delete ctrl.subscription.maxAvailable;
                }
            });
        }

        var getPaymentMethods = function(data) {
            return _.chain(data)
                .filter(function (p) {
                    return p.status === 'ACTIVE' && p.paymentProxy !== 'ON_SITE' && p.paymentProxy !== 'OFFLINE';
                })
                .map(function (p) {
                    return {
                        id: p.paymentProxy,
                        description: PAYMENT_PROXY_DESCRIPTIONS[p.paymentProxy] || 'Unknown provider (' + p.paymentProxy + ')  Please check configuration',
                        onlyForCurrency: p.onlyForCurrency,
                        selected: _.contains(ctrl.subscription.paymentProxies, p.paymentProxy)
                    };
                })
                .uniq('description')
                .value();
        }

        var refreshTimeZone = function() {
            try {
                if(!ctrl.subscription.timeZone) {
                    ctrl.subscription.timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
                }
            } catch (e) {
                //necessary, as IE11 does not support Internationalization Apis
            }
        };

        ctrl.toggleVisibility = function() {
            SubscriptionService.toggleVisibility(ctrl.subscription).then(function(res) {
                reloadSubscription();
            });
        };

        var initExistingSubscription = function() {
            ctrl.selectedLanguages = Object.keys(ctrl.subscription.title).map(function(key) {
                return _.find(ctrl.languages, function(lang) {
                    return lang.locale === key;
                });
            });
            ctrl.preset = evaluatePresetName(ctrl.subscription);
            ctrl.previousFileBlobId = ctrl.subscription.fileBlobId;
            if(ctrl.subscription.maxAvailable === -1) {
                delete ctrl.subscription.maxAvailable;
            }
        }

        var reloadSubscription = function() {
            SubscriptionService.loadDescriptor(ctrl.organizationId, ctrl.subscription.id).then(function(res) {
                ctrl.subscription = res.data;
                initExistingSubscription();
                var onSaleFrom = SubscriptionService.dateTimeObjectToDate(ctrl.subscription.onSaleFromModel);
                if (onSaleFrom) {
                    ctrl.subscription.onSaleFromText = onSaleFrom.format('YYYY-MM-DD HH:mm');
                }
                var onSaleTo = SubscriptionService.dateTimeObjectToDate(ctrl.subscription.onSaleToModel);
                if (onSaleTo) {
                    ctrl.subscription.onSaleToText = onSaleTo.format('YYYY-MM-DD HH:mm');
                }
            });
        };

        ctrl.selectLanguage = function(language) {
            ctrl.selectedLanguages.push(language);
            refreshAvailableLanguages();
        };

        ctrl.deselectLanguage = function(language) {
            _.remove(ctrl.selectedLanguages, language);
            delete ctrl.subscription.title[language.locale];
            delete ctrl.subscription.description[language.locale];
        }

        function refreshAvailableLanguages() {
            ctrl.availableLanguages = ctrl.languages.filter(function(language) {
                return _.findIndex(ctrl.selectedLanguages, language) === -1;
            });
        }

        ctrl.save = function(form, subscription) {
            if(!form.$valid) {
                return;
            }

            // update payment methods
            ctrl.subscription.paymentProxies = ctrl.paymentMethods
                .filter(function(pm) { return pm.selected; })
                .map(function(pm) { return pm.id; });

            if(ctrl.subscription.paymentProxies.length === 0) {
                NotificationHandler.showError('Please select one or more payment methods');
                return;
            }

            if(ctrl.existing) {
                // edit existing subscription
                SubscriptionService.update(subscription).then(function(res) {
                    NotificationHandler.showSuccess('Update successful');
                    reloadSubscription();
                })
            } else {
                // insert new
                SubscriptionService.createNew(subscription).then(function(res) {
                    var id = res.data;
                    $state.go('^.edit', { organizationId: ctrl.organizationId, subscriptionId: id });
                });
            }
        }

        ctrl.cancel = function() {
            $state.go('^.list', { organizationId: ctrl.organizationId });
        };

        ctrl.removeImage = function() {
            //delete id, set base64 as undefined
            ctrl.imageBase64 = undefined;
            ctrl.subscription.fileBlobId = undefined;
        };

        ctrl.resetImage = function() {
            ctrl.subscription.fileBlobId = ctrl.previousFileBlobId;
            ctrl.imageBase64 = undefined;
        };

        $scope.$watch(function () { return ctrl.droppedFile; }, function (droppedFile) {
            if(angular.isDefined(droppedFile)) {
                if(droppedFile !== null) {
                    ImageTransformService.transformAndUploadImages([droppedFile]).then(function(result) {
                        ctrl.subscription.fileBlobId = result.fileBlobId;
                        ctrl.imageBase64 = result.imageBase64;
                    }, function(err) {
                        if(err != null) {
                            NotificationHandler.showError(err);
                        }
                    });
                }
            }
        });
    }

    function SubscriptionService($http, HttpErrorHandler, $q, NotificationHandler) {
        var self = {
            findAllReservations: function(name, page, search, status) {
                return $http.get('/admin/api/reservation/subscription/'+name+'/reservations/list', {params: {page: page, search: search, status: status}});
            },
            loadSubscriptionsDescriptors: function(organizationId) {
                if (!window.USER_IS_OWNER) {
                    return $q.reject('not authorized');
                }
                return $http.get('/admin/api/organization/'+organizationId+'/subscription/list')
                    .error(HttpErrorHandler.handle);
            },
            loadActiveSubscriptionsDescriptors: function(organizationId) {
                return $http.get('/admin/api/organization/'+organizationId+'/subscription/active')
                    .error(HttpErrorHandler.handle);
            },
            loadDescriptor: function(organizationId, subscriptionId) {
                return $http.get('/admin/api/organization/'+organizationId+'/subscription/'+subscriptionId)
                    .error(HttpErrorHandler.handle);
            },
            findLinkedEvents: function(organizationId, subscriptionId) {
                return $http.get('/admin/api/organization/'+organizationId+'/subscription/'+subscriptionId+'/events')
                    .error(HttpErrorHandler.handle);
            },
            findLinkedSubscriptions: function(organizationId, eventId) {
                return $http.get('/admin/api/organization/'+organizationId+'/subscription/for/'+eventId)
                    .error(HttpErrorHandler.handle);
            },
            createNew: function(subscription) {
                var payload = self.modelToSubscriptionPayload(subscription);
                return $http.post('/admin/api/organization/'+payload.organizationId+'/subscription', payload)
                    .error(HttpErrorHandler.handle);
            },
            update: function(subscription) {
                var payload = self.modelToSubscriptionPayload(subscription);
                return $http.post('/admin/api/organization/'+payload.organizationId+'/subscription/'+subscription.id, payload)
                    .error(HttpErrorHandler.handle);
            },
            toggleVisibility: function(subscription) {
                return $http.patch('/admin/api/organization/'+subscription.organizationId+'/subscription/'+subscription.id+'/is-public', null, {
                    params: {
                        'status': !subscription.isPublic
                    }
                }).error(HttpErrorHandler.handle);
            },
            subscriptionAvailabilityTypes: {
                'ONCE_PER_EVENT': 'Once per Event',
                'UNLIMITED': 'Multiple times per Event'
            },
            vatStatus: {
                'INCLUDED': 'Taxes included in the price',
                'NOT_INCLUDED': 'Taxes must be added to the price',
                'NONE': 'Do not apply taxes'
            },
            validityTimeUnits: {
                'DAYS': 'Days',
                'MONTHS': 'Months',
                'YEARS': 'Years'
            },
            subscriptionValidityTypes: {
                'STANDARD': '',
                'CUSTOM': '',
                'NOT_SET': ''
            },
            dateToDateTimeObject: function(srcDate) {
                if(!srcDate) {
                    return null;
                }
                var m = moment(srcDate);
                return {
                    date: m.format('YYYY-MM-DD'),
                    time: m.format('HH:mm')
                };
            },
            dateTimeObjectToDate: function(obj) {
                if(obj && obj.date) {
                    return moment(obj.date + ' ' + obj.time, 'YYYY-MM-DD HH:mm');
                }
                return null;
            },
            modelToSubscriptionPayload: function(subscription) {
                return {
                    id: subscription.id,
                    title: subscription.title,
                    description: subscription.description,
                    maxAvailable: subscription.maxAvailable,
                    onSaleFrom: self.dateTimeObjectToDate(subscription.onSaleFromModel),
                    onSaleTo: self.dateTimeObjectToDate(subscription.onSaleToModel),
                    price: subscription.price,
                    vat: subscription.vat,
                    currency: subscription.currency,
                    vatStatus: subscription.vatStatus,
                    isPublic: subscription.isPublic,
                    organizationId: subscription.organizationId,

                    maxEntries: subscription.maxEntries,
                    validityType: subscription.validityType,
                    validityTimeUnit: subscription.validityTimeUnit,
                    validityUnits: subscription.validityUnits,
                    validityFrom: self.dateTimeObjectToDate(subscription.validityFromModel),
                    validityTo: self.dateTimeObjectToDate(subscription.validityToModel),
                    usageType: subscription.usageType,

                    termsAndConditionsUrl: subscription.termsAndConditionsUrl,
                    privacyPolicyUrl: subscription.privacyPolicyUrl,
                    fileBlobId: subscription.fileBlobId,
                    paymentProxies: subscription.paymentProxies,
                    timeZone: subscription.timeZone,
                    supportsTicketsGeneration: subscription.supportsTicketsGeneration
                };
            }
        };
        return self;
    }
})();