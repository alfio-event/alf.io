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
            url: '/:orgId/list',
            template: '<subscriptions-list organization-id="ctrl.orgId"></subscriptions-list>',
            controllerAs: 'ctrl',
            controller: ['$stateParams', function ($stateParams) {
                this.orgId = $stateParams.orgId;
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
    }).service('SubscriptionService', ['$http', 'HttpErrorHandler', '$q', 'NotificationHandler', SubscriptionService]);

    function ContainerCtrl($stateParams, $state, $scope) {
        var ctrl = this;

        $scope.$watch(function(){
            return $stateParams.orgId
        }, function(newVal, oldVal){
            var orgId = parseInt(newVal, 10);
            ctrl.organization = _.find(ctrl.organizations, function(org) {return org.id === orgId});
        });

        ctrl.$onInit = function() {
            if(ctrl.organizations && ctrl.organizations.length > 0) {
                var orgId = ctrl.organizations[0].id;
                if($stateParams.orgId) {
                    orgId = parseInt($stateParams.orgId, 10);
                }
                ctrl.organization = _.find(ctrl.organizations, function(org) {return org.id === orgId});
                if($state.current.name === 'subscriptions') {
                    $state.go('.list', {orgId: ctrl.organization.id});
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

    function SubscriptionService($http, HttpErrorHandler, $q, NotificationHandler) {
        return {
            loadSubscriptionsDescriptors: function(orgId) {
                return $http.get('/admin/api/organization/'+orgId+'/subscription/list').error(HttpErrorHandler.handle);
            }
        };
    }
})();