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
            getOrganization: function(id) {
                return $http.get('/admin/api/organizations/'+id+'.json').error(HttpErrorHandler.handle);
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

    baseServices.service('UserService', function($http, HttpErrorHandler) {
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
            getEvent: function(name) {
                return $http.get('/admin/api/events/'+name+'.json').error(HttpErrorHandler.handle);
            },
            getEventForUpdate: function(name) {
                return $http.get('/admin/api/events/'+name+'/for-update.json').error(HttpErrorHandler.handle);
            },
            checkEvent : function(event) {
                return $http['post']('/admin/api/events/check', event).error(HttpErrorHandler.handle);
            },
            createEvent : function(event) {
                return $http['post']('/admin/api/events/new', event).error(HttpErrorHandler.handle);
            },
            updateEvent : function(event) {
                return $http['post']('/admin/api/events/'+event.id+'/update', event).error(HttpErrorHandler.handle);
            },
            updateEventHeader: function(eventHeader) {
                return $http['post']('/admin/api/events/'+eventHeader.id+'/header/update', eventHeader).error(HttpErrorHandler.handle);
            },
            reallocateOrphans : function(srcCategory, targetCategoryId, eventId) {
                return $http['put']('/admin/api/events/reallocate', {
                    srcCategoryId: srcCategory.id,
                    targetCategoryId: targetCategoryId,
                    eventId: eventId
                }).error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service("LocationService", function($http, HttpErrorHandler) {
        return {
            geolocate : function(location) {
                return $http.get('/admin/api/location/geo.json?location='+location).error(HttpErrorHandler.handle);
            },
            getMapUrl : function(latitude, longitude) {
                return $http.get('/admin/api/location/map.json?lat='+latitude+'&long='+longitude).error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service('ConfigurationService', function($http, HttpErrorHandler) {
        return {
            loadAll: function() {
                return $http.get('/admin/api/configuration/load').error(HttpErrorHandler.handle);
            },
            update: function(configuration) {
                return $http.post('/admin/api/configuration/update', configuration).error(HttpErrorHandler.handle);
            },
            remove: function(key) {
            	return $http['delete']('/admin/api/configuration/key/' + key).error(HttpErrorHandler.handle);
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