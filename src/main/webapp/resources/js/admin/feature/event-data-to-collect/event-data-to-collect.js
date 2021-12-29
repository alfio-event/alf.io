(function() {
    'use strict';

    var FIELD_TYPES = {
        'input:text': 'Generic Text Input',
        'input:tel': 'Phone Number',
        'textarea': 'Multi-line Text',
        'select': 'Drop-down list',
        'checkbox': 'Multiple choice (checkbox)',
        'radio': 'One choice list (radio button)',
        'country': 'Country',
        'vat:eu': 'EU VAT'
    };

    angular.module('adminApplication')
        .component('eventDataToCollect', {
            controller: ['$uibModal', '$q', 'EventService', 'AdditionalServiceManager', EventDataToCollectCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/event-data-to-collect/event-data-to-collect.html',
            bindings: {
                event: '<'
            }
        }).component('restrictedValuesStatistics', {
            controller: ['EventService', RestrictedValuesStatisticsCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/event-data-to-collect/restricted-values-statistics.html',
            bindings: {
                field: '<',
                closeWindow: '&',
                eventName: '<'
            }
        }).component('standardFields', {
            template: '' +
                '<div class="panel panel-primary">' +
                '   <div class="panel-heading">' +
                '       <div class="panel-title">Standard Fields</div>' +
                '   </div>' +
                '   <ul class="list-group">' +
                '       <li class="list-group-item">First Name</li>' +
                '       <li class="list-group-item">Last Name</li>' +
                '       <li class="list-group-item">Email Address</li>' +
                '   </ul>' +
                '</div>'
        }).filter('fieldType', function() {
            return function(field) {
                return FIELD_TYPES[field.type] || field.type;
            }
        });


    var ERROR_CODES = { DUPLICATE:'duplicate', MAX_LENGTH:'maxlength', MIN_LENGTH:'minlength'};

    function fillExistingTexts(texts) {
        return function(t) {
            var existing = _.find(texts, function(e) {return e.locale === t.locale});
            return existing ? angular.extend({displayLanguage: t.displayLanguage}, existing) : t;
        }
    }

    function errorHandler(error) {
        $log.error(error.data);
        alert(error.data);
    }


    function EventDataToCollectCtrl($uibModal, $q, EventService, AdditionalServiceManager) {
        var ctrl = this;

        ctrl.$onInit = function() {
            loadAll();

            $q.all([EventService.getSupportedLanguages(), AdditionalServiceManager.loadAll(ctrl.event.id)]).then(function(results) {
                var result = results[0].data;
                ctrl.allLanguages = result;
                ctrl.allLanguagesMapping = {};
                var locales = 0;
                angular.forEach(result, function(r) {
                    ctrl.allLanguagesMapping[r.value] = r;
                    locales |= r.value;
                });
                if(ctrl.event && !angular.isDefined(ctrl.event.locales)) {
                    ctrl.event.locales = locales;
                }

                var languages = _.filter(results[0].data, function(l) {return (l.value & ctrl.event.locales) === l.value});
                var titles = _.map(languages, function(l) {
                    return {
                        localeValue: l.value,
                        locale: l.locale,
                        type: 'TITLE',
                        value: '',
                        displayLanguage: l.displayLanguage
                    }
                });
                var descriptions = _.map(languages, function(l) {
                    return {
                        localeValue: l.value,
                        locale: l.locale,
                        type: 'DESCRIPTION',
                        value: '',
                        displayLanguage: l.displayLanguage
                    }
                });

                //-----------

                var result = results[1].data;
                var list = _.map(result, function(item) {
                    item.title = _.map(angular.copy(titles), fillExistingTexts(item.title));
                    item.description = _.map(angular.copy(descriptions), fillExistingTexts(item.description));
                    return item;
                });
                //ugly
                ctrl.event.additionalServices = list;
            });

        };

        ctrl.fieldUp = fieldUp;
        ctrl.fieldDown = fieldDown;
        ctrl.deleteFieldModal = deleteFieldModal;
        ctrl.editField = editField;
        ctrl.additionalServiceDescription = additionalServiceDescription;
        ctrl.additionalServiceType = additionalServiceType;
        ctrl.getCategoryDescription = getCategoryDescription;
        ctrl.openStats = openStats;

        function loadAll() {
            return EventService.getAdditionalFields(ctrl.event.shortName).then(function(result) {
                ctrl.additionalFields = result.data;
                ctrl.standardFieldsIndex = findStandardFieldsIndex(ctrl.additionalFields);
            });
        }

        function findStandardFieldsIndex(array) {
            return _.findIndex(array, function(f) {return f.order >= 0;});
        }

        function getCategoryDescription(categoryId) {
            var category = _.find(ctrl.event.ticketCategories, function(c) { return c.id === categoryId; });
            return category ? category.name : categoryId;
        }

        function fieldUp(index) {
            var targetId = ctrl.additionalFields[index].id;
            var targetPosition = ctrl.additionalFields[index].order;
            var promise = null;
            if(index > 0) {
                var prevTargetId = ctrl.additionalFields[index-1].id;
                promise = EventService.swapFieldPosition(ctrl.event.shortName, targetId, prevTargetId)
            } else {
                promise = EventService.moveField(ctrl.event.shortName, targetId, targetPosition - 1);
            }
            promise.then(function() {
                loadAll();
            });
        }

        function fieldDown(index) {
            var field = ctrl.additionalFields[index];
            var other = ctrl.additionalFields[index+1];
            var targetId = field.id;
            var nextTargetId = other.id;
            var promise;
            if(field.order < 0 && other.order >= 0) {
                promise = EventService.moveField(ctrl.event.shortName, targetId, 0);
            } else {
                promise = EventService.swapFieldPosition(ctrl.event.shortName, targetId, nextTargetId);
            }
            promise.then(function() {
                loadAll();
            });
        }

        function deleteFieldModal(field) {
            $uibModal.open({
                size: 'lg',
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/event-data-to-collect/delete-field-modal.html',
                controller: function($scope) {
                    $scope.field = field;
                    $scope.deleteField = function(id) {
                        EventService.deleteField(ctrl.event.shortName, id).then(function() {
                            loadAll();
                            $scope.$close(true);
                        });
                    }
                }
            });
        }

        function openStats(field) {
            $uibModal.open({
                size: 'md',
                template: '<restricted-values-statistics field="field" close-window="closeFn()" event-name="eventName"></restricted-values-statistics>',
                controller: function($scope) {
                    $scope.field = field;
                    $scope.eventName = ctrl.event.shortName;
                    $scope.closeFn = function() {
                        $scope.$close(true);
                    };
                },
                controllerAs: '$ctrl'
            });
        }

        function editField (event, addNew, field) {
            $uibModal.open({
                size:'lg',
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/event-data-to-collect/edit-field-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.event = event;
                    $scope.addNewField = addNew;
                    $scope.field = addNew ? {} : angular.copy(field);
                    if(!$scope.field.categoryIds) {
                        $scope.field.categoryIds = [];
                    }
                    $scope.fieldTypes = FIELD_TYPES;
                    $scope.joinTitle = function(titles) {
                        return titles.map(function(t) { return t.value;}).join(' / ')
                    };
                    $scope.cancel = function() {
                        $scope.$dismiss();
                    };

                    EventService.getDynamicFieldTemplates().success(function(result) {
                        $scope.dynamicFieldTemplates = result;
                    });

                    $scope.addFromTemplate = function(template) {
                        $scope.field.name = template.name;
                        $scope.field.type = template.type;
                        $scope.field.restrictedValues = _.map(template.restrictedValues, function(v) {return {value: v}});
                        $scope.field.description = template.description;
                        $scope.field.maxLength = template.maxLength;
                        $scope.field.minLength = template.minLength;
                        $scope.field.required = template.required;
                        $scope.field.disabledValues = [];
                        $scope.field.categoryIds = [];
                    };

                    $scope.isRestrictedValueEnabled = isRestrictedValueEnabled;
                    $scope.toggleEnabled = toggleEnabled;
                    $scope.toggleAllCategoriesSelected = toggleAllCategoriesSelected;
                    $scope.isCategorySelected = isCategorySelected;


                    function isRestrictedValueEnabled(restrictedValue, field) {
                        return field.disabledValues.indexOf(restrictedValue) === -1;
                    }

                    function toggleAllCategoriesSelected() {
                        $scope.field.categoryIds = [];
                    }

                    function isCategorySelected(category) {
                        return field.categoryIds.indexOf(category.id) > -1;
                    }

                    function toggleEnabled(restrictedValue, field) {
                        if(isRestrictedValueEnabled(restrictedValue, field)) {
                            field.disabledValues.push(restrictedValue);
                        } else {
                            field.disabledValues.splice(field.disabledValues.indexOf(restrictedValue), 1);
                        }
                    }

                    //
                    EventService.getSupportedLanguages().then(function(res) {
                        var result = res.data;
                        $scope.allLanguages = result;
                        $scope.allLanguagesMapping = {};
                        angular.forEach(result, function(r) {
                            $scope.allLanguagesMapping[r.value] = r;
                        });
                    });

                    //


                    $scope.moveRestrictedValue = function(currentIndex, up) {
                        var newIdx = currentIndex + (up ? -1 : 1);
                        var selectedObj = $scope.field.restrictedValues[currentIndex];
                        var targetObj = $scope.field.restrictedValues[newIdx];
                        $scope.field.restrictedValues[newIdx] = selectedObj;
                        $scope.field.restrictedValues[currentIndex] = targetObj;
                    };

                    $scope.addRestrictedValue = function() {
                        var field = $scope.field;
                        var arr = field.restrictedValues || [];
                        arr.push({isNew:true});
                        field.restrictedValues = arr;
                    };
                    $scope.isLanguageSelected = function(lang, selectedLanguages) {
                        return (selectedLanguages & lang) > 0;
                    };

                    $scope.editField = function (form, field) {
                        if (angular.isDefined(field.id)) {
                            EventService.updateField(ctrl.event.shortName, field).then(function () {
                                return loadAll();
                            }).then(function () {
                                $scope.$close(true);
                            });
                        } else {
                            var duplicate = false;
                            angular.forEach(ctrl.additionalFields, function (f) {
                                if (f.name === field.name) {
                                    form['name'].$setValidity(ERROR_CODES.DUPLICATE, false);
                                    form['name'].$setTouched();
                                    duplicate = true;
                                }
                            })
                            if (!duplicate) {
                                EventService.addField(ctrl.event.shortName, field).then(function (result) {
                                    validationErrorHandler(result, form, form).then(function () {
                                        $scope.$close(true);
                                    });
                                }, errorHandler).then(loadAll);
                            }
                        }
                    };
                }
            });
        }

        function validationErrorHandler(result, form, fieldsContainer) {
            return $q(function(resolve, reject) {
                if(result.data['errorCount'] === 0) {
                    resolve(result);
                } else {
                    _.forEach(result.data.validationErrors, function(error) {
                        var field = fieldsContainer[error.fieldName];
                        if(angular.isDefined(field)) {
                            if (error.code === ERROR_CODES.DUPLICATE) {
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

         function additionalServiceDescription(event, id) {
            var service = _.find(event.additionalServices, function (as) { return as.id === id;});
            if(service) {
                return service.title.map(function(t) { return t.value; }).join(' / ');
            }
            return "#"+id;
        }

        function additionalServiceType(event, id) {
            var service = _.find(event.additionalServices, function (as) { return as.id === id;});
            return service ? service.type : null;
        }
    }

    function RestrictedValuesStatisticsCtrl(EventService) {
        var ctrl = this;

        function getData() {
            EventService.getRestrictedValuesStats(ctrl.eventName, ctrl.field.id)
                .then(function (res) {
                    ctrl.field.stats = res.data;
                    ctrl.loading = false;
                }, function () {
                    ctrl.loading = false;
                });
        }

        ctrl.$onInit = function() {
            ctrl.loading = true;
            getData();
        };

        ctrl.refresh = function() {
            ctrl.loading = true;
            getData();
        };
    }
})();