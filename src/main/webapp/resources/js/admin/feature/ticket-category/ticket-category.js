(function() {
    'use strict';

    angular.module('adminApplication').component('ticketCategoryDetail', {
        bindings: {
            event: '<',
            ticketCategory: '<',
            validCategories: '<',
            moveOrphans: '<',
            unbindTickets: '<',
            editHandler: '<',
            removeHandler: '<',
            boxClass: '<'
        },
        controller: [TicketCategoryDetailCtrl],
        templateUrl: '../resources/js/admin/feature/ticket-category/ticket-category-detail.html'
    }).service('TicketCategoryEditorService', TicketCategoryEditorService);

    function TicketCategoryDetailCtrl() {
        var ctrl = this;

        ctrl.categoryHasDescriptions = function(category) {
            return category && category.description ? Object.keys(category.description).length > 0 : false;
        };

        ctrl.formatDateTimeModification = function(dtm) {
            return dtm.date + " " + dtm.time;
        };

        ctrl.hasCustomCheckIn = function() {
            return ctrl.ticketCategory.formattedValidCheckInFrom ||
                   ctrl.ticketCategory.validCheckInFrom ||
                   ctrl.ticketCategory.formattedValidCheckInTo ||
                   ctrl.ticketCategory.validCheckInTo;
        };

        ctrl.getCheckInFrom = function() {
            if(ctrl.ticketCategory.formattedValidCheckInFrom || ctrl.ticketCategory.validCheckInFrom) {
                return ctrl.ticketCategory.formattedValidCheckInFrom ? ctrl.ticketCategory.formattedValidCheckInFrom : ctrl.formatDateTimeModification(ctrl.ticketCategory.validCheckInFrom);
            } else {
                return ctrl.formatDateTimeModification(ctrl.event.begin);
            }
        };

        ctrl.getCheckInTo = function() {
            if(ctrl.ticketCategory.formattedValidCheckInTo || ctrl.ticketCategory.validCheckInTo) {
                return ctrl.ticketCategory.formattedValidCheckInTo ? ctrl.ticketCategory.formattedValidCheckInTo : ctrl.formatDateTimeModification(ctrl.ticketCategory.validCheckInTo);
            } else {
                return ctrl.formatDateTimeModification(ctrl.event.end);
            }
        };

        ctrl.hasCustomTicketValidity = function() {
            return ctrl.ticketCategory.formattedValidityStart||
                ctrl.ticketCategory.ticketValidityStart ||
                ctrl.ticketCategory.formattedValidityEnd ||
                ctrl.ticketCategory.ticketValidityEnd;
        };

        ctrl.getTicketValidityFrom = function() {
            if(ctrl.ticketCategory.formattedValidityStart || ctrl.ticketCategory.ticketValidityStart) {
                return ctrl.ticketCategory.formattedValidCheckInFrom ? ctrl.ticketCategory.formattedValidCheckInFrom : ctrl.formatDateTimeModification(ctrl.ticketCategory.ticketValidityStart);
            } else {
                return ctrl.formatDateTimeModification(ctrl.event.begin);
            }
        };

        ctrl.getTicketValidityTo = function() {
            if(ctrl.ticketCategory.formattedValidityEnd || ctrl.ticketCategory.ticketValidityEnd) {
                return ctrl.ticketCategory.formattedValidityEnd ? ctrl.ticketCategory.formattedValidityEnd : ctrl.formatDateTimeModification(ctrl.ticketCategory.ticketValidityEnd);
            } else {
                return ctrl.formatDateTimeModification(ctrl.event.end);
            }
        };
    }

    TicketCategoryEditorService.prototype.$inject = ['$uibModal', 'EventService'];

    function TicketCategoryEditorService($uibModal, EventService) {
        this.openCategoryDialog = function(parentScope, category, event, validationErrorHandler, reloadIfSeatsModification) {
            var editCategory = $uibModal.open({
                size:'lg',
                templateUrl:'/resources/angular-templates/admin/partials/event/fragment/edit-category-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.allLanguagesMapping = parentScope.allLanguagesMapping;
                    var original = angular.extend({}, category);
                    $scope.ticketCategory = category;
                    $scope.event = event;
                    $scope.editMode = true;
                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, category, event) {
                        if(!form.$valid) {
                            return;
                        }
                        if(angular.isDefined(event.id)) {
                            EventService.saveTicketCategory(event, category).then(function(result) {
                                validationErrorHandler(result, form, form).then(function() {
                                    reloadIfSeatsModification(!original || (original.bounded ^ category.bounded || original.maxTickets !== category.maxTickets))
                                        .then(function() {
                                            $scope.$close(true);
                                        });
                                });
                            });
                        } else {
                            $scope.$close(true);
                        }
                    };
                }
            });
            return editCategory.result;
        }
    }
})();