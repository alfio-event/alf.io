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
                ctrl.subscriptionsDescriptors = res.data;
            });
        }
    }

    function SubscriptionsEditCtrl($state, SubscriptionService, EventService, UtilsService, $q) {
        var ctrl = this;
        ctrl.existing = false;

        ctrl.subscriptionAvailabilityTypes = SubscriptionService.subscriptionAvailabilityTypes;
        ctrl.vatStatuses = SubscriptionService.vatStatus;
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
                        validFromModel: {},
                        validToModel: {},
                        organizationId: ctrl.organizationId
                    }
                }));
            }
            $q.all(promises).then(function(res) {
                ctrl.languages = res[0].data;
                ctrl.currencies = res[1].data;
                ctrl.subscription = res[2].data;
                if(ctrl.subscription.validFrom) {
                    ctrl.subscription.validFromModel = createDateTimeObject(ctrl.subscription.validFrom);
                } else {
                    ctrl.subscription.validFromModel = createDateTimeObject(moment());
                }
                if(ctrl.subscription.validTo) {
                    ctrl.subscription.validToModel = createDateTimeObject(ctrl.subscription.validTo);
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

        function createDateTimeObject(srcDate) {
            if(!srcDate) {
                return null;
            }
            var m = moment(srcDate);
            return {
                date: m.format('YYYY-MM-DD'),
                time: m.format('HH:mm')
            };
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
        return {
            loadSubscriptionsDescriptors: function(organizationId) {
                return $http.get('/admin/api/organization/'+organizationId+'/subscription/list')
                    .error(HttpErrorHandler.handle);
            },
            createNew: function(subscription) {
                var payload = {
                    id: subscription.id,
                    maxEntries: subscription.maxEntries,
                    validFrom: moment(subscription.validFromText, 'YYYY-MM-DD HH:mm'),
                    price: subscription.price,
                    currency: subscription.currency,
                    vat: subscription.vat,
                    vatStatus: subscription.vatStatus,
                    availability: subscription.availability,
                    isPublic: subscription.isPublic,
                    title: subscription.title,
                    description: subscription.description,
                    organizationId: subscription.organizationId
                };

                if(subscription.validToModel) {
                    payload.validTo = moment(subscription.validToText, 'YYYY-MM-DD HH:mm');
                }

                return $http.post('/admin/api/organization/'+payload.organizationId+'/subscription/', payload)
                    .error(HttpErrorHandler.handle);
            },
            subscriptionAvailabilityTypes: {
                'ONCE_PER_EVENT': 'Once per Event',
                'UNLIMITED': 'Unlimited'
            },
            vatStatus: {
                'INCLUDED': 'Taxes are already included in the price',
                'NOT_INCLUDED': 'Taxes must be added to the price',
                'NONE': 'Do not apply taxes'
            }
        };
    }
})();