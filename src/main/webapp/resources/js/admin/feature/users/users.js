(function() {
    'use strict';

    angular.module('adminApplication').component('users', {
        controller: ['$window', 'UserService', UsersCtrl],
        templateUrl: '../resources/js/admin/feature/users/users.html',
        bindings: {
            title: '@',
            type: '@'
        }
    }).filter('userEnabled', function() {
        return function(list, activeRequired) {
            return _.filter(list, function(e) { return e.enabled === activeRequired })
        }
    }).filter('orgSelected', function() {
        return function(list, orgId) {
            if(orgId == null || orgId == undefined) {
                return list;
            }
            return _.filter(list, function(e) { return _.any(e.memberOf, function(i) { return i.id === orgId })})
        }
    });



    function UsersCtrl($window, UserService) {
        var ctrl = this;

        ctrl.loadUsers = loadUsers;
        ctrl.deleteUser = deleteUser;
        ctrl.resetPassword = resetPassword;
        ctrl.enable = enable;
        ctrl.selectedOrganization = null;

        ctrl.$onInit = function() {
            ctrl.users = [];
            ctrl.organizations = [];
            loadUsers();
        };

        var filterFunction = ctrl.type == 'user' ? function(user) {return user.type !== 'API_KEY'} : function(user) {return user.type === 'API_KEY'};

        function loadUsers() {
            self.loading = true;
            UserService.getAllUsers().then(function(result) {
                var filteredUsers = result.data.filter(filterFunction);
                ctrl.users = _.sortByOrder(filteredUsers, ['enabled','username'], [false, true]);
                ctrl.organizations = _.chain(result.data)
                    .map('memberOf')
                    .flatten()
                    .uniq(false, 'id')
                    .value();
                ctrl.loading = false;
            });
        }


        function deleteUser(user) {
            if($window.confirm('The ' + ctrl.type + ' ' + user.username + ' will be deleted. Are you sure?')) {
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