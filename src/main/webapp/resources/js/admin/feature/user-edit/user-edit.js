(function() {
    'use strict';

    angular.module('adminApplication').component('userEdit', {
        controller: ['$q', '$state', 'OrganizationService', 'UserService', 'ValidationService', UserEditCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/user-edit/user-edit.html',
        bindings: {
            userId: '<',
            type:'@',
            for:'@'
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

            if (ctrl.for === 'apikey') {
                ctrl.user.type = 'API_KEY';
                ctrl.user.target = 'API_KEY';
            } else {
                ctrl.user.target = 'USER';
            }

            ctrl.organizations = [];
            ctrl.roles = [];

            $q.all([OrganizationService.getAllOrganizations(), UserService.getAllRoles()]).then(function(results) {
                ctrl.organizations = results[0].data;
                ctrl.roles = _.filter(results[1].data, function(r) { return (r.target || []).indexOf(ctrl.user.target) > -1; });
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
                    if(angular.isDefined(user.password) && ctrl.for !== 'apikey') {
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