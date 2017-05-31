(function () {

    'use strict';

    angular.module('adminApplication').service('OrganizationService', OrganizationService);

    function OrganizationService($http, HttpErrorHandler) {
        return {
            getAllOrganizations: function () {
                return $http.get('/admin/api/organizations.json').error(HttpErrorHandler.handle);
            },
            getOrganization: function (id) {
                return $http.get('/admin/api/organizations/' + id + '.json').error(HttpErrorHandler.handle);
            },
            createOrganization: function (organization) {
                return $http['post']('/admin/api/organizations/new', organization).error(HttpErrorHandler.handle);
            },
            updateOrganization: function (organization) {
                return $http['post']('/admin/api/organizations/update', organization).error(HttpErrorHandler.handle);
            },
            checkOrganization: function (organization) {
                return $http['post']('/admin/api/organizations/check', organization).error(HttpErrorHandler.handle);
            }
        };
    }

    OrganizationService.$inject = ['$http', 'HttpErrorHandler'];


})();