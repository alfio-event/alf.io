(function () {
    "use strict";
    angular.module('alfio-additional-services', ['adminServices', 'ui.bootstrap'])
        .directive('additionalServices', [function() {
            return {
                scope: {
                    selectedLanguages: '=',
                    onModification: '&',
                    eventId: '=',
                    eventShortName: '=',
                    eventStartDate: '=',
                    eventIsFreeOfCharge: '=',
                    supportsQuantity: '=',
                    title: '@',
                    icon: '@',
                    type: '@'
                },
                bindToController: true,
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/additional-service/additional-services.html',
                controller: 'AdditionalServicesController',
                controllerAs: 'ctrl'
            };
        }])
        .directive('editAdditionalService', function() {
            return {
                scope: {
                    item: '=editingItem',
                    titles: '=',
                    descriptions: '=',
                    onEditComplete: '&',
                    onDismiss: '&',
                    eventStartDate: '=',
                    selectedLanguages: '=',
                    title:'<',
                    type:'<',
                    supportsQuantity: '='
                },
                bindToController: true,
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/additional-service/edit-additional-service.html',
                controller: 'EditAdditionalServiceController',
                controllerAs: 'ctrl'
            };
        })
        .filter('formatDateTimeModification', [function() {
            return function(dateTimeModification) {
                if(angular.isDefined(dateTimeModification)) {
                    return dateTimeModification.date + ' ' + dateTimeModification.time;
                }
                return moment().format('YYYY-MM-DD HH:mm');
            };
        }])
        .filter('showMissingASText', function() {
            return function(text) {
                if(text.value && text.value !== '') {
                    return text.value;
                } else {
                    return '!! missing '+text.locale + ' !!';
                }
            };
        })
        .controller('AdditionalServicesController', AdditionalServicesController)
        .controller('EditAdditionalServiceController', EditAdditionalServiceController)
        .service('AdditionalServiceManager', AdditionalServiceManager);

    function AdditionalServicesController(AdditionalServiceManager, EventService, $q, $uibModal) {
        var self = this;

        self.propagateChanges = angular.isDefined(self.eventId);

        $q.all([EventService.getSupportedLanguages(), AdditionalServiceManager.loadAll(self.eventId), AdditionalServiceManager.getUseCount(self.eventId)]).then(function(results) {
            var languages = _.filter(results[0].data, function(l) {return (l.value & self.selectedLanguages) === l.value});
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

            var result = results[1].data;
            self.titles = titles;
            self.descriptions = descriptions;
            self.list = _.map(result, function(item) {
                item.title = _.map(angular.copy(titles), fillExistingTexts(item.title));
                item.description = _.map(angular.copy(descriptions), fillExistingTexts(item.description));
                return item;
            });
            self.displayList = buildDisplayList(self.list);
            self.additionalServiceUseCount = results[2].data;
            var countStatus = 0;
            self.displayList.map(function(i) {
                if (Object.values(self.additionalServiceUseCount[i.id] || {}).some((v) => v > 0)) {
                    countStatus++;
                }
            });
            self.allowDownload = countStatus > 0;
        });

        function fillExistingTexts(texts) {
            return function(t) {
                var existing = _.find(texts, function(e) {return e.locale === t.locale});
                return existing ? angular.extend({displayLanguage: t.displayLanguage}, existing) : t;
            }
        }

        self.zipTitleAndDescription = function(item) {
            return _.zip(item.title, item.description);
        };

        self.getSold = function(additionalServiceId) {
            var statusCount = self.additionalServiceUseCount[additionalServiceId] || {};
            var acquired = statusCount['ACQUIRED'] || 0;
            var checkedIn = statusCount['CHECKED_IN'] || 0;
            var toBePaid = statusCount['TO_BE_PAID'] || 0;
            return [
                {c : acquired, s: 'Acquired' },
                {c: checkedIn, s: 'Checked in'},
                {c: toBePaid, s: 'To be paid on site'}
            ];
        };

        self.countSold = function(additionalServiceId) {
            var soldCount = self.getSold(additionalServiceId);
            return soldCount.reduce(function(acc, cur) {return acc + cur.c}, 0);
        };

        self.formatCountSold = function(additionalServiceId) {
            var composition = self.getSold(additionalServiceId).filter(function(v) {return v.c > 0})
            var showBreakDown = composition.length > 1;
            var total = self.countSold(additionalServiceId);
            var afterText = '';
            if (showBreakDown) {
                afterText = ' (of which ';
                afterText += composition.map((function(v) { return v.s + ': ' + v.c})).join(', ');
                afterText += ')';
            }
            return total + afterText;
        };

        self.addedItem = undefined;

        self.edit = function(item) {
            self.editActive = true;
            var parentCtrl = self;
            var modal = $uibModal.open({
                size:'lg',
                template:'<edit-additional-service data-type="ctrl.type" data-supports-quantity="ctrl.supportsQuantity" data-title="ctrl.title" data-editing-item="ctrl.item" data-titles="ctrl.titles" data-descriptions="ctrl.descriptions" data-on-edit-complete="ctrl.onEditComplete(item)" data-on-dismiss="ctrl.onDismiss()" data-event-start-date="ctrl.eventStartDate"></edit-additional-service>',
                backdrop: 'static',
                controller: function() {
                    var ctrl = this;
                    ctrl.item = angular.copy(item);
                    if (ctrl.item && ctrl.item.availableQuantity === -1) {
                        delete ctrl.item.availableQuantity;
                    }
                    ctrl.title = parentCtrl.title;
                    ctrl.type = parentCtrl.type;
                    ctrl.supportsQuantity = parentCtrl.supportsQuantity;
                    ctrl.selectedLanguages = parentCtrl.selectedLanguages;
                    ctrl.titles = _.filter(angular.copy(parentCtrl.titles), function(t) {
                        return (t.localeValue & ctrl.selectedLanguages) === t.localeValue;
                    });
                    ctrl.descriptions = _.filter(angular.copy(parentCtrl.descriptions), function(d) {
                        return (d.localeValue & ctrl.selectedLanguages) === d.localeValue;
                    });
                    ctrl.onEditComplete = function(item) {
                        modal.close(item);
                    };
                    ctrl.onDismiss = function() {
                        modal.dismiss();
                    };
                    ctrl.eventStartDate = parentCtrl.eventStartDate;
                },
                bindToController: true,
                controllerAs: 'ctrl'
            });
            modal.result.then(function(editedItem) {
                self.onEditComplete(editedItem, item);
            }, function() {
                self.onDismiss();
            });
        };

        self.onEditComplete = function(item, originalItem) {

            var afterUpdate = function(r, originalItem) {
                if (!_.find(self.list, function (i) {
                        return (self.propagateChanges && i.id == r.id) || i === originalItem;
                    })) {
                    r.ordinal = self.list.length;
                    self.list.push(r);
                } else if(self.list.indexOf(originalItem) >= 0){
                    self.list[self.list.indexOf(originalItem)] = r;
                }
                editComplete();
            };

            if(self.propagateChanges) {
                AdditionalServiceManager.save(self.eventId, item).then(function(result) {
                    //HACK
                    if(originalItem && !angular.isDefined(originalItem.id)) {
                        result.data.description = originalItem.description;
                        result.data.title = originalItem.title;
                        result.data.zippedTitleAndDescriptions = originalItem.zippedTitleAndDescriptions;
                    }

                    afterUpdate(result.data, originalItem);
                });
            } else {
                afterUpdate(item, originalItem);
            }

        };

        self.onDismiss = function() {
            editComplete();
        };

        self.delete = function(item) {
            var afterDelete = function(r) {
                self.list = _.filter(self.list, function (i) {
                    return i !== r;
                });
                editComplete();
            };

            if(self.propagateChanges) {
                AdditionalServiceManager.remove(self.eventId, item).then(function() {
                    afterDelete(item);
                });
            } else {
                afterDelete(item);
            }
        };

        var buildDisplayList = function(list) {
            return _.map(list, function(item) {
                item.zippedTitleAndDescriptions = _.zip(item.title, item.description);
                return item;
            });
        };

        var editComplete = function() {
            self.displayList = buildDisplayList(self.list);
            self.onModification({'additionalServices': self.list});
            self.editingItem = undefined;
            self.editActive = false;
        };
    }

    AdditionalServicesController.$inject = ['AdditionalServiceManager', 'EventService', '$q','$uibModal'];

    function EditAdditionalServiceController(ValidationService, AdditionalServiceManager, $q) {
        var ctrl = this;
        if(!ctrl.item) {
            ctrl.item = {
                maxQtyPerOrder: 1,
                priceInCents: 0,
                fixPrice: ctrl.type === 'SUPPLEMENT',
                inception: {},
                expiration: {}
            };
            if(angular.isDefined(ctrl.eventStartDate)) {
                var d = moment.max(moment(ctrl.eventStartDate), moment().startOf('hour'));
                var now = moment().startOf('hour');
                ctrl.item = angular.extend(ctrl.item, {
                    inception : {
                        date: now.format('YYYY-MM-DD'),
                        time: now.format('HH:mm')
                    },
                    expiration : {
                        date: d.format('YYYY-MM-DD'),
                        time: d.format('HH:mm')
                    }
                });
            }
        }

        if(!angular.isDefined(ctrl.item.title)) {
            ctrl.item.title = ctrl.titles;
            ctrl.item.description = ctrl.descriptions;
        } else {
            ctrl.item.title = _.map(ctrl.titles, function(t) {
                var existing = _.find(ctrl.item.title, function(e) {return e.locale === t.locale});
                return existing ? angular.extend({displayLanguage: t.displayLanguage}, existing) : t;
            });
            ctrl.item.description = _.map(ctrl.descriptions, function(d) {
                var existing = _.find(ctrl.item.description, function(e) {return e.locale === d.locale});
                return existing ? angular.extend({displayLanguage: d.displayLanguage}, existing) : d;
            });
        }

        ctrl.item.zippedTitleAndDescriptions = _.zip(ctrl.item.title, ctrl.item.description);

        ctrl.vatTypes = [
            {key: 'INHERITED', value:'Use event settings'},
            {key: 'NONE', value:'Do not apply VAT'}/*,
             {key: 'CUSTOM_INCLUDED', value::'Price VAT inclusive, apply special VAT'},
             {key: 'CUSTOM_EXCLUDED', value:: 'Price VAT exclusive, apply special VAT'}*/];
        ctrl.types = ['DONATION'];
        ctrl.supplementPolicies = [
            {key: 'MANDATORY_ONE_FOR_TICKET', value:'Make the additional service mandatory: one for each ticket bought'},
            {key: 'OPTIONAL_UNLIMITED_AMOUNT', value:'Apply no limit on the quantity'},
            {key: 'OPTIONAL_MAX_AMOUNT_PER_TICKET', value:'Apply a limit for each ticket bought'},
            {key: 'OPTIONAL_MAX_AMOUNT_PER_RESERVATION', value:'Apply a limit for the whole reservation'}
        ];
        ctrl.save = function() {
            if(ctrl.additionalServiceForm.$valid) {
                ctrl.item.type = ctrl.type; // fix type
                ctrl.item.availableQuantity = ctrl.item.availableQuantity || -1;
                ValidationService.validationPerformer($q, AdditionalServiceManager.validate, ctrl.item, ctrl.additionalServiceForm).then(function() {
                    ctrl.onEditComplete({'item': ctrl.item});
                }, angular.noop);
            }
        };

        ctrl.cancel = function() {
            ctrl.onDismiss();
        };
    }

    EditAdditionalServiceController.$inject = ['ValidationService', 'AdditionalServiceManager', '$q'];

    function AdditionalServiceManager($http, HttpErrorHandler, $q) {
        return {
            loadAll: function(eventId) {
                if(angular.isDefined(eventId)) {
                    return $http.get('/admin/api/event/'+eventId+'/additional-services/').error(HttpErrorHandler.handle);
                }
                var deferred = $q.defer();
                deferred.resolve({data:[]});
                return deferred.promise;
            },
            getUseCount: function(eventId) {
                return $http.get('/admin/api/event/'+eventId+'/additional-services/count').error(HttpErrorHandler.handle);
            },
            save: function(eventId, additionalService) {
                return (angular.isDefined(additionalService.id)) ? this.update(eventId, additionalService) : this.create(eventId, additionalService);
            },
            create: function(eventId, additionalService) {
                return $http.post('/admin/api/event/'+eventId+'/additional-services/', additionalService).error(HttpErrorHandler.handle);
            },
            update: function(eventId, additionalService) {
                return $http['put']('/admin/api/event/'+eventId+'/additional-services/'+additionalService.id, additionalService).error(HttpErrorHandler.handle);
            },
            remove: function(eventId, additionalService) {
                return $http['delete']('/admin/api/event/'+eventId+'/additional-services/'+additionalService.id).error(HttpErrorHandler.handle);
            },
            validate: function(additionalService) {
                return $http.post('/admin/api/additional-services/validate', additionalService).error(HttpErrorHandler.handle);
            }

        }
    }

    AdditionalServiceManager.$inject = ['$http', 'HttpErrorHandler', '$q'];

})();