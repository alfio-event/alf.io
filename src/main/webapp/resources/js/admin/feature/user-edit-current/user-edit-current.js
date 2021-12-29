(function() {
    'use strict';

    angular.module('adminApplication').component('userEditCurrent', {
        controller: ['UserService', '$timeout', UserEditCurrentCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/user-edit-current/user-edit-current.html'
    });


    function UserEditCurrentCtrl(UserService, $timeout) {
        var ctrl = this;

        ctrl.$onInit = function() {
            ctrl.loading = true;
            UserService.loadCurrentUser().then(function(resp) {
                ctrl.loading = false;
                ctrl.user = resp.data;
                ctrl.original = ctrl.user;
                ctrl.isAdmin = (ctrl.user.username === 'admin');
            });
        }

        ctrl.saveUserInfo = saveUserInfo;
        ctrl.doReset = doReset;
        ctrl.updatePassword = updatePassword;

        function saveUserInfo () {
            if(ctrl.editUser.$valid) {
                ctrl.loading = true;
                var promise = UserService.checkUser(ctrl.user).then(function() {
                    return UserService.updateCurrentUserContactInfo(ctrl.user).then(function() {
                        ctrl.original = angular.copy(ctrl.user);
                        ctrl.loading = false;
                        ctrl.showMessage = true;
                    });
                });
                promise.then(function() {}, function(err) {
                    ctrl.loading = false;
                    ctrl.error = true;
                });
            }
        }

        function doReset() {
            ctrl.user = angular.copy(ctrl.original);
            ctrl.passwordContainer = {};
            ctrl.changePasswordErrors = {};
        }

        function updatePassword() {
            ['alfio.new-password-invalid', 'alfio.new-password-does-not-match', 'alfio.old-password-invalid'].forEach(function(e) {
                ctrl.changePassword.$setValidity(e, true);
            });
            if(ctrl.changePassword.$valid) {
                ctrl.loading = true;
                UserService.updatePassword(ctrl.passwordContainer).then(function(result) {
                    var validationResult = result.data;
                    if(validationResult.success) {
                        ctrl.passwordContainer = {};
                        ctrl.changePasswordErrors = {};
                        ctrl.showMessage = true;
                        $timeout(function() {
                            ctrl.changePassword.$setPristine();
                            ctrl.changePassword.$setUntouched();
                        });
                    } else {
                        angular.forEach(validationResult.validationErrors, function(e) {
                            ctrl.changePassword.$setValidity(e.fieldName, false);
                        });
                    }
                    ctrl.loading = false;
                });
            }
            ctrl.dismissMessage = function() {
                ctrl.showMessage = false;
            };
        }
    }
})();