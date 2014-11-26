(function () {
    "use strict";

    var BASE_TEMPLATE_URL = "/admin/partials";
    var BASE_STATIC_URL = "/resources/angular-templates/admin/partials";
    var PAYMENT_PROXY_DESCRIPTIONS = {
        "STRIPE": "Credit card payments",
        "ON_SITE": "On site (cash) payment"
    };
    var admin = angular.module('adminApplication', ['ui.bootstrap', 'ui.router', 'adminDirectives', 'adminServices', 'utilFilters', 'ngMessages']);

    admin.config(function($stateProvider, $urlRouterProvider) {
        $urlRouterProvider.otherwise("/");
        $stateProvider
            .state('index', {
                url: "/",
                templateUrl: BASE_TEMPLATE_URL + "/index.html"
            })
            .state('index.new-organization', {
                url: "new-organization",
                views: {
                    "newOrganization": {
                        templateUrl: BASE_STATIC_URL + "/main/edit-organization.html",
                        controller: 'CreateOrganizationController'
                    }
                }
            })
            .state('index.new-user', {
                url: "new-user",
                views: {
                    "newUser": {
                        templateUrl: BASE_STATIC_URL + "/main/edit-user.html",
                        controller: 'CreateUserController'
                    }
                }
            })
            .state('events', {
                abstract: true,
                url: '/events',
                templateUrl: BASE_STATIC_URL + "/event/index.html"
            })
            .state('events.new', {
                url: '/new',
                templateUrl: BASE_STATIC_URL + "/event/edit-event.html",
                controller: 'CreateEventController'
            })
            .state('events.detail', {
                url: '/:eventName',
                templateUrl: BASE_STATIC_URL + '/event/detail.html',
                controller: 'EventDetailController'
            })
            .state('configuration', {
                url: '/configuration',
                templateUrl: BASE_STATIC_URL + '/configuration/index.html',
                controller: 'ConfigurationController'
            })
    });

    var validationResultHandler = function(form, deferred) {
        return function(validationResult) {
            if(validationResult.errorCount > 0) {
                angular.forEach(validationResult.validationErrors, function(error) {
                    form.$setError(error.fieldName, error.message);
                });
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

    admin.controller('CreateOrganizationController', function($scope, $state, $rootScope, $q, OrganizationService) {
        $scope.organization = {};
        $scope.save = function(form, organization) {
            if(!form.$valid) {
                return;
            }
            validationPerformer($q, OrganizationService.checkOrganization, organization, form).then(function() {
                OrganizationService.createOrganization(organization).success(function() {
                    $rootScope.$emit('ReloadOrganizations', {});
                    $state.go('index');
                });
            }, angular.noop);
        };
        $scope.cancel = function() {
            $state.go('index');
        };
    });

    admin.controller('CreateUserController', function($scope, $state, $rootScope, $q, OrganizationService, UserService) {
        $scope.user = {};
        $scope.organizations = {};
        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
        });

        $scope.save = function(form, user) {
            if(!form.$valid) {
                return;
            }
            validationPerformer($q, UserService.checkUser, user, form).then(function() {
                UserService.createUser(user).success(function() {
                    $rootScope.$emit('ReloadUsers', {});
                    $state.go('index');
                });
            }, angular.noop);
        };

        $scope.cancel = function() {
            $state.go('index');
        };

    });

    var createCategory = function(sticky, $scope, expirationExtractor) {
        var lastCategory = _.last($scope.event.ticketCategories);
        var inceptionDate, notBefore;
        if(angular.isDefined(lastCategory)) {
            var lastExpiration = angular.isFunction(expirationExtractor) ? expirationExtractor(lastCategory) : lastCategory.expiration.date;
            inceptionDate = moment(lastExpiration).format('YYYY-MM-DD');
            notBefore = inceptionDate;
        } else {
            inceptionDate = moment().format('YYYY-MM-DD');
            notBefore = undefined;
        }

        return {
            inception: {
                date: inceptionDate
            },
            tokenGenerationRequested: false,
            expiration: {},
            sticky: sticky,
            notBefore: notBefore
        };

    };

    var createAndPushCategory = function(sticky, $scope, expirationExtractor) {
        $scope.event.ticketCategories.push(createCategory(sticky, $scope, expirationExtractor));
    };

    var initScopeForEventEditing = function ($scope, OrganizationService, PaymentProxyService, LocationService, $state) {
        $scope.organizations = {};

        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
        });

        PaymentProxyService.getAllProxies().success(function(result) {
            $scope.allowedPaymentProxies = _.map(result, function(p) {
                return {
                    id: p,
                    description: PAYMENT_PROXY_DESCRIPTIONS[p] || 'Unknown provider ('+p+')  Please check configuration'
                };
            });
        });

        $scope.addCategory = function() {
            createAndPushCategory(false, $scope);
        };

        $scope.canAddCategory = function(categories) {
            var remaining = _.foldl(categories, function(difference, category) {
                return difference - category.maxTickets;
            }, $scope.event.availableSeats);

            return remaining > 0 && _.every(categories, function(category) {
                return angular.isDefined(category.name) &&
                    angular.isDefined(category.maxTickets) &&
                    category.maxTickets > 0 &&
                    angular.isDefined(category.expiration.date);
            });
        };

        $scope.cancel = function() {
            $state.go('index');
        };

    };

    admin.controller('CreateEventController', function($scope, $state, $rootScope,
                                                       $q, OrganizationService, PaymentProxyService,
                                                       EventService, LocationService) {

        $scope.event = {
            freeOfCharge: false,
            begin: {},
            end: {}
        };
        initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, $state);
        $scope.event.ticketCategories = [];
        createAndPushCategory(true, $scope);

        $scope.save = function(form, event) {
            validationPerformer($q, EventService.checkEvent, event, form).then(function() {
                EventService.createEvent(event).success(function() {
                    $state.go('index');
                });
            }, angular.noop);
        };

    });

    admin.controller('EventDetailController', function ($scope,
                                                        $stateParams,
                                                        OrganizationService,
                                                        EventService,
                                                        LocationService,
                                                        $rootScope,
                                                        PaymentProxyService,
                                                        $state,
                                                        $log,
                                                        $q,
                                                        $modal) {
        var loadData = function() {
            $scope.loading = true;
            EventService.getEvent($stateParams.eventName).success(function(result) {
                $scope.event = result.event;
                $scope.organization = result.organization;
                $scope.validCategories = _.filter(result.event.ticketCategories, function(tc) {
                    return !tc.expired;
                });
                $scope.loading = false;
                $scope.loadingMap = true;
                LocationService.getMapUrl(result.event.latitude, result.event.longitude).success(function(mapUrl) {
                    $scope.event.geolocation = {
                        mapUrl: mapUrl,
                        timeZone: result.event.timeZone
                    };
                    $scope.loadingMap = false;
                });
            });
        };
        loadData();
        initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, $state);
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

        $scope.toggleEditHeader = function(editEventHeader) {
            $scope.editEventHeader = !editEventHeader;
        };

        $scope.toggleEditPrices = function(editPrices) {
            $scope.editPrices = !editPrices;
        };

        var validationErrorHandler = function(result, form, fieldsContainer) {
            return $q(function(resolve, reject) {
                if(result.data['errorCount'] == 0) {
                    resolve(result);
                } else {
                    form.$setValidity(false);
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

        $scope.saveEventHeader = function(form, header) {
            EventService.updateEventHeader(header).then(function(result) {
                validationErrorHandler(result, form, form.editEventHeader).then(function(result) {
                    $scope.editEventHeader = false;
                    loadData();
                });
            }, errorHandler);
        };

        $scope.saveEventPrices = function(form, eventPrices) {
            EventService.updateEventPrices(eventPrices).then(function(result) {
                validationErrorHandler(result, form, form.editPrices).then(function(result) {
                    $scope.editPrices = false;
                    loadData();
                });
            }, errorHandler);
        };

        var openCategoryDialog = function(category, event) {
            var editCategory = $modal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/edit-category-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.ticketCategory = category;
                    $scope.event = event;
                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, category, event) {
                        if(!form.$valid) {
                            return;
                        }
                        EventService.saveTicketCategory(event, category).then(function(result) {
                            validationErrorHandler(result, form, form.ticketCategory).then(function() {
                                $scope.$close(true);
                            });
                        }, errorHandler);
                    };
                }
            });
            return editCategory.result;
        };

        $scope.addCategory = function(event) {
            openCategoryDialog(createCategory(true, $scope, function(obj) {return obj.formattedExpiration}), event).then(function() {
                loadData();
            });
        };

        $scope.editCategory = function(category, event) {
            var inception = moment(category.formattedInception);
            var expiration = moment(category.formattedExpiration);
            var categoryObj = {
                name: category.name,
                price: category.price,
                description: category.description,
                maxTickets: category.maxTickets,
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
    });

    admin.controller('MessageBarController', function($scope, $rootScope) {
        $rootScope.$on('Message', function(m) {
            $scope.message = m;
        });
    });

    admin.controller('ConfigurationController', function($scope, ConfigurationService) {
        $scope.loading = true;
        ConfigurationService.loadAll().success(function(result) {
            $scope.settings = result;
            $scope.loading = false;
        });
        
        $scope.removeConfigurationKey = function(key) {
        	$scope.loading = true;
            ConfigurationService.remove(key).then(function() {return ConfigurationService.loadAll();}).then(function(result) {
            	console.log(result);
            	$scope.settings = result.data;
                $scope.loading = false;
            });
        };
        
        $scope.configurationChange = function(conf) {
            if(!conf.value) {
                return;
            }
            $scope.loading = true;
            ConfigurationService.update(conf).success(function(result) {
                $scope.settings = result;
                $scope.loading = false;
            });
        };
    });

    admin.run(function($rootScope, PriceCalculator) {
        var calculateNetPrice = function(event) {
            if(isNaN(event.regularPrice) || isNaN(event.vat)) {
                return numeral(0.0);
            }
            if(!event.vatIncluded) {
                return numeral(event.regularPrice);
            }
            return numeral(event.regularPrice).divide(numeral(1).add(numeral(event.vat).divide(100)));
        };

        $rootScope.evaluateBarType = function(index) {
            var barClasses = ['danger', 'warning', 'info', 'success'];
            if(index < barClasses.length) {
                return barClasses[index];
            }
            return index % 2 == 0 ? 'info' : 'success';
        };

        $rootScope.calcBarValue = function(categorySeats, eventSeats) {
            return PriceCalculator.calcBarValue(categorySeats, eventSeats);
        };

        $rootScope.calcCategoryPricePercent = function(category, event) {
            return PriceCalculator.calcCategoryPricePercent(category, event);
        };

        $rootScope.calcCategoryPrice = function(category, event) {
            return PriceCalculator.calcCategoryPrice(category, event);
        };

        $rootScope.calcPercentage = function(fraction, total) {
            return PriceCalculator.calcPercentage(fraction, total);
        };

        $rootScope.applyPercentage = function(total, percentage) {
            return PriceCalculator.applyPercentage(total, percentage);
        };

        $rootScope.calculateTotalPrice = function(event) {
            return PriceCalculator.calculateTotalPrice(event);
        };
    });

})();