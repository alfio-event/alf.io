(function() {
    'use strict';

    angular.module('adminApplication').component('organizationEdit', {
        controller: ['OrganizationService', 'ValidationService', '$state', '$q', '$scope', 'ConfigurationService', 'UserService', OrganizationEditCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/organization-edit/organization-edit.html',
        bindings: {
            organizationId: '<',
            type:'@'
        },
        require: {
            organizationsCtrl: '^?organizations'
        }
    });


    function OrganizationEditCtrl(OrganizationService, ValidationService, $state, $q, $scope, ConfigurationService, UserService) {
        var ctrl = this;

        ctrl.cancel = cancel;
        ctrl.save = save;

        this.$onInit = function() {
            $q.all([ConfigurationService.loadInstanceSettings(), UserService.loadCurrentUser()]).then(function(results) {
                ctrl.baseUrl = results[0].data.baseUrl;
                ctrl.isAdmin = (results[1].data.role === 'ADMIN');
            });
            var originalSlug;
            if(ctrl.type === 'edit') {
                OrganizationService.getOrganization(ctrl.organizationId).then(function(result) {
                    ctrl.organization = result.data;
                    originalSlug = result.data.slug;
                });
            } else {
                ctrl.organization = {};
            }
            $scope.$watch('$ctrl.organization.slug', function(newValue) {
                if(newValue && newValue !== originalSlug) {
                    ValidationService.validationPerformer($q, OrganizationService.checkSlug, ctrl.organization, ctrl.insertNewOrganization)
                        .then(function() {
                            ctrl.insertNewOrganization['slug'].$setValidity('value_already_in_use', true);
                            console.log(ctrl.insertNewOrganization['slug'].$error);
                        });
                } else if (ctrl.insertNewOrganization && ctrl.insertNewOrganization['slug']) {
                    ctrl.insertNewOrganization['slug'].$setValidity('value_already_in_use', true);
                }
            });
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