(function() {
    'use strict';

    angular.module('adminApplication')
        .component('promoCodes', {
            controller: ['$window', '$uibModal', '$q', 'PromoCodeService', 'ConfigurationService', PromoCodeCtrl],
            templateUrl: '../resources/js/admin/feature/promo-codes/promo-codes.html',
            bindings: {
                forEvent: '<',
                forOrganization: '<',
                event: '<',
                organizationId: '<'
            }
        })
        .component('promoCodeList', {
            controller: [PromoCodeListCtrl],
            templateUrl: '../resources/js/admin/feature/promo-codes/list.html',
            bindings: {
                forEvent: '<',
                event: '<',
                organizationId: '<',
                promocodes: '<',
                ticketCategoriesById: '<',
                changeDate: '<',
                disablePromocode: '<',
                deletePromocode: '<',
                isAccess: '<'
            }
        });

    function PromoCodeListCtrl() {}

    function PromoCodeCtrl($window, $uibModal, $q, PromoCodeService, ConfigurationService) {
        var ctrl = this;

        ctrl.isInternal = isInternal;
        ctrl.deletePromocode = deletePromocode;
        ctrl.disablePromocode = disablePromocode;
        ctrl.changeDate = changeDate;
        ctrl.addPromoCode = addPromoCode;

        ctrl.$onInit = function() {
            loadData();
        };

        function loadData() {
            var loader = ctrl.forEvent ? function () {return PromoCodeService.list(ctrl.event.id)} : function() {return PromoCodeService.listOrganization(ctrl.organizationId)};

            if(ctrl.forEvent) {
                ConfigurationService.loadSingleConfigForEvent(ctrl.event.id, 'USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL')
                    .then(function(result) {
                        ctrl.promoCodeDescription = (result.data === 'true') ? 'Partner' : 'Promo';
                    });
            } else {
                ctrl.promoCodeDescription = 'Promo';
            }

            loader().then(function(res) {
                ctrl.promocodes = res.data.filter(function(pc) {
                    return pc.codeType === 'DISCOUNT' || pc.codeType === 'DYNAMIC';
                });
                ctrl.accesscodes = res.data.filter(function(pc) {
                    return pc.codeType === 'ACCESS';
                });
                angular.forEach(ctrl.promocodes, function(v) {
                    (function(v) {
                        PromoCodeService.countUse(v.id).then(function(val) {
                            v.useCount = parseInt(val.data, 10);
                        });
                    })(v);
                });

                ctrl.ticketCategoriesById = {};
                ctrl.restrictedCategories = [];
                if(ctrl.forEvent) {
                    angular.forEach(ctrl.event.ticketCategories, function(v) {
                        ctrl.ticketCategoriesById[v.id] = v;
                        if(v.accessRestricted) {
                            ctrl.restrictedCategories.push(v);
                        }
                    });
                }
            });
        }

        function isInternal(event) {
            return true;
        }

        function errorHandler(error) {
            $log.error(error.data);
            alert(error.data);
        }

        function deletePromocode(promocode) {
            if($window.confirm('Delete ' +ctrl.promoCodeDescription+ ' code ' + promocode.promoCode + '?')) {
                PromoCodeService.remove(promocode.id).then(loadData, errorHandler);
            }
        }

        function disablePromocode(promocode) {
            if($window.confirm('Disable ' +ctrl.promoCodeDescription+ ' code ' + promocode.promoCode + '?')) {
                PromoCodeService.disable(promocode.id).then(loadData, errorHandler);
            }
        }

        function changeDate(promocode) {

            //TODO: transform component style
            $uibModal.open({
                size: 'lg',
                templateUrl: '../resources/js/admin/feature/promo-codes/edit-date-promo-code-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.cancel = function() {$scope.$dismiss('canceled');};
                    $scope.forEvent = ctrl.forEvent;
                    $scope.event = ctrl.event;
                    var start = moment(promocode.formattedStart);
                    var end = moment(promocode.formattedEnd);
                    $scope.promoCodeDescription = ctrl.promoCodeDescription;
                    $scope.promocode = {
                        start: {date: start.format('YYYY-MM-DD'), time: start.format('HH:mm')},
                        end: {date: end.format('YYYY-MM-DD'), time: end.format('HH:mm')},
                        maxUsage: promocode.maxUsage,
                        description: promocode.description,
                        emailReference: promocode.emailReference,
                        codeType: promocode.codeType,
                        hiddenCategoryId: promocode.hiddenCategoryId
                    };
                    $scope.validCategories = _.map(ctrl.event.ticketCategories, function(c) {
                        var c1 = angular.copy(c, {});
                        var promoCodeIdx = _.indexOf(promocode.categories, c.id);
                        c1.selected = promoCodeIdx > -1;
                        return c1;
                    });
                    $scope.update = function(toUpdate) {
                        toUpdate.categories = _.chain($scope.validCategories)
                            .filter(function(c) { return c.selected; })
                            .map('id')
                            .value();
                        PromoCodeService.update(promocode.id, toUpdate).then(function() {
                            $scope.$close(true);
                        }).then(loadData);
                    };
                    $scope.addCategoryIfNotPresent = function(ev, id) {
                        if(ev.target.checked && $scope.promocode.categories.indexOf(id) === -1) {
                            $scope.promocode.categories.push(id);
                        }
                    }
                }
            });
        }

         function validationErrorHandler(result, form, fieldsContainer) {
            return $q(function(resolve, reject) {
                if(result.data['errorCount'] == 0) {
                    resolve(result);
                } else {
                    _.forEach(result.data.validationErrors, function(error) {
                        var field = fieldsContainer[error.fieldName];
                        if(angular.isDefined(field)) {
                            if (error.code == ERROR_CODES.DUPLICATE) {
                                field.$setValidity(ERROR_CODES.DUPLICATE, false);
                                field.$setTouched();
                            } else {
                                field.$setValidity('required', false);
                                field.$setTouched();
                            }
                        }
                    });
                    reject('validation error');
                }
            });
        }

        function addPromoCode(codeType) {
            var event = ctrl.event;
            var organizationId = ctrl.organizationId;
            var forEvent = ctrl.forEvent;
            var eventId = forEvent ? event.id : null;
            //TODO: transform component style
            $uibModal.open({
                size:'lg',
                templateUrl: '../resources/js/admin/feature/promo-codes/edit-promo-code-modal.html',
                backdrop: 'static',
                controller: function($scope) {

                    $scope.event = event;
                    $scope.forEvent = forEvent;
                    $scope.promoCodeDescription = ctrl.promoCodeDescription;

                    var now = moment();
                    var eventBegin = forEvent ? moment(event.formattedBegin) : moment().add(1,'d').endOf('d');

                    if(forEvent) {
                        $scope.validCategories = _.filter(event.ticketCategories, function(tc) {
                            return !tc.expired && !tc.accessRestricted;
                        });

                        $scope.restrictedCategories = _.filter(event.ticketCategories, function(tc) {
                            return !tc.expired && tc.accessRestricted;
                        });
                    }

                    $scope.discountTypes = {
                        PERCENTAGE: 'Percentage',
                        FIXED_AMOUNT_RESERVATION: 'Fixed amount, per reservation',
                        FIXED_AMOUNT: 'Fixed amount, per ticket'
                    };

                    $scope.promocode = {
                        discountType :'PERCENTAGE',
                        start : {date: now.format('YYYY-MM-DD'), time: now.format('HH:mm')},
                        end: {date: eventBegin.format('YYYY-MM-DD'), time: eventBegin.format('HH:mm')},
                        categories:[],
                        codeType: codeType,
                        hiddenCategoryId: null
                    };

                    $scope.addCategory = function addCategory(index, value) {
                        $scope.promocode.categories[index] = value;
                    };

                    $scope.$watch('promocode.promoCode', function(newVal) {
                        if(newVal) {
                            $scope.promocode.promoCode = newVal.toUpperCase();
                        }
                    });

                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, promocode) {
                        if(!form.$valid) {
                            return;
                        }
                        $scope.$close(true);


                        if(forEvent) {
                            promocode.categories = _.filter(promocode.categories, function(i) {return i != null;});
                        }
                        promocode.eventId = eventId;
                        promocode.organizationId = organizationId;

                        PromoCodeService.add(promocode).then(function(result) {
                            validationErrorHandler(result, form, form.promocode).then(function() {
                                $scope.$close(true);
                            });
                        }, errorHandler).then(loadData);
                    };
                }
            });
        };
    }
})();