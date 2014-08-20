(function () {
    "use strict";

    var BASE_TEMPLATE_URL = "/admin/partials";
    var BASE_STATIC_URL = "/resources/angularTemplates/admin/partials";

    var admin = angular.module('adminApplication', ['ui.bootstrap', 'ui.router', 'adminDirectives', 'adminServices']);

    admin.config(function($stateProvider, $urlRouterProvider) {
        $urlRouterProvider.otherwise("/");
        $stateProvider
            .state('index', {
                url: "/",
                templateUrl: BASE_TEMPLATE_URL + "/index.html"
            })
            .state('index.new-organization', {
                url: "new-organization",
                views: {
                    "newOrganization": {
                        templateUrl: BASE_STATIC_URL + "/main/new-organization.html",
                        controller: 'InsertNewOrganizationController'
                    }
                }
            })
            .state('index.new-user', {
                url: "new-user",
                views: {
                    "newUser": {
                        templateUrl: BASE_STATIC_URL + "/main/new-user.html",
                        controller: 'InsertNewUserController'
                    }
                }
            })
    });

    admin.controller('InsertNewOrganizationController', function($scope, $state, $rootScope, OrganizationService) {
        $scope.organization = {};
        $scope.save = function(organization) {
            OrganizationService.insertOrganization(organization).success(function() {
                $rootScope.$emit('ReloadOrganizations', {});
                $state.go("index");
            });
        };
    });

    admin.controller('InsertNewUserController', function($scope, OrganizationService) {
        $scope.user = {};
        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
        });
        $scope.save = function(user) {
            alert('aa');
        };
    });

    admin.controller('MessageBarController', function($scope, $rootScope) {
        $rootScope.$on('Message', function(m) {
            $scope.message = m;
        });
    });

})();