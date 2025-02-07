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
        'vat:eu': 'EU VAT',
        'input:dateOfBirth': 'Date of Birth'
    };

    angular.module('adminApplication')
        .component('additionalFields', {
            controller: ['$uibModal', '$q', 'EventService', 'AdditionalServiceManager', 'AdditionalFieldsService', EventDataToCollectCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/additional-fields-to-collect/additional-fields-to-collect.html',
            bindings: {
                event: '<',
                subscriptionDescriptor: '<'
            }
        }).component('restrictedValuesStatistics', {
            controller: ['EventService', 'AdditionalFieldsService', RestrictedValuesStatisticsCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/additional-fields-to-collect/restricted-values-statistics.html',
            bindings: {
                field: '<',
                closeWindow: '&',
                purchaseContextType: '<',
                publicIdentifier: '<'
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
        }).service('AdditionalFieldsService', ['$http', 'HttpErrorHandler', AdditionalFieldsService]);


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


    function EventDataToCollectCtrl($uibModal, $q, EventService, AdditionalServiceManager, AdditionalFieldsService) {
        var ctrl = this;

        ctrl.$onInit = function() {
            loadAll();

            if (ctrl.event) {
                loadEventLanguages();
            } else {
                EventService.getSupportedLanguages().then(function (result) {
                    ctrl.allLanguages = result.data;
                    ctrl.allLanguagesMapping = {};
                    ctrl.selectedLanguages = Object.keys(ctrl.subscriptionDescriptor.title).map(function(key) {
                        return _.find(ctrl.allLanguages, function(lang) {
                            return lang.locale === key;
                        });
                    });
                    var locales = 0;
                    angular.forEach(ctrl.selectedLanguages, function(r) {
                        ctrl.allLanguagesMapping[r.value] = r;
                        locales |= r.value;
                    });
                    ctrl.selectedLocales = locales;
                })
            }

        };
        ctrl.purchaseContextType = ctrl.event ? 'event' : 'subscription';
        ctrl.publicIdentifier = ctrl.event ? ctrl.event.shortName : ctrl.subscriptionDescriptor.id;

        ctrl.fieldUp = fieldUp;
        ctrl.fieldDown = fieldDown;
        ctrl.deleteFieldModal = deleteFieldModal;
        ctrl.editField = editField;
        ctrl.additionalServiceDescription = additionalServiceDescription;
        ctrl.additionalServiceType = additionalServiceType;
        ctrl.getCategoryDescription = getCategoryDescription;
        ctrl.openStats = openStats;

        function loadAll() {
            return AdditionalFieldsService.getAdditionalFields(ctrl.purchaseContextType, ctrl.publicIdentifier).then(function(result) {
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
                promise = AdditionalFieldsService.swapFieldPosition(ctrl.purchaseContextType, ctrl.publicIdentifier, targetId, prevTargetId)
            } else {
                promise = AdditionalFieldsService.moveField(ctrl.purchaseContextType, ctrl.publicIdentifier, targetId, targetPosition - 1);
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
                promise = AdditionalFieldsService.moveField(ctrl.purchaseContextType, ctrl.publicIdentifier, targetId, 0);
            } else {
                promise = AdditionalFieldsService.swapFieldPosition(ctrl.purchaseContextType, ctrl.publicIdentifier, targetId, nextTargetId);
            }
            promise.then(function() {
                loadAll();
            });
        }

        function deleteFieldModal(field) {
            $uibModal.open({
                size: 'lg',
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/additional-fields-to-collect/delete-field-modal.html',
                controller: function($scope) {
                    $scope.field = field;
                    $scope.deleteField = function(id) {
                        AdditionalFieldsService.deleteField(ctrl.purchaseContextType, ctrl.publicIdentifier, id).then(function() {
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
                template: '<restricted-values-statistics field="field" close-window="closeFn()" purchase-context-type="purchaseContextType" public-identifier="publicIdentifier"></restricted-values-statistics>',
                controller: function($scope) {
                    $scope.field = field;
                    $scope.purchaseContextType = ctrl.purchaseContextType;
                    $scope.publicIdentifier = ctrl.publicIdentifier;
                    $scope.closeFn = function() {
                        $scope.$close(true);
                    };
                },
                controllerAs: '$ctrl'
            });
        }

        function loadEventLanguages() {
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
                ctrl.selectedLocales = ctrl.event.locales;
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
        }

        function editField (addNew, field) {
            $uibModal.open({
                size:'lg',
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/additional-fields-to-collect/edit-field-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.event = ctrl.event;
                    $scope.subscriptionDescriptor = ctrl.subscriptionDescriptor;
                    $scope.locales = ctrl.selectedLocales;
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

                    AdditionalFieldsService.getDynamicFieldTemplates(ctrl.purchaseContextType, ctrl.publicIdentifier).success(function(result) {
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
                    $scope.allLanguages = ctrl.allLanguages;
                    $scope.allLanguagesMapping = ctrl.allLanguagesMapping;

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
                            AdditionalFieldsService.updateField(ctrl.purchaseContextType, ctrl.publicIdentifier, field).then(function () {
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
                                AdditionalFieldsService.addField(ctrl.purchaseContextType, ctrl.publicIdentifier, field).then(function (result) {
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
                            if (error.code === ERROR_CODES.DUPLICATE || error.code === 'pattern') {
                                field.$setValidity(error.code, false);
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

    function RestrictedValuesStatisticsCtrl(EventService, AdditionalFieldsService) {
        var ctrl = this;

        function getData() {
            AdditionalFieldsService.getRestrictedValuesStats(ctrl.purchaseContextType, ctrl.publicIdentifier, ctrl.field.id)
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

    function AdditionalFieldsService($http, HttpErrorHandler) {
        return {
            getAdditionalFields: function(purchaseContextType, publicIdentifier) {
                return $http.get('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field').error(HttpErrorHandler.handle);
            },
            getRestrictedValuesStats: function(purchaseContextType, publicIdentifier, id) {
                return $http.get('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field/'+id+'/stats').error(HttpErrorHandler.handle);
            },
            saveFieldDescription: function(purchaseContextType, publicIdentifier, fieldDescription) {
                return $http.post('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field/descriptions', fieldDescription);
            },
            addField: function(purchaseContextType, publicIdentifier, field) {
                return $http.post('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field/new', field).error(HttpErrorHandler.handle);
            },
            updateField: function(purchaseContextType, publicIdentifier, toUpdate) {

                //new restrictedValues are complex objects, already present restrictedValues are plain string
                if(toUpdate && toUpdate.restrictedValues && toUpdate.restrictedValues.length > 0) {
                    var res = [];
                    for(var i = 0; i < toUpdate.restrictedValues.length; i++) {
                        res.push(toUpdate.restrictedValues[i].isNew ? toUpdate.restrictedValues[i].value: toUpdate.restrictedValues[i]);
                    }
                    toUpdate.restrictedValues = res;
                }
                //

                return $http['post']('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field/'+toUpdate.id, toUpdate);
            },
            deleteField: function(purchaseContextType, publicIdentifier, id) {
                return $http['delete']('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field/'+id);
            },
            swapFieldPosition: function(purchaseContextType, publicIdentifier, id1, id2) {
                return $http.post('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field/swap-position/'+id1+'/'+id2, null);
            },
            moveField: function(purchaseContextType, publicIdentifier, id, position) {
                return $http.post('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field/set-position/'+id, null, {
                    params: {
                        newPosition: position
                    }
                });
            },
            getDynamicFieldTemplates: function(purchaseContextType, publicIdentifier) {
                return $http['get']('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field/templates').error(HttpErrorHandler.handle);
            },
        }
    }
})();