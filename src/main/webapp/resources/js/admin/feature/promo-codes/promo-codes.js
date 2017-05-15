(function() {
    'use strict';

    angular.module('adminApplication').component('promoCodes', {
        controller: ['$window', '$uibModal', '$q', 'PromoCodeService', PromoCodeCtrl],
        templateUrl: '../resources/js/admin/feature/promo-codes/promo-codes.html',
        bindings: {
            forEvent: '<',
            forOrganization: '<',
            event: '<',
        }
    });


    function PromoCodeCtrl($window, $uibModal, $q, PromoCodeService) {
        var ctrl = this;

        ctrl.isInternal = isInternal;
        ctrl.deletePromocode = deletePromocode;
        ctrl.disablePromocode = disablePromocode;
        ctrl.changeDate = changeDate;
        ctrl.addPromoCode = addPromoCode;

        ctrl.$onInit = function() {
            loadData();
        }

        function loadData() {
            PromoCodeService.list(ctrl.event.id).then(function(res) {
                ctrl.promocodes = res.data;
                angular.forEach(ctrl.promocodes, function(v) {
                    (function(v) {
                        PromoCodeService.countUse(v.promoCode.id).then(function(val) {
                            v.useCount = parseInt(val.data, 10);
                        });
                    })(v);
                });
            });
        }

        function isInternal(event) {
            return event.type === 'INTERNAL';
        }

        function errorHandler(error) {
            $log.error(error.data);
            alert(error.data);
        }

        function deletePromocode(promocode) {
            if($window.confirm('Delete promo code ' + promocode.promoCode + '?')) {
                PromoCodeService.remove(promocode.id).then(loadData, errorHandler);
            }
        }

        function disablePromocode(promocode) {
            if($window.confirm('Disable promo code ' + promocode.promoCode + '?')) {
                PromoCodeService.disable(promocode.id).then(loadData, errorHandler);
            }
        }

        function changeDate(promocode) {

            var eventId = ctrl.event.id;

            //TODO: transform component style
            $uibModal.open({
                size: 'lg',
                templateUrl: '../resources/js/admin/feature/promo-codes/edit-date-promo-code-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.cancel = function() {$scope.$dismiss('canceled');};
                    var start = moment(promocode.formattedStart);
                    var end = moment(promocode.formattedEnd);
                    $scope.promocode = {start: {date: start.format('YYYY-MM-DD'), time: start.format('HH:mm')}, end: {date: end.format('YYYY-MM-DD'), time: end.format('HH:mm')}};
                    $scope.update = function(toUpdate) {
                        PromoCodeService.update(eventId, promocode.promoCode, toUpdate).then(function() {
                            $scope.$close(true);
                        }).then(loadData);
                    };
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

        function addPromoCode(event) {
            //TODO: transform component style
            $uibModal.open({
                size:'lg',
                templateUrl: '../resources/js/admin/feature/promo-codes/edit-promo-code-modal.html',
                backdrop: 'static',
                controller: function($scope) {

                    $scope.event = event;

                    var now = moment();
                    var eventBegin = moment(event.formattedBegin);

                    $scope.validCategories = _.filter(event.ticketCategories, function(tc) {
                        return !tc.expired;
                    });

                    $scope.promocode = {discountType :'PERCENTAGE', start : {date: now.format('YYYY-MM-DD'), time: now.format('HH:mm')}, end: {date: eventBegin.format('YYYY-MM-DD'), time: eventBegin.format('HH:mm')}, categories:[]};

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
                    $scope.update = function(form, promocode, event) {
                        if(!form.$valid) {
                            return;
                        }
                        $scope.$close(true);


                        promocode.categories = _.filter(promocode.categories, function(i) {return i != null;});

                        PromoCodeService.add(event.id, promocode).then(function(result) {
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