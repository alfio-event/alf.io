(function () {
    "use strict";
    var baseServices = angular.module('adminServices', []);

    baseServices.config(['$httpProvider', function($httpProvider) {
        var token = $("meta[name='_csrf']").attr("content");
        var header = $("meta[name='_csrf_header']").attr("content");
        $httpProvider.defaults.headers.common['X-Requested-With'] = 'XMLHttpRequest';
        $httpProvider.defaults.headers.common[header] = token;
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
            getAllProxies : function() {
                return $http.get('/admin/api/paymentProxies.json').error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service("EventService", function($http, HttpErrorHandler) {
        return {
            getAllEvents : function() {
                return $http.get('/admin/api/events.json').error(HttpErrorHandler.handle);
            },
            getEvent: function(name) {
                return $http.get('/admin/api/events/'+name+'.json').error(HttpErrorHandler.handle);
            },
            getEventById: function(eventId) {
                return $http.get('/admin/api/events/id/'+eventId+'.json').error(HttpErrorHandler.handle);
            },
            checkEvent : function(event) {
                return $http['post']('/admin/api/events/check', event).error(HttpErrorHandler.handle);
            },
            createEvent : function(event) {
                return $http['post']('/admin/api/events/new', event).error(HttpErrorHandler.handle);
            },
            updateEventHeader: function(eventHeader) {
                return $http['post']('/admin/api/events/'+eventHeader.id+'/header/update', eventHeader).error(HttpErrorHandler.handle);
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
            getPendingPayments: function(eventName) {
                return $http.get('/admin/api/events/'+eventName+'/pending-payments').error(HttpErrorHandler.handle);
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
                return $http.post('/admin/api/events/'+eventName+'/additional-field/descriptions', fieldDescription)
            },
        };
    });

    baseServices.service("LocationService", function($http, HttpErrorHandler) {
        return {
            geolocate : function(location) {
                return $http.get('/admin/api/location/geo.json?location='+location).error(HttpErrorHandler.handle);
            },
            getMapUrl : function(latitude, longitude) {
                return $http.get('/admin/api/location/map.json?lat='+latitude+'&long='+longitude).error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service('ValidationService', function() {
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
                        angular.forEach(validationResult.validationErrors, function(error) {
                            form.$setError(error.fieldName, error.message);
                        });
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

    baseServices.service("PriceCalculator", function() {
        var instance = {
            calculateTotalPrice: function(event, viewMode) {
                if(isNaN(event.regularPrice) || isNaN(event.vat)) {
                    return '0.00';
                }
                var vat = numeral(0.0);
                if((viewMode && angular.isDefined(event.id)) || !event.vatIncluded) {
                    vat = instance.applyPercentage(event.regularPrice, event.vat);
                }
                return numeral(vat.add(event.regularPrice).format('0.00')).value();
            },
            calcBarValue: function(categorySeats, eventSeats) {
                return instance.calcPercentage(categorySeats, eventSeats).format('0.00');
            },
            calcCategoryPricePercent: function(category, event, editMode) {
                if(isNaN(event.regularPrice) || isNaN(category.price)) {
                    return '0.00';
                }
                
                //TODO cleanup, not happy about that
                var regularPrice = event.regularPrice;
                if(editMode && event.vatIncluded) {
                    regularPrice = instance.calculateTotalPrice(event, true);
                }
                //
                return instance.calcPercentage(category.price, regularPrice).format('0.00');
            },
            calcCategoryPrice: function(category, event) {
                if(isNaN(event.vat) || isNaN(category.price)) {
                    return '0.00';
                }
                var vat = numeral(0.0);
                if(event.vatIncluded) {
                    vat = instance.applyPercentage(category.price, event.vat);
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
        return {
                add : function(eventId, promoCode) {
                    return $http['post']('/admin/api/events/' + eventId + '/promo-code', promoCode).error(HttpErrorHandler.handle);
                },
                remove: function(eventId, promoCode) {
                    return $http['delete']('/admin/api/events/' + eventId + '/promo-code/' + encodeURIComponent(promoCode)).error(HttpErrorHandler.handle);
                },
                list: function(eventId) {
                    return $http.get('/admin/api/events/' + eventId + '/promo-code').error(HttpErrorHandler.handle);
                },
                countUse : function(eventId, promoCode) {
                    return $http.get('/admin/api/events/' + eventId + '/promo-code/' + encodeURIComponent(promoCode)+ '/count-use');
                },
                disable : function(eventId, promoCode) {
                    return $http['post']('/admin/api/events/' + eventId + '/promo-code/' + encodeURIComponent(promoCode)+ '/disable');
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
            }
        };
    }]);

    baseServices.service('EventUtilsService', ['$http', 'HttpErrorHandler', function($http, HttpErrorHandler) {
        return {
            generateShortName: function(displayName) {
                return $http.get('/admin/api/utils/short-name/generate?displayName='+displayName).error(HttpErrorHandler.handle);
            },
            validateShortName: function(shortName) {
                return $http['post']('/admin/api/utils/short-name/validate', null, {params: {shortName: shortName}}).error(HttpErrorHandler.handle);
            },
            renderCommonMark: function(text) {
            	return $http.get('/admin/api/utils/render-commonmark', {params: {text: text}});
            }
        };
    }]);
})();