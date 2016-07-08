(function () {
    "use strict";
    angular.module('alfio-additional-services', ['adminServices', 'ui.bootstrap'])
        .directive('additionalServices', [function() {
            return {
                scope: {
                    selectedLanguages: '=',
                    availableLanguages: '=',
                    onModification: '&',
                    eventId: '=',
                    eventStartDate: '=',
                    eventIsFreeOfCharge: '='
                },
                bindToController: true,
                templateUrl: '/resources/angular-templates/admin/partials/event/fragment/additional-services.html',
                controller: 'AdditionalServicesController',
                controllerAs: 'asCtrl'
            };
        }])
        .directive('editAdditionalService', function() {
            return {
                scope: {
                    item: '=editingItem',
                    availableLanguages: '=',
                    selectedLanguages: '=',
                    onEditComplete: '&',
                    onDismiss: '&',
                    eventStartDate: '='
                },
                bindToController: true,
                templateUrl: '/resources/angular-templates/admin/partials/event/fragment/edit-additional-service.html',
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
        .controller('AdditionalServicesController', AdditionalServicesController)
        .controller('EditAdditionalServiceController', EditAdditionalServiceController)
        .service('AdditionalServiceManager', AdditionalServiceManager);

    function AdditionalServicesController(AdditionalServiceManager) {
        var self = this;

        self.propagateChanges = angular.isDefined(self.eventId);

        self.zipTitleAndDescription = function(item) {
            return _.zip(item.title, item.description);
        };

        AdditionalServiceManager.loadAll(self.eventId).then(function(success) {
            var result = success.data;
            self.list = result;
            self.displayList = buildDisplayList(result);
        });
        self.addedItem = undefined;

        self.edit = function(item) {
            self.editingItem = item;
            self.editActive = true;
        };

        self.onEditComplete = function(item) {

            var afterUpdate = function(r) {
                if (!_.find(self.list, function (i) {
                        return (self.propagateChanges && i.id == r.id) || i === r;
                    })) {
                    r.ordinal = self.list.length;
                    self.list.push(r);
                }
                editComplete();
            };

            if(self.propagateChanges) {
                AdditionalServiceManager.save(self.eventId, item).then(function(result) {
                    afterUpdate(result.data);
                });
            } else {
                afterUpdate(item);
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

    AdditionalServicesController.$inject = ['AdditionalServiceManager'];

    function EditAdditionalServiceController(ValidationService, AdditionalServiceManager, $q) {
        var ctrl = this;
        if(!ctrl.item) {
            ctrl.item = {
                availableQuantity: -1,
                maxQtyPerOrder: 1,
                priceInCents: 0,
                fixPrice: false,
                inception: {},
                expiration: {}
            };
            if(angular.isDefined(ctrl.eventStartDate)) {
                var d = moment.max(moment(ctrl.eventStartDate), moment().startOf('hour'));
                ctrl.item = angular.extend(ctrl.item, {
                    expiration : {
                        date: d.format('YYYY-MM-DD'),
                        time: d.format('HH:mm')
                    }
                });
            }
        }

        if(!angular.isDefined(ctrl.item.title)) {
            var languages = _.filter(ctrl.availableLanguages, function(l) {return (l.value & ctrl.selectedLanguages) === l.value});
            ctrl.item.title = _.map(languages, function(l) {
                return {
                    locale: l.locale,
                    type: 'TITLE',
                    value: '',
                    displayLanguage: l.displayLanguage
                }
            });
            ctrl.item.description = _.map(languages, function(l) {
                return {
                    locale: l.locale,
                    type: 'DESCRIPTION',
                    value: '',
                    displayLanguage: l.displayLanguage
                }
            });
        }

        ctrl.item.zippedTitleAndDescriptions = _.zip(ctrl.item.title, ctrl.item.description);

        ctrl.vatTypes = [
            {key: 'INHERITED', value:'Use event settings'},
            {key: 'NONE', value:'Do not apply VAT'}/*,
             {key: 'CUSTOM_INCLUDED', value::'Price VAT inclusive, apply special VAT'},
             {key: 'CUSTOM_EXCLUDED', value:: 'Price VAT exclusive, apply special VAT'}*/];
        ctrl.types = ['DONATION'];
        ctrl.save = function() {
            if(ctrl.additionalServiceForm.$valid) {
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