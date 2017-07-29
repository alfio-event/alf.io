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

    directives.directive('eventsList', function() {
        return {
            scope: true,
            templateUrl: '/resources/angular-templates/admin/partials/main/events.html',
            controller: function($scope, EventService) {

                $scope.loading = true;

                EventService.getAllActiveEvents().success(function(data) {
                    $scope.events = data;
                    $scope.loading = false;
                });

                $scope.supportsOfflinePayments = function(event) {
                    return _.contains(event.allowedPaymentProxies, 'OFFLINE');
                };
            },
            link: angular.noop
        };
    });

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
                    if(!angular.isDefined(modelObj) || !angular.isDefined(modelObj.date) || !angular.isDefined(modelObj.time)) {
                        return getNowAtStartOfHour();
                    }
                    var date = moment(modelObj.date + 'T' + modelObj.time);
                    return date.isValid() ? date : getNowAtStartOfHour();
                };


                var startDate = initDateUsingNow(scope.startModelObj);
                var endDate = initDateUsingNow(scope.endModelObj);

                var result = startDate.format(dateFormat) + ' / ' + endDate.format(dateFormat);
                ctrl.$setViewValue(result);
                element.val(result);

                var minDate = scope.minDate || getNowAtStartOfHour();

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
                watchObj: '='
            },
            require: '^ngModel',
            link: function(scope, element, attrs, ctrl) {
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
                    if(!angular.isDefined(modelObj) || !angular.isDefined(modelObj.date) || !angular.isDefined(modelObj.time)) {
                        return getNowAtStartOfHour();
                    }
                    var date = moment(modelObj.date + 'T' + modelObj.time);
                    return date.isValid() ? date : getNowAtStartOfHour();
                };


                var startDate = initDateUsingNow(scope.startModelObj);

                var result = startDate.format(dateFormat);
                ctrl.$setViewValue(result);
                element.val(result);

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
                    singleDatePicker: true
                });

                scope.startModelObj['date'] = startDate.format('YYYY-MM-DD');
                scope.startModelObj['time'] = startDate.format('HH:mm');


                function updateDates(picker, override) {
                    if(angular.isDefined(picker)) {
                        scope.$apply(function() {
                            updateInDigest(picker, override);
                        });
                    }
                }

                function updateInDigest(picker, override) {
                    var start = picker.startDate;
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
                element.focus();
            }
        };
    });

    directives.directive('controlButtons', function() {
        return {
            restrict: 'E',
            templateUrl: '/resources/angular-templates/admin/partials/form/control-buttons.html',
            scope: {
                formObj: '=',
                cancelHandler: '='
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
            templateUrl: '/resources/angular-templates/admin/partials/form/field-error.html',
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
            templateUrl: '/resources/angular-templates/admin/partials/event/fragment/event-header.html',
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
            templateUrl: '/resources/angular-templates/admin/partials/event/fragment/edit-event-header.html',
            controller: function EditEventHeaderController($scope, $stateParams, LocationService, FileUploadService, UtilsService, EventService) {
                if(!angular.isDefined($scope.fullEditMode)) {
                    var source = _.pick($scope.eventObj, ['id','shortName', 'displayName', 'organizationId', 'location',
                        'description', 'websiteUrl', 'externalUrl', 'termsAndConditionsUrl', 'imageUrl', 'fileBlobId', 'formattedBegin','type',
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
                }

                $scope.selectedLanguages = {};

                EventService.getSupportedLanguages().success(function(result) {
                    var locales = $scope.obj.locales;
                    var selected = _.filter(result, function(r) {
                        return (r.value & locales) === r.value;
                    });
                    $scope.selectedLanguages.langs = _.map(selected, function(r) {
                        return r.value;
                    });
                });


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
                        console.log(result);
                        $scope.obj['geolocation'] = result;
                        $scope.loadingMap = false;
                    }, function(e) {
                        $scope.mapError = e;
                        delete $scope.obj['geolocation'];
                        $scope.loadingMap = false;
                    });
                };

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
                        if(droppedFile === null) {
                            alert('File drag&drop is not working, please click on the element and select the file.')
                        } else {
                            $scope.imageDropped([droppedFile]);
                        }
                    }
                });

                $scope.imageDropped = function(files) {
                    var reader = new FileReader();
                    reader.onload = function(e) {
                        $scope.$applyAsync(function() {
                            var imageBase64 = e.target.result;
                            $scope.imageBase64 = imageBase64;
                            FileUploadService.upload({file : imageBase64.substring(imageBase64.indexOf('base64,') + 7), type : files[0].type, name : files[0].name}).success(function(imageId) {
                                $scope.obj.fileBlobId = imageId;
                            })
                        })

                    };
                    if (files.length <= 0) {
                		alert('Your image not uploaded correctly.Please upload the image again');
	                } else if (!((files[0].type == 'image/png') || (files[0].type == 'image/jpeg'))) {
	                	alert('only png or jpeg files are accepted');
	                } else if (files[0].size > 1024000) {
	                	alert('Image size exceeds the allowable limit 1MB');
	                } else {
	                	reader.readAsDataURL(files[0]);
	                }
                };
            }
        }
    });

    directives.directive('editPrices', ['UtilsService', function(UtilsService) {
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
            templateUrl: '/resources/angular-templates/admin/partials/event/fragment/edit-prices.html',
            controller: function EditPricesController($scope, PriceCalculator) {
                if(!angular.isDefined($scope.fullEditMode)) {
                    var source = _.pick($scope.eventObj, ['id','freeOfCharge', 'allowedPaymentProxies', 'availableSeats',
                        'regularPrice', 'currency', 'vatPercentage', 'vatIncluded', 'organizationId']);
                    angular.extend($scope.obj, source);
                }

                $scope.calculateTotalPrice = function(event) {
                    return PriceCalculator.calculateTotalPrice(event, false);
                };

                UtilsService.getAvailableCurrencies().then(function(result) {
                    $scope.currencies = result.data;
                });

            }
        }
    }]);

    directives.directive('prices', function() {
        return {
            restrict: 'E',
            templateUrl: '/resources/angular-templates/admin/partials/event/fragment/prices.html',
            transclude:true,
            controller: function ViewPricesController($scope, PriceCalculator) {
                $scope.calculateTotalPrice = function(event) {
                    return PriceCalculator.calculateTotalPrice(event, true);
                };
            }
        };
    });

    directives.directive('editCategory', function() {
        return {
            restrict: 'E',
            templateUrl: '/resources/angular-templates/admin/partials/event/fragment/edit-category.html',
            controller: function($scope) {
                $scope.buildPrefix = function(index, name) {
                    return angular.isDefined(index) ? index + "-" + name : name;
                }

                $scope.isLanguagePresent = function(locales, value) {
                    return (locales & value) === value;
                }
            }
        };
    });

    directives.directive('pendingPaymentsLink', ['$rootScope', '$interval', 'EventService', function($rootScope, $interval, EventService) {
        return {
            restrict: 'E',
            scope: {
                event: '=',
                styleClass: '@'
            },
            bindToController: true,
            controllerAs: 'ctrl',
            template: '<a ng-class="ctrl.styleClass" data-ui-sref="events.single.pending-payments({eventName: ctrl.event.shortName})"><i class="fa fa-dollar"></i> Pending Payments <pending-payments-badge event-name="{{ctrl.event.shortName}}"></pending-payments-badge></a>',
            controller: ['$scope', function($scope) {
                var ctrl = this;
                var eventName = ctrl.event.shortName;
                ctrl.styleClass = ctrl.styleClass || 'btn btn-warning';
                var getPendingPayments = function() {
                    EventService.getPendingPayments(eventName).success(function(data) {
                        ctrl.pendingReservations = data.length;
                        $rootScope.$broadcast('PendingReservationsFound', data);
                    });
                };
                getPendingPayments();
                var promise = $interval(getPendingPayments, 1000);

                $scope.$on('$destroy', function() {
                    $interval.cancel(promise);
                });
            }]
        }
    }]);

    directives.directive('pendingPaymentsBadge', function($rootScope, $interval, EventService) {
        return {
            restrict: 'E',
            scope: false,
            template: '<span class="badge">{{pendingReservations}}</span>',
            link: function(scope, element, attrs) {
                var eventName = attrs.eventName;
                scope.pendingReservations = 0;
                if(angular.isDefined(eventName)) {
                    var getPendingPayments = function() {
                        EventService.getPendingPayments(eventName).success(function(data) {
                            scope.pendingReservations = data.length;
                            $rootScope.$broadcast('PendingReservationsFound', data);
                        });
                    };
                    getPendingPayments();
                    var promise = $interval(getPendingPayments, 10000);

                    element.on('$destroy', function() {
                        $interval.cancel(promise);
                    });
                } else {
                    $rootScope.$on('PendingReservationsFound', function(data) {
                        scope.pendingReservations = data.length;
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
                listValues: '='
            },
            templateUrl:'/resources/angular-templates/admin/partials/configuration/setting.html',
            link: angular.noop,
            controller: function($scope, $rootScope, ConfigurationService) {
                $scope.displayDelete = $scope.displayDeleteIfNeeded && angular.isDefined($scope.setting) && !angular.isDefined($scope.setting.pluginId);
                $scope.removeConfiguration = function(config) {
                    $scope.loading = true;
                    $scope.deleteHandler({config: config}).then(function() {$rootScope.$broadcast('ReloadSettings');});
                };
            }
        }
    });

    directives.directive('editMessages', function() {
       return {
           restrict: 'E',
           scope: {
               editMode: '=',
               messages: '='
           },
           templateUrl: '/resources/angular-templates/admin/partials/custom-message/edit-messages.html',
           controller: ['$scope', function($scope) {
               $scope.attachTicketFlag = false;
               $scope.updateTicketFlag = function() {
                   $scope.messages.forEach(function(m) {
                       m.attachTicket = !m.attachTicket;
                   });
               };
           }]
       };
    });

    directives.directive('waitingQueueDisplayCounter', function() {
        return {
            restrict: 'E',
            bindToController: true,
            scope: {
                event: '=',
                styleClass: '@'
            },
            controllerAs: 'ctrl',
            controller: function(WaitingQueueService) {
                var ctrl = this;
                WaitingQueueService.countSubscribers(this.event).success(function(result) {
                    ctrl.count = result;
                });
                ctrl.styleClass = ctrl.styleClass || 'btn btn-warning';
            },
            template: '<a data-ng-class="ctrl.styleClass" data-ui-sref="events.single.show-waiting-queue({eventName: ctrl.event.shortName})"><i class="fa fa-group"></i> Waiting queue <span class="badge">{{ctrl.count}}</span></a>'
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
                text: '='
            },
            controllerAs: 'ctrl',
            template:'<span><a class="btn btn-xs btn-default" ng-click="ctrl.openModal()"><i class="fa fa-eye"></i> preview</a></span>',
            controller: function() {
                var ctrl = this;

                ctrl.openModal = function() {
                    if (ctrl.text) {
                        UtilsService.renderCommonMark(ctrl.text)
                            .then(function (res) {
                                    return $uibModal.open({
                                        size: 'sm',
                                        template: '<div class="modal-header"><h1>Preview</h1></div><div class="modal-body" ng-bind-html="text"></div><div class="modal-footer"><button class="btn btn-default" data-ng-click="ok()">close</button></div>',
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

    directives.directive('alfioSidebar', ['EventService', 'UtilsService', '$state', '$window', '$rootScope', function(EventService, UtilsService, $state, $window, $rootScope) {
        return {
            restrict: 'E',
            bindToController: true,
            scope: {},
            controllerAs: 'ctrl',
            templateUrl: '/resources/angular-templates/admin/partials/main/sidebar.html',
            controller: ['$location', '$anchorScroll', '$scope', function($location, $anchorScroll, $scope) {
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
                            ctrl.event = event.event;
                            ctrl.internal = (ctrl.event.type === 'INTERNAL');
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
                            ctrl.openFieldSelectionModal = function() {
                                EventService.exportAttendees(ctrl.event);
                            };
                            ctrl.downloadSponsorsScan = function() {
                                $window.open($window.location.pathname+"/api/events/"+ctrl.event.shortName+"/sponsor-scan/export.csv");
                            };
                            ctrl.downloadInvoices = function() {
                                $window.open($window.location.pathname+"/api/events/"+ctrl.event.shortName+"/all-invoices");
                            };
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
                loadEventData();
                toUnbind.push($rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState, fromParams) {
                    ctrl.currentView = detectCurrentView(toState);
                    ctrl.isDetail = ctrl.currentView === 'EVENT_DETAIL';
                    ctrl.displayEventData = toState.data && toState.data.displayEventData;
                    loadEventData();
                    if(!ctrl.displayEventData) {
                        delete ctrl.event;
                    }
                }));

                ctrl.isConfiguration = function() {
                    return ctrl.currentView === 'CONFIGURATION';
                };

                toUnbind.push($rootScope.$on('ConfigurationMenuLoaded', function(e, organizations) {
                    ctrl.organizations = organizations;
                }));

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
                        return ['minlength','maxlength','pattern','email','url','duplicate'].filter(function(k) {
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
            template: '<div class="markdown-help text-right"><img class="markdown-logo" src="../resources/images/markdown-logo.svg" /> <a href="http://commonmark.org/help/" target="_blank">Markdown (CommonMark) supported</a></div> '
        };
    })
    
})();
