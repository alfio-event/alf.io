(function () {
    "use strict";

    var BASE_TEMPLATE_URL = "/admin/partials";
    var BASE_STATIC_URL = "/resources/angularTemplates/admin/partials";
    var PAYMENT_PROXY_DESCRIPTIONS = {
        "STRIPE": "Credit card payments",
        "PAYPAL": "PayPal account",
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
                templateUrl: BASE_STATIC_URL + "/event/detail.html",
                controller: 'EventDetailController'
            })
    });

    var validationResultHandler = function(form, deferred) {
        return function(validationResult) {
            if(validationResult.errorCount > 0) {
                angular.forEach(validationResult.validationErrors, function(error) {
                    form.$setError(error.fieldName, error.message);
                });
                deferred.reject("invalid form");
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

    var calcPercentage = function(fraction, total) {
        if(isNaN(fraction) || isNaN(total)){
            return numeral(0.0);
        }
        return numeral(fraction).divide(total).multiply(100);
    };

    var applyPercentage = function(total, percentage) {
        return numeral(percentage).divide(100).multiply(total);
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
                    $state.go("index");
                });
            }, angular.noop);
        };
        $scope.cancel = function() {
            $state.go("index");
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
                    $state.go("index");
                });
            }, angular.noop);
        };

        $scope.cancel = function() {
            $state.go("index");
        };

    });

    admin.controller('CreateEventController', function($scope, $state, $rootScope, $q, OrganizationService, PaymentProxyService, EventService) {
        $scope.event = {
            freeOfCharge: false,
            begin: {},
            end: {}
        };
        $scope.organizations = {};

        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
        });

        PaymentProxyService.getAllProxies().success(function(result) {
            $scope.allowedPaymentProxies = _.map(result, function(p) {
                return {
                    id: p,
                    description: PAYMENT_PROXY_DESCRIPTIONS[p] || "Unknown provider ("+p+")  Please check configuration"
                };
            });
        });

        var createCategory = function(sticky) {
            var lastCategory = _.last($scope.event.ticketCategories);
            var inceptionDate, notBefore;
            if(angular.isDefined(lastCategory)) {
                inceptionDate = moment(lastCategory.expiration.date).format('YYYY-MM-DD');
                notBefore = inceptionDate;
            } else {
                inceptionDate = moment().format('YYYY-MM-DD');
                notBefore = undefined;
            }

            var category = {
                inception: {
                    date: inceptionDate
                },
                expiration: {},
                sticky: sticky,
                notBefore: notBefore
            };
            $scope.event.ticketCategories.push(category);
        };

        $scope.event.ticketCategories = [];
        createCategory(true);

        $scope.addCategory = function() {
            createCategory(false);
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

        $scope.save = function(form, event) {
            validationPerformer($q, EventService.checkEvent, event, form).then(function() {
                EventService.createEvent(event).success(function() {
                    $state.go("index");
                });
            }, angular.noop);
        };

        $scope.cancel = function() {
            $state.go("index");
        };
    });

    admin.controller('EventDetailController', function($scope, $stateParams, OrganizationService, EventService) {
        EventService.getEvent($stateParams.eventName).success(function(result) {
            $scope.event = result.event;
            $scope.organization = result.organization;
            $scope.ticketCategories = result.ticketCategories;
        });
    });

    admin.controller('MessageBarController', function($scope, $rootScope) {
        $rootScope.$on('Message', function(m) {
            $scope.message = m;
        });
    });

    admin.run(function($rootScope) {
        var calculateNetPrice = function(event) {
            if(isNaN(event.regularPrice) || isNaN(event.vat)) {
                return numeral(0.0);
            }
            if(!event.vatIncluded) {
                return numeral(event.regularPrice);
            }
            return numeral(event.regularPrice).divide(numeral(1).add(numeral(event.vat).divide(100)));
        };

        $rootScope.calculateTotalPrice = function(event) {
            if(isNaN(event.regularPrice) || isNaN(event.vat)) {
                return "0.00";
            }
            var vat = numeral(0.0);
            if(!event.vatIncluded) {
                vat = applyPercentage(event.regularPrice, event.vat);
            }
            return vat.add(event.regularPrice).value();
        };

        $rootScope.evaluateBarType = function(index) {
            var barClasses = ['danger', 'warning', 'info', 'success'];
            if(index < barClasses.length) {
                return barClasses[index];
            }
            return index % 2 == 0 ? 'info' : 'success';
        };

        $rootScope.calcBarValue = function(categorySeats, eventSeats) {
            return calcPercentage(categorySeats, eventSeats).format('0.00');
        };

        $rootScope.calcCategoryPrice = function(category, event) {
            if(isNaN(event.regularPrice) || isNaN(category.discount)) {
                return '0.00';
            }
            var netPrice = calculateNetPrice(event);
            var result = netPrice.subtract(applyPercentage(netPrice, category.discount));
            if(event.vatIncluded) {
                return result.add(applyPercentage(result, event.vat)).format('0.00');
            }
            return result.format('0.00');

        };
    });

})();