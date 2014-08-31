(function () {
    "use strict";

    var BASE_TEMPLATE_URL = "/admin/partials";
    var BASE_STATIC_URL = "/resources/angularTemplates/admin/partials";

    var admin = angular.module('adminApplication', ['ui.bootstrap', 'ui.router', 'adminDirectives', 'adminServices', 'xeditable', 'utilFilters']);

    admin.run(function(editableOptions, editableThemes) {
        editableThemes.bs3.submitTpl = '<button type="submit" class="btn btn-primary"><span class="fa fa-check"></span></button>';
        editableThemes.bs3.cancelTpl = '<button type="button" class="btn btn-default" ng-click="$form.$cancel()">'+
            '<span class="fa fa-times"></span>'+
            '</button>';
        editableOptions.theme = 'bs3';
    });

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
                        templateUrl: BASE_STATIC_URL + "/main/edit-organization.html",
                        controller: 'InsertNewOrganizationController'
                    }
                }
            })
            .state('index.new-user', {
                url: "new-user",
                views: {
                    "newUser": {
                        templateUrl: BASE_STATIC_URL + "/main/edit-user.html",
                        controller: 'InsertNewUserController'
                    }
                }
            })
    });

    var validationResultHandler = function(form, deferred) {
        return function(validationResult) {
            if(validationResult.errorCount > 0) {
                angular.forEach(validationResult.validationErrors, function(error) {
                    form.$setError(error.fieldName, error.message);
                });
                deferred.resolve("invalid form");
            }
            deferred.resolve();
        };
    };

    var validationPerformer = function($q, validator, data, form) {
        var deferred = $q.defer();
        validator(data).success(validationResultHandler(form, deferred)).error(function(error) {
            deferred.reject(error);
        });
        return deferred.promise;
    };

    admin.controller('InsertNewOrganizationController', function($scope, $state, $rootScope, $q, OrganizationService) {
        $scope.organization = {};
        $scope.save = function(organization) {
            OrganizationService.createOrganization(organization).success(function() {
                $rootScope.$emit('ReloadOrganizations', {});
                $state.go("index");
            });
        };
        $scope.$watch('insertNewOrganization', function(form) {
            if(!form.$visible) {
                form.$show();
            }
        });
        $scope.cancel = function() {
            $state.go("index");
        };

        $scope.check = function(data, form) {
            return validationPerformer($q, OrganizationService.checkOrganization, data, form);
        };
    });

    admin.controller('InsertNewUserController', function($scope, $state, $rootScope, $q, OrganizationService, UserService) {
        $scope.user = {};
        $scope.organizations = {};
        $scope.$watch('editUser', function(form) {
            if(!form.$visible) {
                form.$show();
            }
        });
        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
        });

        $scope.save = function(user) {
            UserService.createUser(user).success(function() {
                $rootScope.$emit('ReloadUsers', {});
                $state.go("index");
            });
        };

        $scope.cancel = function() {
            $state.go("index");
        };

        $scope.check = function(data, form) {
            return validationPerformer($q, UserService.checkUser, data, form);
        };
    });

    admin.controller('MessageBarController', function($scope, $rootScope) {
        $rootScope.$on('Message', function(m) {
            $scope.message = m;
        });
    });

})();