(function () {
    "use strict";
    function parseDate(val) {
        var d;
        if(angular.isDefined(val) && (d = moment(val)).isValid()) {
            return d;
        }
        return moment().startOf('day');
    }

    var directives = angular.module('adminDirectives', ['ui.bootstrap', 'adminServices']);

    directives.directive('dateRange', function() {
        return {
            restrict: 'A',
            scope: {
                minDate: '=',
                maxDate: '=',
                startDate: '=',
                startModelObj: '=startModel',
                endModelObj: '=endModel',
                watchObj: '='
            },
            require: '^ngModel',
            link: function(scope, element, attrs, ctrl) {
                element.attr('autocomplete', 'off');
                var dateFormat = 'YYYY-MM-DD HH:mm';
                var fillDate = function(modelObject) {
                    if(!angular.isDefined(modelObject.date)) {
                        modelObject.date = {};
                        modelObject.time = {};
                    }
                };

                var getNowAtStartOfHour = function(dayToAdd, hourToAdd) {
                    return moment().startOf('hour').add(dayToAdd, 'd').add(hourToAdd, 'h');
                };

                var initDateUsingNow = function(modelObj, dayToAdd, hoursToAddIfDefault) {
                    if(!angular.isDefined(modelObj) || !angular.isDefined(modelObj.date) || !angular.isDefined(modelObj.time)) {
                        return getNowAtStartOfHour(dayToAdd, hoursToAddIfDefault);
                    }
                    var date = moment(modelObj.date + 'T' + modelObj.time);
                    return date.isValid() ? date : getNowAtStartOfHour(dayToAdd, hoursToAddIfDefault);
                };


                var startDate = initDateUsingNow(scope.startModelObj, 1, 0);
                var endDate = initDateUsingNow(scope.endModelObj, 1, 1);

                var result = startDate.format(dateFormat) + ' / ' + endDate.format(dateFormat);
                ctrl.$setViewValue(result);
                element.val(result);

                var minDate = scope.minDate || getNowAtStartOfHour(0, 0);

                var pickerElement = element.daterangepicker({
                    format: dateFormat,
                    separator: ' / ',
                    startDate: startDate,
                    endDate: endDate,
                    minDate: minDate,
                    maxDate: scope.maxDate,
                    timePicker: true,
                    timePicker12Hour: false,
                    timePickerIncrement: 1
                });

                scope.startModelObj['date'] = startDate.format('YYYY-MM-DD');
                scope.startModelObj['time'] = startDate.format('HH:mm');
                scope.endModelObj['date'] = endDate.format('YYYY-MM-DD');
                scope.endModelObj['time'] = endDate.format('HH:mm');


                function updateDates(picker, override) {
                	if(angular.isDefined(picker)) {
                        scope.$apply(function() {
                            updateInDigest(picker, override);
                        });
                    }
                }

                function updateInDigest(picker, override) {
                    var start = picker.startDate;
                    var end = picker.endDate;
                    scope.startModelObj['date'] = start.format('YYYY-MM-DD');
                    scope.startModelObj['time'] = start.format('HH:mm');
                    scope.endModelObj['date'] = end.format('YYYY-MM-DD');
                    scope.endModelObj['time'] = end.format('HH:mm');
                    if (override) {
                        element.val(start.format(dateFormat) + ' / ' + end.format(dateFormat))
                    }
                    ctrl.$setViewValue(element.val());
                }

                if(scope.watchObj) {
                    var clearListener = scope.$watch('watchObj', function(newVal, oldVal) {
                        if(newVal && newVal['date']) {
                            var dr = pickerElement.data('daterangepicker');
                            if(angular.equals({date: dr.endDate.format('YYYY-MM-DD'), time: dr.endDate.format('HH:mm')}, oldVal)) {
                                dr.setCustomDates(dr.startDate, moment(newVal['date'] + 'T' + newVal['time']));
                                updateInDigest(dr, true);
                            } else {
                                clearListener();
                            }
                        }
                    }, true);
                }

                element.on('apply.daterangepicker', function(ev, picker) {
                	updateDates(picker);
                });

                element.on('hide.daterangepicker', function(ev, picker) {
                	updateDates(picker, true);
                });
            }
        };
    });

    directives.directive('singleDate', function() {
        return {
            restrict: 'A',
            scope: {
                minDate: '=',
                maxDate: '=',
                startDate: '=',
                startModelObj: '=startModel',
                watchObj: '=',
                noInitDate: '=',
                displayOnTop: '='
            },
            require: '^ngModel',
            link: function(scope, element, attrs, ctrl) {
                element.attr('autocomplete', 'off');
                var dateFormat = 'YYYY-MM-DD HH:mm';
                var fillDate = function(modelObject) {
                    if(!angular.isDefined(modelObject.date)) {
                        modelObject.date = {};
                        modelObject.time = {};
                    }
                };

                var getNowAtStartOfHour = function() {
                    return moment().startOf('hour');
                };

                var initDateUsingNow = function(modelObj) {
                    if(!(modelObj && modelObj.date && modelObj.time)) {
                        return getNowAtStartOfHour();
                    }
                    var date = moment(modelObj.date + 'T' + modelObj.time);
                    return date.isValid() ? date : getNowAtStartOfHour();
                };


                var startDate = initDateUsingNow(scope.startModelObj);

                var result = startDate.format(dateFormat);

                if(!scope.noInitDate || (scope.startModelObj && scope.startModelObj.date && scope.startModelObj.time)) {
                    ctrl.$setViewValue(result);
                    element.val(result);
                }


                var minDate = scope.minDate || getNowAtStartOfHour();

                var pickerElement = element.daterangepicker({
                    format: dateFormat,
                    separator: ' / ',
                    startDate: startDate,
                    minDate: minDate,
                    maxDate: scope.maxDate,
                    timePicker: true,
                    timePicker12Hour: false,
                    timePickerIncrement: 1,
                    singleDatePicker: true,
                    drops: !!scope.displayOnTop ? 'up' : 'down'
                });

                if(!scope.noInitDate) {
                    scope.startModelObj['date'] = startDate.format('YYYY-MM-DD');
                    scope.startModelObj['time'] = startDate.format('HH:mm');
                }

                function updateDates(picker, override) {
                    if(angular.isDefined(picker)) {
                        scope.$apply(function() {
                            updateInDigest(picker, override);
                        });
                    }
                }

                function updateInDigest(picker, override) {

                    if(picker.element.val() == "") {
                        scope.startModelObj = null;
                        ctrl.$setViewValue(element.val());
                        return;
                    }

                    var start = picker.startDate;

                    scope.startModelObj = scope.startModelObj || {};

                    scope.startModelObj['date'] = start.format('YYYY-MM-DD');
                    scope.startModelObj['time'] = start.format('HH:mm');
                    if (override) {
                        element.val(start.format(dateFormat))
                    }
                    ctrl.$setViewValue(element.val());
                }

                if(scope.watchObj) {
                    var clearListener = scope.$watch('watchObj', function(newVal, oldVal) {
                        if(newVal && newVal['date']) {
                            var dr = pickerElement.data('daterangepicker');
                            if(angular.equals({date: dr.endDate.format('YYYY-MM-DD'), time: dr.endDate.format('HH:mm')}, oldVal)) {
                                dr.setCustomDates(dr.startDate, moment(newVal['date'] + 'T' + newVal['time']));
                                updateInDigest(dr, true);
                            } else {
                                clearListener();
                            }
                        }
                    }, true);
                }

                element.on('apply.daterangepicker', function(ev, picker) {
                    updateDates(picker);
                });

                element.on('hide.daterangepicker', function(ev, picker) {
                    updateDates(picker, true);
                });

            }
        };
    });

    directives.directive('grabFocus', function() {
        return {
            restrict: 'A',
            link: function(scope, element, attrs) {
                setTimeout(function() {
                    element.focus();
                }, 10);
            }
        };
    });

    directives.directive('controlButtons', function() {
        return {
            restrict: 'E',
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/form/control-buttons.html',
            scope: {
                formObj: '=',
                cancelHandler: '=',
                disableSubmit:'<'
            },
            link: function(scope, element, attrs) {
                scope.successText = angular.isDefined(attrs.successText) ? attrs.successText : "Save";
                if(angular.isDefined(attrs.successText)) {
                    scope.successText = attrs.successText;
                }
            },
            controller: function($scope, $rootScope) {
                $scope.cancel = function() {
                    if(angular.isFunction($scope.cancelHandler)) {
                        $scope.cancelHandler();
                    } else if(angular.isFunction($scope.$parent.cancel)) {
                        $scope.$parent.cancel();
                    }
                };

                $scope.ok = function(frm) {
                    if(!frm.$valid) {
                        $rootScope.$broadcast('ValidationFailed');
                    }
                    return frm.$valid;
                };
            }
        };
    });

    directives.directive('fieldError', function() {
        return {
            restrict: 'E',
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/form/field-error.html',
            scope: {
                formObj: '=',
                fieldObj: '=',
                minChar: '=',
                maxChar: '=',
                requiredPattern: '=',
                showExistingErrors: '=',
                editMode: '=',
                fieldLabel: '='
            },
            link:angular.noop
        };
    });

    //edit event: fragments
    directives.directive('eventHeader', function() {
        return {
            restrict: 'E',
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/event-header.html',
            link: angular.noop,
            transclude: true
        }
    });

    directives.directive('editEventHeader', function() {
        return {
            scope: {
                obj: '=targetObj',
                eventObj: '=',
                organizations: '=',
                fullEditMode: '=',
                showDatesWarning: '=',
                showExistingErrors: '=',
                allLanguages: '=',
                allLanguagesMapping: '='
            },
            restrict: 'E',
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/edit-event-header.html',
            controller: function EditEventHeaderController($scope, $stateParams, LocationService, FileUploadService, UtilsService, EventService, ConfigurationService, ImageTransformService) {

                ConfigurationService.loadInstanceSettings().then(function(result) {
                    var data = result.data;
                    $scope.baseUrl = data.baseUrl;
                    $scope.descriptionLimit = data.descriptionMaxLength;
                });


                if(!angular.isDefined($scope.fullEditMode)) {
                    var source = _.pick($scope.eventObj, ['id','shortName', 'displayName', 'organizationId', 'location',
                        'description', 'websiteUrl', 'externalUrl', 'termsAndConditionsUrl', 'privacyPolicyUrl', 'imageUrl', 'fileBlobId', 'formattedBegin','format',
                        'formattedEnd', 'geolocation', 'locales']);
                    angular.extend($scope.obj, source);
                    var beginDateTime = moment(source['formattedBegin']);
                    var endDateTime = moment(source['formattedEnd']);
                    $scope.obj['begin'] = {
                        date: beginDateTime.format('YYYY-MM-DD'),
                        time: beginDateTime.format('HH:mm')
                    };
                    $scope.obj['end'] = {
                        date: endDateTime.format('YYYY-MM-DD'),
                        time: endDateTime.format('HH:mm')
                    };

                    if(!$scope.obj.geolocation) {
                        $scope.obj.geolocation = {timeZone: $scope.eventObj.timeZone, latitude: $scope.eventObj.latitude, longitude: $scope.eventObj.longitude};
                    }
                }

                $scope.formats = [
                    {
                        id: 'IN_PERSON',
                        description: 'in person'
                    },{
                        id: 'ONLINE',
                        description: 'online'
                    },{
                        id: 'HYBRID',
                        description: 'hybrid (in person + online)'
                    }
                ];

                if(!$scope.obj.format) {
                    $scope.obj.format = 'IN_PERSON';
                }

                LocationService.getTimezones().then(function(res) {
                    $scope.timezones = res.data;
                    if(!$scope.obj.geolocation) {
                        try {
                            $scope.obj.geolocation = {
                                timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
                            }
                        } catch(e) {
                            //necessary, as IE11 does not support Internationalization Apis
                        }
                    }
                });

                $scope.selectedLanguages = {
                    langs: []
                };

                var evaluateAvailableLanguages = function(allLanguages) {
                    $scope.availableLanguages = _.filter(allLanguages, function(r) {
                        return !isLangSelected(r);
                    });
                };

                var isLangSelected = function(r) {
                    var locales = $scope.obj.locales;
                    return (r.value & locales) === r.value;
                };

                function handleLocales() {
                    EventService.getSupportedLanguages().success(function(allLanguages) {
                        var selected = _.filter(allLanguages, isLangSelected);
                        if(selected.length === 0 && allLanguages.length > 0) {
                            $scope.addDescription(allLanguages[0]);
                            selected.push(allLanguages[0]);
                        }
                        $scope.selectedLanguages.langs = _.map(selected, function(r) {
                            return r.value;
                        });

                        $scope.allLanguages = allLanguages;
                        evaluateAvailableLanguages(allLanguages);
                    });
                }

                handleLocales();
                $scope.$watch('obj.locales', function(newValue) {
                    handleLocales();
                });

                $scope.addDescription = function(language) {
                    $scope.toggleLanguageSelection(language);
                    evaluateAvailableLanguages($scope.allLanguages);
                };
                $scope.removeDescription = function(language) {
                    $scope.toggleLanguageSelection(language);
                    evaluateAvailableLanguages($scope.allLanguages);
                };

                $scope.isLanguageSelected = function(lang) {
                    return lang && $scope.selectedLanguages.langs && $scope.selectedLanguages.langs.indexOf(lang.value) > -1;
                };

                $scope.toggleLanguageSelection = function(lang) {
                    if($scope.isLanguageSelected(lang)) {
                        _.remove($scope.selectedLanguages.langs, function(l) { return l === lang.value });
                    } else {
                        $scope.selectedLanguages.langs.push(lang.value);
                    }
                    $scope.updateLocales();
                };


                $scope.updateLocales = function() {
                    var locales = 0;
                    angular.forEach($scope.selectedLanguages.langs, function(val) {
                        locales = locales | val;
                    });
                    $scope.obj.locales = locales;
                };

                var isUpdate = angular.isDefined($scope.eventObj) && angular.isDefined($scope.eventObj.id);
                $scope.isUpdate = isUpdate;

                var previousFileBlobId;

                if($scope && $scope.eventObj && $scope.eventObj.fileBlobId) {
                    previousFileBlobId = $scope.eventObj.fileBlobId;
                }

                $scope.previousFileBlobId = previousFileBlobId;

                $scope.updateLocation = function (location) {
                    if(!angular.isDefined(location) || location.trim() === '') {
                        delete $scope.obj['geolocation'];
                        return;
                    }
                    $scope.loadingMap = true;
                    LocationService.clientGeolocate(location).then(function(result) {
                        delete $scope['mapError'];

                        if(result.latitude !== null && result.longitude !== null) {
                            $scope.obj['geolocation'] = result;
                        }
                        $scope.loadingMap = false;
                    }, function(e) {
                        $scope.mapError = e;
                        delete $scope.obj['geolocation'];
                        $scope.loadingMap = false;
                    });
                };

                $scope.$watch('obj.location', function(newValue, oldValue) {
                    if ($scope.obj && $scope.obj.location && !$scope.loadingMap && newValue != oldValue) {
                        $scope.updateLocation($scope.obj.location);
                    }
                });

                $scope.updateURL = function(eventName) {
                    if(!angular.isDefined(eventName) || eventName === '') {
                        return;
                    }
                    var targetElement = $('#shortName');
                    var shouldUpdate = function() {
                        return targetElement.val() === "" || (!isUpdate && !targetElement.hasClass('ng-touched'));
                    };
                    if(shouldUpdate()) {
                        $scope.loading = true;
                        UtilsService.generateShortName(eventName).success(function(data) {
                            if(shouldUpdate()) {
                                $scope.obj.shortName = data;
                            }
                        })['finally'](function() {
                            $scope.loading = false;
                        });
                    }
                };

                $scope.uploadedImage = {};

                $scope.removeImage = function(obj) {
                    //delete id, set base64 as undefined
                    $scope.imageBase64 = undefined;
                    obj.fileBlobId = undefined;
                };

                $scope.resetImage = function(obj) {
                    obj.fileBlobId = previousFileBlobId;
                    $scope.imageBase64 = undefined;
                };

                $scope.$watch('droppedFile', function (droppedFile) {
                    if(angular.isDefined(droppedFile)) {
                        if(droppedFile !== null) {
                            $scope.imageDropped([droppedFile]);
                        }
                    }
                });

                $scope.imageDropped = function(files) {
                    ImageTransformService.transformAndUploadImages(files).then(function(result) {
                        $scope.obj.fileBlobId = result.fileBlobId;
                        $scope.imageBase64 = result.imageBase64;
                    }, function(err) {
                        if(err != null) {
                            alert(err);
                        }
                    });
                };

                $scope.isObjectEmpty = function(obj) {
                    return !obj || Object.keys(obj).length === 0;
                }
            }
        }
    });

    directives.directive('editPrices', ['UtilsService', '$filter', 'SubscriptionService', function(UtilsService, $filter, SubscriptionService) {
        return {
            scope: {
                obj: '=targetObj',
                eventObj: '=',
                fullEditMode: '=',
                allowedPaymentProxies: '=',
                showPriceWarning: '=',
                showExistingErrors: '='
            },
            restrict: 'E',
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/edit-prices.html',
            controller: function EditPricesController($scope, PriceCalculator) {
                if(!angular.isDefined($scope.fullEditMode)) {
                    var source = _.pick($scope.eventObj, ['id','freeOfCharge', 'allowedPaymentProxies', 'availableSeats',
                        'regularPrice', 'currency', 'vatPercentage', 'vatIncluded', 'organizationId', 'linkedSubscriptions']);
                    angular.extend($scope.obj, source);
                }

                $scope.calculateTotalPrice = function(event) {
                    return PriceCalculator.calculateTotalPrice(event, false);
                };

                var initPaymentProxies = function() {
                    $scope.paymentProxies = _.map($filter('paymentMethodFilter')($scope.allowedPaymentProxies,  false, $scope.obj.currency), function(it) {
                        return {
                            proxy: it,
                            selected: _.findIndex($scope.obj.allowedPaymentProxies, function(pp) { return pp === it.id; }) > -1
                        }
                    });
                };

                initPaymentProxies();

                $scope.updatePaymentProxies = function() {
                    $scope.obj.allowedPaymentProxies = _.chain($scope.paymentProxies)
                        .filter(function(it) { return it.selected; })
                        .map(function(it) { return it.proxy.id; })
                        .value();
                };

                UtilsService.getAvailableCurrencies().then(function(result) {
                    $scope.currencies = result.data;
                });

                if(angular.isDefined($scope.fullEditMode)) {
                    $scope.$watch("allowedPaymentProxies", function() {
                        initPaymentProxies();
                    }, true);
                }

                SubscriptionService.loadActiveSubscriptionsDescriptors($scope.obj.organizationId).then(function(res) {
                    $scope.subscriptionDescriptors = res.data.map(function(descriptor) {
                        descriptor.selected = ($scope.obj && $scope.obj.linkedSubscriptions && _.contains($scope.obj.linkedSubscriptions, descriptor.id));
                        return descriptor;
                    });
                    $scope.$watch('subscriptionDescriptors', function(newVal) {
                        $scope.obj.linkedSubscriptions = newVal
                            .filter(function(sub) { return sub.selected; })
                            .map(function(sub) { return sub.id; });
                    }, true);
                });

                $scope.getFirstTranslation = UtilsService.getFirstTranslation;
            }
        }
    }]);

    directives.directive('prices', function() {
        return {
            restrict: 'E',
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/prices.html',
            transclude:true,
            controller: function ViewPricesController($scope, PriceCalculator, UtilsService) {
                $scope.calculateTotalPrice = function(event) {
                    return PriceCalculator.calculateTotalPrice(event, true);
                };
                $scope.getFirstTranslation = UtilsService.getFirstTranslation;
            }
        };
    });

    directives.directive('editCategory', function() {
        return {
            restrict: 'E',
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/edit-category.html',
            controller: function($scope, ConfigurationService) {

                if($scope.event.id !== undefined) {
                    ConfigurationService.loadSingleConfigForEvent($scope.event.shortName, 'CHECK_IN_COLOR_CONFIGURATION').then(function(result) {
                        var data = {};
                        if(result.data && result.data.length > 0) {
                            try {
                                data = JSON.parse(result.data);
                            } catch(e) {
                                console.error("cannot deserialize JSON", e);
                            }
                        }
                        if(data && data.configurations) {
                            var configurations = data.configurations
                                .map(function(c) { c.categories = c.categories.filter(function(tcId) { return tcId === $scope.ticketCategory.id}); return c;})
                                .filter(function(c) {return c.categories.length > 0;});
                            if(configurations.length > 0) {
                                $scope.ticketCategory['badgeColor'] = configurations[0].colorName;
                            }
                        }
                    });
                }

                $scope.buildPrefix = function(index, name) {
                    return angular.isDefined(index) ? index + "-" + name : name;
                };

                ConfigurationService.loadInstanceSettings().then(function(result) {
                    $scope.baseUrl = result.baseUrl;
                    $scope.descriptionLimit = result.descriptionMaxLength;
                });

                $scope.isLanguagePresent = function(locales, value) {
                    return (locales & value) === value;
                };

                $scope.categoryTypes = [
                    {
                        name: 'Public',
                        tokenGenerationRequested: false
                    },{
                        name: 'Hidden',
                        tokenGenerationRequested: true
                    }
                ];

                var allocationStrategies = [
                    {
                        name: 'Grow dynamically',
                        bounded: false
                    }, {
                        name: 'Fixed number of tickets',
                        bounded: true
                    }
                ];

                var ticketAccessTypes = [
                    {
                        id: 'IN_PERSON',
                        description: 'in person'
                    },{
                        id: 'ONLINE',
                        description: 'online'
                    }
                ];

                $scope.allocationStrategies = allocationStrategies;
                $scope.ticketAccessTypes = ticketAccessTypes;
                $scope.showTicketAccessTypes = $scope.event.format === 'HYBRID';
                $scope.onTokenGenerationRequestedChange = function() {
                    if($scope.ticketCategory.tokenGenerationRequested) {
                        $scope.ticketCategory.bounded = true;
                        $scope.allocationStrategies = allocationStrategies.slice(1);
                    } else {
                        $scope.allocationStrategies = allocationStrategies;
                    }
                };

                $scope.helpAllocationStrategyCollapse = true;
                $scope.toggleAllocationStrategyCollapse = function() {
                    $scope.helpCategoryVisibilityCollapse = true;
                    $scope.helpAllocationStrategyCollapse = !$scope.helpAllocationStrategyCollapse;
                };

                $scope.helpCategoryVisibilityCollapse = true;
                $scope.toggleCategoryVisibilityCollapse = function() {
                    $scope.helpAllocationStrategyCollapse = true;
                    $scope.helpCategoryVisibilityCollapse = !$scope.helpCategoryVisibilityCollapse;
                };

                $scope.helpAccessCodeCollapse = true;
                $scope.toggleAccessCodeCollapse = function() {
                    $scope.helpAccessCodeCollapse = !$scope.helpAccessCodeCollapse;
                };

                var hasCustomCheckIn = function(ticketCategory) {
                    return ticketCategory.formattedValidCheckInFrom ||
                        ticketCategory.validCheckInFrom ||
                        ticketCategory.formattedValidCheckInTo ||
                        ticketCategory.validCheckInTo;
                };

                var hasCustomTicketValidity = function(ticketCategory) {
                    return ticketCategory.formattedValidityStart||
                        ticketCategory.ticketValidityStart ||
                        ticketCategory.formattedValidityEnd ||
                        ticketCategory.ticketValidityEnd;
                };

                var eventDateToMoment = function(d) {
                    if(d.date) {
                        return moment(d.date + 'T' + d.time);
                    }
                    return moment(d);
                };

                var eventStartDate = eventDateToMoment($scope.event.begin);
                var eventEndDate = eventDateToMoment($scope.event.end);
                $scope.eventStartDate = eventStartDate.format('YYYY-MM-DD HH:mm');
                $scope.eventEndDate = eventEndDate.format('YYYY-MM-DD HH:mm');

                $scope.ticketValidity = [];
                $scope.ticketValidityTypes = [{
                    code: 'ALL',
                    description: 'For the entire event'
                }, {
                    code: 'CUSTOM',
                    description: 'Custom'
                }];
                $scope.ticketValidityType = hasCustomTicketValidity($scope.ticketCategory) ? 'CUSTOM' : 'ALL';

                $scope.checkInAllowedOptions = [
                    {
                        code: 'ANYTIME',
                        description: 'At any time'
                    }, {
                        code: 'CUSTOM',
                        description: 'Custom'
                    }
                ];
                $scope.ticketCheckInStrategies = [
                    {
                        code: 'ONCE_PER_EVENT',
                        description: 'Attendees check in only once, using their ticket'
                    },
                    {
                        code: 'ONCE_PER_DAY',
                        description: 'Attendees check in the first day using their ticket, then the following day(s) using their badge'
                    }
                ];
                $scope.badgeColors = [
                    { code: 'primary', description: 'blue' },
                    { code: 'secondary', description: 'gray' },
                    { code: 'success', description: 'green' },
                    { code: 'danger', description: 'red' },
                    { code: 'warning', description: 'yellow' },
                    { code: 'info', description: 'cyan'},
                    { code: 'light', description: 'white' },
                    { code: 'dark', description: 'black' }
                ];
                $scope.checkInStrategiesVisible = eventEndDate.endOf('day').diff(eventStartDate.startOf('day'), 'days') > 0;
                $scope.ticketCheckInStrategy = $scope.ticketCategory.ticketCheckInStrategy || 'ONCE_PER_EVENT';
                $scope.checkInAllowed = hasCustomCheckIn($scope.ticketCategory) ? 'CUSTOM' : 'ANYTIME';
                var supportedLanguages = _.filter($scope.allLanguagesMapping, function(l) {
                    return $scope.isLanguagePresent($scope.event.locales, l.value);
                });
                var filterLanguages = function(included) {
                    return _.filter(supportedLanguages, function(l) {
                        return included === ($scope.ticketCategory.description != null && angular.isDefined($scope.ticketCategory.description[l.locale]));
                    });
                };
                $scope.definedLanguages = filterLanguages(true);
                $scope.availableLanguages = filterLanguages(false);
                $scope.addDescription = function(language) {
                    if($scope.ticketCategory.description == null) {
                        $scope.ticketCategory.description = {};
                    }
                    $scope.ticketCategory.description[language.locale] = '';
                    $scope.definedLanguages = filterLanguages(true);
                    $scope.availableLanguages = filterLanguages(false);
                }

                //super ugly!
                $scope.$parent.editCategoryCallback = function() {
                    if($scope.ticketValidityType !== 'CUSTOM') {
                        delete $scope.ticketCategory.customValidityStartFromString;
                        delete $scope.ticketCategory.ticketValidityStart;
                        delete $scope.ticketCategory.customValidityStartToString;
                        delete $scope.ticketCategory.ticketValidityEnd;
                    }

                    if($scope.checkInAllowed !== 'CUSTOM') {
                        delete $scope.ticketCategory.validCheckInFromString;
                        delete $scope.ticketCategory.validCheckInFrom;
                        delete $scope.ticketCategory.validCheckInToString;
                        delete $scope.ticketCategory.validCheckInTo;
                    }
                }
            }
        };
    });

    directives.directive('pendingPaymentsLink', ['$rootScope', '$interval', 'EventService', function($rootScope, $interval, EventService) {
        return {
            restrict: 'AE',
            scope: {
                event: '=',
                eventName: '=',
                styleClass: '@'
            },
            bindToController: true,
            controllerAs: 'ctrl',
            template: '<a ng-class="ctrl.styleClass" data-ui-sref="events.single.pending-payments({eventName: ctrl.eventName})"><i class="fa fa-dollar"></i> Pending Payments <pending-payments-badge event-name="{{ctrl.eventName}}"></pending-payments-badge></a>',
            controller: ['$scope', function($scope) {
                var ctrl = this;
                ctrl.styleClass = ctrl.styleClass || 'btn btn-warning';
                var getPendingPayments = function() {
                    var eventPresent = ctrl.event && _.includes(ctrl.event.allowedPaymentProxies, 'OFFLINE');
                    if(eventPresent || (!ctrl.event && ctrl.eventName != null && ctrl.eventName.length > 0)) {
                        EventService.getPendingPaymentsCount(ctrl.eventName).then(function(count) {
                            $rootScope.$broadcast('PendingReservationsFound', count);
                        });
                    }
                };
                getPendingPayments();
                var promise = $interval(getPendingPayments, 10000);

                $scope.$on('$destroy', function() {
                    $interval.cancel(promise);
                });
            }]
        }
    }]);

    directives.directive('pendingPaymentsBadge', function($rootScope, $interval, EventService) {
        return {
            restrict: 'AE',
            scope: false,
            template: '<span ng-class="styleClass">{{pendingReservationsCount}}</span>',
            link: function(scope, element, attrs) {
                var eventName = attrs.eventName;
                scope.pendingReservationsCount = 0;
                scope.styleClass = attrs.styleClass || "badge";
                if(angular.isDefined(eventName) && eventName != null && eventName.length > 0) {
                    var getPendingPayments = function() {
                        EventService.getPendingPaymentsCount(eventName).then(function(count) {
                            scope.pendingReservationsCount = count;
                            $rootScope.$broadcast('PendingReservationsFound', count);
                        });
                    };
                    if (window.USER_IS_OWNER) {
                        getPendingPayments();
                        var promise = $interval(getPendingPayments, 10000);

                        scope.$on('$destroy', function() {
                            $interval.cancel(promise);
                        });
                    }
                } else {
                    var listener = $rootScope.$on('PendingReservationsFound', function(data) {
                        scope.pendingReservationsCount = data;
                    });
                    scope.$on('$destroy', function() {
                        listener();
                    });
                }
            }
        }
    });

    directives.directive('errorSensitive', function($rootScope) {
        return {
            restrict: 'A',
            require: '^form',
            link: function(scope, element, attrs, formController) {
                var prefixes = (attrs['errorSensitive'] || '').split(',');
                var touchField = function(prefix, name) {
                    var obj;
                    if(angular.isDefined(prefix) && angular.isDefined(formController[prefix])) {
                        obj = formController[prefix][name];
                    } else {
                        obj = formController[name];
                    }
                    if(angular.isDefined(obj)) {
                        scope.$applyAsync(function() {
                            obj.$setTouched();
                        });
                    }
                };
                $rootScope.$on('ValidationFailed', function() {
                    element.addClass('force-show-errors');
                    _.chain(element.find(':input'))
                        .filter(function(e) {
                            return prefixes.length > 0 && _.filter(prefixes, function(p) {
                                    return angular.isDefined(formController[p]) && angular.isDefined(formController[p][e.name]);
                                }).length > 0;
                        }).forEach(function(el) {
                            _.forEach(prefixes, function(prefix) {
                                touchField(prefix,el.name);
                            });
                            touchField(undefined, el.name);
                        });
                });

            }
        }
    });

    directives.directive('ticketStatus', function() {
        return {
            restrict: 'E',
            scope: {
                statisticsContainer: '='
            },
            template:'<canvas class="chart chart-pie" data-data="statistics" data-labels="labels" data-options="{animation:false}"></canvas>',
            controller: function($scope) {
                $scope.$watch('statisticsContainer', function(newVal) {
                    if(angular.isDefined(newVal)) {
                        $scope.statistics = [newVal.checkedInTickets, newVal.soldTickets, newVal.notSoldTickets, newVal.notAllocatedTickets, newVal.dynamicAllocation];
                    }
                });
                $scope.labels = ['Checked in', 'Sold', 'Still available', 'Not yet allocated', 'Dynamically allocated'];
            }
        }
    });

    directives.directive('setting', function() {
        return {
            restrict: 'E',
            scope: {
                setting: '=obj',
                displayDeleteIfNeeded: '=',
                deleteHandler: '&',
                listValues: '=',
                updateHandler: '&'
            },
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/configuration/setting.html',
            link: angular.noop,
            controller: function($scope, $rootScope, ConfigurationService) {
                $scope.displayDelete = $scope.displayDeleteIfNeeded && angular.isDefined($scope.setting) && !angular.isDefined($scope.setting.pluginId);
                $scope.removeConfiguration = function(config) {
                    $scope.loading = true;
                    $scope.deleteHandler({config: config}).then(function() {$rootScope.$broadcast('ReloadSettings');});
                };
                $scope.getLabelValue = function(setting) {
                    return (setting && setting.configurationPathLevel) ? setting.configurationPathLevel.toLowerCase().replace('_', ' ') : "";
                };
                $scope.showDeleteBtn = $scope.displayDelete && $scope.setting.id > -1 && $scope.setting.componentType !== 'BOOLEAN';
                if(angular.isFunction($scope.updateHandler)) {
                    $scope.onValueChange = function(newVal) {
                        $scope.updateHandler({config: newVal})
                    };
                }
            }
        }
    });

    directives.directive('editMessages', function() {
       return {
           restrict: 'E',
           scope: {
               editMode: '=',
               messages: '=',
               online: '='
           },
           templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/custom-message/edit-messages.html',
           controller: ['$scope', function($scope) {
               $scope.attachTicketFlag = false;
               $scope.updateTicketFlag = function() {
                   $scope.messages.forEach(function(m) {
                       m.attachTicket = !m.attachTicket;
                   });
               };
               $scope.sendAttachmentMessage = $scope.online ? 'Append access information at the end of the email' : 'Send attendee\'s ticket along with this message';
           }]
       };
    });

    directives.directive('waitingQueueDisplayCounter', function() {
        return {
            restrict: 'AE',
            bindToController: true,
            scope: {
                eventName: '=',
                styleClass: '@',
                justCount: '='
            },
            controllerAs: 'ctrl',
            controller: function(WaitingQueueService) {
                var ctrl = this;
                WaitingQueueService.countSubscribers(ctrl.eventName).success(function(result) {
                    ctrl.count = result;
                });
                ctrl.styleClass = ctrl.styleClass || 'btn btn-warning';
            },
            template: '<a data-ng-class="ctrl.styleClass" data-ui-sref="events.single.show-waiting-queue({eventName: ctrl.eventName})"><i class="fa fa-group" ng-if="!ctrl.justCount"></i> <span ng-class="{\'sr-only\': ctrl.justCount}">Waiting list</span> <span ng-class="{\'badge\': !ctrl.justCount}">{{ctrl.count}}</span></a>'
        }
    });

    directives.directive('waitingQueueStatus', function() {
        return {
            restrict: 'E',
            bindToController: true,
            scope: {
                eventName: '='
            },
            controllerAs: 'ctrl',
            controller: ['WaitingQueueService', function(WaitingQueueService) {
                var ctrl = this;
                var storeStatus = function (result) {
                    var status = result.data;
                    ctrl.active = status.active;
                    ctrl.paused = status.paused;
                };
                WaitingQueueService.getWaitingQueueStatus(ctrl.eventName).then(storeStatus);
                ctrl.togglePaused = function() {
                    WaitingQueueService.setPaused(ctrl.eventName, !ctrl.paused).then(storeStatus);
                }
            }],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/waiting-queue/status.html'
        }
    });

    directives.directive('validateShortName', ['UtilsService', function(UtilsService) {
        return {
            require: 'ngModel',
            link: function(scope, element, attrs, ngModelCtrl) {
                var isUpdate = scope[attrs.validateShortName];
                if(!isUpdate) {
                    ngModelCtrl.$asyncValidators.validateShortName = function(modelValue, viewValue) {
                        var value = modelValue || viewValue;
                        scope.loading = true;
                        return UtilsService.validateShortName(value)['finally'](function() {
                            scope.loading = false;
                        });
                    }
                }
            }
        }
    }]);

    directives.directive('displayCommonmarkPreview', ['UtilsService', '$uibModal', function(UtilsService, $uibModal) {
        return {
            restrict: 'E',
            bindToController: true,
            scope: {
                text: '=',
                buttonText: '@'
            },
            controllerAs: 'ctrl',
            template:'<span><a class="btn btn-xs btn-default" ng-click="ctrl.openModal()"><i class="fa fa-eye"></i> {{ctrl.buttonText}}</a></span>',
            controller: function() {
                var ctrl = this;
                if(!ctrl.buttonText) {
                    ctrl.buttonText = 'preview';
                }

                ctrl.openModal = function() {
                    if (ctrl.text) {
                        UtilsService.renderCommonMark(ctrl.text)
                            .then(function (res) {
                                    return $uibModal.open({
                                        size: 'lg',
                                        template: '<div class="modal-header"><h1>Preview</h1></div><div class="modal-body markdown-content" ng-bind-html="text"></div><div class="modal-footer"><button class="btn btn-default" data-ng-click="ok()">close</button></div>',
                                        backdrop: 'static',
                                        controller: function ($scope) {
                                            $scope.ok = function () {
                                                $scope.$close(true);
                                            };
                                            $scope.text = res.data;
                                        }
                                    })
                                }, function(res) {
                                    return $uibModal.open({
                                        size: 'sm',
                                        template: '<div class="modal-body">There was an error fetching the preview</div><div class="modal-footer"><button class="btn btn-default" data-ng-click="ok()">close</button></div>',
                                        backdrop: 'static',
                                        controller: function ($scope) {
                                            $scope.ok = function () {
                                                $scope.$close(true);
                                            };
                                        }
                                    })
                                }
                            );
                    }
                };

            }
        }
    }]);

    directives.directive('alfioSidebar', ['EventService', 'OrganizationService', 'UtilsService', 'ConfigurationService', '$state', '$window', '$rootScope', function(EventService, OrganizationService, UtilsService, ConfigurationService, $state, $window, $rootScope) {
        return {
            restrict: 'E',
            bindToController: true,
            scope: {},
            controllerAs: 'ctrl',
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/main/sidebar.html',
            controller: ['$location', '$anchorScroll', '$scope', 'MenuButtonService', function($location, $anchorScroll, $scope, MenuButtonService) {
                var ctrl = this;
                var toUnbind = [];
                var detectCurrentView = function(state) {
                    if(!state.data) {
                        return 'UNKNOWN';
                    }
                    return state.data.view || 'UNKNOWN';
                };
                var loadEventData = function() {
                    if(ctrl.displayEventData && $state.params.eventName) {
                        EventService.getEvent($state.params.eventName).success(function(event) {
                            ConfigurationService.loadSingleConfigForEvent(event.event.shortName, 'USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL')
                                .then(function(result) {
                                    ctrl.promoCodeDescription = (result.data === 'true') ? 'Partner' : 'Promo';
                                });
                            ctrl.event = event.event;
                            ctrl.internal = true;
                            ctrl.freeOfCharge = ctrl.event.free;
                            ctrl.offlineEnabled = !ctrl.freeOfCharge && ctrl.event.allowedPaymentProxies.includes('OFFLINE');
                            ctrl.owner = ctrl.event.visibleForCurrentUser;
                            ctrl.openDeleteWarning = function() {
                                EventService.deleteEvent(ctrl.event).then(function(result) {
                                    $state.go('index');
                                });
                            };
                            ctrl.deactivateEvent = function(e) {
                                EventService.toggleActivation(e.id, false).then(function() {
                                    $rootScope.$emit('ReloadEvent');
                                })
                            };
                            MenuButtonService.configureMenu(ctrl);
                            ctrl.goToCategory = function(category) {
                                ctrl.navigateTo('ticket-category-'+category.id);
                            };
                            ctrl.categoryFilter = {
                                active: true,
                                expired: false,
                                freeText: ''
                            };
                            ctrl.filterChanged = function() {
                                $rootScope.$emit('SidebarCategoryFilterUpdated', ctrl.categoryFilter);
                            };
                            toUnbind.push($rootScope.$on('CategoryFilterUpdated', function(ev, categoryFilter) {
                                if(categoryFilter) {
                                    ctrl.categoryFilter.freeText = categoryFilter.freeText;
                                }
                            }));
                        });
                    }
                };

                $rootScope.$on('EventUpdated', function() {
                    loadEventData();
                });

                ctrl.currentView = detectCurrentView($state.current);
                ctrl.isDetail = ctrl.currentView === 'EVENT_DETAIL';
                ctrl.displayEventData = $state.current.data && $state.current.data.displayEventData;
                loadEventData();
                var displayConfiguration = function() {

                    ConfigurationService.loadCurrentConfigurationContext(OrganizationService, EventService).then(function(res) {
                        ctrl.organizations = res.organizations;
                        ctrl.settingCategories = [
                            {
                                id: 'GENERAL',
                                name: 'General'
                            },
                            {
                                id: 'RESERVATION_UI',
                                name: 'Reservation UI'
                            },
                            {
                                id: 'WAITING_LIST',
                                name: 'Waiting List'
                            },
                            {
                                id: 'MAIL',
                                name: 'E-Mail'
                            },
                            {
                                id: 'INVOICE',
                                name: 'Invoice'
                            },
                            {
                                id: 'PAYMENT',
                                name: 'Payment'
                            },
                            {
                                id: 'IMPORT_ATTENDEES',
                                name: 'Import Attendees'
                            },
                            {
                                id: 'SUBSCRIPTIONS',
                                name: 'Subscriptions'
                            }
                        ];
                    });
                };

                toUnbind.push($rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState, fromParams) {
                    ctrl.currentView = detectCurrentView(toState);
                    ctrl.isDetail = ctrl.currentView === 'EVENT_DETAIL';
                    ctrl.displayEventData = toState.data && toState.data.displayEventData;
                    loadEventData();
                    if(!ctrl.displayEventData) {
                        delete ctrl.event;
                    }
                    if(ctrl.isConfiguration()) {
                        displayConfiguration();
                    }
                }));

                ctrl.isConfiguration = function() {
                    return ctrl.currentView === 'CONFIGURATION';
                };

                if(ctrl.isConfiguration()) {
                    displayConfiguration();
                }

                ctrl.navigateTo = function(id) {
                    //thanks to http://stackoverflow.com/a/14717011
                    $location.hash(id);
                    $anchorScroll();
                };

                $scope.$on('$destroy', function() {
                    toUnbind.forEach(function(f) {f();});
                });

                UtilsService.getApplicationInfo().then(function(result) {
                    ctrl.applicationInfo = result.data;
                });
            }]
        }
    }]);

    directives.directive('bsFormError', function() {
        return {
            restrict: 'A',
            scope: true,
            link: function($scope, element, attrs) {

                $scope.$watch(attrs.bsFormError + '.$error', function(newVal, oldVal) {

                    if(!newVal) {
                        return;
                    }

                    var addOrRemove = function(add) {
                        if(add) {
                            element.addClass('has-error');
                        } else {
                            element.removeClass('has-error');
                        }
                    };

                    var hasErrors = function(target) {
                        return ['minlength','maxlength','pattern','email','url','duplicate','value_already_in_use'].filter(function(k) {
                            return target[k] && target[k] !== '';
                        }).length > 0;
                    };

                    addOrRemove(hasErrors(newVal));

                }, true);
            }
        };
    });

    directives.directive('commonMarkHelp', function() {
        return {
            restrict: 'E',
            scope: {},
            template: '<div class="markdown-help text-right"><img class="markdown-logo" src="../resources/images/markdown-logo.svg" /> <a href="http://commonmark.org/help/" target="_blank">How to format text</a></div> '
        };
    });

    directives.directive('containerFluidResponsive', ['$window', function($window) {
        return {
            restrict: 'A',
            scope: false,
            link: function(scope, element, attrs) {
                if($window.matchMedia('screen and (max-width: 991px)').matches) {
                    element.removeClass('container');
                }
            }
        };
    }]);

    directives.directive('urlTextField', function() {
        return {
            restrict: 'A',
            scope: {},
            link: function($scope, element, attrs) {
                element.on('focus', function() {
                    if(element.val() === '') {
                        element.val('https://');
                        setTimeout(function() {
                            element.get(0).setSelectionRange(8, 8);
                        });
                    }
                });
                var urlProtocolMatcher = /^https?:\/\/.+$/;
                element.on('paste', function(e) {
                    var pastedText = e.originalEvent.clipboardData.getData('text'); //thanks to https://stackoverflow.com/a/11605419
                    if(element.val() === 'https://' && urlProtocolMatcher.test(pastedText)) {
                        element.select();
                    }
                })
            }
        }
    });
})();
