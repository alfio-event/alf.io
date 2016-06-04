(function () {
    "use strict";
    angular.module('alfio-users', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider
                .state('index.new-organization', {
                    url: "organizations/new",
                    views: {
                        "newOrganization": {
                            templateUrl: "/resources/angular-templates/admin/partials/main/edit-organization.html",
                            controller: 'EditOrganizationController',
                            controllerAs: 'editOrg'
                        }
                    }
                })
                .state('index.edit-organization', {
                    url: "organizations/:organizationId/edit",
                    views: {
                        "newOrganization": {
                            templateUrl: "/resources/angular-templates/admin/partials/main/edit-organization.html",
                            controller: 'EditOrganizationController',
                            controllerAs: 'editOrg'
                        }
                    }
                })
                .state('index.new-user', {
                    url: "users/new",
                    views: {
                        "editUser": {
                            templateUrl: "/resources/angular-templates/admin/partials/main/edit-user.html",
                            controller: 'EditUserController',
                            controllerAs: 'editUserCtrl'
                        }
                    }
                })
                .state('index.edit-user', {
                    url: "users/:userId/edit",
                    views: {
                        "editUser": {
                            templateUrl: "/resources/angular-templates/admin/partials/main/edit-user.html",
                            controller: 'EditUserController',
                            controllerAs: 'editUserCtrl'
                        }
                    }
                })
                .state('edit-current-user', {
                    url: "/profile/edit",
                    templateUrl: "/resources/angular-templates/admin/partials/main/edit-current-user.html",
                    controller: 'EditCurrentUserController',
                    controllerAs: 'editCurrentUserCtrl',
                    resolve: {
                        user: ['UserService', function (UserService) {
                            return UserService.loadCurrentUser().then(function(resp) {
                                return resp.data;
                            });
                        }]
                    }
                })
        }])
        .controller('EditOrganizationController', EditOrganizationController)
        .controller('EditUserController', EditUserController)
        .controller('EditCurrentUserController', EditCurrentUserController)
        .controller('OrganizationListController', OrganizationListController)
        .controller('UsersListController', UsersListController)
        .service('OrganizationService', OrganizationService)
        .service('UserService', UserService)
        .directive("organizationsList", organizationList)
        .directive("usersList", usersList);

    function EditOrganizationController($state, $stateParams, $rootScope, $q, OrganizationService, ValidationService) {

        var self = this;

        var updateOrAction = OrganizationService.createOrganization;

        if(angular.isDefined($stateParams.organizationId)) {
            updateOrAction = OrganizationService.updateOrganization;
            OrganizationService.getOrganization($stateParams.organizationId).success(function(result) {
                self.organization = result;
            });
        }
        self.organization = {};
        self.save = function(form, organization) {
            if(!form.$valid) {
                return;
            }
            ValidationService.validationPerformer($q, OrganizationService.checkOrganization, organization, form).then(function() {
                updateOrAction(organization).success(function() {
                    $rootScope.$emit('ReloadOrganizations', {});
                    $state.go('index');
                });
            }, angular.noop);
        };
        self.cancel = function() {
            $state.go('index');
        };
    }

    EditOrganizationController.$inject = ['$state', '$stateParams', '$rootScope', '$q', 'OrganizationService', 'ValidationService'];

    function EditUserController($state, $stateParams, $rootScope, $q, OrganizationService, UserService, ValidationService) {
        var self = this;

        if(angular.isDefined($stateParams.userId)) {
            UserService.loadUser($stateParams.userId).success(function(result) {
                self.user = result;
            });
        }
        self.user = {};
        self.organizations = [];
        self.roles = [];

        $q.all([OrganizationService.getAllOrganizations(), UserService.getAllRoles()]).then(function(results) {
            self.organizations = results[0].data;
            self.roles = results[1].data;
        });

        self.save = function(form, user) {
            if(!form.$valid) {
                return;
            }

            var successFn = function() {
                $rootScope.$emit('ReloadUsers', {});
                $state.go('index');
            };

            ValidationService.validationPerformer($q, UserService.checkUser, user, form).then(function() {
                UserService.editUser(user).success(function(user) {
                    if(angular.isDefined(user.password)) {
                        UserService.showUserData(user).then(function() {
                            successFn();
                        });
                    } else {
                        successFn();
                    }

                });
            }, angular.noop);
        };

        self.cancel = function() {
            $state.go('index');
        };

    }

    EditUserController.$inject = ['$state', '$stateParams', '$rootScope', '$q', 'OrganizationService', 'UserService', 'ValidationService', '$q'];

    function EditCurrentUserController($state, user, UserService, ValidationService) {
        var self = this;
        self.user = user;
        self.original = user;

        self.saveUserInfo = function() {
            if(self.editUser.$valid) {
                self.loading = true;
                var promise = UserService.checkUser(self.user).then(function() {
                    return UserService.editUser(self.user).then(function() {
                        self.original = angular.copy(self.user);
                        self.loading = false;
                    });
                });
                promise.then(function() {}, function(err) {
                    self.loading = false;
                    self.error = true;
                });
            }
        };

        self.doReset = function() {
            self.user = angular.copy(self.original);
            self.passwordContainer = {};
            self.changePasswordErrors = {};
        };

        self.updatePassword = function() {
            if(self.changePassword.$valid) {
                self.loading = true;
                UserService.updatePassword(self.passwordContainer).then(function(result) {
                    var validationResult = result.data;
                    if(validationResult.success) {
                        self.passwordContainer = {};
                        self.changePasswordErrors = {};
                        alert('succeeded');
                    } else {
                        angular.forEach(validationResult.validationErrors, function(e) {
                            self.changePassword.$setValidity(e.fieldName, false);
                        });
                    }
                    self.loading = false;
                });
            }
        };

    }

    EditCurrentUserController.$inject = ['$state', 'user', 'UserService', 'ValidationService'];

    function OrganizationService($http, HttpErrorHandler) {
        return {
            getAllOrganizations : function() {
                return $http.get('/admin/api/organizations.json').error(HttpErrorHandler.handle);
            },
            getOrganization: function(id) {
                return $http.get('/admin/api/organizations/'+id+'.json').error(HttpErrorHandler.handle);
            },
            createOrganization : function(organization) {
                return $http['post']('/admin/api/organizations/new', organization).error(HttpErrorHandler.handle);
            },
            updateOrganization : function(organization) {
                return $http['post']('/admin/api/organizations/update', organization).error(HttpErrorHandler.handle);
            },
            checkOrganization : function(organization) {
                return $http['post']('/admin/api/organizations/check', organization).error(HttpErrorHandler.handle);
            }
        };
    }

    OrganizationService.$inject = ['$http', 'HttpErrorHandler'];

    function UserService($http, $uibModal, $window, HttpErrorHandler) {
        return {
            getAllRoles: function() {
                return $http.get('/admin/api/roles.json').error(HttpErrorHandler.handle);
            },
            getAllUsers : function() {
                return $http.get('/admin/api/users.json').error(HttpErrorHandler.handle);
            },
            editUser : function(user) {
                var url = angular.isDefined(user.id) ? '/admin/api/users/edit' : '/admin/api/users/new';
                return $http['post'](url, user).error(HttpErrorHandler.handle);
            },
            checkUser : function(user) {
                return $http['post']('/admin/api/users/check', user).error(HttpErrorHandler.handle);
            },
            loadUser: function(userId) {
                return $http.get('/admin/api/users/'+userId+'.json').error(HttpErrorHandler.handle);
            },
            loadCurrentUser: function() {
                return $http.get('/admin/api/users/current.json').error(HttpErrorHandler.handle);
            },
            updatePassword: function(passwordContainer) {
                return $http.post('/admin/api/users/update-password.json', passwordContainer).error(HttpErrorHandler.handle);
            },
            deleteUser: function(user) {
                return $http['delete']('/admin/api/users/'+user.id).error(HttpErrorHandler.handle);
            },
            resetPassword: function(user) {
                return $http['put']('/admin/api/users/'+user.id+'/reset-password').error(HttpErrorHandler.handle);
            },

            showUserData: function(user) {
                return $uibModal.open({
                    size:'sm',
                    templateUrl:'/resources/angular-templates/admin/partials/event/fragment/show-user-data-modal.html',
                    backdrop: 'static',
                    controller: function($scope) {
                        $scope.baseUrl = $window.location.origin;
                        $scope.user = user;
                        $scope.ok = function() {
                            $scope.$close(true);
                        };
                    }
                }).result;
            }
        };
    }

    UserService.$inject = ['$http', '$uibModal', '$window', 'HttpErrorHandler'];

    function organizationList() {
        return {
            scope: true,
            templateUrl: '/resources/angular-templates/admin/partials/main/organizations.html',
            controller: 'OrganizationListController',
            controllerAs: 'orgList'
        };
    }

    function OrganizationListController($rootScope, OrganizationService) {
        var self = this;
        var loadOrganizations = function() {
            self.loading = true;
            OrganizationService.getAllOrganizations().success(function(result) {
                self.organizations = result;
                self.loading = false;
            });
        };
        $rootScope.$on('ReloadOrganizations', function(e) {
            loadOrganizations();
        });
        self.organizations = [];
        loadOrganizations();
    }

    OrganizationListController.$inject = ['$rootScope', 'OrganizationService'];

    function usersList() {
        return {
            scope: true,
            templateUrl: '/resources/angular-templates/admin/partials/main/users.html',
            controller: 'UsersListController',
            controllerAs: 'usersList'
        };
    }

    function UsersListController($rootScope, UserService, $window) {
        var self = this;
        self.users = [];
        var loadUsers = function() {
            self.loading = true;
            UserService.getAllUsers().success(function(result) {
                self.users = _.sortBy(result, 'username');
                self.loading=false;
            });
        };
        $rootScope.$on('ReloadUsers', function() {
            loadUsers();
        });
        loadUsers();
        self.deleteUser = function(user) {
            if($window.confirm('The user '+user.username+' will be deleted. Are you sure?')) {
                UserService.deleteUser(user).success(function() {
                    loadUsers();
                });
            }
        };
        self.resetPassword = function(user) {
            if($window.confirm('The password for the user '+ user.username+' will be reset. Are you sure?')) {
                UserService.resetPassword(user).success(function(reset) {
                    UserService.showUserData(reset);
                })
            }
        };
    }

    UsersListController.$inject = ['$rootScope', 'UserService', '$window'];

})();