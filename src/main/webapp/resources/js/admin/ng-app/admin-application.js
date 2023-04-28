(function () {
    "use strict";

    var BASE_STATIC_URL = window.ALFIO_CONTEXT_PATH + "/resources/angular-templates/admin/partials";

    //
    var FIELD_TYPES = ['input:text', 'input:tel', 'textarea', 'select', 'country'];
    var ERROR_CODES = { DUPLICATE:'duplicate', MAX_LENGTH:'maxlength', MIN_LENGTH:'minlength'};
    
    var admin = angular.module('adminApplication', ['ngSanitize','ui.bootstrap', 'ui.router', 'adminDirectives',
        'adminServices', 'utilFilters', 'ngMessages', 'ngFileUpload', 'nzToggle', 'alfio-email', 'alfio-util', 'alfio-configuration', 'alfio-additional-services', 'alfio-event-statistic',
        'ui.ace', 'monospaced.qrcode', 'checklist-model', 'group', 'subscriptions', angularDragula(angular)]);

    var loadEvent = {
        'loadEvent': function($stateParams, EventService) {
            return EventService.getEvent($stateParams.eventName);
        }
    };

    function loadEventCtrl(loadEvent) {
        this.loadEvent = loadEvent.data.event;
    }

    admin.config(function($stateProvider, $urlRouterProvider, growlProvider) {
        $urlRouterProvider.otherwise("/");
        $stateProvider
            .state('index', {
                url: "/",
                template: ['<div class="container" container-fluid-responsive="">',
                               '<h1>Events</h1>',
                               '<hr />',
                               '<export-reservations-button></export-reservations-button>',
                               '<active-events-list></active-events-list>',
                               '<expired-events-list></expired-events-list>',
                           '</div>'].join('')
            })
            .state('organizations', {
                url: "/organizations/",
                template: "<organizations></organizations>"
            })
            .state('organizations.new', {
                url: "new",
                views: {
                    "newOrganization": {
                        template: "<organization-edit type='new'></organization-edit>"
                    }
                }
            })
            .state('organizations.edit', {
                url: ":organizationId/edit",
                views: {
                    "newOrganization": {
                        template: "<organization-edit type='edit' organization-id='$ctrl.$state.params.organizationId'></organization-edit>",
                        controller: ['$state', function($state) {this.$state = $state;}],
                        controllerAs: '$ctrl'
                    }
                }
            })
            .state('organization-edit-resources', {
                url: '/organizations/:organizationId/show-resources/:resourceName/',
                template: '<resources-edit for-organization="true" organization-id="ctrl.organizationId" resource-name="ctrl.resourceName"></resources-show>',
                controller: function($state) {
                    this.organizationId = $state.params.organizationId;
                    this.resourceName = $state.params.resourceName;
                },
                controllerAs: 'ctrl'
            })
            .state('users', {
                url: "/users/",
                template: "<users data-title='Users' type='user'></users>"
            })
            .state('users.new', {
                url: "new",
                views: {
                    "editUser": {
                        template: "<user-edit type='new' for='user'></user-edit>"
                    }
                }
            })
            .state('users.edit', {
                url: ":userId/edit",
                views: {
                    "editUser": {
                        template: "<user-edit type='edit' for='user' user-id='$ctrl.$state.params.userId'></user-edit>",
                        controller: ['$state', function($state) {this.$state = $state;}],
                        controllerAs: '$ctrl'
                    }
                }
            })
            .state('apikey', {
                url: "/api-keys/",
                template: "<users data-title='Api Keys' type='apikey'></users>"
            })
            .state('apikey.new', {
                url: "new",
                views: {
                    "editUser": {
                        template: "<user-edit type='new' for='apikey'></user-edit>"
                    }
                }
            })
            .state('apikey.edit', {
                url: ":userId/edit",
                views: {
                    "editUser": {
                        template: "<user-edit type='edit' for='apikey' user-id='$ctrl.$state.params.userId'></user-edit>",
                        controller: ['$state', function($state) {this.$state = $state;}],
                        controllerAs: '$ctrl'
                    }
                }
            })
            .state('apikey.bulk', {
                url: "bulk-creation",
                views: {
                    "editUser": {
                        template: "<api-key-bulk-import></api-key-bulk-import>"
                    }
                }
            })
            .state('edit-current-user', {
                url: "/profile/edit",
                template: '<user-edit-current></user-edit-current>'
            })
            .state('events', {
                abstract: true,
                url: '/events',
                templateUrl: BASE_STATIC_URL + "/event/index.html"
            })
            .state('events.new', {
                url: '/new?eventName',
                templateUrl: BASE_STATIC_URL + "/event/edit-event.html",
                controller: 'CreateEventController',
                data: {
                    eventType: 'INTERNAL'
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
                    this.event = getEvent.data.event;
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
            .state('events.single.dataToCollect', {
                url:'/attendee-data-to-collect',
                template:'<event-data-to-collect event="$ctrl.loadEvent"></event-data-to-collect>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.promoCodes', {
                url:'/promo-codes',
                template:'<promo-codes for-event="true" event="$ctrl.loadEvent"></promo-codes>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.additionalServices', {
                url: '/additional-services',
                template: '<additional-services data-event-short-name="$ctrl.loadEvent.shortName" data-type="SUPPLEMENT" data-title="Additional options" data-icon="fa-money" selected-languages="$ctrl.loadEvent.locales" event-is-free-of-charge="$ctrl.loadEvent.freeOfCharge" event-id="$ctrl.loadEvent.id" event-start-date="$ctrl.loadEvent.formattedBegin"></additional-services>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.donations', {
                url: '/donations',
                template: '<additional-services data-event-short-name="$ctrl.loadEvent.shortName" data-type="DONATION" data-title="Donation options" data-icon="fa-gift" selected-languages="$ctrl.loadEvent.locales" event-is-free-of-charge="$ctrl.loadEvent.freeOfCharge" event-id="$ctrl.loadEvent.id" event-start-date="$ctrl.loadEvent.formattedBegin"></additional-services>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
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
            .state('events.single.reservationsList', {
                url: '/reservations/?search',
                template: '<reservations-list purchase-context="ctrl.event" purchase-context-type="ctrl.purchaseContextType"></reservations-list>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                    this.purchaseContextType = 'event';
                },
                controllerAs: 'ctrl'
            })
            .state('events.single.paymentsList', {
                url: '/transactions/?search',
                template: '<payments-list purchase-context="ctrl.event" purchase-context-type="ctrl.purchaseContextType"></payments-list>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                    this.purchaseContextType = 'event';
                },
                controllerAs: 'ctrl'
            })
            .state('events.single.ticketsList', {
                url: '/category/:categoryId/tickets',
                template: '<tickets-list event="$ctrl.event" category-id="$ctrl.categoryId"></tickets-list>',
                controller: ['loadEvent', '$stateParams', function(loadEvent, $stateParams) {
                    this.event = loadEvent.data.event;
                    this.categoryId = $stateParams.categoryId;
                }],
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.pending-payments', {
                url: '/pending-payments/',
                templateUrl: BASE_STATIC_URL + '/pending-payments/index.html',
                controller: 'PendingPaymentsController',
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
            }).state('events.single.show-resources', {
                url: '/show-resources',
                template: '<resources-show event="ctrl.event"></resources-show>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                },
                controllerAs: 'ctrl'
            }).state('events.single.edit-resources', {
                url: '/show-resources/:resourceName/',
                template: '<resources-edit event="ctrl.event" resource-name="ctrl.resourceName"></resources-show>',
                controller: function(getEvent, $state) {
                    this.event = getEvent.data.event;
                    this.resourceName = $state.params.resourceName;
                },
                controllerAs: 'ctrl'
            }).state('events.single.create-reservation', {
                url:'/reservation/new',
                template: '<reservation-create event="ctrl.event"></reservation-create>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                },
                controllerAs: 'ctrl',
                data: {
                    view: 'CREATE_RESERVATION'
                }
            }).state('events.single.import-reservation', {
                url:'/reservation/import',
                template: '<reservation-import event="ctrl.event"></reservation-import>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                },
                controllerAs: 'ctrl',
                data: {
                    view: 'IMPORT_RESERVATION'
                }
            }).state('events.single.import-status', {
                url:'/reservation/import/:requestId/status',
                template: '<reservation-import-progress event="ctrl.event"></reservation-import-progress>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                },
                controllerAs: 'ctrl',
                data: {
                    view: 'IMPORT_STATUS'
                }
            }).state('events.single.view-reservation', {
                url:'/reservation/:reservationId?fromCreation',
                template: '<reservation-view purchase-context="ctrl.event" purchase-context-type="ctrl.purchaseContextType" reservation-descriptor="ctrl.reservationDescriptor"></reservation-view>',
                controller: function(getEvent, getReservationDescriptor) {
                    this.event = getEvent.data.event;
                    this.purchaseContextType = 'event';
                    this.reservationDescriptor = getReservationDescriptor.data.data;
                },
                controllerAs: 'ctrl',
                data: {
                    view: 'VIEW_RESERVATION'
                },
                resolve: {
                    'getReservationDescriptor': function(AdminReservationService, $stateParams) {
                        return AdminReservationService.load('event', $stateParams.eventName, $stateParams.reservationId);
                    }
                }
            }).state('events.single.polls-list', {
                url:'/polls/list',
                template: '<polls-list event="ctrl.event"></polls-list>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                },
                controllerAs: 'ctrl',
                data: {
                    view: 'POLL_LIST'
                }
            }).state('events.single.polls-detail', {
                url:'/polls/view/:pollId',
                template: '<poll-detail event="ctrl.event"></poll-detail>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                },
                controllerAs: 'ctrl',
                data: {
                    view: 'POLL_DETAIL'
                }
            }).state('events.single.polls-create', {
                url:'/polls/new',
                template: '<poll-edit event="ctrl.event"></poll-edit>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                },
                controllerAs: 'ctrl',
                data: {
                    view: 'POLL_CREATE'
                }
            }).state('events.single.polls-edit', {
                url:'/polls/edit/:pollId',
                template: '<poll-edit event="ctrl.event"></poll-edit>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                },
                controllerAs: 'ctrl',
                data: {
                    view: 'POLL_EDIT'
                }
            }).state('events.single.polls-edit-participants', {
                url:'/polls/:pollId/participants',
                template: '<poll-participants event="ctrl.event"></poll-participants>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                },
                controllerAs: 'ctrl',
                data: {
                    view: 'POLL_EDIT_PARTICIPANTS'
                }
            })

            .state('extension', {
                url: '/extension',
                abstract: true,
                template: '<div><div data-ui-view></div></div>'
            }).state('extension.list', {
                url: '/list',
                template: '<extension></extension>'
            }).state('extension.new', {
                url: '/new',
                template: '<extension-add-update></extension-add-update>'
            }).state('extension.edit', {
                url: '/:path/:name/edit',
                template: '<extension-add-update to-update="ctrl.toUpdate" close="ctrl.close($script)" dismiss="ctrl.dismiss()"></extension-add-update>',
                controllerAs: 'ctrl',
                controller: ['$stateParams', function($stateParams) {
                    this.toUpdate = {path: $stateParams.path, name: $stateParams.name};
                }]
            }).state('extension.log', {
                url: '/log',
                template: '<extension-log></extension-log>'
            });

        growlProvider.globalPosition('bottom-right');
    });

    navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;

    admin.run(function($rootScope, $uibModal, $window, $state) {
        $rootScope.$on('ErrorNotAuthorized', function() {
            $uibModal.open({
                size:'sm',
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/error/not-authorized.html'
            }).result.then(angular.noop, function() {
                $state.go('index');
            });
        });
        $rootScope.$on('ErrorNotLoggedIn', function() {
            $window.location.reload();
        });
    });

    var validationResultHandler = function(form, deferred, $scope, NotificationHandler) {

        var errors = {
            'error.coordinates': 'Event Geolocation is missing. Please check maps\' configuration.',
            'error.beginDate': 'Please check Event\'s start/end date.',
            'error.endDate': 'Please check Event\'s start/end date.',
            'error.allowedpaymentproxies': 'Please select at least one payment method.'
        };

        return function(validationResult) {
            if(validationResult.errorCount > 0) {
                var errorText;
                angular.forEach(validationResult.validationErrors, function(error) {
                    var match = error.fieldName.match(/ticketCategories\[([0-9]+)\]/);
                    if(match) {
                        //HACK
                        var categoryPos = parseInt(match[1]);
                        $scope.event.ticketCategories[categoryPos].error = error.code;
                    }
                    if(angular.isFunction(form.$setError)) {
                        form.$setError(error.fieldName, error.code);
                    }
                    if(errors[error.code]) {
                        errorText = errors[error.code];
                    }
                });

                if(errorText) {
                    NotificationHandler.showError(errorText);
                }
                setTimeout(function() {
                    var firstInvalidElem = $("input.ng-invalid:first, textarea.input.ng-invalid:first, select.ng-invalid:first");
                    if(firstInvalidElem.length > 0) {
                        $('html, body').animate({scrollTop: firstInvalidElem.offset().top - 80},500,function() {
                            firstInvalidElem.focus()
                        });
                    }

                }, 0);
                deferred.reject('invalid form');
            }
            deferred.resolve();
        };
    };

    var validationPerformer = function($q, validator, data, form, $scope, NotificationHandler) {
        var deferred = $q.defer();
        validator(data).success(validationResultHandler(form, deferred, $scope, NotificationHandler)).error(function(error) {
            deferred.reject(error);
        });
        return deferred.promise;
    };

    admin.controller('MenuController', ['$scope', '$http', '$window', 'UtilsService', '$state', '$rootScope', 'EventService', 'MenuButtonService', function($scope, $http, $window, UtilsService, $state, $rootScope, EventService, MenuButtonService) {
        var ctrl = this;
        ctrl.menuCollapsed = true;
        ctrl.eventName = $state.params.eventName;
        $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams) {
            ctrl.eventName = toParams.eventName;
        });
        $rootScope.$on('EventLoaded', function(_, event) {
            ctrl.event = event;
            MenuButtonService.configureMenu(ctrl);
        });
        ctrl.toggleCollapse = function(currentStatus) {
            ctrl.menuCollapsed = !currentStatus;
        };
        var deleteCheckInDatabase = function () {
            try {
                Dexie.delete('AlfioDatabase');
                $window.sessionStorage.clear();
            } catch (e) {
            }
        };

        $window.addEventListener("beforeunload", deleteCheckInDatabase);

        ctrl.doLogout = function(idpLogoutRedirectionUrl) {
            UtilsService.logout().then(function() {
                //delete alf.io IndexedDb
                deleteCheckInDatabase();
                $window.location.reload();
                if(!(idpLogoutRedirectionUrl === '')){
                    $window.location.replace(idpLogoutRedirectionUrl);
                }
            });
        };
        ctrl.openDeleteWarning = function() {
            EventService.deleteEvent(ctrl.event).then(function(result) {
                $state.go('index');
            });
        };
    }]);


    var createCategoryValidUntil = function(sticky, categoryEndTime, event) {
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
            bounded: false,
            ticketAccessType: event.format === 'HYBRID' ? 'IN_PERSON' : 'INHERIT'
        };

    };

    var initScopeForEventEditing = function ($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state, PAYMENT_PROXY_DESCRIPTIONS) {
        $scope.organizations = {};

        $scope.isInternal = function(event) {
            return true;
        };

        EventService.getSupportedLanguages().success(function(result) {
            $scope.allLanguages = result;
            $scope.allLanguagesMapping = {};
            var locales = 0;
            angular.forEach(result, function(r) {
                $scope.allLanguagesMapping[r.value] = r;
                locales |= r.value;
            });
        });
        if($scope.event && !angular.isDefined($scope.event.locales)) {
            $scope.event.locales = 0;
        }

        EventService.getDynamicFieldTemplates().success(function(result) {
            $scope.dynamicFieldTemplates = result;
        });

        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
            if(result.length === 1) {
                $scope.event.organizationId = result[0].id;
                loadAllowedPaymentProxies(result[0].id);
            }
        });

        function loadAllowedPaymentProxies(orgId) {
            PaymentProxyService.getAllProxies(orgId).success(function(result) {
                $scope.allowedPaymentProxies = _.map(result, function(p) {
                    return {
                        id: p.paymentProxy,
                        description: PAYMENT_PROXY_DESCRIPTIONS[p.paymentProxy] || 'Unknown provider ('+p.paymentProxy+')  Please check configuration',
                        enabled: p.status === 'ACTIVE',
                        onlyForCurrency: p.onlyForCurrency
                    };
                });
            });
        }

        $scope.$watch('event.organizationId', function(newVal) {
            if(newVal !== undefined && newVal !== null) {
                loadAllowedPaymentProxies(newVal)
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
                name: nameExists ? '' : template.name,
                order: event.ticketFields.length+1,
                type: template.type,
                restrictedValues: _.map(template.restrictedValues, function(v) {return {value: v}}),
                description: template.description,
                maxLength: template.maxLength,
                minLength: template.minLength,
                required: template.required
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
        };

    };

    admin.controller('CreateEventController', function($scope, $state, $rootScope, $uibModal,
                                                       $q, OrganizationService, PaymentProxyService,
                                                       EventService, LocationService, PAYMENT_PROXY_DESCRIPTIONS, TicketCategoryEditorService,
                                                       NotificationHandler) {

        var eventType = $state.$current.data.eventType;

        function initTicketCategoriesAndAdditionalServices() {
            if($scope.event.ticketCategories  === undefined) {
                $scope.event.ticketCategories = [];
            }
            if($scope.event.additionalServices === undefined) {
                $scope.event.additionalServices = [];
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
                    freeOfCharge: false,
                    begin: {},
                    end: {},
                    metadata: {}
                };
                initTicketCategoriesAndAdditionalServices();
        }

        $scope.joinTitle = function(titles) {
            return titles.map(function(t) { return t.value;}).join(' / ')
        };

        $scope.reset = function() {
            $scope.event = {
                type: eventType,
                freeOfCharge: false,
                begin: {},
                end: {}
            };
            initTicketCategoriesAndAdditionalServices();
            initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state, PAYMENT_PROXY_DESCRIPTIONS);
        };


        $scope.allocationStrategyRadioClass = 'radio-inline';
        initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state, PAYMENT_PROXY_DESCRIPTIONS);
        var editCategory = function(category) {
            return TicketCategoryEditorService.openCategoryDialog($scope, category, $scope.event, null, null);
        };
        $scope.addCategory = function() {
            var category = createCategoryValidUntil(true, $scope.event.begin, $scope.event);
            if (!$scope.event.freeOfCharge) {
                category.price = $scope.event.regularPrice
            }
            editCategory(category).then(function(res) {
                category.ordinal = $scope.event.ticketCategories.length + 1;
                $scope.event.ticketCategories.push(category);
            });
        };

        $scope.swap = function(category, up) {
            var list = $scope.event.ticketCategories.slice();
            var index = category.ordinal - 1;
            var target = up ? index - 1 : index + 1;
            var toBeSwapped = list[target];
            toBeSwapped.ordinal = index + 1;
            category.ordinal = target + 1;
            list[target] = category;
            list[index] = toBeSwapped;
            $scope.event.ticketCategories.length = 0;
            for(var i=0; i<list.length; i++) {
                $scope.event.ticketCategories.push(list[i]);
            }
        };

        $scope.setAdditionalServices = function(event, additionalServices) {
            event.additionalServices = additionalServices;
        };

        $scope.editCategory = function(category) {
            editCategory(category);
        };

        $scope.removeCategory = function(category) {
            var categories = $scope.event.ticketCategories;
            $scope.event.ticketCategories = _.filter(categories, function(cat) { return cat !== category; });
        };

        $scope.save = function(form, event) {
            $scope.submitting = true;
            var deactivateLoading = function() {
                $scope.submitting = false;
            };
            validationPerformer($q, EventService.checkEvent, event, form, $scope, NotificationHandler).then(function() {
                EventService.createEvent(event).success(function() {
                    $scope.additionalFieldsToBeCreated = $scope.additionalFieldsToBeCreated || [];
                    $scope.additionalServicesToBeCreated = $scope.additionalServicesToBeCreated || [];

                    EventService.getEvent(event.shortName).then(function(createdEvent) {
                        $q.all($scope.additionalServicesToBeCreated.map(function(as) {
                            return EventService.createAdditionalService(createdEvent.data.event.id, as);
                        })).then(function(createdAdditionalServices) {
                            var mappedAdditionalServicesId = {};
                            for(var i = 0; i < $scope.additionalServicesToBeCreated.length; i++) {
                                mappedAdditionalServicesId[$scope.additionalServicesToBeCreated[i].id] =  createdAdditionalServices[i].data;
                            }

                            return $q.all($scope.additionalFieldsToBeCreated.map(function(af) {
                                var description = {};
                                angular.forEach(af.description, function(v,k) {
                                    description[k] = {label: v.description.label, placeholder: v.description.placeholder, restrictedValues: v.description.restrictedValues}
                                });
                                var newAdditionalField = {
                                    order: af.order,
                                    useDefinedOrder: true,
                                    name: af.name,
                                    type: af.type,
                                    required: af.required,
                                    readOnly: af.readOnly,
                                    minLength: af.minLength,
                                    maxLength: af.maxLength,
                                    restrictedValues: af.restrictedValues.map(function(rv) {return {value: rv}}),
                                    description: description,
                                    forAdditionalService: mappedAdditionalServicesId[af.additionalServiceId],
                                    categoryIds: af.categoryIds.map(function(name) {return createdEvent.data.event.ticketCategories.filter(function(tc) {return tc.name === name})[0].id})
                                };

                                return EventService.addField(event.shortName, newAdditionalField);
                            }));
                        }).then(function(res) {
                            if(window.sessionStorage) {
                                delete window.sessionStorage.new_event;
                            }
                            $state.go('events.single.detail', {eventName: event.shortName});
                        }, deactivateLoading);
                    });

                }).error(deactivateLoading);
            }, deactivateLoading);
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

        var calcDynamicTickets = function(eventSeats, categories) {
            var value = 0;
            if(eventSeats) {
                value = eventSeats - _.chain(categories).filter('bounded').reduce(function(sum, c) {
                    return sum + (c.maxTickets || 0);
                }, 0).value();
            }
            return value;
        };

        $scope.calcDynamicTickets = calcDynamicTickets;

        $scope.allDynamicCategories = function(eventSeats, categories) {
            var dynamic = _.filter(categories, function(cat) { return !cat.bounded; });
            if(dynamic.length > 1 && dynamic.length === categories.length) {
                return "All Categories";
            } else if(dynamic.length > 1 && (calcDynamicTickets(eventSeats, categories) / eventSeats * 100.0) < 25) {
                return "Dynamic";
            }
            return _.map(dynamic, 'name').join(', ');
        };




        $scope.openCopyEvent = function(eventNameToPreselect) {
            var currentEventInScope = angular.copy($scope.event);

            var modal = $uibModal.open({
                size: 'lg',
                template: '<copy-event dismiss="ctrl.onDismiss()" event="ctrl.eventTemplate" on-copy="ctrl.onEditComplete" event-name-to-preselect="ctrl.eventNameToPreselect"></copy-event>',
                backdrop: 'static',
                controller: function($scope) {
                    var ctrl = this;
                    ctrl.eventTemplate = currentEventInScope;
                    ctrl.eventNameToPreselect = eventNameToPreselect;
                    ctrl.onEditComplete = function(item) {
                        modal.close(item);
                    };
                    ctrl.onDismiss = function() {
                        modal.dismiss();
                    };
                },
                bindToController: true,
                controllerAs: 'ctrl'
            });

            function createDateTimeObject(dateString) {
                if(dateString == null) {
                    return null;
                }

                var d = moment(dateString, 'YYYY-MM-DD HH:mm');
                return {
                    date: d.format('YYYY-MM-DD'),
                    time: d.format('HH:mm')
                };
            }

            function fromDateTimeObjectToMoment(dt) {
                return moment(dt.date+' ' + dt.time, 'YYYY-MM-DD HH:mm');
            }

            modal.result.then(function(res) {
                var startAndEndDate = res[0];
                var selectedEvent = res[1];
                var additionalFields = res[2];
                var additionalServices = res[3];

                if (additionalFields && additionalFields.length > 0) {
                    $scope.additionalFieldsToBeCreated = additionalFields;
                }

                if (additionalServices &&  additionalServices.length > 0) {
                    $scope.additionalServicesToBeCreated = additionalServices;
                }

                EventService.getEvent(selectedEvent.shortName).success(function(result) {
                    // copy
                    var momentStartEvent = fromDateTimeObjectToMoment(startAndEndDate.begin);
                    var momentEventToCopyStart = moment(result.event.formattedBegin, 'YYYY-MM-DD HH:mm');

                    var eventToCopy = result.event;

                    var adjustDate = function(formattedDate) {
                        return moment(momentStartEvent).add(moment(formattedDate, 'YYYY-MM-DD HH:mm').diff(momentEventToCopyStart)).format('YYYY-MM-DD HH:mm');
                    }

                    if($scope.additionalFieldsToBeCreated) {
                        angular.forEach($scope.additionalFieldsToBeCreated, function(af) {
                            af.categoryIds = af.categoryIds.map(function(id) {
                                return eventToCopy.ticketCategories.filter(function(tc) {return tc.id === id})[0].name;
                            });
                        });
                    }

                    if ($scope.additionalServicesToBeCreated) {
                        angular.forEach($scope.additionalServicesToBeCreated, function(as) {
                            //adjust time
                            as.inception = createDateTimeObject(adjustDate(fromDateTimeObjectToMoment(as.inception).format('YYYY-MM-DD HH:mm')));
                            as.expiration = createDateTimeObject(adjustDate(fromDateTimeObjectToMoment(as.expiration).format('YYYY-MM-DD HH:mm')));
                        });
                    }

                    $scope.event.dateString = startAndEndDate.dateString;
                    $scope.event.begin = angular.copy(startAndEndDate.begin);
                    $scope.event.end = angular.copy(startAndEndDate.end);

                    $scope.event.location = eventToCopy.location;
                    $scope.event.geolocation = {timeZone: eventToCopy.timeZone, latitude: eventToCopy.latitude, longitude: eventToCopy.longitude};
                    $scope.event.availableSeats = selectedEvent.availableSeats;
                    $scope.event.fileBlobId = selectedEvent.fileBlobId;
                    $scope.event.websiteUrl = eventToCopy.websiteUrl;
                    $scope.event.termsAndConditionsUrl = eventToCopy.termsAndConditionsUrl;
                    $scope.event.privacyPolicyUrl = eventToCopy.privacyPolicyUrl;
                    $scope.event.locales = eventToCopy.locales;
                    $scope.event.description = angular.copy(eventToCopy.description);
                    $scope.event.freeOfCharge = eventToCopy.freeOfCharge;
                    $scope.event.regularPrice = eventToCopy.regularPrice;
                    $scope.event.currency = eventToCopy.currency;
                    $scope.event.vatPercentage = eventToCopy.vatPercentage;
                    $scope.event.vatIncluded = eventToCopy.vatIncluded;
                    $scope.event.allowedPaymentProxies = angular.copy(eventToCopy.allowedPaymentProxies);
                    $scope.event.format = eventToCopy.format;
                    $scope.event.metadata = {
                        copiedFrom: eventToCopy.shortName
                    };

                    //legacy event, has all the ticket categories with ordinal 0
                    var isAllOrdinal0 = eventToCopy.ticketCategories.reduce(function(accumulator, tc) {return accumulator && (tc.ordinal === 0);}, true);

                    $scope.event.ticketCategories = eventToCopy.ticketCategories.map(function(tc, idx) {
                        //inception/expiration : we keep the same date interval
                        var categoryAdjustedStart = adjustDate(tc.formattedInception);
                        var categoryAdjustedEnd = adjustDate(tc.formattedExpiration);
                        var cat = {
                            name: tc.name,
                            bounded: tc.bounded,
                            ordinal: isAllOrdinal0 ? idx : tc.ordinal,
                            dateString: categoryAdjustedStart + ' / ' + categoryAdjustedEnd,
                            inception: createDateTimeObject(categoryAdjustedStart),
                            expiration: createDateTimeObject(categoryAdjustedEnd),
                            maxTickets: tc.maxTickets,
                            price: tc.price,
                            tokenGenerationRequested: tc.accessRestricted,
                            code: tc.code,
                            description: tc.description ? angular.copy(tc.description) : null,
                            ticketAccessType: eventToCopy.format === 'HYBRID' ? tc.ticketAccessType : null,
                            metadata: {
                                copiedFrom: tc.id
                            }
                        };

                        if (tc.formattedValidCheckInFrom) {
                            var adjustedCheckInValidStart = adjustDate(tc.formattedValidCheckInFrom);
                            cat.validCheckInFromString = adjustedCheckInValidStart;
                            cat.validCheckInFrom = createDateTimeObject(adjustedCheckInValidStart);
                        }

                        if (tc.formattedValidCheckInTo) {
                            var adjustedCheckInValidEnd = adjustDate(tc.formattedValidCheckInTo);
                            cat.validCheckInToString = adjustedCheckInValidEnd;
                            cat.validCheckInTo = createDateTimeObject(adjustedCheckInValidEnd);
                        }

                        if (tc.formattedTicketValidityStart) {
                            var adjustedValidityStart = adjustDate(tc.formattedTicketValidityStart);
                            cat.customValidityStartToString = adjustedValidityStart;
                            cat.ticketValidityStart = createDateTimeObject(adjustedValidityStart);
                        }

                        if (tc.formattedTicketValidityEnd) {
                            var adjustedValidityEnd = adjustDate(tc.formattedTicketValidityEnd);
                            cat.customValidityEndToString = adjustedValidityEnd;
                            cat.ticketValidityEnd = createDateTimeObject(adjustedValidityEnd);
                        }
                        return cat;
                    });
                    //
                });
            });
        }

        if($state && $state.params && $state.params.eventName) {
            $scope.openCopyEvent($state.params.eventName);
        }


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
                                                        $window,
                                                        $uibModal,
                                                        PAYMENT_PROXY_DESCRIPTIONS,
                                                        UtilsService,
                                                        NotificationHandler,
                                                        $timeout,
                                                        TicketCategoryEditorService,
                                                        GroupService,
                                                        ConfigurationService,
                                                        SubscriptionService) {
        var loadData = function() {
            $scope.loading = true;

            return EventService.getEvent($state.params.eventName).success(function(result) {
                if($scope.event) {
                    //for sidebar
                    $rootScope.$emit('EventUpdated');
                }
                $scope.event = result.event;
                $scope.loading = false;

                ConfigurationService.loadSingleConfigForEvent(result.event.shortName, 'BASE_URL').then(function(res) {
                    $scope.eventPublicURL = res.data + '/event/' + result.event.shortName;
                });

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

                $scope.loadingMap = true;
                LocationService.getMapUrl(result.event.latitude, result.event.longitude).then(function(mapUrl) {
                    $scope.event.geolocation = {
                        latitude: result.event.latitude,
                        longitude: result.event.longitude,
                        mapUrl: mapUrl,
                        timeZone: result.event.timeZone
                    };
                    $scope.loadingMap = false;
                });

                $scope.unbindTickets = function(event , category) {
                    EventService.unbindTickets(event, category).success(function() {
                        loadData();
                    });
                };
                $scope.toggleRearrange = function() {
                    var event = $scope.event;
                    EventService.rearrangeCategories(event).then(function() {
                        loadData();
                    })
                };
                $q.all([GroupService.loadActiveLinks($state.params.eventName), GroupService.loadGroups($scope.event.organizationId), SubscriptionService.loadSubscriptionsDescriptors($scope.event.organizationId)])
                    .then(function(results) {
                        var confResult = results[0];
                        var lists = results[1].data;
                        if(confResult.status === 200) {
                            _.forEach(confResult.data, function(list) {
                                var group = _.find(lists, function(l) { return l.id === list.groupId});
                                list.groupName = group ? group.name : "";
                                if(list.ticketCategoryId != null) {
                                    var category = _.find($scope.event.ticketCategories, function(c) { return c.id === list.ticketCategoryId;});
                                    if(category) {
                                        category.attendeesList = list;
                                    }
                                } else {
                                    $scope.event.attendeesList = list;
                                }
                            });
                        }
                        $scope.event.subscriptionDescriptors = _.map(_.filter(results[2].data, function(s) {
                            return _.contains($scope.event.linkedSubscriptions, s.descriptor.id);
                        }), 'descriptor');
                    });
            });
        };
        $scope.baseUrl = window.location.origin;
        loadData().then(function() {
            initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state, PAYMENT_PROXY_DESCRIPTIONS);
            $scope.selection = {
                active: true,
                expired: $scope.event.ticketCategories.filter(function(tc) { return tc.containingOrphans; }).length > 0,
                freeText: ''
            };
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

        $scope.isPending = function(token) {
            return token.status === 'WAITING';
        };

        $scope.isReady = function(token) {
            return token.status === 'WAITING';
        };

        $scope.canBeDeleted = function(event, category) {
            return event.ticketCategories.length > 1
                && category.checkedInTickets + category.soldTickets + category.pendingTickets === 0
        };

        $scope.deleteCategory = function(category, event) {
            EventService.deleteCategory(category, event).then(function(result) {
                if(result === 'OK') {
                    loadData();
                }
            });
        };

        $scope.moveOrphans = function(srcCategory, targetCategoryId, eventId) {
            EventService.reallocateOrphans(srcCategory, targetCategoryId, eventId).success(function(result) {
                if(result === 'OK') {
                    loadData();
                }
            });
        };

        $scope.containsValidTokens = function(tokens) {
            return _.all(tokens, function(t) {
                return t.status !== 'WAITING';
            });
        };

        $scope.groupTokensByStatus = function(tokens) {
            return _.chain(tokens)
                .filter(function(t) { return t.status !== 'CANCELLED'; })
                .sortByOrder(['id'], ['desc'])
                .groupBy(function(token) {
                    switch(token.status) {
                        case 'FREE':
                            return token.sentTimestamp === null ? 'Free' : 'Invitation Sent';
                        case 'PENDING':
                            return 'Pending';
                        case 'TAKEN':
                            return 'Confirmed';
                    }
                }).value();
        };

        $scope.eventHeader = {};
        $scope.eventPrices = {};

        var validationErrorHandler = function(result, form, fieldsContainer) {
            return $q(function(resolve, reject) {
                if(result.data['errorCount'] === 0) {
                    resolve(result);
                } else {
                    _.forEach(result.data.validationErrors, function(error) {
                        var field = fieldsContainer[error.fieldName];
                        if(angular.isDefined(field)) {
                            if (error.code === ERROR_CODES.DUPLICATE) {
                                field.$setValidity(ERROR_CODES.DUPLICATE, false);
                                field.$setTouched();
                            } else {
                                field.$setValidity('required', false);
                                field.$setTouched();
                            }
                        }
                    });
                    reject('validation error');
                }
            });
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
                        });
                    };
                }
            });
            editEventHeader.result.then(function() {
                loadData().then(function(res) {
                    $rootScope.$emit('ReloadEventPie', res.data.event);
                });
            });
        };

        var reloadIfSeatsModification = function(seatsModified) {
            var message = "Modification applied. " + (seatsModified ? "Seats modification will become effective in 30s. The data will be reloaded automatically." : "");
            return loadData().then(function(res) {
                NotificationHandler.showSuccess(message);
                $rootScope.$emit('ReloadEventPie', res.data.event);
                if(seatsModified) {
                    $timeout(function() {
                        var info = NotificationHandler.showInfo("Reloading data...");
                        loadData().then(function(res2) {
                            info.destroy();
                            NotificationHandler.showSuccess("Success!");
                            $rootScope.$emit('ReloadEventPie', res2.data.event);
                        });
                    }, 30000 );
                }
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
                    var seats = $scope.event.availableSeats;
                    $scope.allowedPaymentProxies = _.filter(parentScope.allowedPaymentProxies, function(pp) {
                        return pp.id !== 'ON_SITE' || !parentScope.event.online;
                    });

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
                                $scope.$close(eventPrices.availableSeats !== seats);
                            });
                        });
                    };
                }
            });
            editPrices.result.then(reloadIfSeatsModification);
        };

        $scope.openDeleteWarning = function(event) {
            EventService.deleteEvent(event.id).then(function(result) {
                $state.go('index');
            });
        };


        var parentScope = $scope;

        $scope.addCategory = function(event) {
            var eventBegin = moment(event.begin);
            TicketCategoryEditorService.openCategoryDialog($scope, createCategoryValidUntil(true, {date: eventBegin.format('YYYY-MM-DD'), time: eventBegin.format('HH:mm')}, event), event, validationErrorHandler, reloadIfSeatsModification);
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
                        });
                    };
                }
            });
        };

        $scope.editCategory = function(category, event) {
            var inception = moment(category.formattedInception);
            var expiration = moment(category.formattedExpiration);

            function prepareValidDate(date) {
                if(date) {
                    var m = moment(date);
                    return {date: m.format('YYYY-MM-DD'), time: m.format('HH:mm')};
                } else {
                    return null;
                }
            }

            var validCheckInFrom = prepareValidDate(category.formattedValidCheckInFrom);
            var validCheckInTo = prepareValidDate(category.formattedValidCheckInTo);
            var ticketValidityStart = prepareValidDate(category.formattedTicketValidityStart);
            var ticketValidityEnd = prepareValidDate(category.formattedTicketValidityEnd);

            var categoryObj = {
                id: category.id,
                name: category.name,
                price: category.price,
                description: category.description,
                maxTickets: category.maxTickets,
                bounded: category.bounded,
                code: category.code,
                inception: {
                    date: inception.format('YYYY-MM-DD'),
                    time: inception.format('HH:mm')
                },
                expiration: {
                    date: expiration.format('YYYY-MM-DD'),
                    time: expiration.format('HH:mm')
                },
                validCheckInTo: validCheckInTo,
                validCheckInFrom: validCheckInFrom,
                ticketValidityStart: ticketValidityStart,
                ticketValidityEnd: ticketValidityEnd,
                tokenGenerationRequested: category.accessRestricted,
                ticketCheckInStrategy: category.ticketCheckInStrategy,
                ticketAccessType: category.ticketAccessType,
                sticky: false
            };

            TicketCategoryEditorService.openCategoryDialog($scope, categoryObj, event, validationErrorHandler, reloadIfSeatsModification).then(function() {
                loadData();
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
            var pathName = $window.location.pathname;
            if(!pathName.endsWith("/")) {
                pathName = pathName + "/";
            }
            $window.open(pathName+"api/events/"+parentScope.event.shortName+"/sponsor-scan/export");
        };

        $scope.activateEvent = function(id) {

            UtilsService.getApplicationInfo().then(function(appInfo) {
                if (appInfo.data.isDemoMode) {
                    NotificationHandler.showError('Cannot publish events in demo mode for privacy reasons');
                } else {
                    EventService.toggleActivation(id, true).then(function() {
                        $scope.eventHasBeenActivated = true;
                        loadData();
                    });
                }
            });
        };

        $scope.deactivateEvent = function(id) {
            EventService.toggleActivation(id, false).then(function() {
                $scope.eventHasBeenActivated = false;
                loadData();
            });
        };

        $scope.closeActivationAlert = function() {
            $scope.eventHasBeenActivated = false;
        };

        var categoryFilterListener = $rootScope.$on('SidebarCategoryFilterUpdated', function(e, categoryFilter) {
            if(categoryFilter) {
                $scope.selection.freeText = categoryFilter.freeText;
            }
        });

        var eventUpdateListener = $rootScope.$on('ReloadEvent', function() {
            $scope.eventHasBeenActivated = false;
            loadData();
        });
        
        $scope.updateSelectionText = function() {
            $rootScope.$emit('CategoryFilterUpdated', $scope.selection);
        };

        $scope.$on('$destroy', function() {
            [categoryFilterListener, eventUpdateListener].forEach(function(f) {f();});
        });

        $scope.categoryHasDescriptions = function(category) {
            return category && category.description ? Object.keys(category.description).length > 0 : false;
        };

    });

    admin.controller('SendInvitationsController', function($scope, $stateParams, $state, EventService, $window, NotificationHandler) {
        $scope.eventName = $stateParams.eventName;
        $scope.categoryId = $stateParams.categoryId;

        var loadSentCodes = function() {
            EventService.loadSentCodes($stateParams.eventName, $stateParams.categoryId).then(function(result) {
                $scope.codes = result.data;
            }, function(err) {
                NotificationHandler.showError(err.data);
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
                $scope.results = undefined;
                NotificationHandler.showSuccess("Invitations have been sent.");
            }).error(function(e) {
                NotificationHandler.showError(e.data);
            });
        };

        $scope.cancelCodes = function() {
            $scope.results = undefined;
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
            NotificationHandler.showError(data);
            $scope.success = false;
        };
        $scope.uploadUrl = '/admin/api/events/'+$stateParams.eventName+'/categories/'+$stateParams.categoryId+'/link-codes';
    });


    admin.controller('EventCheckInController', function($scope, $stateParams, $timeout, $log,
                                                        $state, EventService, CheckInService,
                                                        AdminReservationService, $uibModal, $window, $q,
                                                        NotificationHandler) {

        $scope.selection = {};
        $scope.checkedInSelection = {};
        $scope.itemsPerPage = 10;
        $scope.currentPage = 1;
        $scope.currentPageCheckedIn = 1;
        $scope.advancedSearch = {};
        $scope.tickets = [];
        $scope.checkedInTickets = [];
        $scope.showAlert = true;

        var db = new Dexie('AlfioDatabase', {autoOpen: false});
        db.version(1).stores({
            alfioCheckIn: "id, status, lastName"
        });
        db.open()['catch'](function(err) {
            $scope.disabled = true;
        });

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

        $scope.hideAlert = function() {
            $scope.showAlert = false;
        };

        EventService.getEvent($stateParams.eventName).success(function(result) {
            $scope.event = result.event;
            loadTickets(result.event.id);
        });

        function reloadTickets() {
            loadTickets($scope.event.id);
        }

        function loadTickets(eventId) {
            return CheckInService.findAllTicketIds(eventId).then(function(resp) {
                var chain = $q.when([]);
                var transactions = [];
                if(resp.data.length > 0) {
                    var chunks = _.chunk(resp.data, 200);
                    $scope.chunks = chunks.length;
                    $scope.loading = true;
                    var completedChunks = 0;
                    $scope.completedChunks = completedChunks;
                    _.forEach(chunks, function(array) {
                        chain = chain.then(function() {
                            return CheckInService.downloadTickets(eventId, array)
                                .then(function(resp) {
                                    $scope.completedChunks = ++completedChunks;
                                    transactions.push(db.transaction('rw', db.alfioCheckIn, function() {
                                        return db.alfioCheckIn.bulkPut(resp.data);
                                    }));
                                });
                        });
                    });
                }
                return chain.then(function() {
                    $q.all(transactions).then(function() {
                        loadTicketsFromDB(db, $scope);
                    });
                });
            });
        }
        
        $scope.reloadTickets = reloadTickets;

        $scope.showReservationModal = function showReservationModal(event, ticket) {
            var modal = $uibModal.open({
                size:'min-1200',
                templateUrl: BASE_STATIC_URL + '/event/show-reservation-modal.html',
                backdrop: 'static',
                controllerAs: 'ctrl',
                controller: function($scope) {
                    var ctrl = this;
                    ctrl.event = event;
                    ctrl.purchaseContextType = 'event';
                    ctrl.resetReservationView = false;
                    ctrl.showReservation = false;
                    var reservationInfo = {eventName: event.shortName, reservationId: ticket.ticketsReservationId}
                    AdminReservationService.load('event', reservationInfo.eventName, reservationInfo.reservationId).then(function (reservationDescriptor) {
                        ctrl.reservationDescriptor = reservationDescriptor.data.data;
                        ctrl.showReservation = true;
                    });

                    ctrl.onClose = function() {
                        modal.close();
                    }

                    ctrl.onUpdate = function(reservationInfo) {
                        ctrl.resetReservationView = true;
                        reloadTickets();
                        AdminReservationService.load('event', reservationInfo.eventName, reservationInfo.reservationId).then(function (reservationDescriptor) {
                            ctrl.reservationDescriptor = reservationDescriptor.data.data;
                            ctrl.resetReservationView = false;
                        })
                    }
                }
            })
        };

        var parentScope = $scope;

        $scope.newReservationsModal = function newReservationsModal(event) {
            var modal = $uibModal.open({
                size:'min-1200',
                templateUrl: BASE_STATIC_URL + '/event/new-reservation-modal.html',
                backdrop: 'static',
                controllerAs: 'ctrl',
                controller: function($scope) {
                    var ctrl = this;
                    ctrl.resetReservationView = false;
                    ctrl.showReservation = false;
                    ctrl.event = event;
                    ctrl.purchaseContextType = 'event';
                    ctrl.close = function() {
                        modal.close();
                    };
                    ctrl.onCreation = function(reservationInfo) {
                        AdminReservationService.confirm('event', reservationInfo.eventName, reservationInfo.reservationId).then(function(reservationDescriptor) {
                            ctrl.onConfirm(reservationInfo);
                        }, function(err) {
                            AdminReservationService.load('event', reservationInfo.eventName, reservationInfo.reservationId).then(function (reservationDescriptor) {
                                ctrl.reservationDescriptor = reservationDescriptor.data.data;
                                ctrl.showReservation = true;
                            });
                        });
                    };
                    ctrl.onUpdate = function(reservationInfo) {
                        ctrl.resetReservationView = true;
                        AdminReservationService.load('event', reservationInfo.eventName, reservationInfo.reservationId).then(function (reservationDescriptor) {
                            ctrl.reservationDescriptor = reservationDescriptor.data.data;
                            ctrl.resetReservationView = false;
                        })
                    };

                    ctrl.onConfirm = function(reservationInfo) {
                        reloadTickets();
                        modal.close();
                        parentScope.selection.freeText = reservationInfo.reservationId;
                    }
                }
            });
        };

        $scope.manualCheckIn = function(ticket) {
            CheckInService.manualCheckIn(ticket)
                .then(function(result) {
                    if(result.data) {
                        return NotificationHandler.showSuccess(ticket.fullName+" checked in!")
                    } else {
                        return NotificationHandler.showWarning("Can't check-in "+ticket.fullName)
                    }
                })
                .then($scope.reloadTickets).then(function() {
                    $scope.selection = {};
                });
        };

        $scope.showQrCode = function(ticket, event) {
            var modal = $uibModal.open({
                size:'sm',
                templateUrl:BASE_STATIC_URL + '/event/qr-code.html',
                backdrop: 'static',
                controllerAs: 'ctrl',
                controller: function($scope) {
                    var ctrl = this;
                    ctrl.event = event;
                    ctrl.ticket = ticket;
                    ctrl.close = function() {
                        modal.close();
                    }
                }
            });
        };

        $scope.revertCheckIn = function(ticket) {
            CheckInService.revertCheckIn(ticket).then(function(result) {
                if(result.data) {
                    NotificationHandler.showSuccess("Reverted "+ticket.fullName);
                }
            }).then($scope.reloadTickets);
        };

        $scope.triggerSearch = function() {
            loadTicketsFromDB(db, $scope);
        };

        $scope.updatePage = function(newPage) {
            $scope.currentPage = newPage;
            loadTicketsFromDB(db, $scope, newPage);
        };

        $scope.updatePageCheckedIn = function(newPage) {
            $scope.currentPageCheckedIn = newPage;
            loadTicketsFromDB(db, $scope, newPage);
        };

        var loadTicketsFromDB = function (db, $scope, newPage) {

            var eventId = $scope.event.id;
            var query = $scope.selection.freeText;
            var pageNotCheckedIn = $scope.currentPage;
            var offsetNotCheckedIn = $scope.itemsPerPage * (pageNotCheckedIn - 1);

            var pageCheckedIn = $scope.currentPageCheckedIn;
            var offsetCheckedIn = $scope.itemsPerPage * (pageCheckedIn - 1);

            var filter = function(ticket) {
                return ticket.eventId === eventId && (!query || query === '' ||
                    ([ticket.fullName, ticket.email, ticket.ticketCategory.name, ticket.uuid, ticket.ticketsReservationId, ticket.extReference].join('/')).toLowerCase().indexOf(query.toLowerCase()) > -1);
            };

            var deferred1 = $q.defer();
            var deferred2 = $q.defer();
            db.alfioCheckIn
                .where("status")
                .notEqual('CHECKED_IN')
                .and(filter)
                .offset(offsetNotCheckedIn)
                .limit($scope.itemsPerPage)
                .toArray()
                .then(function (tickets) {
                    $timeout(function () {
                        $scope.tickets = tickets;
                        deferred1.resolve();
                    }, 50);
                });

            db.alfioCheckIn
                .where("status")
                .equals('CHECKED_IN')
                .and(filter)
                .offset(offsetCheckedIn)
                .limit($scope.itemsPerPage)
                .toArray()
                .then(function(tickets) {
                    $timeout(function() {
                        $scope.checkedInTickets = tickets;
                        deferred2.resolve();
                    }, 50);
                });

            db.alfioCheckIn
                .where("status")
                .notEqual('CHECKED_IN')
                .and(filter)
                .count()
                .then(function (count) {
                    $timeout(function () {
                        $scope.count = count;
                    }, 50);
                });

            db.alfioCheckIn
                .where("status")
                .equals('CHECKED_IN')
                .and(filter)
                .count()
                .then(function(count) {
                    $timeout(function() {
                        $scope.checkedInCount = count;
                    }, 50);
                });

            $q.all([deferred1.promise, deferred2.promise]).then(function() {
                $scope.loading = false;
            })
        };
    });

    admin.controller('EventCheckInScanController', function($scope, $stateParams, $timeout, $log, $state, EventService, CheckInService) {

        $scope.scanning = {visible : false, ticket : {}};


        var canReadCamera = navigator.mediaDevices !== undefined;

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
                    var stream = $scope.stream;
                    if (stream.getVideoTracks) {
                        var track = stream.getVideoTracks();
                        if (track && track[0] && track[0].stop) {
                            track[0].stop()
                        } else if (stream.stop) {
                            stream.stop()
                        }
                    }
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

            navigator.mediaDevices.enumerateDevices().then(function(sources) {
                var videos = [];
                angular.forEach(sources, function(v,i) {
                    if(v.kind === 'videoinput') {
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

    admin.controller('PendingPaymentsController', function($scope, EventService, $stateParams, $log, $window, $uibModal, ReservationIdentifierConfiguration) {

        EventService.getEvent($stateParams.eventName).then(function(result) {
            $scope.event = result.data.event;
            getPendingPayments();
        });

        var getPendingPayments = function(force) {
            EventService.getPendingPayments($stateParams.eventName, force).success(function(data) {
                var pendingReservations = data.map(function(pending) {

                    ReservationIdentifierConfiguration.getReservationIdentifier('event', $scope.event.shortName, pending.ticketReservation, false).then(function(result) {
                        pending.publicId = result;
                    });
                    return pending;
                });
                $scope.pendingReservations = pendingReservations;
                $scope.orderByFieldDesc = {};

                $scope.changeSorting = function(field) {
                    $scope.orderByField = field;
                    var sortDesc = false;
                    if(angular.isDefined($scope.orderByFieldDesc[field])) {
                        sortDesc = !$scope.orderByFieldDesc[field];
                    }
                    $scope.orderByFieldDesc[field]=sortDesc;
                    var sorted = _.sortBy(pendingReservations, field);
                    if(sortDesc) {
                        sorted = _(sorted).reverse().value()
                    }
                    $scope.pendingReservations = sorted;
                };

                $scope.sortingIndicator = function(field) {
                    if($scope.orderByField === field) {
                        return $scope.orderByFieldDesc[field] ? 'fa-sort-desc' : 'fa-sort-asc';
                    }
                    return '';
                };
                $scope.loading = false;
            });
        };

        var eventName = $stateParams.eventName;
        $scope.eventName = eventName;
        $scope.uploadSuccess = function(data) {
            $scope.results = data;
            getPendingPayments();
        };

        $scope.uploadUrl = '/admin/api/events/'+$stateParams.eventName+'/pending-payments/bulk-confirmation';

        $scope.registerPayment = function(eventName, id) {
            $scope.loading = true;
            EventService.registerPayment(eventName, id).then(
                function() {
                    getPendingPayments(true);
                },
                function() {
                    $scope.loading = false;
                }
            );
        };

        $scope.showTransactionDialog = function(pendingPaymentDescriptor) {
            $uibModal.open({
                size:'md',
                templateUrl:BASE_STATIC_URL + '/pending-payments/show-transaction-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    var ctrl = this;
                    ctrl.paymentInfo = pendingPaymentDescriptor;
                    ctrl.reservationId = pendingPaymentDescriptor.ticketReservation.id;
                    ctrl.cancel = function() {
                        $scope.$dismiss('cancelled');
                    };
                    ctrl.confirm = function() {
                        $scope.$close('CONFIRM');
                    };
                    ctrl.discardPayment = function() {
                        $scope.$close('DISCARD');
                    };
                },
                controllerAs: '$ctrl'
            }).result.then(function(command) {
                if(command === 'CONFIRM') {
                    $scope.loading = true;
                    return EventService.registerPayment(eventName, pendingPaymentDescriptor.ticketReservation.id);
                } else if (command === 'DISCARD') {
                    $scope.loading = true;
                    return EventService.cancelMatchingPayment(eventName, pendingPaymentDescriptor.ticketReservation.id, pendingPaymentDescriptor.transaction.id);
                }
                return null;
            }).then(function() {
                getPendingPayments(true);
            }, function() {
                $scope.loading = false;
            });
        };

        $scope.deletePayment = function(eventName, id, credit) {
            var confirmPromise = $uibModal.open({
                size:'md',
                templateUrl:BASE_STATIC_URL + '/pending-payments/delete-or-credit-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    var ctrl = this;
                    ctrl.credit = credit;
                    ctrl.reservationId = id;
                    ctrl.notify = !credit;
                    ctrl.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    ctrl.confirm = function() {
                        $scope.$close(ctrl.notify);
                    };
                },
                controllerAs: '$ctrl'
            }).result;

            confirmPromise.then(function(notify) {
                return EventService.cancelPayment(eventName, id, credit, notify);
            }).then(function() {
                getPendingPayments(true);
            }, function() {
                $scope.loading = false;
            });
        };
    });

    admin.controller('ComposeCustomMessage', function($scope, $stateParams, EventService, $uibModal, $state, $q) {


        $q.all([EventService.getSelectedLanguages($stateParams.eventName),
            EventService.getEvent($stateParams.eventName)])
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

                $scope.categories = results[1].data.event.ticketCategories;
                $scope.categoryId = undefined;

                var eventDescriptor = results[1].data;
                $scope.organization = eventDescriptor.organization;
                $scope.eventName = eventDescriptor.event.shortName;
                $scope.onlineEvent = eventDescriptor.event.online;
        });

        $scope.cancel = function() {
            $state.go('events.single.detail', {eventName: $stateParams.eventName});
        };


        $scope.showPreview = function(frm, eventName, categoryId, messages, categories, online) {
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
                        $scope.online = online;
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
        ctrl.removeSubscriber = function(subscriber) {
            WaitingQueueService.removeSubscriber(ctrl.eventName, subscriber).success(function(result) {
                ctrl.subscriber = result.modified;
                ctrl.subscriptions = result.list;
                ctrl.showConfirmation = true;
            });
        };
        ctrl.restoreSubscriber = function(subscriber) {
            WaitingQueueService.restoreSubscriber(ctrl.eventName, subscriber).success(function(result) {
                ctrl.subscriber = result.modified;
                ctrl.subscriptions = result.list;
                ctrl.showConfirmation = true;
            });
        };

        ctrl.dismissMessage = function() {
            ctrl.showConfirmation = false;
        };
    }]);

    admin.controller('LayoutController', ['$state', '$rootScope', function($state, $rootScope) {
        var ctrl = this;
        var checkSidebar = function() {
            ctrl.displaySidebar = angular.isDefined($state.$current.data) && angular.isDefined($state.$current.data.view);
        };
        checkSidebar();
        $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState, fromParams) {
            checkSidebar();
        })
    }]);

    admin.run(function($rootScope, PriceCalculator) {

        $rootScope.evaluateBarType = function(index) {
            var barClasses = ['warning', 'info', 'success'];
            if(index < barClasses.length) {
                return barClasses[index];
            }
            return index % 2 == 0 ? 'info' : 'success';
        };

        $rootScope.evaluateTicketStatus = function(status) {
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

        $rootScope.calcBarValue = PriceCalculator.calcBarValue;

        $rootScope.calcCategoryPricePercent = PriceCalculator.calcCategoryPricePercent;

        $rootScope.calcCategoryPrice = PriceCalculator.calcCategoryPrice; 

        $rootScope.calcPercentage = PriceCalculator.calcPercentage;

        $rootScope.applyPercentage = PriceCalculator.applyPercentage;

        $rootScope.calculateTotalPrice = PriceCalculator.calculateTotalPrice;
    });

    admin.component('countryName', {
        bindings: {
            code: '<'
        },
        controller: ['CountriesService', function (CountriesService) {
            var ctrl = this;
            CountriesService.getDescription(this.code).then(function (countryName) {
                ctrl.countryName = countryName;
            })
        }],
        template: '<span>{{$ctrl.countryName}}</span>'
    });

})();
