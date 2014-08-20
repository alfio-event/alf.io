(function () {
    "use strict";
    var directives = angular.module('adminDirectives', ['adminServices']);

    directives.directive("organizationsList", function() {
        return {
            scope: true,
            templateUrl: '/resources/angularTemplates/admin/partials/main/organizations.html',
            controller: function($scope, $rootScope, OrganizationService) {
                var loadOrganizations = function() {
                    OrganizationService.getAllOrganizations().success(function(result) {
                        $scope.organizations = result;
                    });
                };
                $rootScope.$on('ReloadOrganizations', function(e) {
                    loadOrganizations();
                });
                $scope.organizations = [];
                loadOrganizations();
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