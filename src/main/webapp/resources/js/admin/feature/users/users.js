(function() {
    'use strict';

    angular.module('adminApplication').component('users', {
        controller: ['$window', 'UserService', '$uibModal', '$q', 'NotificationHandler', UsersCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/users/users.html',
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
            if(orgId == null || !angular.isDefined(orgId)) {
                return list;
            }
            return _.filter(list, function(e) { return _.any(e.memberOf, function(i) { return i.id === orgId })})
        }
    }).filter('roleDesc', function() {
        return function(role, roleList) {
            if(role == null || roleList == null) {
                return role;
            }
            return _.filter(roleList, {role:role})[0].description;
        }
    });



    function UsersCtrl($window, UserService, $uibModal, $q, NotificationHandler) {
        var ctrl = this;

        ctrl.loadUsers = loadUsers;
        ctrl.deleteUser = deleteUser;
        ctrl.resetPassword = resetPassword;
        ctrl.enable = enable;
        ctrl.downloadApiKeys = downloadAllApiKeys;
        ctrl.viewApiKey = viewApiKeyQR;
        ctrl.rotateSystemApiKey = rotateSystemApiKey;
        ctrl.revealSystemApiKey = revealSystemApiKey;
        ctrl.copySystemApiKey = copySystemApiKey;
        ctrl.selectedOrganization = null;

        ctrl.$onInit = function() {
            ctrl.users = [];
            ctrl.organizations = [];
            $q.all([UserService.getAllRoles(), UserService.loadCurrentUser()]).then(function(results) {
                ctrl.roles = results[0].data;
                ctrl.isAdmin = (results[1].data.role === 'ADMIN');
            });
            loadUsers();
        };

        var filterFunction = function(user) { return ctrl.type === 'user' ^ user.type === 'API_KEY'; };

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

        function downloadAllApiKeys(orgId) {
            if(angular.isDefined(orgId)) {
                $window.open('/admin/api/api-keys/organization/'+orgId+'/all');
            }
        }

        function viewApiKeyQR(user) {
            var modal = $uibModal.open({
                size:'sm',
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/users/api-key-qr.html',
                backdrop: 'static',
                controllerAs: 'ctrl',
                controller: function() {
                    var ctrl = this;
                    ctrl.user = user;
                    ctrl.qrCodeData = function() {
                        return {
                            apiKey: user.username,
                            baseUrl: $window.location.origin
                        };
                    };
                    ctrl.close = function() {
                        modal.close();
                    }
                }
            });
        }

        function revealSystemApiKey() {
            UserService.retrieveSystemApiKey().then(function(result) {
                ctrl.systemApiKey = result.data;
            });
        }

        function rotateSystemApiKey() {

            $uibModal.open({
                size: 'lg',
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/users/confirm-api-key-rotation.html',
                backdrop: 'static',
                controller: function ($scope) {
                    $scope.cancel = function() {
                        $scope.$dismiss();
                    };
                    $scope.confirm = function() {
                        $scope.$close(true);
                    }
                }
            }).result.then(function(result) {
                if (result) {
                    UserService.rotateSystemApiKey().then(function(result) {
                        ctrl.systemApiKey = result.data;
                        NotificationHandler.showSuccess('API Key successfully rotated.');
                    });
                }
            });
        }

        function copySystemApiKey() {
            var listener = function(clipboardEvent) {
                var clipboard = clipboardEvent.clipboardData || window['clipboardData'];
                clipboard.setData('text', ctrl.systemApiKey);
                clipboardEvent.preventDefault();
                NotificationHandler.showSuccess('System API Key copied to Clipboard!');
            };
            document.addEventListener('copy', listener, false);
            document.execCommand('copy');
            document.removeEventListener('copy', listener, false);
        }
    }
})();