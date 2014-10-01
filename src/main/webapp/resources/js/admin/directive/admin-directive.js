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
            templateUrl: '/resources/angularTemplates/admin/partials/main/organizations.html',
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
            templateUrl: '/resources/angularTemplates/admin/partials/main/users.html',
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
            templateUrl: '/resources/angularTemplates/admin/partials/main/events.html',
            controller: function($scope, EventService) {
                EventService.getAllEvents().success(function(data) {
                    $scope.events = data;
                });
            },
            link: angular.noop
        };
    });

    directives.directive('dateTime', function() {
        return {
            templateUrl: '/resources/angularTemplates/admin/partials/form/dateTime.html',
            scope: {
                modelObject: '=',
                prefix: '@',
                idPrefix: '@',
                dateCellClass: '@',
                timeCellClass: '@',
                notBefore: '@'
            },
            link: function(scope, element, attrs) {
                scope.open = function($event) {
                    $event.preventDefault();
                    $event.stopPropagation();
                    scope.opened = true;
                };

                var date = "";
                if(angular.isDefined(scope.modelObject.date)) {
                    date = scope.modelObject.date;
                }
                scope.date = moment(date).format('YYYY-MM-DD');
                scope.minDate = moment().toDate();

                scope.$watch('date', function(d) {
                    if(angular.isDefined(d) && angular.isDefined(scope.modelObject)) {
                        var date = moment(d);
                        if(date.isValid()) {
                            scope.modelObject['date'] = date.format('YYYY-MM-DD');
                        }
                    }
                });
            }
        }
    });

    directives.directive('afterDate', function() {
        return {
            restrict: 'A',
            scope: false,
            link: function(scope, element, attrs) {
                var setDate = function(val) {
                    var minDate = parseDate(val);
                    scope.minDate = minDate.toDate();
                    var d;
                    if(!angular.isDefined(scope.modelObject.date)
                        || !(d = moment(scope.modelObject.date)).isValid()
                        || d.isBefore(minDate)) {
                        scope.date = minDate.format('YYYY-MM-DD');
                    }
                };
                setDate(attrs.afterDate);
                attrs.$observe('afterDate', function(val) {
                    setDate(val);
                });
            }
        }
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
            templateUrl: '/resources/angularTemplates/admin/partials/form/controlButtons.html',
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
            templateUrl: '/resources/angularTemplates/admin/partials/form/fieldError.html',
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