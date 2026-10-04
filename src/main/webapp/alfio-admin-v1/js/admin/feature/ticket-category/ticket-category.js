(function() {
    'use strict';

    angular.module('adminApplication').component('ticketCategoryDetail', {
        bindings: {
            event: '<',
            ticketCategory: '<',
            editHandler: '<',
            removeHandler: '<',
            boxClass: '<',
            panelModeEnabled: '<',
            swapEnabled: '<',
            swapHandler: '<',
            isFirst: '<',
            isLast: '<'
        },
        controller: [TicketCategoryDetailCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/ticket-category/ticket-category-detail.html'
    }).service('TicketCategoryEditorService', TicketCategoryEditorService);

    function TicketCategoryDetailCtrl() {
        var ctrl = this;

        ctrl.deleteEnabled = !angular.isDefined(ctrl.event.id) || ctrl.event.ticketCategories.length > 1;

        ctrl.baseUrl = window.location.origin;

        var applyTaxes = ctrl.ticketCategory.price > 0 && (ctrl.ticketCategory.configuration || []).findIndex(function(c) {
            return c.key === 'APPLY_TAX_TO_CATEGORY' && c.value === 'false'
        }) === -1;

        ctrl.plusVat = applyTaxes && !ctrl.event.vatIncluded;

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
            } else if(ctrl.event.formattedBegin) {
                return ctrl.event.formattedBegin;
            } else {
                return ctrl.formatDateTimeModification(ctrl.event.begin);
            }
        };

        ctrl.getCheckInTo = function() {
            if(ctrl.ticketCategory.formattedValidCheckInTo || ctrl.ticketCategory.validCheckInTo) {
                return ctrl.ticketCategory.formattedValidCheckInTo ? ctrl.ticketCategory.formattedValidCheckInTo : ctrl.formatDateTimeModification(ctrl.ticketCategory.validCheckInTo);
            } else if(ctrl.event.formattedEnd) {
                return ctrl.event.formattedEnd;
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
            if(ctrl.ticketCategory.formattedTicketValidityStart || ctrl.ticketCategory.ticketValidityStart) {
                return ctrl.ticketCategory.formattedTicketValidityStart ? ctrl.ticketCategory.formattedTicketValidityStart : ctrl.formatDateTimeModification(ctrl.ticketCategory.ticketValidityStart);
            } else if(ctrl.event.formattedBegin) {
                return ctrl.event.formattedBegin;
            } else {
                return ctrl.formatDateTimeModification(ctrl.event.begin);
            }
        };

        ctrl.getTicketValidityTo = function() {
            if(ctrl.ticketCategory.formattedTicketValidityEnd || ctrl.ticketCategory.ticketValidityEnd) {
                return ctrl.ticketCategory.formattedTicketValidityEnd ? ctrl.ticketCategory.formattedTicketValidityEnd : ctrl.formatDateTimeModification(ctrl.ticketCategory.ticketValidityEnd);
            } else if(ctrl.event.formattedEnd) {
                return ctrl.event.formattedEnd;
            } else {
                return ctrl.formatDateTimeModification(ctrl.event.end);
            }
        };
    }

    TicketCategoryEditorService.prototype.$inject = ['$uibModal', 'EventService', 'NotificationHandler'];

    function TicketCategoryEditorService($uibModal, EventService, NotificationHandler) {
        this.openCategoryDialog = function(parentScope, category, event, validationErrorHandler, reloadIfSeatsModification) {
            var editCategory = $uibModal.open({
                size:'lg',
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/edit-category-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.allLanguagesMapping = parentScope.allLanguagesMapping;
                    var original = angular.extend({}, category);
                    $scope.ticketCategory = category;
                    $scope.event = event;
                    $scope.editMode = true;
                    $scope.reservationLinks = [];
                    $scope.newLink = {quantity: 1, expiresAtString: null, expiresAtModel: null};

                    if (category.id && !category.tokenGenerationRequested) {
                        EventService.listReservationLinks(event.shortName, category.id).then(function(res) {
                            $scope.reservationLinks = res.data;
                        });
                    }

                    $scope.generateReservationLink = function() {
                        var model = $scope.newLink.expiresAtModel;
                        if (!$scope.newLink.quantity || $scope.newLink.quantity < 1 || !(model && model.date && model.time)) { return; }
                        var expiresAt = moment(model.date + 'T' + model.time).toDate();
                        EventService.createReservationLink(event.shortName, category.id, $scope.newLink.quantity, expiresAt).then(function(res) {
                            $scope.reservationLinks.unshift(res.data);
                            $scope.newLink = {quantity: 1, expiresAtString: null, expiresAtModel: null};
                            NotificationHandler.showSuccess('Link generated');
                        });
                    };

                    $scope.revokeReservationLink = function(link) {
                        if (!confirm('Revoke this link? It will no longer work.')) { return; }
                        EventService.revokeReservationLink(event.shortName, category.id, link.id).then(function() {
                            link.status = 'REVOKED';
                        });
                    };

                    $scope.reservationLinkUrl = function(token) {
                        return window.location.origin + '/e/' + event.shortName + '/c/' + token;
                    };

                    $scope.copyToClipboard = function(text) {
                        navigator.clipboard.writeText(text);
                    };

                    $scope.displayLinkStatus = function(link) {
                        if (link.status === 'USED') { return 'Used'; }
                        if (link.status === 'REVOKED') { return 'Revoked'; }
                        if (new Date(link.expiresAt) < new Date()) { return 'Expired'; }
                        return 'Active';
                    };

                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, category, event) {
                        if(!form.$valid) {
                            return;
                        }

                        if($scope.editCategoryCallback) {
                            $scope.editCategoryCallback(); //<- ugly workaround to cleanup not up to date data
                        }

                        if(angular.isDefined(event.id)) {
                            //remove all empty descriptions
                            if(category.description) {
                                _.forIn(category.description, function(v, p) {
                                    if(v === '') {
                                        delete category.description[p];
                                    }
                                })
                            }
                            EventService.saveTicketCategory(event, category).then(function(result) {
                                if (result.data['errorCount'] > 0 && result.data.validationErrors.some(e => e.fieldName.startsWith('ticketValidity') || e.fieldName.startsWith('validCheckIn'))) {
                                    // handle special cases
                                    let messages = [];
                                    if (result.data.validationErrors.some(e => e.fieldName.startsWith('ticketValidity'))) {
                                        messages.push('Ticket Validity');
                                    }
                                    if (result.data.validationErrors.some(e => e.fieldName.startsWith('validCheckIn'))) {
                                        messages.push('Check In Dates');
                                    }
                                    NotificationHandler.showError("Please check " + messages.join(' and '));
                                }
                                validationErrorHandler(result, form, form).then(function() {
                                    reloadIfSeatsModification(!original || (original.bounded ^ category.bounded || original.maxTickets !== category.maxTickets))
                                        .then(function() {
                                            $scope.$close(category);
                                        });
                                });
                            });
                        } else {
                            $scope.$close(category);
                        }
                    };
                }
            });
            return editCategory.result;
        }
    }
})();