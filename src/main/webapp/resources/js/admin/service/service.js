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

    baseServices.service("OrganizationService", function($http, HttpErrorHandler) {
        return {
            getAllOrganizations : function() {
                return $http.get('/admin/api/organizations.json').error(HttpErrorHandler.handle);
            },
            getOrganization: function(id) {
                return $http.get('/admin/api/organizations/'+id+'.json').error(HttpErrorHandler.handle);
            },
            createOrganization : function(organization) {
                return $http['post']('/admin/api/organizations/new', organization).error(HttpErrorHandler.handle);
            },
            checkOrganization : function(organization) {
                return $http['post']('/admin/api/organizations/check', organization).error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service('PaymentProxyService', function($http, HttpErrorHandler) {
        return {
            getAllProxies : function() {
                return $http.get('/admin/api/paymentProxies.json').error(HttpErrorHandler.handle);
            }
        };
    });

    baseServices.service('UserService', function($http, $modal, HttpErrorHandler) {
        return {
            getAllUsers : function() {
                return $http.get('/admin/api/users.json').error(HttpErrorHandler.handle);
            },
            editUser : function(user) {
                var url = angular.isDefined(user.id) ? '/admin/api/users/edit' : '/admin/api/users/new';
                return $http['post'](url, user).error(HttpErrorHandler.handle);
            },
            checkUser : function(user) {
                return $http['post']('/admin/api/users/check', user).error(HttpErrorHandler.handle);
            },
            loadUser: function(userId) {
                return $http.get('/admin/api/users/'+userId+'.json').error(HttpErrorHandler.handle);
            },
            deleteUser: function(user) {
                return $http['delete']('/admin/api/users/'+user.id).error(HttpErrorHandler.handle);
            },
            resetPassword: function(user) {
                return $http['put']('/admin/api/users/'+user.id+'/reset-password').error(HttpErrorHandler.handle);
            },

            showUserData: function(user) {
                return $modal.open({
                    size:'sm',
                    templateUrl:'/resources/angular-templates/admin/partials/event/fragment/show-user-data-modal.html',
                    backdrop: 'static',
                    controller: function($scope) {
                        $scope.user = user;
                        $scope.ok = function() {
                            $scope.$close(true);
                        };
                    }
                }).result;
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
            getAvailableLanguages: function(eventName) {
                return $http['get']('/admin/api/events/'+eventName+'/languages').error(HttpErrorHandler.handle);
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
            }
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

    baseServices.service('ConfigurationService', function($http, HttpErrorHandler) {
        return {
            loadAll: function() {
                return $http.get('/admin/api/configuration/load').error(HttpErrorHandler.handle);
            },
            update: function(configuration) {
                return $http.post('/admin/api/configuration/update', configuration).error(HttpErrorHandler.handle);
            },
            bulkUpdate: function(settings) {
                return $http.post('/admin/api/configuration/update-bulk', settings).error(HttpErrorHandler.handle);
            },
            remove: function(key) {
            	return $http['delete']('/admin/api/configuration/key/' + key).error(HttpErrorHandler.handle);
            }
        };
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
})();