(function() {
    'use strict';

    angular.module('adminApplication').component('userEdit', {
        controller: ['$q', '$state', 'OrganizationService', 'UserService', 'ValidationService', UserEditCtrl],
        templateUrl: '../resources/js/admin/feature/user-edit/user-edit.html',
        bindings: {
            userId: '<',
            type:'@'
        },
        require: {
            usersCtrl: '^?users'
        }
    });


    function UserEditCtrl($q, $state, OrganizationService, UserService, ValidationService) {
        var ctrl = this;

        ctrl.cancel = cancel;
        ctrl.save = save;

        ctrl.$onInit = function() {
            ctrl.user = {};
            ctrl.organizations = [];
            ctrl.roles = [];

            $q.all([OrganizationService.getAllOrganizations(), UserService.getAllRoles()]).then(function(results) {
                ctrl.organizations = results[0].data;
                ctrl.roles = results[1].data;
            });

            if(ctrl.type === 'edit') {
                UserService.loadUser(ctrl.userId).then(function(result) {
                    ctrl.user = result.data;
                });
            }
        }


        function cancel() {
            $state.go('^');
        }

        function save(form, user) {
            if(!form.$valid) {
                return;
            }

            function successFn() {
                if (ctrl.usersCtrl) {
                    ctrl.usersCtrl.loadUsers();
                    $state.go('^');
                }
            }

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
        }
    }
})();