(function () {
    "use strict";
    angular.module('alfio-users', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider
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
        .controller('EditCurrentUserController', EditCurrentUserController);

    function EditCurrentUserController($state, user, UserService, $timeout) {
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
                        self.showMessage = true;
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
            ['alfio.new-password-invalid', 'alfio.new-password-does-not-match', 'alfio.old-password-invalid'].forEach(function(e) {
                self.changePassword.$setValidity(e, true);
            });
            if(self.changePassword.$valid) {
                self.loading = true;
                UserService.updatePassword(self.passwordContainer).then(function(result) {
                    var validationResult = result.data;
                    if(validationResult.success) {
                        self.passwordContainer = {};
                        self.changePasswordErrors = {};
                        self.showMessage = true;
                        $timeout(function() {
                            self.changePassword.$setPristine();
                            self.changePassword.$setUntouched();
                        });
                    } else {
                        angular.forEach(validationResult.validationErrors, function(e) {
                            self.changePassword.$setValidity(e.fieldName, false);
                        });
                    }
                    self.loading = false;
                });
            }
            self.dismissMessage = function() {
                self.showMessage = false;
            };
        };

        self.isAdmin = (self.user.username === 'admin');

    }

    EditCurrentUserController.$inject = ['$state', 'user', 'UserService', '$timeout'];






})();