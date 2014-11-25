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
                    OrganizationService.getAllOrganizations().success(function(result) {
                        $scope.organizations = result;
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
            controller: function($scope, $rootScope, UserService) {
                $scope.users = [];
                var loadUsers = function() {
                    UserService.getAllUsers().success(function(result) {
                        $scope.users = result;
                    });
                };
                $rootScope.$on('ReloadUsers', function() {
                    loadUsers();
                });
                loadUsers();
            },
            link: angular.noop
        };
    });

    directives.directive('eventsList', function() {
        return {
            scope: true,
            templateUrl: '/resources/angular-templates/admin/partials/main/events.html',
            controller: function($scope, EventService) {
                EventService.getAllEvents().success(function(data) {
                    $scope.events = data;
                });
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

                var getTodayTruncatedToNextHour = function() {
                    return moment().startOf('hour').add(1,'hours');
                };

                var initDateUsingNow = function(modelObj) {
                    if(!angular.isDefined(modelObj) || !angular.isDefined(modelObj.date) || !angular.isDefined(modelObj.time)) {
                        return getTodayTruncatedToNextHour();
                    }
                    var date = moment(modelObj.date + 'T' + modelObj.time);
                    return date.isValid() ? date : getTodayTruncatedToNextHour();
                };


                var startDate = initDateUsingNow(scope.startModelObj);
                var endDate = initDateUsingNow(scope.endModelObj);

                var result = startDate.format(dateFormat) + ' / ' + endDate.format(dateFormat);
                ctrl.$setViewValue(result);
                element.val(result);

                var minDate = scope.minDate || getTodayTruncatedToNextHour();

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
            templateUrl: '/resources/angular-templates/admin/partials/form/controlButtons.html',
            scope: {
                formObj: '=',
                cancelHandler: '='
            },
            link: function(scope, element, attrs) {
                scope.cancel = function() {
                    if(angular.isFunction(scope.cancelHandler)) {
                        scope.cancelHandler();
                    } else if(angular.isFunction(scope.$parent.cancel)) {
                        scope.$parent.cancel();
                    }
                }
            }
        };
    });

    directives.directive('fieldError', function() {
        return {
            restrict: 'E',
            templateUrl: '/resources/angular-templates/admin/partials/form/fieldError.html',
            scope: {
                formObj: '=',
                fieldObj: '=',
                minChar: '=',
                requiredPattern: '='
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
                showDatesWarning: '='
            },
            restrict: 'E',
            templateUrl: '/resources/angular-templates/admin/partials/event/fragment/edit-event-header.html',
            controller: function EditEventHeaderController($scope, LocationService) {
                if(!angular.isDefined($scope.fullEditMode)) {
                    var source = _.pick($scope.eventObj, ['id','shortName', 'organizationId', 'location',
                        'description', 'websiteUrl', 'termsAndConditionsUrl', 'imageUrl', 'formattedBegin',
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

                $scope.updateLocation = function (location) {
                    $scope.loadingMap = true;
                    LocationService.geolocate(location).success(function(result) {
                        $scope.obj['geolocation'] = result;
                        $scope.loadingMap = false;
                    }).error(function() {
                        $scope.loadingMap = false;
                    });
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
                showPriceWarning: '='
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
            templateUrl: '/resources/angular-templates/admin/partials/event/fragment/edit-category.html'
        };
    });


})();