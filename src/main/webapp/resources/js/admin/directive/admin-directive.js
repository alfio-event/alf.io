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

    directives.directive("organizationsList", function() {
        return {
            scope: true,
            templateUrl: '/resources/angular-templates/admin/partials/main/organizations.html',
            controller: function($scope, $rootScope, OrganizationService) {
                var loadOrganizations = function() {
                    $scope.loading = true;
                    OrganizationService.getAllOrganizations().success(function(result) {
                        $scope.organizations = result;
                        $scope.loading = false;
                    });
                };
                $rootScope.$on('ReloadOrganizations', function(e) {
                    loadOrganizations();
                });
                $scope.organizations = [];
                loadOrganizations();
            },
            link: angular.noop
        };
    });
    directives.directive("usersList", function() {
        return {
            scope: true,
            templateUrl: '/resources/angular-templates/admin/partials/main/users.html',
            controller: function($scope, $rootScope, UserService, $window) {
                $scope.users = [];
                var loadUsers = function() {
                    $scope.loading = true;
                    UserService.getAllUsers().success(function(result) {
                        $scope.users = result;
                        $scope.loading=false;
                    });
                };
                $rootScope.$on('ReloadUsers', function() {
                    loadUsers();
                });
                loadUsers();
                $scope.deleteUser = function(user) {
                    if($window.confirm('The user '+user.username+' will be deleted. Are you sure?')) {
                        UserService.deleteUser(user).success(function() {
                            loadUsers();
                        });
                    }
                };
                $scope.resetPassword = function(user) {
                    if($window.confirm('The password for the user '+ user.username+' will be reset. Are you sure?')) {
                        UserService.resetPassword(user).success(function(reset) {
                            UserService.showUserData(reset);
                        })
                    }
                };
            },
            link: angular.noop
        };
    });

    directives.directive('eventsList', function() {
        return {
            scope: true,
            templateUrl: '/resources/angular-templates/admin/partials/main/events.html',
            controller: function($scope, EventService) {

                $scope.loading = true;

                EventService.getAllEvents().success(function(data) {
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
                endModelObj: '=endModel'
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

                element.daterangepicker({
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

                element.on('apply.daterangepicker', function(ev, picker) {
                    if(angular.isDefined(picker)) {
                        scope.$apply(function() {
                            var start = picker.startDate;
                            var end = picker.endDate;
                            scope.startModelObj['date'] = start.format('YYYY-MM-DD');
                            scope.startModelObj['time'] = start.format('HH:mm');
                            scope.endModelObj['date'] = end.format('YYYY-MM-DD');
                            scope.endModelObj['time'] = end.format('HH:mm');
                            ctrl.$setViewValue(element.val());
                        });
                    }
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
                requiredPattern: '=',
                showExistingErrors: '='
            },
            link:angular.noop
        };
    });

    //edit event: fragments
    directives.directive('eventHeader', function() {
        return {
            restrict: 'E',
            templateUrl: '/resources/angular-templates/admin/partials/event/fragment/event-header.html',
            link: angular.noop
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
                showExistingErrors: '='
            },
            restrict: 'E',
            templateUrl: '/resources/angular-templates/admin/partials/event/fragment/edit-event-header.html',
            controller: function EditEventHeaderController($scope, LocationService, FileUploadService, EventUtilsService) {
                if(!angular.isDefined($scope.fullEditMode)) {
                    var source = _.pick($scope.eventObj, ['id','shortName', 'displayName', 'organizationId', 'location',
                        'description', 'websiteUrl', 'termsAndConditionsUrl', 'imageUrl', 'fileBlobId', 'formattedBegin',
                        'formattedEnd', 'geolocation']);
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
                    LocationService.geolocate(location).success(function(result) {
                        delete $scope['mapError'];
                        $scope.obj['geolocation'] = result;
                        $scope.loadingMap = false;
                    }).error(function(e) {
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
                        EventUtilsService.generateShortName(eventName).success(function(data) {
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
                }

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
                    if(files.length > 0 && (files[0].type == 'image/png') || files[0].type == 'image/jpeg') {
                        reader.readAsDataURL(files[0]);
                    }
                };
            }
        }
    });

    directives.directive('editPrices', function() {
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
                        'regularPrice', 'currency', 'vat', 'vatIncluded']);
                    if(source.vatIncluded) {
                        source.regularPrice = PriceCalculator.calculateTotalPrice(source, true);
                    }
                    angular.extend($scope.obj, source);
                }

                $scope.calculateTotalPrice = function(event) {
                    return PriceCalculator.calculateTotalPrice(event, false);
                };

            }
        }
    });

    directives.directive('prices', function() {
        return {
            restrict: 'E',
            templateUrl: '/resources/angular-templates/admin/partials/event/fragment/prices.html',
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
            }
        };
    });

    directives.directive('pendingReservationsBadge', function($rootScope, $interval, EventService) {
        return {
            restrict: 'E',
            scope: false,
            templateUrl: '/resources/angular-templates/admin/partials/pending-reservations/badge.html',
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
                        $scope.statistics = [newVal.soldTickets, newVal.checkedInTickets, newVal.notSoldTickets, newVal.notAllocatedTickets, newVal.dynamicAllocation];
                    }
                });
                $scope.labels = ['Sold', 'Checked in', 'Still available', 'Not yet allocated', 'Dynamically allocated'];
            }
        }
    });

    directives.directive('setting', function() {
        return {
            restrict: 'E',
            scope: {
                setting: '=obj'
            },
            templateUrl:'/resources/angular-templates/admin/partials/configuration/setting.html',
            link: angular.noop,
            controller: function($scope, $rootScope, ConfigurationService) {
                $scope.removeConfigurationKey = function(key) {
                    $scope.loading = true;
                    ConfigurationService.remove(key).then(function() {$rootScope.$broadcast('ReloadSettings');});
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
           templateUrl: '/resources/angular-templates/admin/partials/custom-message/edit-messages.html'
       };
    });

    directives.directive('waitingQueueDisplayCounter', function() {
        return {
            restrict: 'E',
            bindToController: true,
            scope: {
                event: '='
            },
            controllerAs: 'ctrl',
            controller: function(WaitingQueueService) {
                var ctrl = this;
                WaitingQueueService.countSubscribers(this.event).success(function(result) {
                    ctrl.count = result;
                });
            },
            template: '<span><a class="btn btn-warning" data-ui-sref="events.show-waiting-queue({eventName: ctrl.event.shortName})"><i class="fa fa-group"></i> waiting queue <span class="badge">{{ctrl.count}}</span></a></span>'
        }
    });

    directives.directive('validateShortName', ['EventUtilsService', function(EventUtilsService) {
        return {
            require: 'ngModel',
            link: function(scope, element, attrs, ngModelCtrl) {
                var isUpdate = scope[attrs.validateShortName];
                if(!isUpdate) {
                    ngModelCtrl.$asyncValidators.validateShortName = function(modelValue, viewValue) {
                        var value = modelValue || viewValue;
                        scope.loading = true;
                        return EventUtilsService.validateShortName(value)['finally'](function() {
                            scope.loading = false;
                        });
                    }
                }
            }
        }
    }]);

})();
