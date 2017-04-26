(function() {
    'use strict';

    angular.module('adminApplication').component('users', {
        controller: ['$window', 'UserService', UsersCtrl],
        templateUrl: '../resources/js/admin/feature/users/users.html'
    });



    function UsersCtrl($window, UserService) {
        var ctrl = this;

        ctrl.loadUsers = loadUsers;
        ctrl.deleteUser = deleteUser;
        ctrl.resetPassword = resetPassword;
        ctrl.enable = enable;

        ctrl.$onInit = function() {
            ctrl.users = [];
            loadUsers();
        }


        function loadUsers() {
            self.loading = true;
            UserService.getAllUsers().then(function(result) {
                ctrl.users = _.sortBy(result.data, 'username');
                ctrl.loading = false;
            });
        }


        function deleteUser(user) {
            if($window.confirm('The user '+user.username+' will be deleted. Are you sure?')) {
                UserService.deleteUser(user).then(function() {
                    loadUsers();
                });
            }
        }

        function resetPassword(user) {
            if($window.confirm('The password for the user '+ user.username+' will be reset. Are you sure?')) {
                UserService.resetPassword(user).then(function(reset) {
                    UserService.showUserData(reset.data);
                })
            }
        }

        function enable(user, status) {
            UserService.enable(user, status).then(function() {
                loadUsers();
            });
        }
    }
})();