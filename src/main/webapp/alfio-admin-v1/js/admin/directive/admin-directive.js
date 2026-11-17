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

    // binds the DOM events fired by a Lit component to scope functions, which receive the event detail:
    // <alfio-x lit-events="{'alfio-x-changed': ctrl.onChange}"></alfio-x>
    directives.directive('litEvents', function() {
        return {
            restrict: 'A',
            link: function(scope, element, attrs) {
                var handlers = scope.$eval(attrs.litEvents) || {};
                var listeners = Object.keys(handlers).map(function(name) {
                    var listener = function(e) {
                        scope.$applyAsync(function() {
                            handlers[name](e.detail);
                        });
                    };
                    element[0].addEventListener(name, listener);
                    return {name: name, listener: listener};
                });
                scope.$on('$destroy', function() {
                    listeners.forEach(function(l) {
                        element[0].removeEventListener(l.name, l.listener);
                    });
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
})();
