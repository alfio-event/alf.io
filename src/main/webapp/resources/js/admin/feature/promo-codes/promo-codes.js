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
                sendEmail: '<',
                disablePromocode: '<',
                deletePromocode: '<',
                addPromoCode: '<',
                promoCodeType: '<',
                isAccess: '<',
                sendPromotionalEmail: '<'
            }
        });

    function PromoCodeListCtrl() {}

    function PromoCodeCtrl($window, $uibModal, $q, PromoCodeService, ConfigurationService) {
        var ctrl = this;
        ctrl.isInternal = isInternal;
        ctrl.deletePromocode = deletePromocode;
        ctrl.disablePromocode = disablePromocode;
        ctrl.addPromoCode = addPromoCode;
        ctrl.sendPromotionalEmail = sendPromotionalEmail;
        ctrl.sendEmail = sendEmail;
        ctrl.changeDate = changeDate;

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
            console.error(error.data);
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

        function sendEmail(promocode) {
            if($window.confirm('Send ' +promocode.promoCode+ ' code to ' + promocode.emailReference + '?')) {
                PromoCodeService.sendEmail(promocode.id).then(emailSent, errorHandler);
            }
        }

        function emailSent(promocode){
            console.log('Email sent ',promocode);
        }

        function changeDate(promocode) {
            console.log("EditPromoCode", promocode);
            //TODO: transform component style
            $uibModal.open({
                size: 'lg',
                templateUrl: '../resources/js/admin/feature/promo-codes/edit-date-promo-code-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.cancel = function() {$scope.$dismiss('canceled');};
                    $scope.forEvent = ctrl.forEvent;
                    $scope.event = ctrl.event;
                    $scope.tmpTag = '';
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
                        hiddenCategoryId: promocode.hiddenCategoryId,
                        alfioMetadata: promocode.alfioMetadata
                    };
                    $scope.validCategories = _.map(ctrl.event?.ticketCategories, function(c) {
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
                    $scope.addTag = function(tag) {
                        if (!tag || tag.trim() === '')
                            return;
                        var idx = $scope.promocode.alfioMetadata.tags.indexOf(tag.trim().toLowerCase());
                        if (idx < 0)
                            $scope.promocode.alfioMetadata.tags.push(tag.trim().toLowerCase());
                        $scope.tmpTag = '';
                    };
                    $scope.removeTag = function(tag) {
                        var idx =  $scope.promocode.alfioMetadata.tags.indexOf(tag);
                        if (idx != -1) {
                            $scope.promocode.alfioMetadata.tags.splice(idx,1);
                        }
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

        function sendPromotionalEmail(pCodes){
            var organizationId = ctrl.organizationId;
            var uList = pCodes.map(x => ({description: x.description, emailReference: x.emailReference, selected: false }));
            console.log('sendPromotionalEmail', uList);
            $uibModal.open({
                size: 'lg',
                templateUrl: '../resources/js/admin/feature/promo-codes/send-promotional-email-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.cancel = function() {$scope.$dismiss('canceled');};
                    $scope.recipients = uList;
                    $scope.subject = '';
                    $scope.message = '';
                    $scope.searchText = '';
                    $scope.selectAll = '';
                    $scope.searchText = '';
                    $scope.organizationId = organizationId;

                    $scope.sendEmail = function() {
                        console.log('Service invocation sendPromotionalEmail', $scope.recipients, $scope.subject, $scope.message, $scope.organizationId);

                        var selRecipients = $scope.recipients.filter(x => x.selected);
                        if (!selRecipients || selRecipients.length == 0) {
                            $window.alert('Please select at least one recipient');
                            return;
                        }
                        if (!$scope.subject || $scope.subject == ''){
                            $window.alert('Please type a subject');
                            return;
                        }
                        if (!$scope.message || $scope.message == ''){
                            $window.alert('Please type a message');
                            return;
                        }
                        var body = {
                            recipients: selRecipients.map(x => x.emailReference),
                            subject: $scope.subject,
                            message: $scope.message
                        }

                        if($window.confirm('Send the promotional email to selected recipients?')) {
                            PromoCodeService.sendPromotionalEmail($scope.organizationId, body).then(function() {
                                $scope.$close(true);
                            });
                        }
                    };
                    $scope.doSelectAll = function() {
                        console.log('doSelectAll', $scope.selectAll, $scope.searchText);
                        $scope.recipients
                            .filter(x => $scope.searchText == '' || x.description.toLowerCase().indexOf($scope.searchText.toLowerCase()) != -1 || x.emailReference.toLowerCase().indexOf($scope.searchText.toLowerCase()) != -1)
                            .forEach(x => {
                                x.selected = $scope.selectAll;
                            });
                    }
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
                    $scope.tmpTag = '';

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
                        hiddenCategoryId: null,
                        alfioMetadata: { 'tags' : []},
                        promoCode: makeCode(10)
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

                    $scope.addTag = function(tag) {
                        if (!tag || tag.trim() === '')
                            return;
                        var idx = $scope.promocode.alfioMetadata.tags.indexOf(tag.trim().toLowerCase());
                        if (idx < 0)
                            $scope.promocode.alfioMetadata.tags.push(tag.trim().toLowerCase());
                        $scope.tmpTag = '';
                    };
                    $scope.removeTag = function(tag) {
                        var idx =  $scope.promocode.alfioMetadata.tags.indexOf(tag);
                        if (idx != -1) {
                            $scope.promocode.alfioMetadata.tags.splice(idx,1);
                        }
                    };
                    $scope.newCode = function() {
                        $scope.promocode.promoCode = makeCode(10);
                    }

                    function makeCode(length) {
                       var result           = '';
                       var characters       = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-:_@!$*';
                       var charactersLength = characters.length;
                       for ( var i = 0; i < length; i++ ) {
                          result += characters.charAt(Math.floor(Math.random() * charactersLength));
                       }
                       return result;
                    }
                }
            });
        };
    }
})();