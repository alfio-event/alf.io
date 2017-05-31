(function() {
    'use strict';

    angular.module('adminApplication').component('organizations', {
        controller: ['OrganizationService', OrganizationsCtrl],
        templateUrl: '../resources/js/admin/feature/organizations/organizations.html'
    });



    function OrganizationsCtrl(OrganizationService) {
        var ctrl = this;

        ctrl.load = load;

        this.$onInit = function() {
            load();
        };

        function load() {
            OrganizationService.getAllOrganizations().then(function(result) {
                ctrl.organizations = result.data;
            });
        }
    }
})();