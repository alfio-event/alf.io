(function () {
    "use strict";
    angular.module('alfio-additional-services', ['adminServices', 'ui.bootstrap'])
        .directive('additionalServices', ['$uibModal', function() {
            return {
                scope: {
                    selectedLanguages: '=',
                    availableLanguages: '=',
                    onModification: '&'
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
                    onDismiss: '&'
                },
                bindToController: true,
                templateUrl: '/resources/angular-templates/admin/partials/event/fragment/edit-additional-service.html',
                controller: 'EditAdditionalServiceController',
                controllerAs: 'ctrl'
            };
        })
        .controller('AdditionalServicesController', AdditionalServicesController)
        .controller('EditAdditionalServiceController', EditAdditionalServiceController)
        .service('AdditionalServiceManager', AdditionalServiceManager);

    function AdditionalServicesController() {
        var self = this;
        self.zipTitleAndDescription = function(item) {
            return _.zip(item.title, item.description);
        };

        self.list = [];
        self.displayList = [];
        self.addedItem = undefined;

        self.edit = function(item) {
            self.editingItem = item;
            self.editActive = true;
        };

        self.onEditComplete = function(item) {
            self.list.push(item);
            self.displayList = buildDisplayList(self.list);
            self.onModification({'additionalServices': self.list});
            editComplete();
        };

        self.onDismiss = function() {
            editComplete();
        };

        var buildDisplayList = function(list) {
            return _.map(list, function(item) {
                var i2 = angular.copy(item);
                i2.zippedTitleAndDescriptions = _.zip(i2.title, i2.description);
                return i2;
            });
        };

        var editComplete = function() {
            self.editingItem = undefined;
            self.editActive = false;
        };
    }

    AdditionalServicesController.$inject = [/*'AdditionalServiceManager'*/];

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
                return $q.resolve([]);
            },
            create: function(eventId, additionalService) {
                return $http.post('/admin/api/event/'+eventId+'/additional-services/', additionalService).error(HttpErrorHandler.handle);
            },
            update: function(eventId, additionalService) {
                return $http['put']('/admin/api/event/'+eventId+'/additional-services/'+additionalService.id, additionalService).error(HttpErrorHandler.handle);
            },
            validate: function(additionalService) {
                return $http.post('/admin/api/additional-services/validate', additionalService).error(HttpErrorHandler.handle);
            }

        }
    }

    AdditionalServiceManager.$inject = ['$http', 'HttpErrorHandler', '$q'];

})();