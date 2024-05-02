(function() {
    'use strict';

    angular.module('adminApplication').component('organizations', {
        controller: ['OrganizationService', 'UserService', OrganizationsCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/organizations/organizations.html'
    });



    function OrganizationsCtrl(OrganizationService, UserService) {
        var ctrl = this;

        ctrl.load = load;

        ctrl.$onInit = function() {
            UserService.loadCurrentUser().then(function(result) {
                ctrl.isAdmin = (result.data.role === 'ADMIN');
                load();
            });
        };

        function load() {
            OrganizationService.getAllOrganizations().then(function(result) {
                ctrl.organizations = result.data;
            });
        }
    }
})();