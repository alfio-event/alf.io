(function () {

    'use strict';

    angular.module('adminApplication').service('UserService', UserService);

    function UserService($http, $uibModal, $window, HttpErrorHandler) {
        return {
            getAllRoles: function() {
                return $http.get('/admin/api/roles').error(HttpErrorHandler.handle);
            },
            getAllUsers : function() {
                return $http.get('/admin/api/users').error(HttpErrorHandler.handle);
            },
            editUser : function(user) {
                var url = angular.isDefined(user.id) ? '/admin/api/users/edit' : ('/admin/api/users/new?baseUrl='+window.encodeURIComponent($window.location.origin));
                return $http['post'](url, user).error(HttpErrorHandler.handle);
            },
            updateCurrentUserContactInfo: function(user) {
                return $http['post']('/admin/api/users/current/edit', user).error(HttpErrorHandler.handle);
            },
            bulkImportApiKeys: function(apiKeys) {
                return $http['post']('/admin/api/api-keys/bulk', apiKeys).error(HttpErrorHandler.handle);
            },
            enable : function(user, status) {
                return $http['post']('/admin/api/users/'+user.id+'/enable/'+status).error(HttpErrorHandler.handle);
            },
            checkUser : function(user) {
                return $http['post']('/admin/api/users/check', user).error(HttpErrorHandler.handle);
            },
            loadUser: function(userId) {
                return $http.get('/admin/api/users/'+userId).error(HttpErrorHandler.handle);
            },
            loadCurrentUser: function() {
                return $http.get('/admin/api/users/current').error(HttpErrorHandler.handle);
            },
            updatePassword: function(passwordContainer) {
                return $http.post('/admin/api/users/current/update-password', passwordContainer).error(HttpErrorHandler.handle);
            },
            deleteUser: function(user) {
                return $http['delete']('/admin/api/users/'+user.id).error(HttpErrorHandler.handle);
            },
            resetPassword: function(user) {
                return $http['put']('/admin/api/users/'+user.id+'/reset-password?baseUrl='+window.encodeURIComponent($window.location.origin)).error(HttpErrorHandler.handle);
            },
            retrieveSystemApiKey: function() {
                return $http.get('/admin/api/system/api-key').error(HttpErrorHandler.handle);
            },
            rotateSystemApiKey: function() {
                return $http['put']('/admin/api/system/api-key').error(HttpErrorHandler.handle);
            },

            showUserData: function(user) {
                return $uibModal.open({
                    size:'sm',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/show-user-data-modal.html',
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


})();