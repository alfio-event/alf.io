(function() {
    'use strict';

    angular.module('adminApplication').component('organizationEdit', {
        controller: ['OrganizationService', 'ValidationService', '$state', '$q', OrganizationEditCtrl],
        templateUrl: '../resources/js/admin/feature/organization-edit/organization-edit.html',
        bindings: {
            organizationId: '<',
            type:'@'
        },
        require: {
            organizationsCtrl: '^?organizations'
        }
    });


    function OrganizationEditCtrl(OrganizationService, ValidationService, $state, $q) {
        var ctrl = this;

        ctrl.cancel = cancel;
        ctrl.save = save;

        this.$onInit = function() {
            if(ctrl.type === 'edit') {
                OrganizationService.getOrganization(ctrl.organizationId).then(function(result) {
                    ctrl.organization = result.data;
                });
            } else {
                ctrl.organization = {};
            }
        };


        function cancel() {
            $state.go('^');
        }

        function save(form, organization) {
            if(!form.$valid) {
                return;
            }

            var updateOrAction = ctrl.type === 'edit' ? OrganizationService.updateOrganization : OrganizationService.createOrganization;

            ValidationService.validationPerformer($q, OrganizationService.checkOrganization, organization, form).then(function() {
                updateOrAction(organization).success(function() {
                    if(ctrl.organizationsCtrl) {
                        ctrl.organizationsCtrl.load();
                        $state.go('^');
                    }
                });
            }, angular.noop);
        }
    }
})();