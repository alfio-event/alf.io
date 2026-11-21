(function () {
    "use strict";

    var BASE_STATIC_URL = window.ALFIO_CONTEXT_PATH + "/resources/angular-templates/admin/partials";

    var admin = angular.module('adminApplication', ['ui.bootstrap', 'ui.router', 'adminDirectives',
        'adminServices', 'utilFilters', 'ngMessages', 'ngFileUpload', 'alfio-util',
        'checklist-model', 'group']);

    var loadEvent = {
        'loadEvent': function($stateParams, EventService) {
            return EventService.getEvent($stateParams.eventName);
        }
    };

    function loadEventCtrl(loadEvent) {
        this.loadEvent = loadEvent.data.event;
    }

    admin.config(function($stateProvider, $urlRouterProvider) {
        $urlRouterProvider.otherwise("/");
        $stateProvider
            .state('index', {
                url: "/",
                template: '<alfio-events-page></alfio-events-page>'
            })
            .state('configuration', {
                abstract: true,
                url: '/configuration',
                template: '<div data-ui-view></div>'
            })
            .state('configuration.system', {
                url: '/system',
                template: '<alfio-settings-page></alfio-settings-page>'
            })
            .state('configuration.organization', {
                url: '/organization/:organizationId',
                template: '<alfio-settings-page data-organization-id="{{$ctrl.organizationId}}"></alfio-settings-page>',
                controller: ['$stateParams', function($stateParams) {
                    this.organizationId = $stateParams.organizationId;
                }],
                controllerAs: '$ctrl'
            })
            .state('configuration.event', {
                // an event chosen in the level select of the configuration page
                url: '/organization/:organizationId/event/:eventId',
                template: '<alfio-settings-page data-event-id="{{$ctrl.eventId}}"></alfio-settings-page>',
                controller: ['$stateParams', function($stateParams) {
                    this.eventId = $stateParams.eventId;
                }],
                controllerAs: '$ctrl'
            })
            .state('organizations', {
                url: "/organizations/",
                template: "<div organizations-page><alfio-organization-list></alfio-organization-list><alfio-organization-edit></alfio-organization-edit><ui-view></ui-view></div>"
            })
            .state('organizations.new', {
                url: "new",
                template: '',
                controller: ['$state', '$timeout', '$scope', function($state, $timeout, $scope) {
                    var timeoutId = $timeout(function() {
                        if ($state.current.name !== 'organizations.new') {
                            return;
                        }
                        var container = document.querySelector('div[organizations-page]');
                        if (!container) {
                            $state.go('organizations');
                            return;
                        }
                        var editEl = container.querySelector('alfio-organization-edit');
                        if (editEl && typeof editEl.open === 'function') {
                            editEl.open({ type: 'new' });
                        } else {
                            $state.go('organizations');
                        }
                    });
                    $scope.$on('$destroy', function() {
                        $timeout.cancel(timeoutId);
                    });
                }]
            })
            .state('organizations.edit', {
                url: ":organizationId/edit",
                template: '',
                controller: ['$state', '$timeout', '$scope', function($state, $timeout, $scope) {
                    var rawId = String($state.params.organizationId);
                    var orgId = parseInt(rawId, 10);
                    if (!/^\d+$/.test(rawId) || !Number.isSafeInteger(orgId) || orgId < 1 || orgId > 2147483647) {
                        $state.go('organizations');
                        return;
                    }
                    var timeoutId = $timeout(function() {
                        if ($state.current.name !== 'organizations.edit' || parseInt($state.params.organizationId, 10) !== orgId) {
                            return;
                        }
                        var container = document.querySelector('div[organizations-page]');
                        if (!container) {
                            $state.go('organizations');
                            return;
                        }
                        var editEl = container.querySelector('alfio-organization-edit');
                        if (editEl && typeof editEl.open === 'function') {
                            editEl.open({ type: 'edit', organizationId: orgId });
                        } else {
                            $state.go('organizations');
                        }
                    });
                    $scope.$on('$destroy', function() {
                        $timeout.cancel(timeoutId);
                    });
                }]
            })
            .state('system-edit-resources', {
                url: '/show-resources/:resourceName/',
                template: '<alfio-template-edit data-system data-resource-name="{{ctrl.resourceName}}"></alfio-template-edit>',
                controller: function($state) {
                    this.resourceName = $state.params.resourceName;
                },
                controllerAs: 'ctrl'
            })
            .state('organization-edit-resources', {
                url: '/organizations/:organizationId/show-resources/:resourceName/',
                template: '<alfio-template-edit data-organization-id="{{ctrl.organizationId}}" data-resource-name="{{ctrl.resourceName}}"></alfio-template-edit>',
                controller: function($state) {
                    this.organizationId = $state.params.organizationId;
                    this.resourceName = $state.params.resourceName;
                },
                controllerAs: 'ctrl'
            })
            .state('users', {
                url: "/users/",
                template: "<alfio-users alfio-title='Users' type='user'></alfio-users>"
            })
            .state('apikey', {
                url: "/api-keys/",
                template: "<alfio-users alfio-title='API keys' type='apikey'></alfio-users>"
            })
            .state('edit-current-user', {
                url: "/profile/edit",
                template: '<alfio-user-profile-edit></alfio-user-profile-edit>'
            })
            .state('events', {
                abstract: true,
                url: '/events',
                templateUrl: BASE_STATIC_URL + "/event/index.html"
            })
            .state('events.new', {
                url: '/new?eventName',
                template: '<alfio-event-create></alfio-event-create>'
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
                template: '<alfio-event-detail data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-event-detail>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'EVENT_DETAIL'
                }
            })
            .state('events.single.edit', {
                url: '/edit',
                template: '<alfio-event-create data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-event-create>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.configuration', {
                url: '/configuration',
                template: '<alfio-settings-page data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-settings-page>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'EVENT_CONFIGURATION'
                }
            })
            .state('events.single.dataToCollect', {
                url:'/attendee-data-to-collect',
                template:'<alfio-additional-field-list data-public-identifier="{{$ctrl.loadEvent.publicIdentifier}}" data-purchase-context-type="event" data-organization-id="{{$ctrl.loadEvent.organizationId}}"></alfio-additional-field-list>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.promoCodes', {
                url:'/promo-codes',
                template:'<alfio-promo-code data-event-name="{{$ctrl.loadEvent.shortName}}" data-organization-id="{{$ctrl.loadEvent.organizationId}}" data-for-event="true"></alfio-promo-code>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.additionalServices', {
                url: '/additional-services',
                template: '<alfio-additional-item-list data-public-identifier="{{$ctrl.loadEvent.shortName}}" data-type="SUPPLEMENT" data-title="Additional options" data-icon="cash"></alfio-additional-item-list>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.donations', {
                url: '/donations',
                template: '<alfio-additional-item-list data-public-identifier="{{$ctrl.loadEvent.shortName}}" data-type="DONATION" data-title="Donation options" data-icon="gift"></alfio-additional-item-list>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.checkIn', {
                url: '/check-in?search&page&checkedInPage&t',
                // The Lit component owns filtering and pagination; query changes must preserve its DOM.
                reloadOnSearch: false,
                template: '<alfio-check-in data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-check-in>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'CHECK_IN'
                }
            })
            .state('events.single.checkInScan', {
                url: '/check-in/scan',
                template: '<alfio-check-in-scan data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-check-in-scan>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'CHECK_IN_SCAN'
                }
            })
            .state('events.single.sendInvitations', {
                url: '/c/:categoryId/send-invitation',
                template: '<alfio-send-invitations data-event-name="{{$ctrl.loadEvent.shortName}}" data-category-id="{{$ctrl.categoryId}}"></alfio-send-invitations>',
                controller: ['loadEvent', '$stateParams', function(loadEvent, $stateParams) {
                    this.loadEvent = loadEvent.data.event;
                    this.categoryId = $stateParams.categoryId;
                }],
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'SEND_INVITATIONS'
                }
            })
            .state('events.single.reservationsList', {
                url: '/reservations/?search',
                // The Lit component owns filtering; query changes must preserve its DOM.
                reloadOnSearch: false,
                template: '<alfio-reservations-list data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-reservations-list>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.paymentsList', {
                url: '/transactions/?search',
                // The Lit component owns filtering; query changes must preserve its DOM.
                reloadOnSearch: false,
                template: '<alfio-payments-list data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-payments-list>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.email-log', {
                url: '/email-log?search&page',
                // The Lit component owns filtering; query changes must preserve its DOM.
                reloadOnSearch: false,
                template: '<alfio-email-log data-purchase-context-type="event" data-public-identifier="{{$ctrl.loadEvent.shortName}}"></alfio-email-log>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent
            })
            .state('events.single.email-log-detail', {
                url: '/email-log/:messageId?search&page',
                reloadOnSearch: false,
                template: '<alfio-email-log data-purchase-context-type="event" data-public-identifier="{{$ctrl.loadEvent.shortName}}" data-message-id="{{$ctrl.messageId}}"></alfio-email-log>',
                controller: ['loadEvent', '$stateParams', function(loadEvent, $stateParams) {
                    this.loadEvent = loadEvent.data.event;
                    this.messageId = $stateParams.messageId;
                }],
                controllerAs: '$ctrl',
                resolve: loadEvent
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
                template: '<alfio-pending-payments data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-pending-payments>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'PENDING_RESERVATIONS'
                }
            })
            .state('events.single.compose-custom-message', {
                url: '/compose-custom-message',
                template: '<alfio-compose-message data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-compose-message>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'CUSTOM_MESSAGE'
                }
            })
            .state('events.single.show-waiting-queue', {
                url: '/waiting-queue',
                template: '<alfio-waiting-list data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-waiting-list>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'WAITING_QUEUE'
                }
            }).state('events.single.show-resources', {
                url: '/show-resources',
                template: '<alfio-template-list data-event-name="{{ctrl.event.shortName}}"></alfio-template-list>',
                controller: function(getEvent) {
                    this.event = getEvent.data.event;
                },
                controllerAs: 'ctrl'
            }).state('events.single.edit-resources', {
                url: '/show-resources/:resourceName/',
                template: '<alfio-template-edit data-event-name="{{ctrl.event.shortName}}" data-resource-name="{{ctrl.resourceName}}"></alfio-template-edit>',
                controller: function(getEvent, $state) {
                    this.event = getEvent.data.event;
                    this.resourceName = $state.params.resourceName;
                },
                controllerAs: 'ctrl'
            }).state('events.single.create-reservation', {
                url:'/reservation/new',
                template: '<alfio-reservation-create data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-reservation-create>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'CREATE_RESERVATION'
                }
            }).state('events.single.import-reservation', {
                url:'/reservation/import',
                template: '<alfio-attendees-import data-event-name="{{$ctrl.loadEvent.shortName}}"></alfio-attendees-import>',
                controller: loadEventCtrl,
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'IMPORT_RESERVATION'
                }
            }).state('events.single.import-status', {
                url:'/reservation/import/:requestId/status',
                template: '<alfio-attendees-import-status data-event-name="{{$ctrl.loadEvent.shortName}}" data-request-id="{{$ctrl.requestId}}"></alfio-attendees-import-status>',
                controller: ['loadEvent', '$stateParams', function(loadEvent, $stateParams) {
                    this.loadEvent = loadEvent.data.event;
                    this.requestId = $stateParams.requestId;
                }],
                controllerAs: '$ctrl',
                resolve: loadEvent,
                data: {
                    view: 'IMPORT_STATUS'
                }
            }).state('events.single.view-reservation', {
                url:'/reservation/:reservationId?fromCreation',
                template: '<alfio-reservation-detail data-purchase-context-type="event" data-public-identifier="{{ctrl.eventName}}" data-reservation-id="{{ctrl.reservationId}}" data-from-creation="{{ctrl.fromCreation}}"></alfio-reservation-detail>',
                controller: function($stateParams) {
                    this.eventName = $stateParams.eventName;
                    this.reservationId = $stateParams.reservationId;
                    this.fromCreation = $stateParams.fromCreation;
                },
                controllerAs: 'ctrl',
                data: {
                    view: 'VIEW_RESERVATION'
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
                template: '<alfio-extension-list></alfio-extension-list>'
            }).state('extension.new', {
                url: '/new',
                template: '<alfio-extension-edit></alfio-extension-edit>'
            }).state('extension.edit', {
                url: '/:path/:name/edit',
                template: '<alfio-extension-edit data-path="{{ctrl.toUpdate.path}}" data-name="{{ctrl.toUpdate.name}}"></alfio-extension-edit>',
                controllerAs: 'ctrl',
                controller: ['$stateParams', function($stateParams) {
                    this.toUpdate = {path: $stateParams.path, name: $stateParams.name};
                }]
            }).state('extension.log', {
                url: '/log?search&page&type',
                // The Lit component owns filtering and pagination; query changes must preserve its DOM.
                reloadOnSearch: false,
                template: '<alfio-extension-log></alfio-extension-log>'
             })
            .state('subscriptions', {
                url: '/subscriptions',
                template: '<alfio-subscriptions-page><div ui-view></div></alfio-subscriptions-page>'
            })
            .state('subscriptions.list', {
                url: '/:organizationId/list',
                template: '<alfio-subscriptions-list data-organization-id="{{ctrl.organizationId}}"></alfio-subscriptions-list>',
                controllerAs: 'ctrl',
                controller: ['$stateParams', function ($stateParams) {
                    this.organizationId = $stateParams.organizationId;
                }]
            })
            .state('subscriptions.single', {
                url: '/:organizationId/:subscriptionId',
                template: '<alfio-subscription-detail data-organization-id="{{ctrl.organizationId}}" data-subscription-id="{{ctrl.subscriptionId}}" data-back-to-reservations="{{ctrl.backToReservationList}}"><div ui-view></div></alfio-subscription-detail>',
                abstract: true,
                controllerAs: 'ctrl',
                controller: ['loadSubscription', '$stateParams', '$state', '$rootScope', function(loadSubscription, $stateParams, $state, $rootScope) {
                    this.subscriptionDescriptor = loadSubscription.data;
                    this.organizationId = $stateParams.organizationId;
                    this.subscriptionId = $stateParams.subscriptionId;
                    var ctrl = this;
                    ctrl.backToReservationList = $state.is('subscriptions.single.view-reservation');
                    var unbind = $rootScope.$on('$stateChangeSuccess', function() {
                        ctrl.backToReservationList = $state.is('subscriptions.single.view-reservation');
                    });
                    this.$onDestroy = function() {
                        unbind();
                    };
                }],
                resolve: {
                    'loadSubscription': function(SubscriptionService, $stateParams) {
                        return SubscriptionService.loadDescriptor($stateParams.organizationId, $stateParams.subscriptionId);
                    }
                }
            })
            .state('subscriptions.single.reservationsList', {
                url: '/reservations/?search',
                // The Lit component owns filtering; query changes must preserve its DOM.
                reloadOnSearch: false,
                template: '<alfio-reservations-list data-event-name="{{ctrl.subscriptionDescriptor.publicIdentifier}}" data-organization-id="{{ctrl.subscriptionDescriptor.organizationId}}" data-purchase-context-type="subscription" data-title="{{ctrl.subscriptionDescriptor.title.en}}"></alfio-reservations-list>',
                controllerAs: 'ctrl',
                controller: ['loadSubscription', function(loadSubscription) {
                    this.subscriptionDescriptor = loadSubscription.data;
                }]
            })
            .state('subscriptions.single.view-reservation', {
                url:'/reservation/:reservationId?fromCreation',
                template: '<alfio-reservation-detail data-purchase-context-type="subscription" data-public-identifier="{{ctrl.subscriptionDescriptor.publicIdentifier}}" data-organization-id="{{ctrl.subscriptionDescriptor.organizationId}}" data-reservation-id="{{ctrl.reservationId}}"></alfio-reservation-detail>',
                controllerAs: 'ctrl',
                controller: ['loadSubscription', '$stateParams', function(loadSubscription, $stateParams) {
                    this.subscriptionDescriptor = loadSubscription.data;
                    this.reservationId = $stateParams.reservationId;
                }]
            })
            .state('subscriptions.single.email-log', {
                url: '/email-log?search&page',
                // The Lit component owns filtering; query changes must preserve its DOM.
                reloadOnSearch: false,
                template: '<alfio-email-log data-purchase-context-type="subscription" data-public-identifier="{{ctrl.subscriptionDescriptor.publicIdentifier}}" data-title="{{ctrl.subscriptionDescriptor.title.en}}" data-time-zone="{{ctrl.subscriptionDescriptor.timeZone}}"></alfio-email-log>',
                controllerAs: 'ctrl',
                controller: ['loadSubscription', function(loadSubscription) {
                    this.subscriptionDescriptor = loadSubscription.data;
                }]
            })
            .state('subscriptions.single.email-log-detail', {
                url: '/email-log/:messageId?search&page',
                reloadOnSearch: false,
                template: '<alfio-email-log data-purchase-context-type="subscription" data-public-identifier="{{ctrl.subscriptionDescriptor.publicIdentifier}}" data-title="{{ctrl.subscriptionDescriptor.title.en}}" data-time-zone="{{ctrl.subscriptionDescriptor.timeZone}}" data-message-id="{{ctrl.messageId}}"></alfio-email-log>',
                controllerAs: 'ctrl',
                controller: ['loadSubscription', '$stateParams', function(loadSubscription, $stateParams) {
                    this.subscriptionDescriptor = loadSubscription.data;
                    this.messageId = $stateParams.messageId;
                }]
            })
            .state('subscriptions.single.additional-fields', {
                url:'/additional-fields',
                template: '<alfio-additional-field-list data-public-identifier="{{ctrl.subscriptionDescriptor.publicIdentifier}}" data-purchase-context-type="subscription" data-organization-id="{{ctrl.subscriptionDescriptor.organizationId}}"></alfio-additional-field-list>',
                controllerAs: 'ctrl',
                controller: ['loadSubscription', function(loadSubscription) {
                    this.subscriptionDescriptor = loadSubscription.data;
                }]
            })
            .state('subscriptions.new', {
                url: '/:organizationId/create',
                template: '<alfio-subscription-edit data-organization-id="{{ctrl.organizationId}}"></alfio-subscription-edit>',
                controllerAs: 'ctrl',
                controller: ['$stateParams', function ($stateParams) {
                    this.organizationId = $stateParams.organizationId;
                }]
            })
            .state('subscriptions.edit', {
                url: '/:organizationId/:subscriptionId/edit',
                template: '<alfio-subscription-edit data-organization-id="{{ctrl.organizationId}}" data-subscription-id="{{ctrl.subscriptionId}}"></alfio-subscription-edit>',
                controllerAs: 'ctrl',
                controller: ['$stateParams', function ($stateParams) {
                    this.organizationId = $stateParams.organizationId;
                    this.subscriptionId = $stateParams.subscriptionId;
                }]
            })
            .state('groups', {
                url: '/group',
                template: '<alfio-groups-page><div ui-view></div></alfio-groups-page>'
            })
            .state('groups.all', {
                url: '/:orgId/all',
                template: '<alfio-groups-list data-organization-id="{{ctrl.orgId}}"></alfio-groups-list>',
                controllerAs: 'ctrl',
                controller: ['$stateParams', function ($stateParams) {
                    this.orgId = $stateParams.orgId;
                }]
            })
            .state('groups.edit', {
                url: '/:orgId/edit/:groupId',
                template: '<alfio-group-edit data-organization-id="{{ctrl.orgId}}" data-group-id="{{ctrl.groupId}}"></alfio-group-edit>',
                controllerAs: 'ctrl',
                controller: ['$stateParams', function ($stateParams) {
                    this.orgId = $stateParams.orgId;
                    this.groupId = $stateParams.groupId;
                }]
            })
            .state('groups.new', {
                url: '/:orgId/new',
                template: '<alfio-group-edit data-organization-id="{{ctrl.orgId}}" data-create-new="true"></alfio-group-edit>',
                controllerAs: 'ctrl',
                controller: ['$stateParams', function ($stateParams) {
                    this.orgId = $stateParams.orgId;
                }]
            });
     });

    admin.directive('organizationsPage', ['$state', '$rootScope', function($state, $rootScope) {
        return {
            restrict: 'A',
            link: function(scope, element) {
                var container = element[0];

                var handleAction = function(e) {
                    if (!e.detail) {
                        return;
                    }
                    var targetState = e.detail.type === 'new' ? 'organizations.new'
                                   : e.detail.type === 'edit' ? 'organizations.edit'
                                   : null;
                    if (targetState && $state.current.name !== targetState) {
                        var params = e.detail.type === 'edit' ? { organizationId: e.detail.organizationId } : {};
                        $state.go(targetState, params, { location: true });
                    }
                };

                var handleDialogClosed = function(e) {
                    var editEl = container.querySelector('alfio-organization-edit');
                    if (editEl && typeof editEl.hide === 'function') {
                        editEl.hide();
                    }
                    if (e.detail && e.detail.success) {
                        var listEl = container.querySelector('alfio-organization-list');
                        if (listEl && typeof listEl.refresh === 'function') {
                            listEl.refresh();
                        }
                    }
                    if ($state.current.name !== 'organizations') {
                        $state.go('organizations', {}, { location: true });
                    }
                };

                var handleStateChange = function(event, toState, toParams, fromState, fromParams) {
                    if (fromState && (fromState.name === 'organizations.new' || fromState.name === 'organizations.edit') && toState && toState.name !== fromState.name) {
                        var editEl = container.querySelector('alfio-organization-edit');
                        if (editEl && typeof editEl.hide === 'function') {
                            editEl.hide();
                        }
                    }
                };

                container.addEventListener('alfio-organization-action', handleAction);
                container.addEventListener('alfio-dialog-closed', handleDialogClosed);
                var deregisterStateChangeListener = $rootScope.$on('$stateChangeSuccess', handleStateChange);

                scope.$on('$destroy', function() {
                    container.removeEventListener('alfio-organization-action', handleAction);
                    container.removeEventListener('alfio-dialog-closed', handleDialogClosed);
                    deregisterStateChangeListener();
                });
            }
        };
    }]);

    navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;

    admin.run(function($rootScope, $uibModal, $window, $state, $q) {
        // A page can ask for confirmation before the admin navigates away from it (e.g. unsaved settings): it listens
        // to the "alfio-before-leave" window event and adds to detail.confirmations a promise of true (leave) or false (stay).
        var leaveConfirmed = false;
        $rootScope.$on('$stateChangeStart', function(event, toState, toParams) {
            if (leaveConfirmed) {
                leaveConfirmed = false;
                return;
            }
            var check = new CustomEvent('alfio-before-leave', {detail: {confirmations: []}});
            $window.dispatchEvent(check);
            if (check.detail.confirmations.length === 0) {
                return;
            }
            // the URL goes back to the current page while the user decides
            event.preventDefault();
            $q.all(check.detail.confirmations).then(function(answers) {
                if (answers.every(Boolean)) {
                    leaveConfirmed = true;
                    $state.go(toState, toParams);
                }
            });
        });
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

    admin.controller('MenuController', ['$scope', '$http', '$window', 'UtilsService', function($scope, $http, $window, UtilsService) {
        var ctrl = this;
        ctrl.menuCollapsed = true;
        ctrl.toggleCollapse = function(currentStatus) {
            ctrl.menuCollapsed = !currentStatus;
        };
        var clearSessionStorage = function () {
            try {
                $window.sessionStorage.clear();
            } catch (e) {
            }
        };

        $window.addEventListener("beforeunload", function() {
            try {
                // Keep the unfinished event across reloads; logout still clears it.
                var eventDraft = $window.sessionStorage.getItem('new_event');
                clearSessionStorage();
                if (eventDraft != null) {
                    $window.sessionStorage.setItem('new_event', eventDraft);
                }
            } catch (e) {
                // Session storage can be unavailable.
            }
        });

        ctrl.doLogout = function(idpLogoutRedirectionUrl) {
            UtilsService.logout().then(function() {
                clearSessionStorage();
                // pages asking for confirmation before unload (e.g. check-in) must not block the logout
                $window.dispatchEvent(new CustomEvent('alfio-logout'));
                $window.location.reload();
                if(!(idpLogoutRedirectionUrl === '')){
                    $window.location.replace(idpLogoutRedirectionUrl);
                }
            });
        };
    }]);


    admin.controller('MessageBarController', function($scope, $rootScope) {
        $rootScope.$on('Message', function(m) {
            $scope.message = m;
        });
    });

    admin.controller('LayoutController', ['$state', '$rootScope', function($state, $rootScope) {
        var ctrl = this;
        var checkSidebar = function() {
            ctrl.displaySidebar = angular.isDefined($state.$current.data) && angular.isDefined($state.$current.data.view);
            // the pages of an event show the event menu (<alfio-event-sidebar>)
            ctrl.eventName = undefined;
            if ($state.includes('events.single')) {
                ctrl.eventName = $state.params.eventName;
            }
        };
        checkSidebar();
        $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState, fromParams) {
            checkSidebar();
        })
    }]);

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
