(function () {
    "use strict";
    var baseServices = angular.module('adminServices', ['angular-growl' , 'ngAnimate']);

    baseServices.config(['$httpProvider', function($httpProvider) {
        var token = $("meta[name='_csrf']").attr("content");
        var header = $("meta[name='_csrf_header']").attr("content");
        $httpProvider.defaults.headers.post['X-Requested-With'] = 'XMLHttpRequest';
        $httpProvider.defaults.headers.post[header] = token;

        $httpProvider.defaults.headers.patch = angular.copy($httpProvider.defaults.headers.post);
        $httpProvider.defaults.headers.put = angular.copy($httpProvider.defaults.headers.post);
        $httpProvider.defaults.headers.delete = angular.copy($httpProvider.defaults.headers.post);

        $httpProvider.interceptors.push(['$rootScope', '$location', '$q', function($rootScope, $location, $q) {
            return {
                responseError: function(rejection) {//thanks to https://github.com/witoldsz/angular-http-auth/blob/master/src/http-auth-interceptor.js
                    var status = rejection.status;
                    if(status === 401) {
                        $rootScope.$emit('ErrorNotLoggedIn');
                        return false;
                    }
                    return $q.reject(rejection);
                }
            };
        }]);
    }]);

    baseServices.service('PaymentProxyService', function($http, HttpErrorHandler) {
        return {
            getAllProxies : function(orgId) {
                return $http.get('/admin/api/paymentProxies/'+orgId).error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service('PurchaseContextService', function(EventService, SubscriptionService, AdminReservationService, $http, HttpErrorHandler) {
        return {
            findAllReservations: function(type, contextName, page, search, status) {
                if(type === 'event') {
                    return EventService.findAllReservations(contextName, page, search, status);
                } else {
                    return SubscriptionService.findAllReservations(contextName, page, search, status);
                }
            },
            findAllPayments: function(type, contextName, page, search) {
                return $http.get('/admin/api/payments/'+ type + '/' + contextName + '/list', {params: {page: page, search: search}});
            },
            editPaymentDetails: function(reservationId, purchaseContextType, publicIdentifier) {
                var infoLoader = AdminReservationService.paymentInfo(purchaseContextType, publicIdentifier, reservationId);
                return EventService.editTransactionModal(reservationId, 'edit', infoLoader, function(res) {
                    return $http.put('/admin/api/payments/'+ purchaseContextType + '/' + publicIdentifier + '/reservation/' + reservationId, res)
                        .error(HttpErrorHandler.handle);
                });
            }
        };
    });

    baseServices.service('EventService', function($http, HttpErrorHandler, $uibModal, $window, $rootScope, $q, LocationService, $timeout) {

        function copyGeoLocation(event) {
            event.latitude = event.geolocation.latitude;
            event.longitude = event.geolocation.longitude;
            event.zoneId = event.geolocation.timeZone;
        }

        var service = {
            data: {},
            getEventsCount: function () {
                return $http.get('/admin/api/events-count').error(HttpErrorHandler.handle);
            },
            getAllEvents : function() {
                return $http.get('/admin/api/events').error(HttpErrorHandler.handle);
            },
            getAllActiveEvents : function() {
                return $http.get('/admin/api/active-events').error(HttpErrorHandler.handle);
            },
            getAllExpiredEvents : function() {
                return $http.get('/admin/api/expired-events').error(HttpErrorHandler.handle);
            },
            getEvent: function(name) {
                return $http.get('/admin/api/events/'+name).success(function(result) {
                    $rootScope.$emit('EventLoaded', result.event);
                }).error(HttpErrorHandler.handle);
            },
            getEventById: function(eventId) {
                return $http.get('/admin/api/events/id/'+eventId).success(function(result) {
                    $rootScope.$emit('EventLoaded', result);
                }).error(HttpErrorHandler.handle);
            },
            checkEvent : function(event) {
                return $http['post']('/admin/api/events/check', event).error(HttpErrorHandler.handle);
            },
            createEvent : function(event) {
                copyGeoLocation(event);
                return $http['post']('/admin/api/events/new', event).error(HttpErrorHandler.handle);
            },
            toggleActivation: function(id, active) {
                return $http['put']('/admin/api/events/'+id+'/status?active='+active).error(HttpErrorHandler.handle);
            },
            updateEventHeader: function(eventHeader) {
                var update = function() {
                    return $http['post']('/admin/api/events/'+eventHeader.id+'/header/update', eventHeader).error(HttpErrorHandler.handle);
                };
                if(eventHeader.format === 'ONLINE') {
                    return update();
                }
                //
                if(eventHeader.geolocation && eventHeader.geolocation.latitude) {
                    copyGeoLocation(eventHeader);
                    //
                    return update();
                } else {
                    return LocationService.clientGeolocate(eventHeader.location).then(function(geo) {
                        eventHeader.latitude = geo.latitude;
                        eventHeader.longitude = geo.longitude;
                        eventHeader.zoneId = geo.timeZone;
                        return update();
                    })
                }

            },
            getTicketsForCategory: function(event, ticketCategory, page, search) {
              return $http.get('/admin/api/events/'+event.shortName+'/category/'+ticketCategory.id+'/ticket', {params: {page: page, search: search}}).error(HttpErrorHandler.handle);
            },
            updateEventPrices: function(eventPrices) {
                return $http['post']('/admin/api/events/'+eventPrices.id+'/prices/update', eventPrices).error(HttpErrorHandler.handle);
            },
            saveTicketCategory: function(event, ticketCategory) {
                var url = angular.isDefined(ticketCategory.id) ? ('/admin/api/events/' + event.id + '/categories/' + ticketCategory.id + '/update') : ('/admin/api/events/' + event.id + '/categories/new');
                return $http['post'](url, ticketCategory).error(HttpErrorHandler.handle);
            },
            toggleTicketLocking: function(event, ticket, category) {
                return $http['put']('/admin/api/events/' + event.shortName + '/categories/' + category.id + '/tickets/' + ticket.id +'/toggle-locking');
            },
            reallocateOrphans : function(srcCategory, targetCategoryId, eventId) {
                return $http['put']('/admin/api/events/reallocate', {
                    srcCategoryId: srcCategory.id,
                    targetCategoryId: targetCategoryId,
                    eventId: eventId
                }).error(HttpErrorHandler.handle);
            },
            deleteCategory: function(category, event) {

                var modal = $uibModal.open({
                    size:'md',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/delete-category-modal.html',
                    backdrop: 'static',
                    controller: function($scope) {
                        $scope.cancel = function() {
                            modal.dismiss('canceled');
                        };

                        $scope.deleteCategory = function() {
                            $http['delete']('/admin/api/events/'+event.shortName+'/category/'+category.id)
                                .error(HttpErrorHandler.handle)
                                .then(function() {
                                    modal.close('OK');
                                });
                        };
                        $scope.category = category;
                    }
                });
                return modal.result;
            },
            unbindTickets: function(event, category) {
                return $http['put']('/admin/api/events/'+event.shortName+'/category/'+category.id+'/unbind-tickets').error(HttpErrorHandler.handle);
            },
            getPendingPayments: function(eventName, forceReload) {
                service.data.pendingPayments = service.data.pendingPayments || {};
                var element = service.data.pendingPayments[eventName];
                var now = moment();
                if(!angular.isDefined(element) || now.subtract(20, 's').isAfter(element.ts) || forceReload) {
                    var promise = $http.get('/admin/api/events/'+eventName+'/pending-payments').error(HttpErrorHandler.handle);
                    element = {
                        ts: moment(),
                        payments: promise
                    };
                    service.data.pendingPayments[eventName] = element;
                }
                return element.payments;
            },
            getPendingPaymentsCount: function(eventName) {
                return $http.get('/admin/api/events/'+eventName+'/pending-payments-count').error(HttpErrorHandler.handle).then(function(res) {var v = parseInt(res.data); return isNaN(v) ? 0 : v; });
            },
            editTransactionModal: function(reservationId, type, transactionLoader, callback) {
                var preloadPromise;
                if (transactionLoader) {
                    preloadPromise = transactionLoader.then(function(container) {
                        var transaction = container.data.data.transaction;
                        return {
                            timestamp: {
                                date: moment(transaction.timestamp).format('YYYY-MM-DD'),
                                time: moment(transaction.timestamp).format('HH:mm')
                            },
                            timestampEditable: transaction.timestampEditable,
                            notes: transaction.notes
                        }
                    });
                } else {
                    preloadPromise = $q.resolve({
                        timestamp: {
                            date: moment().format('YYYY-MM-DD'),
                            time: moment().format('HH:mm')
                        },
                        timestampEditable: true,
                        notes: ''
                    });
                }

                var modal = $uibModal.open({
                    size:'md',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/payment/edit-transaction-modal.html',
                    backdrop: 'static',
                    controllerAs: '$ctrl',
                    controller: function() {
                        var ctrl = this;
                        ctrl.actionText = type === 'pending-payment' ? 'Confirm' : 'Edit';
                        ctrl.confirmText = type === 'pending-payment' ? 'Confirm' : 'Save';
                        ctrl.reservationId = reservationId;
                        ctrl.minDate = moment().subtract(1, 'years').format('YYYY-MM-DD');
                        ctrl.isLoading = true;
                        preloadPromise.then(function(res) {
                            ctrl.isLoading = false;
                            ctrl.transaction = res;
                        }, function(err) {
                            modal.dismiss('error');
                        });

                        ctrl.cancel = function() {
                            modal.dismiss('cancelled');
                        };
                        ctrl.confirm = function() {
                            var result = {
                                timestamp: ctrl.transaction.timestampEditable ? ctrl.transaction.timestamp : null,
                                notes: ctrl.transaction.notes
                            };
                            if (callback) {
                                callback(result).then(function() {
                                    modal.close(result);
                                });
                            } else {
                                modal.close(result);
                            }
                        }
                    }
                });
                return modal.result;
            },
            registerPayment: function(eventName, reservationId) {
                return service.editTransactionModal(reservationId, 'pending-payment').then(function(metadata) {
                    return $http['post']('/admin/api/events/'+eventName+'/pending-payments/'+reservationId+'/confirm', metadata)
                         .error(HttpErrorHandler.handle);
                });
            },
            cancelPayment: function(eventName, reservationId, credit, notify) {
                return $http['delete']('/admin/api/events/'+eventName+'/pending-payments/'+reservationId, {
                    params: {
                        credit: credit,
                        notify: notify
                    }
                }).error(HttpErrorHandler.handle);
            },
            cancelMatchingPayment: function(eventName, reservationId, transactionId) {
                return $http['delete']('/admin/api/events/'+eventName+'/reservation/'+reservationId+'/transaction/'+transactionId+'/discard').error(HttpErrorHandler.handle);
            },
            sendCodesByEmail: function(eventName, categoryId, pairs) {
                return $http['post']('/admin/api/events/'+eventName+'/categories/'+categoryId+'/send-codes', pairs).error(HttpErrorHandler.handle);
            },
            loadSentCodes: function(eventName, categoryId) {
                return $http['get']('/admin/api/events/'+eventName+'/categories/'+categoryId+'/sent-codes').error(HttpErrorHandler.handle);
            },
            deleteRecipientData: function(eventName, categoryId, codeId) {
                return $http['delete']('/admin/api/events/'+eventName+'/categories/'+categoryId+'/codes/'+codeId+'/recipient').error(HttpErrorHandler.handle);
            },
            getSelectedLanguages: function(eventName) {
                return $http['get']('/admin/api/events/'+eventName+'/languages').error(HttpErrorHandler.handle);
            },
            getAllLanguages: function() {
                return $http['get']('/admin/api/events-all-languages').error(HttpErrorHandler.handle);
            },
            getSupportedLanguages: function() {
                return $http['get']('/admin/api/events-supported-languages').error(HttpErrorHandler.handle);
            },
            getDynamicFieldTemplates: function() {
                return $http['get']('/admin/api/event/additional-field/templates').error(HttpErrorHandler.handle);
            },
            getMessagesPreview: function(eventName, categoryId, messages) {
                var queryString = angular.isNumber(categoryId) ? '?categoryId='+categoryId : '';
                return $http['post']('/admin/api/events/'+eventName+'/messages/preview'+queryString, messages).error(HttpErrorHandler.handle);
            },
            sendMessages: function(eventName, categoryId, messages) {
                var queryString = angular.isDefined(categoryId) && categoryId !== "" && categoryId !== null ? '?categoryId='+categoryId : '';
                return $http['post']('/admin/api/events/'+eventName+'/messages/send'+queryString, messages).error(HttpErrorHandler.handle);
            },
            getFields : function(eventName) {
                return $http['get']('/admin/api/events/'+eventName+'/fields');
            },
            getAdditionalFields: function(eventName) {
                return $http.get('/admin/api/events/'+eventName+'/additional-field').error(HttpErrorHandler.handle);
            },
            getRestrictedValuesStats: function(eventName, id) {
                return $http.get('/admin/api/events/'+eventName+'/additional-field/'+id+'/stats').error(HttpErrorHandler.handle);
            },
            saveFieldDescription: function(eventName, fieldDescription) {
                return $http.post('/admin/api/events/'+eventName+'/additional-field/descriptions', fieldDescription);
            },
            addField: function(eventName, field) {
            	return $http.post('/admin/api/events/'+eventName+'/additional-field/new', field).error(HttpErrorHandler.handle);
            },
            updateField: function(eventName, toUpdate) {

                //new restrictedValues are complex objects, already present restrictedValues are plain string
                if(toUpdate && toUpdate.restrictedValues && toUpdate.restrictedValues.length > 0) {
                    var res = [];
                    for(var i = 0; i < toUpdate.restrictedValues.length; i++) {
                        res.push(toUpdate.restrictedValues[i].isNew ? toUpdate.restrictedValues[i].value: toUpdate.restrictedValues[i]);
                    }
                    toUpdate.restrictedValues = res;
                }
                //

                return $http['post']('/admin/api/events/'+eventName+'/additional-field/'+toUpdate.id, toUpdate);
            },
            deleteField: function(eventName, id) {
            	return $http['delete']('/admin/api/events/'+eventName+'/additional-field/'+id);
            },
            swapFieldPosition: function(eventName, id1, id2) {
            	return $http.post('/admin/api/events/'+eventName+'/additional-field/swap-position/'+id1+'/'+id2, null);
            },
            moveField: function(eventName, id, position) {
                return $http.post('/admin/api/events/'+eventName+'/additional-field/set-position/'+id, null, {
                    params: {
                        newPosition: position
                    }
                });
            },
            getAllReservationStatus : function(eventName) {
                return $http.get('/admin/api/reservation/event/'+eventName+'/reservations/all-status');
            },
            findAllReservations: function(eventName, page, search, status) {
                return $http.get('/admin/api/reservation/event/'+eventName+'/reservations/list', {params: {page: page, search: search, status: status}});
            },
            deleteEvent: function(event) {
                var modal = $uibModal.open({
                    size:'lg',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/delete-event-modal.html',
                    backdrop: 'static',
                    controller: function($scope) {
                        $scope.cancel = function() {
                            modal.dismiss('canceled');
                        };

                        $scope.deleteEvent = function() {
                            $http['delete']('/admin/api/events/'+event.id).then(function() {
                                modal.close('OK');
                            });
                        };
                        $scope.event = event;
                    }
                });
                return modal.result;
            },

            exportAttendees: function(event) {
                var modal = $uibModal.open({
                    size:'lg',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/select-field-modal.html',
                    backdrop: 'static',
                    controller: function($scope) {
                        $scope.selected = {};
                        $scope.format = 'excel';
                        service.getFields(event.shortName).then(function(fields) {
                            $scope.fields = fields.data;
                            angular.forEach(fields.data, function(v) {
                                $scope.selected[v.key] = false;
                            })
                        });

                        $scope.selectAll = function() {
                            angular.forEach($scope.selected, function(v,k) {
                                $scope.selected[k] = true;
                            });
                        };

                        $scope.deselectAll = function() {
                            angular.forEach($scope.selected, function(v,k) {
                                $scope.selected[k] = false;
                            });
                        };

                        $scope.download = function() {
                            var queryString = "format="+$scope.format+"&";
                            angular.forEach($scope.selected, function(v,k) {
                                if(v) {
                                    queryString+="fields="+ encodeURIComponent(k) +"&";
                                }
                            });
                            var pathName = $window.location.pathname;
                            if(!pathName.endsWith("/")) {
                                pathName = pathName + "/";
                            }
                            $window.open(pathName+"api/events/"+event.shortName+"/export?"+queryString);
                        };
                    }
                });
            },
            removeTicketModal: function(event, reservationId, ticketId, invoiceRequested) {
                var deferred = $q.defer();
                var promise = deferred.promise;

                var modal = $uibModal.open({
                    size:'lg',
                    template:'<tickets-remove event="event" can-generate-credit-note="invoiceRequested" reservation-id="reservationId" ticket-id="ticketId" on-success="success(result)" on-cancel="close()"></tickets-remove>',
                    backdrop: 'static',
                    controller: function($scope) {
                        $scope.event = event;
                        $scope.ticketId = ticketId;
                        $scope.reservationId = reservationId;
                        $scope.invoiceRequested = invoiceRequested;
                        $scope.close = function() {
                            $scope.$dismiss(false);
                        };

                        $scope.success = function (result) {
                            $scope.$close(result);
                        }
                    }
                });
                return modal.result;
            },

            removeTickets: function(eventName, reservationId, ticketIds, ticketIdsToRefund, notify, issueCreditNote) {
                return $http.post('/admin/api/reservation/event/'+eventName+'/'+reservationId+'/remove-tickets', {
                    ticketIds: ticketIds,
                    refundTo: ticketIdsToRefund,
                    notify : notify,
                    issueCreditNote: issueCreditNote
                });
            },

            countInvoices: function(eventName) {
                return $http.get('/admin/api/events/'+eventName+'/invoices/count').error(HttpErrorHandler.handle);
            },

            getTicketsStatistics: function(eventName, from, to) {
                return $http.get('/admin/api/events/'+eventName+'/ticket-sold-statistics', {params: {from: from, to: to}});
            },

            rearrangeCategories: function(event) {
                var modal = $uibModal.open({
                    size:'lg',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/rearrange-categories.html',
                    backdrop: 'static',
                    controller: function($scope) {
                        var ctrl = this;
                        ctrl.event = event;
                        var setOrdinal = function(categories) {
                            for(var i=0, o=1; i < categories.length; i++, o++) {
                                var category = categories[i];
                                category.ordinal = o;
                            }
                            return categories;
                        };
                        ctrl.categories = event.ticketCategories.map(function(category) {
                            return {
                                id: category.id,
                                name: category.name,
                                ordinal: category.ordinal
                            };
                        });
                        ctrl.sortedCategories = setOrdinal(_.sortByAll(ctrl.categories, ['ordinal', 'formattedInception', 'id']));
                        $scope.$on('categories-bag.drop', function (e, el) {
                            $timeout(function() {
                                ctrl.sortedCategories = setOrdinal(ctrl.sortedCategories);
                            }, 10);
                        });
                        ctrl.swap = function(index, category, up) {
                            var list = ctrl.sortedCategories.slice();
                            var target = up ? index - 1 : index + 1;
                            var toBeSwapped = list[target];
                            list[target] = category;
                            list[index] = toBeSwapped;
                            ctrl.sortedCategories.length = 0;
                            for(var i=0; i<list.length; i++) {
                                ctrl.sortedCategories.push(list[i]);
                            }
                            setOrdinal(ctrl.sortedCategories);
                        };
                        ctrl.save = function() {
                            $scope.$close(ctrl.sortedCategories);
                        };
                        ctrl.dismiss = function() {
                            $scope.$dismiss(false);
                        }

                    },
                    controllerAs:'$ctrl'
                });
                return modal.result.then(function(categories) {
                    return $http.put('/admin/api/events/'+event.shortName+'/rearrange-categories', categories).error(HttpErrorHandler.handle);
                });
            },

            createAdditionalService: function(eventId, additionalService) {
                return $http.post('/admin/api/event/'+eventId+'/additional-services/', additionalService).error(HttpErrorHandler.handle);
            },

            updateEventMetadata: function(eventName, metadata) {
                return $http.put('/admin/api/events/'+eventName+'/metadata', metadata).error(HttpErrorHandler.handle);
            },
            retrieveMetadata: function(eventName) {
                return $http.get('/admin/api/events/'+eventName+'/metadata').error(HttpErrorHandler.handle);
            },

            updateCategoryMetadata: function(eventName, categoryId, metadata) {
                return $http.put('/admin/api/events/'+eventName+'/category/'+categoryId+'/metadata', metadata).error(HttpErrorHandler.handle);
            },
            retrieveCategoryMetadata: function(eventName, categoryId) {
                return $http.get('/admin/api/events/'+eventName+'/category/'+categoryId+'/metadata').error(HttpErrorHandler.handle);
            },
            executeCapability: function(eventName, capability, parameters) {
                return $http.post('/admin/api/events/'+eventName+'/capability/'+capability, parameters);
            }
        };
        return service;
    });

    baseServices.service("LocationService", function($http, $q, HttpErrorHandler) {

        var reqCounter = 0;

        function getMapUrl(latitude, longitude) {
            return $http.get('/admin/api/location/static-map-image', {params: {lat: latitude, lng: longitude}}).then(function(res) {
                return res.data;
            });
        }


        function handleGoogleGeolocate(location, locService, apiKeyAndProvider, resolve, reject) {
            var key = apiKeyAndProvider.keys['MAPS_CLIENT_API_KEY'];

            var keyParam = key ? ('&key='+encodeURIComponent(key)) : '';

            if(!window.google || !window.google.maps) {

                reqCounter++;

                var script = document.createElement('script');

                var callBackName = 'clientGeolocate'+reqCounter;

                script.src = 'https://maps.googleapis.com/maps/api/js?libraries=places&callback='+callBackName+keyParam;
                document.head.appendChild(script);
                window[callBackName] = function() {
                    search();
                }
            } else {
                search();
            }

            function search() {
                var geocoder = new window.google.maps.Geocoder();
                geocoder.geocode({'address': location}, function(results, status) {
                    if (status === 'OK') {
                        var ret = {};
                        ret.latitude = ""+results[0].geometry.location.lat()
                        ret.longitude = ""+results[0].geometry.location.lng()
                        $q.all([getMapUrl(ret.latitude, ret.longitude), locService.getTimezone(ret.latitude, ret.longitude)]).then(function(success) {
                            ret.mapUrl = success[0];
                            var tz = success[1];
                            if(tz.data) {
                                ret.timeZone = tz.data;
                            }
                            resolve(ret);
                        }, function () {
                            reject();
                        })

                    } else {
                        reject();
                    }
                });
            }
        }

        
        function handleHEREGeolocate(location, locService, apiKeyAndProvider, resolve, reject) {
            var apiKey = apiKeyAndProvider.keys['MAPS_HERE_API_KEY'];
            $http.get('https://geocoder.ls.hereapi.com/6.2/geocode.json', {params: {apiKey: apiKey, searchtext: location}}).then(function(res) {
                var view = res.data.Response.View;
                if(view.length > 0 && view[0].Result.length > 0 && view[0].Result[0].Location) {
                    var location = view[0].Result[0].Location;
                    var pos = location.DisplayPosition;
                    var ret = {latitude: pos.Latitude, longitude: pos.Longitude};

                    $q.all([getMapUrl(ret.latitude, ret.longitude), locService.getTimezone(ret.latitude, ret.longitude)]).then(function(success) {
                        ret.mapUrl = success[0];
                        var tz = success[1];
                        if(tz.data) {
                            ret.timeZone = tz.data;
                        }
                        resolve(ret);
                    }, function () {
                        reject();
                    })
                } else {
                    reject();
                }

            }, function () {
                reject();
            })
        }

        return {
            mapApiKey: function() {
                return $http.get('/admin/api/location/map-provider-client-api-key').then(function(res) {
                    return res.data;
                });
            },
            clientGeolocate: function(location) {
                var locService = this;
                return $q(function(resolve, reject) {

                    locService.mapApiKey().then(function(apiKeyAndProvider) {

                        if(apiKeyAndProvider.provider === 'GOOGLE') {
                            handleGoogleGeolocate(location, locService, apiKeyAndProvider, resolve, reject);
                        } else if (apiKeyAndProvider.provider === 'HERE') {
                            handleHEREGeolocate(location, locService, apiKeyAndProvider, resolve, reject);
                        } else {
                            resolve({latitude: null, longitude: null});
                        }
                    })

                });
            },
            getTimezone : function(latitude, longitude) {
              return $http.get('/admin/api/location/timezone', {params: {lat: latitude, lng: longitude}});
            },
            getTimezones: function() {
                return $http.get('/admin/api/location/timezones');
            },
            getMapUrl : getMapUrl
        };
    });

    baseServices.service('ValidationService', function(NotificationHandler) {
        return {
            validationPerformer: function($q, validator, data, form) {
                var deferred = $q.defer();
                validator(data).success(this.validationResultHandler(form, deferred)).error(function(error) {
                    deferred.reject(error);
                });
                return deferred.promise;
            },
            validationResultHandler: function(form, deferred) {
                return function(validationResult) {
                    if(validationResult.errorCount > 0) {
                        if(form.$setError) {
                            angular.forEach(validationResult.validationErrors, function(error) {
                                form.$setError(error.fieldName, error.message);
                            });
                        } else if (form.$setValidity) {
                            angular.forEach(validationResult.validationErrors, function(error) {
                                form[error.fieldName].$setValidity(error.code, false);
                            });
                        } else {
                            var firstError = validationResult.validationErrors[0];
                            NotificationHandler.showError(firstError.description);
                        }
                        deferred.reject('invalid form');
                    }
                    deferred.resolve();
                };
            }
        }
    });

    baseServices.service("HttpErrorHandler", ['$log', 'NotificationHandler', function($log, NotificationHandler) {
        var getMessage = function(body, status) {
            switch(status) {
                case 400:
                    return 'Malformed Request';
                case 404:
                    return 'Resource not found';
                case 403:
                    return 'Your account is not authorized to perform this operation.';
                case 500:
                    return 'Internal Server Error: ' + body;
                default:
                    return 'Connection Error';
            }
        };
        return {
            handle : function(body, status) {
                var message = getMessage(body, status);
                $log.warn(message, status, body);
                NotificationHandler.showError(message);
            }
        };
    }]);

    baseServices.service("NotificationHandler", ["growl", "$sanitize", function (growl, $sanitize) {
        var config = {ttl: 5000, disableCountDown: true};
        var sanitize = function(message) {
            var sanitized = $sanitize(message);
            return sanitized.split(' ').map(function(part) {
                return encodeURIComponent(part);
            }).join(' ');
        };
        return {
            showSuccess: function (message) {
                return growl.success(sanitize(message), config);
            },
            showWarning: function (message) {
                return growl.warning(sanitize(message), config);
            },
            showInfo : function (message) {
                return growl.info(sanitize(message), config);
            },
            showError : function (message) {
                return growl.error(sanitize(message), config);
            }
        }

    }]);

    baseServices.service("PriceCalculator", function() {
        var instance = {
            calculateTotalPrice: function(event, viewMode) {
                if(isNaN(event.regularPrice) || isNaN(event.vatPercentage)) {
                    return '0.00';
                }
                var vat = numeral(0.0);
                if((viewMode && angular.isDefined(event.id)) || !event.vatIncluded) {
                    vat = instance.applyPercentage(event.regularPrice, event.vatPercentage);
                }
                return numeral(vat.add(event.regularPrice).format('0.00')).value();
            },
            calcBarValue: function(category) {
                if(category.bounded) {
                    return category.maxTickets || 1;
                }
                return 0;
            },
            calcCategoryPricePercent: function(category, event, editMode) {
                if(isNaN(event.regularPrice) || isNaN(category.price)) {
                    return '0.00';
                }
                return instance.calcPercentage(category.price, event.regularPrice).format('0.00');
            },
            calcCategoryPrice: function(category, event) {
                if(isNaN(event.vatPercentage) || isNaN(category.price)) {
                    return '0.00';
                }
                var vat = numeral(0.0);
                if(event.vatIncluded) {
                    vat = instance.applyPercentage(category.price, event.vatPercentage);
                }
                return numeral(category.price).add(vat).format('0.00');
            },
            calcPercentage: function(fraction, total) {
                if(isNaN(fraction) || isNaN(total)){
                    return numeral(0.0);
                }
                return numeral(numeral(fraction).divide(total).multiply(100).format('0.00'));
            },
            applyPercentage: function(total, percentage) {
                return numeral(numeral(percentage).divide(100).multiply(total).format('0.00'));
            }
        };
        return instance;
    });
    
    baseServices.service("PromoCodeService", function($http, HttpErrorHandler) {

        function addUtfOffsetIfNecessary(promoCode) {
            if(promoCode.eventId == null) {
                promoCode.utcOffset = (new Date()).getTimezoneOffset()*-60; //in seconds
            }
        }

        return {
                add : function(promoCode) {
                    addUtfOffsetIfNecessary(promoCode);
                    return $http['post']('/admin/api/promo-code', promoCode).error(HttpErrorHandler.handle);
                },
                remove: function(promoCodeId) {
                    return $http['delete']('/admin/api/promo-code/' + promoCodeId).error(HttpErrorHandler.handle);
                },
                list: function(eventId) {
                    return $http.get('/admin/api/events/' + eventId + '/promo-code').error(HttpErrorHandler.handle);
                },
                listOrganization : function(organizationId) {
                    return $http.get('/admin/api/organization/' + organizationId + '/promo-code').error(HttpErrorHandler.handle);
                },
                countUse : function(promoCodeId) {
                    return $http.get('/admin/api/promo-code/' + promoCodeId + '/count-use');
                },
                disable: function(promoCodeId) {
                    return $http['post']('/admin/api/promo-code/' + promoCodeId + '/disable');
                },
                update: function(promoCodeId, toUpdate) {
                    addUtfOffsetIfNecessary(toUpdate);
                    return $http.post('/admin/api/promo-code/' + promoCodeId, toUpdate);
                },
                getUsageDetails: function(promoCodeId, eventShortName) {
                    return $http.get('/admin/api/promo-code/' + promoCodeId + '/detailed-usage', {
                        params: {
                            eventShortName
                        }
                    });
                }
        };
    });

    baseServices.service("CheckInService", ['$http', 'HttpErrorHandler', '$window', function($http, HttpErrorHandler, $window) {
        return {
            findAllTicketIds : function(eventId) {
                var sessionStorageKey = 'CHECK_IN_TIMESTAMP_'+eventId;
                var since = $window.sessionStorage.getItem(sessionStorageKey);
                var sinceParam = since ? '?changedSince='+since : '';
                return $http.get('/admin/api/check-in/' + eventId + '/ticket-identifiers' + sinceParam)
                    .then(function(resp) {
                        $window.sessionStorage.setItem(sessionStorageKey, resp.headers('Alfio-TIME'));
                        return resp;
                    }, HttpErrorHandler.handle);
            },

            downloadTickets: function(eventId, ids) {
                return $http.post('/admin/api/check-in/'+eventId+'/tickets', ids).error(HttpErrorHandler.handle);
            },
            
            getTicket: function(eventId, code) {
                var ticketIdentifier = code.split('/')[0];
                return $http.get('/admin/api/check-in/' + eventId + '/ticket/' + ticketIdentifier + "?qrCode=" + encodeURIComponent(code)).error(HttpErrorHandler.handle);
            },
            
            checkIn: function(eventId, ticket) {
                var ticketIdentifier = ticket.code.split('/')[0];
                return $http['post']('/admin/api/check-in/' + eventId + '/ticket/' + ticketIdentifier, ticket).error(HttpErrorHandler.handle);
            },

            manualCheckIn: function(ticket) {
                return $http['post']('/admin/api/check-in/' + ticket.eventId + '/ticket/' + ticket.uuid + '/manual-check-in', ticket).error(HttpErrorHandler.handle);
            },

            revertCheckIn: function(ticket) {
                return $http['post']('/admin/api/check-in/' + ticket.eventId + '/ticket/' + ticket.uuid + '/revert-check-in', ticket).error(HttpErrorHandler.handle);
            },
            
            confirmPayment: function(eventId, ticket) {
                var ticketIdentifier = ticket.code.split('/')[0];
                return $http['post']('/admin/api/check-in/' + eventId + '/ticket/' + ticketIdentifier + '/confirm-on-site-payment').error(HttpErrorHandler.handle);
            }
        };
    }]);

    baseServices.service("FileUploadService", function($http, HttpErrorHandler) {
        return {
            upload : function(file) {
                return $http['post']('/admin/api/file/upload', file).error(HttpErrorHandler.handle);
            },

            uploadImageWithResize: function(file) {
                return $http['post']('/admin/api/file/upload?resizeImage=true', file).error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service('WaitingQueueService', ['$http', 'HttpErrorHandler', function($http, HttpErrorHandler) {
        return {
            getWaitingQueueStatus: function(eventName) {
                return $http.get('/admin/api/event/'+eventName+'/waiting-queue/status').error(HttpErrorHandler.handle);
            },
            setPaused: function(eventName, status) {
                return $http.put('/admin/api/event/'+eventName+'/waiting-queue/status', {status: status}).error(HttpErrorHandler.handle);
            },
            countSubscribers: function(eventName) {
                return $http.get('/admin/api/event/'+eventName+'/waiting-queue/count').error(HttpErrorHandler.handle);
            },
            loadAllSubscribers: function(eventName) {
                return $http.get('/admin/api/event/'+eventName+'/waiting-queue/load').error(HttpErrorHandler.handle);
            },
            removeSubscriber: function(eventName, subscriber) {
                return $http['delete']('/admin/api/event/'+eventName+'/waiting-queue/subscriber/'+subscriber.id).error(HttpErrorHandler.handle);
            },
            restoreSubscriber: function(eventName, subscriber) {
                return $http['put']('/admin/api/event/'+eventName+'/waiting-queue/subscriber/'+subscriber.id+'/restore').error(HttpErrorHandler.handle);
            }
        };
    }]);

    baseServices.service('UtilsService', ['$http', 'HttpErrorHandler', function($http, HttpErrorHandler) {
        return {
            generateShortName: function(displayName) {
                return $http.get('/admin/api/utils/short-name/generate?displayName='+displayName).error(HttpErrorHandler.handle);
            },
            validateShortName: function(shortName) {
                return $http['post']('/admin/api/utils/short-name/validate', null, {params: {shortName: shortName}}).error(HttpErrorHandler.handle);
            },
            renderCommonMark: function(text) {
            	return $http.get('/admin/api/utils/render-commonmark', {params: {text: text}}).error(HttpErrorHandler.handle);
            },
            getApplicationInfo: function() {
                return $http.get('/admin/api/utils/alfio/info').error(HttpErrorHandler.handle);
            },
            getAvailableCurrencies: function() {
                return $http.get('/admin/api/utils/currencies').error(HttpErrorHandler.handle);
            },
            logout: function() {
                return $http.post("/logout", {}).error(HttpErrorHandler.handle);
            },
            getFirstTranslation: function(localized) {
                var keys = Object.keys(localized);
                if(keys.length > 0) {
                    return localized[keys[0]];
                }
                return null;
            }
        };
    }]);

    baseServices.service('CountriesService', ['$http', 'HttpErrorHandler', '$q', function($http, HttpErrorHandler) {
        var request = $http.get('/admin/api/utils/countriesForVat').then(function(res) {
            return res.data;
        }, HttpErrorHandler.handle);
        return {
            getCountries: function() {
                return request;
            },

            getDescription: function(countryCode) {
                return request.then(function(countries) {
                    return countries[countryCode] || countryCode;
                });
            }

        };
    }]);

    baseServices.service('MenuButtonService', ['EventService', '$window', '$uibModal', 'NotificationHandler', function(EventService, $window, $uibModal, NotificationHandler) {
        return {
            configureMenu: function(ctrl) {
                ctrl.openFieldSelectionModal = function() {
                    EventService.exportAttendees(ctrl.event);
                };
                ctrl.downloadSponsorsScan = function() {
                    var pathName = $window.location.pathname;
                    if(!pathName.endsWith("/")) {
                        pathName = pathName + "/";
                    }
                    $window.open(pathName+"api/events/"+ctrl.event.shortName+"/sponsor-scan/export");
                };
                ctrl.openWaitingQueueModal = function() {
                    var outCtrl = ctrl;
                    var modal = $uibModal.open({
                        size:'lg',
                        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/event/fragment/download-waiting-queue.html',
                        backdrop: 'static',
                        controllerAs: 'ctrl',
                        controller: function($scope) {
                            var ctrl = this;
                            $scope.format = 'excel';

                            $scope.download = function() {
                                var queryString = "format="+$scope.format;
                                var pathName = $window.location.pathname;
                                if(!pathName.endsWith("/")) {
                                    pathName = pathName + "/";
                                }
                                $window.open(pathName+"api/event/" + outCtrl.event.shortName + "/waiting-queue/download?"+queryString);
                            };

                            ctrl.close = function() {
                                modal.close();
                            }
                        }
                    });
                };
                ctrl.downloadInvoices = function(type) {
                    EventService.countInvoices(ctrl.event.shortName).then(function (res) {
                        var count = res.data;
                        if(count > 0) {
                            var pathName = $window.location.pathname;
                            if(!pathName.endsWith("/")) {
                                pathName = pathName + "/";
                            }
                            var suffix = '';
                            if(type === 'xls') {
                                suffix = '-xls';
                            }
                            $window.open(pathName+"api/events/"+ctrl.event.shortName+"/all-documents"+suffix);
                        } else {
                            NotificationHandler.showInfo("No invoices have been found.");
                        }
                    });
                };
            }
        }
    }]);

    baseServices.service('ImageTransformService', ['FileUploadService', '$window', '$q', function(FileUploadService, $window, $q) {
        return {
            transformAndUploadImages: function(files) {
                var deferred = $q.defer();
                var reader = new FileReader();
                reader.onload = function(e) {
                    var imageBase64 = e.target.result;
                    var fileType = files[0].type;
                    var fileName = files[0].name;
                    var fileContent = imageBase64.substring(imageBase64.indexOf('base64,') + 7);
                    if (fileType=== 'image/svg+xml') {
                        var img = new Image();
                        var fromSvgToPng = function(image) {
                            var cnv = document.createElement('canvas');
                            cnv.width = image.width;
                            cnv.height = image.height;
                            var canvasCtx = cnv.getContext('2d');
                            canvasCtx.drawImage(image, 0, 0);
                            var imgData = cnv.toDataURL('image/png');
                            img.remove();
                            fileType = "image/png";
                            fileName = fileName+".png";
                            fileContent = imgData.substring(imgData.indexOf('base64,') + 7);
                            FileUploadService.uploadImageWithResize({file : fileContent, type : fileType, name : fileName}).then(function(res) {
                                deferred.resolve({
                                    imageBase64: imgData,
                                    fileBlobId: res.data
                                });
                            }, function(err) {
                                deferred.reject(null); // error is already notified by the NotificationService
                            });
                        };
                        var parser = new DOMParser();
                        var svgRoot = parser.parseFromString(atob(fileContent), 'text/xml').getElementsByTagName("svg")[0];
                        if (svgRoot.hasAttribute('height')) {
                            img.height = svgRoot.getAttribute('height');
                            img.width = svgRoot.getAttribute('width');
                        } else {
                            img.height = 500;
                        }
                        img.setAttribute('aria-hidden', 'true');
                        img.style.position = 'absolute';
                        img.style.top = '-10000px';
                        img.style.left = '-10000px';
                        img.onload = function() {
                            // see FF limitation https://stackoverflow.com/a/61195034
                            // we need to set in a explicit way the size _inside_ the svg
                            svgRoot.setAttribute('width', img.width+'px');
                            svgRoot.setAttribute('height', img.height+'px');
                            var serializedSvg = new XMLSerializer().serializeToString(svgRoot);
                            img.onload = function() {
                                fromSvgToPng(img);
                            }
                            img.src = 'data:image/svg+xml;base64,'+btoa(serializedSvg);
                        };
                        $window.document.body.appendChild(img);
                        img.src = imageBase64;
                    } else {
                        FileUploadService.uploadImageWithResize({file : fileContent, type : fileType, name : fileName}).then(function(res) {
                            deferred.resolve({
                                imageBase64: imageBase64,
                                fileBlobId: res.data
                            });
                        }, function(err) {
                            deferred.reject(null); // error is already notified by the NotificationService
                        });
                    }
                };
                if (files.length <= 0) {
                    deferred.reject('Your image was not uploaded correctly.Please upload the image again');
                } else if (!((files[0].type === 'image/png') || (files[0].type === 'image/jpeg') || (files[0].type === 'image/gif') || (files[0].type === 'image/svg+xml'))) {
                    deferred.reject('Only PNG, JPG, GIF or SVG image files are accepted');
                } else if (files[0].size > (1024 * 1024)) {
                    deferred.reject('Image is too big');
                } else {
                    reader.readAsDataURL(files[0]);
                }
                return deferred.promise;
            }
        };
    }])
})();