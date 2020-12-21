(function() {
    'use strict';

    angular.module('subscriptions', ['adminServices'])
    .config(['$stateProvider', function($stateProvider) {
        $stateProvider.state('subscriptions', {
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
    }]).component('subscriptionsContainer', {
            controller: ['$stateParams', '$state', '$scope', ContainerCtrl],
            templateUrl: '../resources/js/admin/feature/subscriptions/all.html',
            bindings: { organizations: '<'}
    });




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
                    $state.go('.all', {orgId: ctrl.organization.id});
                }
            }
        };
    }
})();