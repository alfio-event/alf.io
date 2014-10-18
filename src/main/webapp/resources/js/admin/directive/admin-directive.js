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

    directives.config(function(datepickerConfig, datepickerPopupConfig, $provide) {
        datepickerConfig.startingDay = 1;
        datepickerConfig.yearRange = 3;
        datepickerPopupConfig.datepickerPopup='dd/MM/yyyy';

        //this patch temporary fixes the issue https://github.com/angular-ui/bootstrap/issues/2169
        //thanks to rigup (https://github.com/angular-ui/bootstrap/commit/42cc3f269bae020ba17b4dcceb4e5afaf671d49b)
        //to be removed as soon as a newer version will be released
        $provide.decorator('dateParser', function($delegate){

            var oldParse = $delegate.parse;
            $delegate.parse = function(input, format) {
                if ( !angular.isString(input) || !format ) {
                    return input;
                }
                return oldParse.apply(this, arguments);
            };

            return $delegate;
        });
        //end
    });

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

                var fillDate = function(modelObject) {
                    if(!angular.isDefined(modelObject.date)) {
                        modelObject.date = {};
                        modelObject.time = {};
                    }
                };
                fillDate(scope.startModelObj);
                fillDate(scope.endModelObj);
                var minDate = scope.minDate || moment();

                element.daterangepicker({
                    format: 'YYYY-MM-DD HH:mm',
                    separator: ' / ',
                    startDate: scope.startDate,
                    minDate: minDate,
                    maxDate: scope.maxDate,
                    timePicker: true,
                    timePicker12Hour: false,
                    timePickerIncrement: 15
                },
                function(start, end, label) {
                    scope.$apply(function() {
                        scope.startModelObj['date'] = start.format('YYYY-MM-DD');
                        scope.startModelObj['time'] = start.format('HH:mm');
                        scope.endModelObj['date'] = end.format('YYYY-MM-DD');
                        scope.endModelObj['time'] = end.format('HH:mm');
                        ctrl.$setViewValue(element.val());
                    });
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
})();