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
    }])
    .component('subscriptionsContainer', {
        controller: ['$stateParams', '$state', '$scope', ContainerCtrl],
        templateUrl: '../resources/js/admin/feature/subscriptions/container.html',
        bindings: { organizations: '<'}
    })
    .component('subscriptionsList', {
        controller: ['SubscriptionService', SubscriptionsListCtrl],
        templateUrl: '../resources/js/admin/feature/subscriptions/list.html',
        bindings: {
            organizationId: '<'
        }
    })
    .component('subscriptionsEdit', {
        controller: ['$state', 'SubscriptionService', 'EventService', 'UtilsService', '$q', SubscriptionsEditCtrl],
        templateUrl: '../resources/js/admin/feature/subscriptions/edit.html',
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

    function SubscriptionsListCtrl(SubscriptionService) {
        var ctrl = this;

        ctrl.$onInit = function() {
            SubscriptionService.loadSubscriptionsDescriptors(ctrl.organizationId).then(function(res) {
                ctrl.subscriptions = res.data;
            });
        }

        ctrl.firstTranslation = function(obj) {
            if(!obj) {
                return '';
            }
            var keys = Object.keys(obj);
            return keys.length > 0 ? obj[keys[0]] : '';
        }
    }

    function SubscriptionsEditCtrl($state, SubscriptionService, EventService, UtilsService, $q) {
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

        ctrl.selectPreset = function(name, event) {
            if(event) {
                event.preventDefault();
            }
            ctrl.subscription = angular.extend(ctrl.subscription, presets[name]);
            ctrl.preset = name;
        }

        ctrl.$onInit = function () {
            var promises = [EventService.getSupportedLanguages(), UtilsService.getAvailableCurrencies()];
            if(ctrl.subscriptionId) {
                ctrl.existing = true;
                promises.push($q.resolve({}));
            } else {
                promises.push($q.resolve({
                    data: {
                        title: {},
                        description: {},
                        validityFromModel: {},
                        validityToModel: {},
                        onSaleFromModel: {},
                        onSaleToModel: {},
                        organizationId: ctrl.organizationId
                    }
                }));
            }
            $q.all(promises).then(function(res) {
                ctrl.languages = res[0].data;
                ctrl.currencies = res[1].data;
                ctrl.subscription = res[2].data;
                if(ctrl.subscription.validFrom) {
                    ctrl.subscription.validFromModel = SubscriptionService.dateToDateTimeObject(ctrl.subscription.validFrom);
                } else {
                    ctrl.subscription.validFromModel = SubscriptionService.dateToDateTimeObject(moment());
                }
                if(ctrl.subscription.validTo) {
                    ctrl.subscription.validToModel = SubscriptionService.dateToDateTimeObject(ctrl.subscription.validTo);
                }
                if(ctrl.existing) {
                    ctrl.selectedLanguages = Object.keys(ctrl.subscription.title).map(function(key) {
                        return _.find(ctrl.languages, function(lang) {
                            return lang.locale === key;
                        });
                    });
                } else {
                    ctrl.selectedLanguages = [ctrl.languages[0]]
                }
                refreshAvailableLanguages();
            });


        }

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
            if(ctrl.existing) {
                // edit existing subscription
                // TODO edit
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
    }

    function SubscriptionService($http, HttpErrorHandler, $q, NotificationHandler) {
        var self = {
            loadSubscriptionsDescriptors: function(organizationId) {
                return $http.get('/admin/api/organization/'+organizationId+'/subscription/list')
                    .error(HttpErrorHandler.handle);
            },
            createNew: function(subscription) {
                var payload = {
                    id: subscription.id,
                    title: subscription.title,
                    description: subscription.description,
                    maxAvailable: subscription.maxAvailable,
                    onSaleFrom: self.dateTimeObjectToDate(subscription.onSaleFromModel, subscription.onSaleFromText),
                    onSaleTo: self.dateTimeObjectToDate(subscription.onSaleToModel, subscription.onSaleToText),
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
                    validityFrom: self.dateTimeObjectToDate(subscription.validityFromModel, subscription.validityFromText),
                    validityTo: self.dateTimeObjectToDate(subscription.validityToModel, subscription.validityToText),
                    usageType: subscription.usageType,
                };

                return $http.post('/admin/api/organization/'+payload.organizationId+'/subscription/', payload)
                    .error(HttpErrorHandler.handle);
            },
            subscriptionAvailabilityTypes: {
                'ONCE_PER_EVENT': 'Once per Event',
                'UNLIMITED': 'Unlimited'
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
            dateTimeObjectToDate: function(obj, objAsString) {
                if(obj && obj.date) {
                    return moment(objAsString, 'YYYY-MM-DD HH:mm');
                }
                return null;
            }
        };
        return self;
    }
})();