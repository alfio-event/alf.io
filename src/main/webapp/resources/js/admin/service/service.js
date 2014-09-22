(function () {
    "use strict";
    var baseServices = angular.module('adminServices', []);

    baseServices.config(['$httpProvider', function($httpProvider) {
        var token = $("meta[name='_csrf']").attr("content");
        var header = $("meta[name='_csrf_header']").attr("content");
        $httpProvider.defaults.headers.common['X-Requested-With'] = 'XMLHttpRequest';
        $httpProvider.defaults.headers.common[header] = token;
    }]);

    baseServices.service("OrganizationService", function($http, HttpErrorHandler) {
        return {
            getAllOrganizations : function() {
                return $http.get('/admin/api/organizations.json').error(HttpErrorHandler.handle);
            },
            createOrganization : function(organization) {
                return $http['post']('/admin/api/organizations/new', organization).error(HttpErrorHandler.handle);
            },
            checkOrganization : function(organization) {
                return $http['post']('/admin/api/organizations/check', organization).error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service('PaymentProxyService', function($http, HttpErrorHandler) {
        return {
            getAllProxies : function() {
                return $http.get('/admin/api/paymentProxies.json').error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service("UserService", function($http, HttpErrorHandler) {
        return {
            getAllUsers : function() {
                return $http.get('/admin/api/users.json').error(HttpErrorHandler.handle);
            },
            createUser : function(user) {
                return $http['post']('/admin/api/users/new', user).error(HttpErrorHandler.handle);
            },
            checkUser : function(user) {
                return $http['post']('/admin/api/users/check', user).error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service("EventService", function($http, HttpErrorHandler) {
        return {
            getAllEvents : function() {
                return $http.get('/admin/api/events.json').error(HttpErrorHandler.handle);
            },
            checkEvent : function(event) {
                return $http['post']('/admin/api/events/check', event).error(HttpErrorHandler.handle);
            },
            createEvent : function(event) {
                return $http['post']('/admin/api/events/new', event).error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service("HttpErrorHandler", function($rootScope, $log) {
        return {
            handle : function(error) {
                $log.warn(error);
                $rootScope.$broadcast('applicationError', error.message);
            }
        };
    });
})();