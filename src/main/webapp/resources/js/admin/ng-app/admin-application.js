(function () {
    "use strict";

    var BASE_TEMPLATE_URL = "/admin/partials";
    var BASE_STATIC_URL = "/resources/angular-templates/admin/partials";

    //
    var FIELD_TYPES = ['input:text', 'input:tel', 'textarea', 'select', 'country'];
    
    var admin = angular.module('adminApplication', ['ngSanitize','ui.bootstrap', 'ui.router', 'adminDirectives', 'adminServices', 'utilFilters', 'ngMessages', 'ngFileUpload', 'nzToggle', 'alfio-plugins', 'alfio-email', 'alfio-util', 'alfio-configuration', 'alfio-users', 'alfio-additional-services', 'alfio-event-statistic']);

    admin.config(function($stateProvider, $urlRouterProvider) {
        $urlRouterProvider.otherwise("/");
        $stateProvider
            .state('index', {
                url: "/",
                templateUrl: BASE_TEMPLATE_URL + "/index.html"
            })
            .state('events', {
                abstract: true,
                url: '/events',
                templateUrl: BASE_STATIC_URL + "/event/index.html"
            })
            .state('events.new', {
                url: '/new',
                templateUrl: BASE_STATIC_URL + "/event/edit-event.html",
                controller: 'CreateEventController',
                data: {
                    eventType: 'INTERNAL'
                }
            })
            .state('events.newLink', {
                url: '/new-external',
                templateUrl: BASE_STATIC_URL + "/event/edit-event.html",
                controller: 'CreateEventController',
                data: {
                    eventType: 'EXTERNAL'
                }
            })
            .state('events.single', {
                abstract: true,
                url: '/:eventName',
                templateUrl: BASE_STATIC_URL + '/event/fragment/event-detail-container.html',
                resolve: {
                    'getEvent': function($stateParams, EventService) {
                        return EventService.getEvent($stateParams.eventName);
                    }
                },
                controllerAs: 'ctrl',
                controller: function(getEvent, $state) {
                    $state.current.data['event'] = getEvent.data.event;
                },
                data: {
                    displayEventData: true,
                    view: 'EVENT'
                }
            })
            .state('events.single.detail', {
                url: '/detail',
                templateUrl: BASE_STATIC_URL + '/event/detail.html',
                controller: 'EventDetailController',
                data: {
                    view: 'EVENT_DETAIL'
                }
            })
            .state('events.single.checkIn', {
                url: '/check-in',
                templateUrl: BASE_STATIC_URL + '/event/check-in.html',
                controller: 'EventCheckInController',
                data: {
                    view: 'CHECK_IN'
                }
            })
            .state('events.single.checkInScan', {
                url: '/check-in/scan',
                templateUrl: BASE_STATIC_URL + '/event/check-in-scan.html',
                controller: 'EventCheckInScanController',
                data: {
                    view: 'CHECK_IN_SCAN'
                }
            })
            .state('events.single.sendInvitations', {
                url: '/c/:categoryId/send-invitation',
                templateUrl: BASE_STATIC_URL + '/event/fragment/send-reserved-codes.html',
                controller: 'SendInvitationsController',
                data: {
                    view: 'SEND_INVITATIONS'
                }
            })
            .state('events.single.pending-reservations', {
                url: '/pending-reservations/',
                templateUrl: BASE_STATIC_URL + '/pending-reservations/index.html',
                controller: 'PendingReservationsController',
                data: {
                    view: 'PENDING_RESERVATIONS'
                }
            })
            .state('events.single.compose-custom-message', {
                url: '/compose-custom-message',
                templateUrl: BASE_STATIC_URL + '/custom-message/index.html',
                controller: 'ComposeCustomMessage',
                data: {
                    view: 'CUSTOM_MESSAGE'
                }
            })
            .state('events.single.show-waiting-queue', {
                url: '/waiting-queue',
                templateUrl: BASE_STATIC_URL + '/waiting-queue/index.html',
                controller: 'ShowWaitingQueue as ctrl',
                data: {
                    view: 'WAITING_QUEUE'
                }
            });
    });

    navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;

    admin.run(function($rootScope, $uibModal, $window, $state) {
        $rootScope.$on('ErrorNotAuthorized', function() {
            $uibModal.open({
                size:'sm',
                templateUrl:'/resources/angular-templates/admin/partials/error/not-authorized.html'
            }).result.then(angular.noop, function() {
                $state.go('index');
            });
        });
        $rootScope.$on('ErrorNotLoggedIn', function() {
            $window.location.reload();
        });
    });

    var validationResultHandler = function(form, deferred) {
        return function(validationResult) {
            if(validationResult.errorCount > 0) {
                angular.forEach(validationResult.validationErrors, function(error) {
                    var match = error.fieldName.match(/ticketCategories\[([0-9]+)\]\.dateString/);
                    if(match) {
                        //HACK
                        $("[data-ng-model=ticketCategory\\.dateString][name="+match[1]+"-dateString]").addClass('ng-invalid');
                        //
                    }
                    if(angular.isFunction(form.$setError)) {
                        form.$setError(error.fieldName, error.message);
                    }
                });
                setTimeout(function() {
                    var firstInvalidElem = $("input.ng-invalid:first, textarea.input.ng-invalid:first, select.ng-invalid:first");
                    if(firstInvalidElem.length > 0) {
                        $('html, body').animate({scrollTop: firstInvalidElem.offset().top - 80},500,function() {
                        firstInvalidElem.focus()
                        })
                    }

                }, 0);
                deferred.reject('invalid form');
            }
            deferred.resolve();
        };
    };

    var validationPerformer = function($q, validator, data, form) {
        var deferred = $q.defer();
        validator(data).success(validationResultHandler(form, deferred)).error(function(error) {
            deferred.reject(error);
        });
        return deferred.promise;
    };

    admin.controller('MenuController', ['$scope', '$http', '$window', 'UtilsService', function($scope, $http, $window, UtilsService) {
        var ctrl = this;
        ctrl.menuCollapsed = true;
        ctrl.toggleCollapse = function(currentStatus) {
            ctrl.menuCollapsed = !currentStatus;
        };
        ctrl.doLogout = function() {
            UtilsService.logout().then(function() {
                $window.location.reload();
            });
        };
    }]);


    var createCategoryValidUntil = function(sticky, categoryEndTime) {
        var now = moment().startOf('hour');
        var inceptionDateTime = {
            date: now.format('YYYY-MM-DD'),
            time: now.format('HH:mm')
        };

        if (!categoryEndTime || !categoryEndTime.date){
            categoryEndTime = {
                date: now.format('YYYY-MM-DD'),
                time: now.format('HH:mm')
            };
        }

        var expirationDateTime = _.clone(categoryEndTime);

        return {
            inception: inceptionDateTime,
            expiration: expirationDateTime,
            tokenGenerationRequested: false,
            sticky: sticky,
            bounded: false
        };

    };

    var createAndPushCategory = function(sticky, $scope) {
        $scope.event.ticketCategories.push(createCategoryValidUntil(sticky, $scope.event.begin));
    };

    var initScopeForEventEditing = function ($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state, PAYMENT_PROXY_DESCRIPTIONS) {
        $scope.organizations = {};

        $scope.isInternal = function(event) {
            return event.type === 'INTERNAL';
        };

        EventService.getSupportedLanguages().success(function(result) {
            $scope.allLanguages = result;
            $scope.allLanguagesMapping = {};
            var locales = 0;
            angular.forEach(result, function(r) {
                $scope.allLanguagesMapping[r.value] = r;
                locales |= r.value;
            });
            if($scope.event && !angular.isDefined($scope.event.locales)) {
                $scope.event.locales = locales;
            }
        });

        EventService.getDynamicFieldTemplates().success(function(result) {
            $scope.dynamicFieldTemplates = result;
        });

        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
        });

        $scope.$watch('event.organizationId', function(newVal) {
            if(newVal !== undefined && newVal !== null) {
                PaymentProxyService.getAllProxies(newVal).success(function(result) {
                    $scope.allowedPaymentProxies = _.map(result, function(p) {
                        return {
                            id: p.paymentProxy,
                            description: PAYMENT_PROXY_DESCRIPTIONS[p.paymentProxy] || 'Unknown provider ('+p.paymentProxy+')  Please check configuration',
                            enabled: p.status === 'ACTIVE'
                        };
                    });
                });
            }
        });

        $scope.canAddCategory = function(categories) {
            var remaining = _.foldl(categories, function(difference, category) {
                var categoryTickets = category.bounded ? category.maxTickets : 0;
                return difference - categoryTickets;
            }, $scope.event.availableSeats);

            var isDefinedMaxTickets = function(category) {
                return !category.bounded || (angular.isDefined(category.maxTickets) && category.maxTickets > 0);
            };

            return remaining > 0 && _.every(categories, function(category) {
                return angular.isDefined(category.name) &&
                    isDefinedMaxTickets(category)  &&
                    angular.isDefined(category.expiration.date);
            });
        };

        $scope.cancel = function() {
            if(window.sessionStorage) {
                delete window.sessionStorage.new_event;
            }
            $state.go('index');
        };

        //----------

        // 
        $scope.fieldTypes = FIELD_TYPES;

        $scope.addNewTicketField = function(event) {
            if(!event.ticketFields) {
                event.ticketFields = [];
            }

            event.ticketFields.push({required:false, order: event.ticketFields.length+1, type:'input:text', maxLength:255});
        };

        $scope.addTicketFieldFromTemplate = function(event, template) {
            if(!event.ticketFields) {
                event.ticketFields = [];
            }
            var nameExists = angular.isDefined(_.find(event.ticketFields, function(f) { return f.name === template.name;}));
            event.ticketFields.push({
                required:false,
                name: nameExists ? '' : template.name,
                order: event.ticketFields.length+1,
                type: template.type,
                restrictedValues: _.map(template.restrictedValues, function(v) {return {value: v}}),
                description: template.description,
                maxLength: template.maxLength,
                minLength: template.minLength
            });
        };

        $scope.removeTicketField = function(fields, field) {
            var index = fields.indexOf(field);
            fields.splice( index, 1 )
        };

        $scope.isLanguageSelected = function(lang, selectedLanguages) {
            return (selectedLanguages & lang) > 0;
        };

        $scope.addRestrictedValue = function(field) {
            var arr = field.restrictedValues || [];
            arr.push({});
            field.restrictedValues = arr;
        }

    };

    admin.controller('CreateEventController', function($scope, $state, $rootScope,
                                                       $q, OrganizationService, PaymentProxyService,
                                                       EventService, LocationService, PAYMENT_PROXY_DESCRIPTIONS) {

        var eventType = $state.$current.data.eventType;

        function initTicketCategoriesAndAdditionalServices() {
            if($scope.event.ticketCategories  === undefined) {
                $scope.event.ticketCategories = [];
            }
            if($scope.event.additionalServices === undefined) {
                $scope.event.additionalServices = [];
            }

            if(eventType === 'INTERNAL' && $scope.event.ticketCategories.length === 0) {
                createAndPushCategory(true, $scope);
            }
        }

        if(window.sessionStorage && window.sessionStorage.new_event) {
            $scope.event = JSON.parse(window.sessionStorage.new_event);

            //hack: remove angular hash for elements in arrays, or else ng-repeat will complain about duplicated data
            angular.forEach($scope.event.ticketCategories, function(v) {
                delete v.$$hashKey
            });
            angular.forEach($scope.event.additionalServices, function(v) {
                delete v.$$hashKey
            });

            initTicketCategoriesAndAdditionalServices();
            //
        } else {
            $scope.event = {
                    type: eventType,
                    freeOfCharge: false,
                    begin: {},
                    end: {}
                };
                initTicketCategoriesAndAdditionalServices();
        }

        $scope.reset = function() {
            $scope.event = {
                type: eventType,
                freeOfCharge: false,
                begin: {},
                end: {}
            };
            initTicketCategoriesAndAdditionalServices();
            initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state, PAYMENT_PROXY_DESCRIPTIONS);
        }


        $scope.allocationStrategyRadioClass = 'radio-inline';
        initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state, PAYMENT_PROXY_DESCRIPTIONS);
        $scope.addCategory = function() {
            createAndPushCategory(false, $scope);
        };

        $scope.setAdditionalServices = function(event, additionalServices) {
            event.additionalServices = additionalServices;
        };



        $scope.save = function(form, event) {
            /*if(!form.$valid) {
                return;
            }*/

            validationPerformer($q, EventService.checkEvent, event, form).then(function() {
                EventService.createEvent(event).success(function() {
                    if(window.sessionStorage) {
                        delete window.sessionStorage.new_event;
                    }
                    $state.go('events.single.detail', {eventName: event.shortName});
                });
            }, angular.noop);
        };

        //persist model
        $scope.$watch('event', function() {
            if(window.sessionStorage) {
                try{
                    window.sessionStorage['new_event'] = JSON.stringify($scope.event);
                } catch(e) {
                }
            }
        }, true);

        $scope.calcDynamicTickets = function(eventSeats, categories) {
            var value = 0;
            if(eventSeats) {
                value = eventSeats - _.chain(categories).filter('bounded').reduce(function(sum, c) {
                        return sum + (c.maxTickets || 0);
                    }, 0).value();

            }
            return value;
        }

    });

    admin.controller('EventDetailController', function ($scope,
                                                        $stateParams,
                                                        OrganizationService,
                                                        PromoCodeService,
                                                        EventService,
                                                        LocationService,
                                                        $rootScope,
                                                        PaymentProxyService,
                                                        $state,
                                                        $log,
                                                        $q,
                                                        $window,
                                                        $uibModal,
                                                        PAYMENT_PROXY_DESCRIPTIONS) {
        var loadData = function() {
            $scope.loading = true;

            return EventService.getEvent($state.params.eventName).success(function(result) {
                if($scope.event) {
                    //for sidebar
                    $rootScope.$emit('EventUpdated');
                }
                $scope.event = result.event;
                var href = $window.location.href;
                $scope.eventPublicURL = href.substring(0, href.indexOf('/admin/')) + '/event/' + result.event.shortName;
                $scope.organization = result.organization;
                $scope.validCategories = _.filter(result.event.ticketCategories, function(tc) {
                    return !tc.expired && tc.bounded;
                });


                //
                $scope.ticketCategoriesById = {};
                angular.forEach(result.event.ticketCategories, function(v) {
                    $scope.ticketCategoriesById[v.id] = v;
                });
                //

                $scope.loading = false;
                $scope.loadingMap = true;
                LocationService.getMapUrl(result.event.latitude, result.event.longitude).success(function(mapUrl) {
                    $scope.event.geolocation = {
                        mapUrl: mapUrl,
                        timeZone: result.event.timeZone
                    };
                    $scope.loadingMap = false;
                });


                PromoCodeService.list(result.event.id).success(function(list) {
                    $scope.promocodes = list;
                    angular.forEach($scope.promocodes, function(v) {
                        (function(v) {
                            PromoCodeService.countUse(result.event.id, v.promoCode).then(function(val) {
                                v.useCount = parseInt(val.data, 10);
                            });
                        })(v);
                    });
                });

                $scope.unbindTickets = function(event , category) {
                    EventService.unbindTickets(event, category).success(function() {
                        loadData();
                    });
                };

                EventService.getAdditionalFields($stateParams.eventName).success(function(result) {
                    $scope.additionalFields = result;
                });
            });
        };
        loadData().then(function() {
            initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state, PAYMENT_PROXY_DESCRIPTIONS);
        });
        $scope.allocationStrategyRadioClass = 'radio';
        $scope.evaluateCategoryStatusClass = function(index, category) {
            if(category.expired) {
                return 'category-expired';
            }
            return 'category-' + $rootScope.evaluateBarType(index);
        };
        
        $scope.evaluateClass = function(token) {
            switch(token.status) {
                case 'WAITING':
                    return 'bg-warning fa fa-cog fa-spin';
                case 'FREE':
                    return 'fa fa-qrcode';
                case 'TAKEN':
                    return 'bg-success fa fa-check';
                case 'CANCELLED':
                    return 'bg-default fa fa-eraser';
            }
        };

        $scope.selection = {
            active: true,
            expired: false,
            freeText: ''
        };

        $scope.getActualCapacity = function(category, event) {
            return category.bounded ? category.maxTickets : (event.dynamicAllocation + category.checkedInTickets + category.soldTickets);
        };

        $scope.isTokenViewCollapsed = function(category) {
            return !category.isTokenViewExpanded;
        };

        $scope.isTicketViewCollapsed = function(category) {
            return !category.isTicketViewExpanded;
        };

        $scope.toggleTokenViewCollapse = function(category) {
            category.isTokenViewExpanded = !category.isTokenViewExpanded;
        };

        $scope.toggleTicketViewCollapse = function(category) {
            category.isTicketViewExpanded = !category.isTicketViewExpanded;
        };

        $scope.evaluateTicketStatus = function(status) {
            var cls = 'fa ';

            switch(status) {
                case 'PENDING':
                    return cls + 'fa-warning text-warning';
                case 'ACQUIRED':
                    return cls + 'fa-bookmark text-success';
                case 'TO_BE_PAID':
                    return cls + 'fa-bookmark-o text-success';
                case 'CHECKED_IN':
                    return cls + 'fa-check-circle text-success';
                case 'CANCELLED':
                    return cls + 'fa-close text-danger';
            }

            return cls + 'fa-cog';
        };

        $scope.isPending = function(token) {
            return token.status === 'WAITING';
        };

        $scope.isReady = function(token) {
            return token.status === 'WAITING';
        };

        $scope.moveOrphans = function(srcCategory, targetCategoryId, eventId) {
            EventService.reallocateOrphans(srcCategory, targetCategoryId, eventId).success(function(result) {
                if(result === 'OK') {
                    loadData();
                }
            });
        };

        $scope.eventHeader = {};
        $scope.eventPrices = {};

        var validationErrorHandler = function(result, form, fieldsContainer) {
            return $q(function(resolve, reject) {
                if(result.data['errorCount'] == 0) {
                    resolve(result);
                } else {
                    _.forEach(result.data.validationErrors, function(error) {
                        var field = fieldsContainer[error.fieldName];
                        if(angular.isDefined(field)) {
                            field.$setValidity('required', false);
                            field.$setTouched();
                        }
                    });
                    reject('validation error');
                }
            });
        };

        var errorHandler = function(error) {
            $log.error(error.data);
            alert(error.data);
        };

        $scope.editHeader = function() {
            var parentScope = $scope;
            var editEventHeader = $uibModal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/edit-event-header-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.eventHeader = parentScope.eventHeader;
                    $scope.event = parentScope.event;
                    $scope.organizations = parentScope.organizations;
                    $scope.allLanguages = parentScope.allLanguages;
                    $scope.allLanguagesMapping = parentScope.allLanguagesMapping;

                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, eventHeader) {
                        if(!form.$valid) {
                            return;
                        }
                        EventService.updateEventHeader(eventHeader).then(function(result) {
                            validationErrorHandler(result, form, form.editEventHeader).then(function(result) {
                                $scope.$close(eventHeader);
                            });
                        }, errorHandler);
                    };
                }
            });
            editEventHeader.result.then(function() {
                loadData();
            });
        };

        $scope.editPrices = function() {
            var parentScope = $scope;
            var editPrices = $uibModal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/edit-event-prices-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.eventPrices = parentScope.eventPrices;
                    $scope.event = parentScope.event;
                    $scope.allowedPaymentProxies = parentScope.allowedPaymentProxies;

                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, eventPrices, organizationId) {
                        if(!form.$valid) {
                            return;
                        }
                        var obj = {'organizationId':organizationId};
                        angular.extend(obj, eventPrices);
                        EventService.updateEventPrices(obj).then(function(result) {
                            validationErrorHandler(result, form, form.editPrices).then(function(result) {
                                $scope.$close(eventPrices);
                            });
                        }, errorHandler);
                    };
                }
            });
            editPrices.result.then(function() {
                loadData();
            });
        };

        $scope.openDeleteWarning = function(event) {
            EventService.deleteEvent(event.id).then(function(result) {
                $state.go('index');
            });
        };


        var parentScope = $scope;

        var openCategoryDialog = function(category, event) {
            var editCategory = $uibModal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/edit-category-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.allLanguagesMapping = parentScope.allLanguagesMapping;
                    $scope.ticketCategory = category;
                    $scope.event = event;
                    $scope.editMode = true;
                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, category, event) {
                        if(!form.$valid) {
                            return;
                        }
                        EventService.saveTicketCategory(event, category).then(function(result) {
                            validationErrorHandler(result, form, form).then(function() {
                                loadData();
                                $scope.$close(true);
                            });
                        }, errorHandler);
                    };
                }
            });
            return editCategory.result;
        };

        $scope.addCategory = function(event) {
            openCategoryDialog(createCategoryValidUntil(true, event.begin), event).then(function() {
                loadData();
            });
        };

        $scope.openConfiguration = function(event, category) {
            $uibModal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/category-configuration-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.allLanguagesMapping = parentScope.allLanguagesMapping;
                    $scope.ticketCategory = category;
                    $scope.event = event;
                    $scope.editMode = true;
                    $scope.modal = $uibModal;
                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, category, event) {
                        if(!form.$valid) {
                            return;
                        }
                        EventService.saveTicketCategory(event, category).then(function(result) {
                            validationErrorHandler(result, form, form).then(function() {
                                $scope.$close(true);
                            });
                        }, errorHandler);
                    };
                }
            });
        };

        $scope.toggleLocking = function(event, ticket, category) {
            EventService.toggleTicketLocking(event, ticket, category).then(function() {
                loadData();
            });
        };

        $scope.editCategory = function(category, event) {
            var inception = moment(category.formattedInception);
            var expiration = moment(category.formattedExpiration);
            var categoryObj = {
                id: category.id,
                name: category.name,
                price: category.price,
                description: category.description,
                maxTickets: category.maxTickets,
                bounded: category.bounded,
                inception: {
                    date: inception.format('YYYY-MM-DD'),
                    time: inception.format('HH:mm')
                },
                expiration: {
                    date: expiration.format('YYYY-MM-DD'),
                    time: expiration.format('HH:mm')
                },
                tokenGenerationRequested: category.accessRestricted,
                sticky: false
            };

            openCategoryDialog(categoryObj, event).then(function() {
                loadData();
            });
        };

        var getPendingPayments = function() {
            EventService.getPendingPayments($stateParams.eventName).success(function(data) {
                $scope.pendingReservations = data;
            });
        };

        getPendingPayments();
        $scope.registerPayment = function(eventName, id) {
            $scope.loading = true;
            EventService.registerPayment(eventName, id).success(function() {
                loadData();
                getPendingPayments();
            }).error(function() {
                $scope.loading = false;
            });
        };
        $scope.deletePayment = function(eventName, id) {
            $scope.loading = true;
            EventService.cancelPayment(eventName, id).success(function() {
                loadData();
                getPendingPayments();
            }).error(function() {
                $scope.loading = false;
            });
        };
        
        //
        
        $scope.deletePromocode = function(promocode) {
            if($window.confirm('Delete promo code ' + promocode.promoCode + '?')) {
                PromoCodeService.remove($scope.event.id, promocode.promoCode).then(loadData, errorHandler);
            }
        };
        
        $scope.disablePromocode = function(promocode) {
            if($window.confirm('Disable promo code ' + promocode.promoCode + '?')) {
                PromoCodeService.disable($scope.event.id, promocode.promoCode).then(loadData, errorHandler);
            }
        };


        //FIXME
        $scope.changeDate = function(promocode) {

            var eventId = $scope.event.id;

            $uibModal.open({
                size: 'lg',
                templateUrl: BASE_STATIC_URL + '/event/fragment/edit-date-promo-code-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.cancel = function() {$scope.$dismiss('canceled');};
                    var start = moment(promocode.formattedStart);
                    var end = moment(promocode.formattedEnd);
                    $scope.promocode = {start: {date: start.format('YYYY-MM-DD'), time: start.format('HH:mm')}, end: {date: end.format('YYYY-MM-DD'), time: end.format('HH:mm')}};
                    $scope.update = function(toUpdate) {
                        PromoCodeService.update(eventId, promocode.promoCode, toUpdate).then(function() {
                            $scope.$close(true);
                        }).then(loadData);
                    };
                }
            });
        };


        //
        $scope.addPromoCode = function(event) {
            $uibModal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/edit-promo-code-modal.html',
                backdrop: 'static',
                controller: function($scope) {

                    $scope.event = event;
                    
                    var now = moment();
                    var eventBegin = moment(event.formattedBegin);

                    $scope.validCategories = _.filter(event.ticketCategories, function(tc) {
                        return !tc.expired;
                    });
                    
                    $scope.promocode = {discountType :'PERCENTAGE', start : {date: now.format('YYYY-MM-DD'), time: now.format('HH:mm')}, end: {date: eventBegin.format('YYYY-MM-DD'), time: eventBegin.format('HH:mm')}, categories:[]};

                    $scope.addCategory = function addCategory(index, value) {
                        $scope.promocode.categories[index] = value;
                    };
                    
                    $scope.$watch('promocode.promoCode', function(newVal) {
                        if(newVal) {
                            $scope.promocode.promoCode = newVal.toUpperCase();
                        }
                    });
                    
                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, promocode, event) {
                        if(!form.$valid) {
                            return;
                        }
                        $scope.$close(true);


                        promocode.categories = _.filter(promocode.categories, function(i) {return i != null;});
                        
                        PromoCodeService.add(event.id, promocode).then(function(result) {
                            validationErrorHandler(result, form, form.promocode).then(function() {
                                $scope.$close(true);
                            });
                        }, errorHandler).then(loadData);
                    };
                }
            });
        };

        $scope.countActive = function(categories) {
            return _.countBy(categories, 'expired')['false'] || '0';
        };

        $scope.countExpired = function(categories) {
            return _.countBy(categories, 'expired')['true'] || '0';
        };


        $scope.openFieldSelectionModal = function() {
            EventService.exportAttendees(parentScope.event);
        };

        $scope.downloadSponsorsScan = function() {
            $window.open($window.location.pathname+"/api/events/"+parentScope.event.shortName+"/sponsor-scan/export.csv");
        };

        $scope.prepareFieldDescriptionEdit = function(baseObject, field) {
            angular.copy(field, baseObject);
        };

        $scope.saveFieldDescription = function(description) {
            EventService.saveFieldDescription($scope.event.shortName, description).then(loadData);
        };
        
        $scope.deleteFieldModal = function(field) {
        	$uibModal.open({
        		size: 'lg',
        		templateUrl: BASE_STATIC_URL + '/event/fragment/delete-field-modal.html',
        		controller: function($scope) {
        			$scope.field = field;
        			$scope.deleteField = function(id) {
        				EventService.deleteField($stateParams.eventName, id).then(function() {
        					return loadData();
                    	}).then(function() {
                    		$scope.$close(true);
                    	});
        			}
        		}
        	});
        };
        
        
        $scope.fieldUp = function(index) {
        	var targetId = $scope.additionalFields[index].id;
        	var prevTargetId = $scope.additionalFields[index-1].id;
        	EventService.swapFieldPosition($stateParams.eventName, targetId, prevTargetId).then(function(result) {
        		return EventService.getAdditionalFields($stateParams.eventName);
        	}).then(function (result) {
        		$scope.additionalFields = result.data;
        	});
        };
        
        $scope.fieldDown = function(index) {
        	var targetId = $scope.additionalFields[index].id;
        	var nextTargetId = $scope.additionalFields[index+1].id;
        	EventService.swapFieldPosition($stateParams.eventName, targetId, nextTargetId).then(function(result) {
        		return EventService.getAdditionalFields($stateParams.eventName);
        	}).then(function (result) {
        		$scope.additionalFields = result.data;
        	});
        }
        
        $scope.addField = function(event) {
        	$uibModal.open({
                size:'lg',
                templateUrl: BASE_STATIC_URL + '/event/fragment/add-field-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                	$scope.event = event;
                	$scope.field = {};
                	$scope.fieldTypes = FIELD_TYPES;
                	
                	
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
                	}

                	//
                	EventService.getSupportedLanguages().success(function(result) {
                        $scope.allLanguages = result;
                        $scope.allLanguagesMapping = {};
                        angular.forEach(result, function(r) {
                            $scope.allLanguagesMapping[r.value] = r;
                        });
                    });
                	
                	//
                	
                	$scope.addRestrictedValue = function() {
                		var field = $scope.field;
                        var arr = field.restrictedValues || [];
                        arr.push({});
                        field.restrictedValues = arr;
                    };
                    $scope.isLanguageSelected = function(lang, selectedLanguages) {
                        return (selectedLanguages & lang) > 0;
                    };
                    
                    $scope.addField = function(form, field) {
                    	EventService.addField($stateParams.eventName, field).then(function(result) {
                    		return loadData();
                    	}).then(function() {
                    		$scope.$close(true);
                    	});
                    };
                }});
        };

        $scope.activateEvent = function(id) {
            EventService.activateEvent(id).then(function() {
                loadData();
            });
        };

        var unbind = $rootScope.$on('SidebarCategoryFilterUpdated', function(e, categoryFilter) {
            if(categoryFilter) {
                $scope.selection.freeText = categoryFilter.freeText;
            }
        });
        
        $scope.updateSelectionText = function() {
            $rootScope.$emit('CategoryFilterUpdated', $scope.selection);
        };

        $scope.$on('$destroy', unbind);


    });

    admin.controller('SendInvitationsController', function($scope, $stateParams, $state, EventService, $window) {
        $scope.eventName = $stateParams.eventName;
        $scope.categoryId = $stateParams.categoryId;

        var loadSentCodes = function() {
            EventService.loadSentCodes($stateParams.eventName, $stateParams.categoryId).then(function(result) {
                $scope.codes = result.data;
            })
        };

        $scope.closeAlert = function() {
            $scope.errorMessage = null;
            $scope.success = false;
        };

        loadSentCodes();

        $scope.sendCodes = function(data) {
            EventService.sendCodesByEmail($stateParams.eventName, $stateParams.categoryId, data).success(function() {
                loadSentCodes();
                $scope.success = true;
            }).error(function(e) {
                $scope.errorMessage = e.data;
                $scope.success = false;
            });
        };

        $scope.clearRecipient = function(id, code) {
            if($window.confirm('About to clear recipient for code '+code+'. Are you sure?')) {
                EventService.deleteRecipientData($stateParams.eventName, $stateParams.categoryId, id).then(function() {
                    loadSentCodes();
                });
            }
        };

        $scope.uploadSuccess = function(data) {
            $scope.results = data;
        };
        $scope.uploadError = function(data) {
            $scope.errorMessage = data;
            $scope.success = false;
        };
        $scope.uploadUrl = '/admin/api/events/'+$stateParams.eventName+'/categories/'+$stateParams.categoryId+'/link-codes';
    });
    
    admin.controller('EventCheckInController', function($scope, $stateParams, $timeout, $log, $state, EventService, CheckInService) {

        $scope.selection = {};
        $scope.checkedInSelection = {};

        $scope.advancedSearch = {};

        $scope.resetAdvancedSearch = function() {
            $scope.advancedSearch = {};
        };

        $scope.toggledAdvancedSearch = function(toggled) {
            if(toggled) {
                $scope.selection.freeText = undefined;
            } else {
                $scope.resetAdvancedSearch();
            }
        };

        $scope.goToScanPage = function() {
            $state.go('events.single.checkInScan', $stateParams);
        };

        EventService.getEvent($stateParams.eventName).success(function(result) {
            $scope.event = result.event;
            CheckInService.findAllTickets(result.event.id).success(function(tickets) {
                $scope.tickets = tickets;
            });
        });

        $scope.toBeCheckedIn = function(ticket) {
            return  ['TO_BE_PAID', 'ACQUIRED'].indexOf(ticket.status) >= 0;
        };

        
        $scope.reloadTickets = function() {
            CheckInService.findAllTickets($scope.event.id).success(function(tickets) {
                $scope.tickets = tickets;
            });
        };

        $scope.manualCheckIn = function(ticket) {
            CheckInService.manualCheckIn(ticket).then($scope.reloadTickets).then(function() {
                $scope.selection = {};
            });
        };
    });

    admin.controller('EventCheckInScanController', function($scope, $stateParams, $timeout, $log, $state, EventService, CheckInService) {

        $scope.scanning = {visible : false, ticket : {}};


        var canReadCamera = MediaStreamTrack.getSources !== undefined;

        $scope.goToScanPage = function() {
            $state.go('events.single.checkInScan', $stateParams);
        };

        $scope.canReadCamera = canReadCamera;
        if(canReadCamera) {

            var processingScannedImage = false;

            navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
            $scope.videos = [];
            $scope.stream = null;

            var timeoutPromise = null;
            var worker = new Worker("../resources/js/jsqrcode/decode-worker.js");

            worker.addEventListener('message', function(message) {
                processingScannedImage = false;
                var result = message.data;
                $scope.scanning.loadingTicket = false;
                if(result === 'error decoding QR Code') {
                    $log.debug('error decoding qr code');
                } else if ($scope.scanning.scannedResult == null) {
                    $scope.$apply(function() {
                        $scope.scanning.visible = false;
                        $scope.scanning.ticket.code = result;
                        $scope.scanning.loadingTicket = true;

                        CheckInService.getTicket($scope.event.id, result).success(function(result) {
                            $scope.scanning.scannedTicketInfo = result.ticket;
                            $scope.scanning.scannedResult = result.result;
                            $scope.scanning.loadingTicket = false;
                        });
                    });
                } else {
                    $log.debug('scanned result already present, skipping');
                }
            }, false);

            var captureFrame = function() {
                if($scope.scanning.visible && $scope.scanning.scannedResult == null && !processingScannedImage) {
                    $log.debug('try to capture frame');
                    try {
                        var videoElement = document.getElementById('checkInVideoElement');
                        var canvas = document.getElementById("checkInImageCanvas");
                        canvas.height = videoElement.videoHeight;
                        canvas.width = videoElement.videoWidth;

                        canvas.getContext("2d").drawImage(videoElement, 0, 0);
                        var imageData = canvas.getContext("2d").getImageData(0,0,canvas.width, canvas.height);
                        worker.postMessage(imageData);
                        processingScannedImage = true;
                    } catch(e) {
                        processingScannedImage = false;
                        $log.debug('error', e)
                    }
                } else {
                    $log.debug('skipping');
                }

                timeoutPromise = $timeout(function() {
                    captureFrame();
                }, 250);
            }

            var endVideoStream = function () {
                processingScannedImage = false;
                if (!!$scope.stream) {
                    $scope.stream.stop();
                }
            }

            var stopScanning = function () {
                endVideoStream();
                $scope.resetScanning();
                $scope.scanning.visible = false;
                $timeout.cancel(timeoutPromise);
            }

            $scope.$on('$destroy', function() {
                worker.terminate();
                endVideoStream();
                stopScanning();
            });

            $scope.stopScanning = stopScanning;

            $scope.selectSource = function(source) {
                if(source == undefined) {
                    return;
                }

                endVideoStream();
                var videoElement = document.getElementById('checkInVideoElement');
                videoElement.src = null;


                var constraint = {video: {optional: [{sourceId: source.source.id}]}};

                navigator.getUserMedia(constraint, function(stream) {
                    $scope.stream = stream; // make stream available to console
                    videoElement.src = window.URL.createObjectURL(stream);
                    videoElement.play();
                    $timeout.cancel(timeoutPromise);
                    captureFrame();
                }, function() {
                    alert('error while loading camera');
                    $timeout.cancel(timeoutPromise);
                });
            };

            MediaStreamTrack.getSources(function(sources) {
                var videos = [];
                angular.forEach(sources, function(v,i) {
                    if(v.kind === 'video') {
                        videos.push({ source: v, label: (v.label || 'camera ' + i)});
                    }
                });
                $scope.$apply(function() {
                    $scope.videos = videos;
                });
            });
        }

        EventService.getEvent($stateParams.eventName).success(function(result) {
            $scope.event = result.event;
        });


        $scope.checkIn = function(ticket) {
            $scope.scanning.checkInInAction = true;
            CheckInService.checkIn($scope.event.id, ticket).success(function(result) {
                $scope.scanning.checkInInAction = false;
                $scope.scanning.scannedTicketInfo = result.ticket;
                $scope.scanning.scannedResult = result.result;


                if(result.ticket.status === 'CHECKED_IN') {
                    $scope.resetScanning();
                }
            });
        };

        $scope.resetScanning = function() {
            $scope.scanning = {visible: $scope.scanning, ticket: {}};
        };

        $scope.resetForm = function(ticket) {
            ticket.code = null;
            $scope.resetScanning();
        };

        $scope.confirmPayment = function() {
            $scope.scanning.confirmPaymentInAction = true;
            CheckInService.confirmPayment($scope.event.id, $scope.scanning.ticket).then(function() {
                CheckInService.getTicket($scope.event.id, $scope.scanning.ticket.code).success(function(result) {
                    $scope.scanning.scannedTicketInfo = result.ticket;
                    $scope.scanning.scannedResult = result.result;
                    $scope.scanning.confirmPaymentInAction = false;
                });
            });
        };

    });

    admin.controller('MessageBarController', function($scope, $rootScope) {
        $rootScope.$on('Message', function(m) {
            $scope.message = m;
        });
    });

    admin.controller('PendingReservationsController', function($scope, EventService, $stateParams, $log, $window) {

        EventService.getEvent($stateParams.eventName).then(function(result) {
            $scope.event = result.data.event;
        });

        var getPendingPayments = function(force) {
            EventService.getPendingPayments($stateParams.eventName, force).success(function(data) {
                $scope.pendingReservations = data;
                $scope.loading = false;
            });
        };

        $scope.eventName = $stateParams.eventName;
        $scope.uploadSuccess = function(data) {
            $scope.results = data;
            getPendingPayments();
        };

        $scope.uploadUrl = '/admin/api/events/'+$stateParams.eventName+'/pending-payments/bulk-confirmation';

        getPendingPayments();
        $scope.registerPayment = function(eventName, id) {
            $scope.loading = true;
            EventService.registerPayment(eventName, id).success(function() {
                getPendingPayments(true);
            }).error(function() {
                $scope.loading = false;
            });
        };
        $scope.deletePayment = function(eventName, id) {
            if(!$window.confirm('Do you really want to delete this reservation?')) {
                return;
            }
            $scope.loading = true;
            EventService.cancelPayment(eventName, id).success(function() {
                getPendingPayments(true);
            }).error(function() {
                $scope.loading = false;
            });
        };
    });

    admin.controller('ComposeCustomMessage', function($scope, $stateParams, EventService, $uibModal, $state, $q) {


        $q.all([EventService.getSelectedLanguages($stateParams.eventName),
            EventService.getCategoriesContainingTickets($stateParams.eventName), EventService.getEvent($stateParams.eventName)])
        .then(function(results) {
                $scope.messages = _.map(results[0].data, function(r) {
                    return {
                        textExample: '{{organizationName}} <{{organizationEmail}}>',
                        subjectExample: 'An important message from {{eventName}}',
                        locale: r.language,
                        text: '',
                        subject: '',
                        attachTicket: false
                    };
                });
                $scope.fullName = 'John Doe';

                $scope.categories = results[1].data;
                $scope.categoryId = undefined;

                var eventDescriptor = results[2].data;
                $scope.organization = eventDescriptor.organization;
                $scope.eventName = eventDescriptor.event.shortName;
        });

        $scope.cancel = function() {
            $state.go('events.single.detail', {eventName: $stateParams.eventName});
        };


        $scope.showPreview = function(frm, eventName, categoryId, messages, categories) {
            if(!frm.$valid) {
                return;
            }
            var error = _.find(messages, function(m) {
                return _.trim(m.text) === '' || _.trim(m.subject) === '';
            });
            if(angular.isDefined(error)) {
                alert('please fill all the messages');
                return;
            }
            EventService.getMessagesPreview(eventName, categoryId, messages).success(function(result) {
                var preview = $uibModal.open({
                    size:'lg',
                    templateUrl:BASE_STATIC_URL + '/custom-message/preview.html',
                    backdrop: 'static',
                    controller: function($scope) {
                        if(angular.isDefined(categoryId)) {
                            var category = _.find(categories, function(c) {return c.id === categoryId});
                            $scope.categoryName = angular.isDefined(category) ? category.name : "";
                        }
                        $scope.messages = result.preview;
                        $scope.affectedUsers = result.affectedUsers;
                        $scope.eventName = eventName;
                        $scope.categoryId = categoryId;
                        $scope.cancel = function() {
                            $scope.$dismiss('canceled');
                        };
                        $scope.sendMessage = function(frm, eventName, categoryId, messages, affectedUsers) {
                            if(!frm.$valid) {
                                return;
                            }
                            if(affectedUsers === 0 && !confirm('No one will receive this message. Do you really want to continue?')) {
                                return;
                            }
                            $scope.pending = true;
                            EventService.sendMessages(eventName, categoryId, messages).success(function(result) {
                                $scope.pending = false;
                                alert(result + ' messages have been enqueued');
                                $scope.$close(true);
                            }).error(function(error) {
                                $scope.pending = false;
                                alert(error);
                            });
                        };
                    }
                });
            }).error(function(resp) {
                alert(resp);
            });
        };
    });

    admin.controller('ShowWaitingQueue', ['WaitingQueueService', '$stateParams', '$state', function(WaitingQueueService, $stateParams, $state) {
        var ctrl = this;
        this.loading = true;
        this.eventName = $stateParams.eventName;
        WaitingQueueService.loadAllSubscribers(this.eventName).success(function(result) {
            ctrl.subscriptions = result;
            ctrl.loading = false;
        });
    }]);

    admin.run(function($rootScope, PriceCalculator) {
        $rootScope.evaluateBarType = function(index) {
            var barClasses = ['warning', 'info', 'success'];
            if(index < barClasses.length) {
                return barClasses[index];
            }
            return index % 2 == 0 ? 'info' : 'success';
        };

        $rootScope.calcBarValue = PriceCalculator.calcBarValue;

        $rootScope.calcCategoryPricePercent = PriceCalculator.calcCategoryPricePercent;

        $rootScope.calcCategoryPrice = PriceCalculator.calcCategoryPrice; 

        $rootScope.calcPercentage = PriceCalculator.calcPercentage;

        $rootScope.applyPercentage = PriceCalculator.applyPercentage;

        $rootScope.calculateTotalPrice = PriceCalculator.calculateTotalPrice;
    });

})();
