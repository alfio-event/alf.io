(function () {
    "use strict";
    var directives = angular.module('adminDirectives', ['ui.bootstrap', 'adminServices']);

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
            controller: function($scope, $rootScope, UserService) {
                $scope.users = [];
                var loadUsers = function() {
                    UserService.getAllUsers().success(function(result) {
                        $scope.users = result;
                    });
                };
                $rootScope.$on('ReloadUsers', function() {
                    loadUsers();
                });
                loadUsers();
            },
            link: angular.noop
        };
    });

    directives.directive('eventsList', function() {
        return {
            scope: true,
            templateUrl: '/resources/angularTemplates/admin/partials/main/events.html',
            controller: function($scope, EventService) {
                EventService.getAllEvents().success(function(data) {
                    $scope.events = data;
                });
            },
            link: angular.noop
        };
    });

    directives.directive('dateTime', function() {
        return {
            templateUrl: '/resources/angularTemplates/admin/partials/form/dateTime.html',
            scope: {
                modelObject: '=',
                prefix: '@',
                idPrefix: '@',
                rowClass: '@',
                dateCellClass: '@',
                timeCellClass: '@'
            },
            link: function(scope, element, attrs) {
                scope.tomorrow = moment().add(1, 'days').toDate();
                //scope.opened = false;
                scope.dateOptions = {
                    formatYear: 'yy',
                    startingDay: 1
                };
                scope.open = function($event) {
                    $event.preventDefault();
                    $event.stopPropagation();
                    scope.opened = true;
                }
            }
        }
    });

    directives.directive('grabFocus', function() {
        return {
            restrict: 'A',
            link: function(scope, element, attrs) {
                element.focus();
            }
        };
    });

    directives.directive('controlButtons', function() {
        return {
            restrict: 'E',
            templateUrl: '/resources/angularTemplates/admin/partials/form/controlButtons.html',
            scope: {
                formObj: '=',
                cancelHandler: '='
            },
            link: function(scope, element, attrs) {
                scope.cancel = function() {
                    if(angular.isFunction(scope.cancelHandler)) {
                        scope.cancelHandler();
                    } else if(angular.isFunction(scope.$parent.cancel)) {
                        scope.$parent.cancel();
                    }
                }
            }
        };
    });
})();