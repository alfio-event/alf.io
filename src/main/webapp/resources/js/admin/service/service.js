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
                    } else if(status === 403) {
                        $rootScope.$emit('ErrorNotAuthorized');
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
                return $http.get('/admin/api/paymentProxies/'+orgId+'.json').error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service("EventService", function($http, HttpErrorHandler, $uibModal, $window, $rootScope, $q, LocationService) {

        function copyGeoLocation(event) {
            event.latitude = event.geolocation.latitude;
            event.longitude = event.geolocation.longitude;
            event.zoneId = event.geolocation.timeZone;
        }

        var service = {
            data: {},
            getAllEvents : function() {
                return $http.get('/admin/api/events.json').error(HttpErrorHandler.handle);
            },
            getAllActiveEvents : function() {
                return $http.get('/admin/api/active-events.json').error(HttpErrorHandler.handle);
            },
            getAllExpiredEvents : function() {
                return $http.get('/admin/api/expired-events.json').error(HttpErrorHandler.handle);
            },
            getEvent: function(name) {
                return $http.get('/admin/api/events/'+name+'.json').success(function(result) {
                    $rootScope.$emit('EventLoaded', result.event);
                }).error(HttpErrorHandler.handle);
            },
            getEventById: function(eventId) {
                return $http.get('/admin/api/events/id/'+eventId+'.json').success(function(result) {
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
                //
                if(eventHeader.geolocation && eventHeader.geolocation.latitude) {
                    copyGeoLocation(eventHeader);
                    //
                    return $http['post']('/admin/api/events/'+eventHeader.id+'/header/update', eventHeader).error(HttpErrorHandler.handle);
                } else {
                    return LocationService.clientGeolocate(eventHeader.location).then(function(geo) {
                        eventHeader.latitude = geo.latitude;
                        eventHeader.longitude = geo.longitude;
                        eventHeader.zoneId = geo.timeZone;
                        return $http['post']('/admin/api/events/'+eventHeader.id+'/header/update', eventHeader).error(HttpErrorHandler.handle);
                    })
                }

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
            registerPayment: function(eventName, reservationId) {
                return $http['post']('/admin/api/events/'+eventName+'/pending-payments/'+reservationId+'/confirm').error(HttpErrorHandler.handle);
            },
            cancelPayment: function(eventName, reservationId) {
                return $http['delete']('/admin/api/events/'+eventName+'/pending-payments/'+reservationId).error(HttpErrorHandler.handle);
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
                var queryString = angular.isDefined(categoryId) && categoryId !== "" ? '?categoryId='+categoryId : '';
                return $http['post']('/admin/api/events/'+eventName+'/messages/preview'+queryString, messages).error(HttpErrorHandler.handle);
            },
            sendMessages: function(eventName, categoryId, messages) {
                var queryString = angular.isDefined(categoryId) && categoryId !== "" ? '?categoryId='+categoryId : '';
                return $http['post']('/admin/api/events/'+eventName+'/messages/send'+queryString, messages).error(HttpErrorHandler.handle);
            },
            getCategoriesContainingTickets: function(eventName) {
                return $http['get']('/admin/api/events/'+eventName+'/categories-containing-tickets')
            },
            getFields : function(eventName) {
                return $http['get']('/admin/api/events/'+eventName+'/fields');
            },
            getAdditionalFields: function(eventName) {
                return $http.get('/admin/api/events/'+eventName+'/additional-field');
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
            	return $http.post('/admin/api/events/'+eventName+'/additional-field/swap-position/'+id1+'/'+id2);
            },
            findAllReservations: function(eventName) {
                return $http.get('/admin/api/reservation/event/'+eventName+'/reservations/list');
            },
            deleteEvent: function(event) {
                var modal = $uibModal.open({
                    size:'lg',
                    templateUrl: '/resources/angular-templates/admin/partials/event/fragment/delete-event-modal.html',
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
                    templateUrl:'/resources/angular-templates/admin/partials/event/fragment/select-field-modal.html',
                    backdrop: 'static',
                    controller: function($scope) {
                        $scope.selected = {};
                        service.getFields(event.shortName).then(function(fields) {
                            $scope.fields = fields.data;
                            angular.forEach(fields.data, function(v) {
                                $scope.selected[v] = false;
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
                            var queryString = "";
                            angular.forEach($scope.selected, function(v,k) {
                                if(v) {
                                    queryString+="fields="+k+"&";
                                }
                            });
                            $window.open($window.location.pathname+"/api/events/"+event.shortName+"/export.csv?"+queryString);
                        };
                    }
                });
            },

            cancelReservationModal: function(event, reservationId) {
                var deferred = $q.defer();
                var promise = deferred.promise;

                var modal = $uibModal.open({
                    size:'lg',
                    template:'<reservation-cancel event="event" reservation-id="reservationId" on-success="success()" on-cancel="close()"></reservation-cancel>',
                    backdrop: 'static',
                    controller: function($scope) {
                        $scope.event = event;
                        $scope.reservationId = reservationId;
                        $scope.close = function() {
                            $scope.$close(false);
                            deferred.reject();
                        };

                        $scope.success = function () {
                            $scope.$close(false);
                            deferred.resolve();
                        }
                    }
                });
                return promise;
            },

            removeTicketModal: function(event, reservationId, ticketId) {
                var deferred = $q.defer();
                var promise = deferred.promise;

                var modal = $uibModal.open({
                    size:'lg',
                    template:'<tickets-remove event="event" reservation-id="reservationId" ticket-id="ticketId" on-success="success()" on-cancel="close()"></tickets-remove>',
                    backdrop: 'static',
                    controller: function($scope) {
                        $scope.event = event;
                        $scope.ticketId = ticketId;
                        $scope.reservationId = reservationId;
                        $scope.close = function() {
                            $scope.$close(false);
                            deferred.reject();
                        };

                        $scope.success = function () {
                            $scope.$close(false);
                            deferred.resolve();
                        }
                    }
                });
                return promise;
            },

            removeTickets: function(eventName, reservationId, ticketIds, ticketIdsToRefund, notify) {
                return $http.post('/admin/api/reservation/event/'+eventName+'/'+reservationId+'/remove-tickets', {ticketIds: ticketIds, refundTo: ticketIdsToRefund, notify : notify});
            },

            cancelReservation: function(eventName, reservationId, refund, notify) {
                return $http.post('/admin/api/reservation/event/'+eventName+'/'+reservationId+'/cancel?refund=' + refund+"&notify="+notify);
            }
        };
        return service;
    });

    baseServices.service("LocationService", function($http, $q, HttpErrorHandler) {

        function mapUrl(lat, lng, key) {
            var keyParam = key ? ('&key='+encodeURIComponent(key)) : '';
            return "https://maps.googleapis.com/maps/api/staticmap?center="+lat+","+lng+"&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7C"+lat+","+lng+""+keyParam;
        }

        return {
            mapApiKey: function() {
                return $http.get('/admin/api/location/maps-client-api-key.json').then(function(res) {
                    return res.data;
                });
            },
            clientGeolocate: function(location) {
              return this.mapApiKey().then(function(key) {
                  var keyParam = key ? ('&key='+encodeURIComponent(key)) : ''

                  return $http.get('https://maps.googleapis.com/maps/api/geocode/json?address=' + encodeURIComponent(location) + keyParam).then(function (res) {
                      if(res && res.data && res.data.results && res.data.results.length > 0) {
                          //take first;
                          var ret = {};
                          ret.latitude = "" + res.data.results[0].geometry.location.lat;
                          ret.longitude = "" + res.data.results[0].geometry.location.lng;
                          ret.mapUrl = mapUrl(ret.latitude, ret.longitude, key);
                          ret.timeZone = "";
                          return ret;
                      } else {
                          return null;
                      }
                  }).then(function(res) {
                      if(res) {
                          return $http.get('https://maps.googleapis.com/maps/api/timezone/json?timestamp=0&location='+res.latitude+','+res.longitude+''+keyParam).then(function(tz) {
                              res.timeZone = tz.data.timeZoneId;
                              return res;
                          });
                      }
                      return null;
                  });
              });
            },
            getMapUrl : function(latitude, longitude) {
                return this.mapApiKey().then(function(key) {
                    return mapUrl(latitude, longitude, key);
                }, HttpErrorHandler.handle);
            }
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

    baseServices.service("HttpErrorHandler", function($rootScope, $log) {
        return {
            handle : function(error) {
                $log.warn(error);
                $rootScope.$broadcast('applicationError', error.message);
            }
        };
    });

    baseServices.service("NotificationHandler", ["growl", function (growl) {
        var config = {ttl: 5000, disableCountDown: true};
        return {
            showSuccess: function (message) {
                growl.success(message, config);
            },
            showWarning: function (message) {
                growl.warning(message, config);
            },
            showInfo : function (message) {
                growl.info(message, config);
            },
            showError : function (message) {
                growl.error(message, config);
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
                }
        };
    });

    baseServices.service("CheckInService", function($http) {
        return {
            findAllTickets : function(eventId) {
                return $http.get('/admin/api/check-in/' + eventId + '/ticket');
            },
            
            getTicket: function(eventId, code) {
                var ticketIdentifier = code.split('/')[0];
                return $http.get('/admin/api/check-in/' + eventId + '/ticket/' + ticketIdentifier + "?qrCode=" + encodeURIComponent(code));
            },
            
            checkIn: function(eventId, ticket) {
                var ticketIdentifier = ticket.code.split('/')[0];
                return $http['post']('/admin/api/check-in/' + eventId + '/ticket/' + ticketIdentifier, ticket);
            },

            manualCheckIn: function(ticket) {
                return $http['post']('/admin/api/check-in/' + ticket.eventId + '/ticket/' + ticket.uuid + '/manual-check-in', ticket);
            },

            revertCheckIn: function(ticket) {
                return $http['post']('/admin/api/check-in/' + ticket.eventId + '/ticket/' + ticket.uuid + '/revert-check-in', ticket);
            },
            
            confirmPayment: function(eventId, ticket) {
                var ticketIdentifier = ticket.code.split('/')[0];
                return $http['post']('/admin/api/check-in/' + eventId + '/ticket/' + ticketIdentifier + '/confirm-on-site-payment');
            }
        };
    });

    baseServices.service("FileUploadService", function($http) {
        return {
            upload : function(file) {
                return $http['post']('/admin/api/file/upload', file);
            }
        };
    });

    baseServices.service('WaitingQueueService', ['$http', 'HttpErrorHandler', function($http, HttpErrorHandler) {
        return {
            countSubscribers: function(event) {
                return $http.get('/admin/api/event/'+event.shortName+'/waiting-queue/count').error(HttpErrorHandler.handle);
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
                return $http.post("/logout").error(HttpErrorHandler.handle);
            }
        };
    }]);
})();