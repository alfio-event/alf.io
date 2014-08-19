(function () {
    "use strict";
    var directives = angular.module('adminDirectives', ['adminServices']);

    directives.directive("organizationsList", function() {
        return {
            scope: true,
            templateUrl: '/resources/angularTemplates/admin/partials/main/organizations.html',
            controller: function($scope, OrganizationService) {
                $scope.organizations = [];
                OrganizationService.getAllOrganizations().success(function(result) {
                    $scope.organizations = result;
                });
            },
            link: angular.noop
        };
    });
    directives.directive("usersList", function() {
        return {
            scope: true,
            templateUrl: '/resources/angularTemplates/admin/partials/main/users.html',
            controller: function($scope, UserService) {
                $scope.users = [];
                UserService.getAllUsers().success(function(result) {
                    $scope.users = result;
                });
            },
            link: angular.noop
        };
    });
})();