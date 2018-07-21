(function() {
    'use strict';

    angular.module('attendee-list', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider.state('attendee-lists', {
                url: '/attendee-list',
                template: '<attendee-lists-container organizations="ctrl.organizations"></attendee-lists-container>',
                controller: ['loadOrganizations', function (loadOrganizations) {
                    this.organizations = loadOrganizations.data;
                }],
                controllerAs: 'ctrl',
                resolve: {
                    'loadOrganizations': function(OrganizationService) {
                        return OrganizationService.getAllOrganizations();
                    }
                }
            }).state('attendee-lists.all', {
                url: '/:orgId/all',
                template: '<attendee-lists organization-id="ctrl.orgId"></attendee-lists>',
                controller: ['$stateParams', function ($stateParams) {
                    this.orgId = $stateParams.orgId;
                }],
                controllerAs: 'ctrl'
            }).state('attendee-lists.edit', {
                url: '/:orgId/edit/:listId',
                template: '<attendee-list list-id="ctrl.listId" organization-id="ctrl.orgId"></attendee-list>',
                controller: ['$stateParams', function ($stateParams) {
                    this.orgId = $stateParams.orgId;
                    this.listId = $stateParams.listId;
                }],
                controllerAs: 'ctrl'
            }).state('attendee-lists.new', {
                url: '/:orgId/new',
                template: '<attendee-list create-new="true" organization-id="ctrl.orgId"></attendee-list>',
                controller: ['$stateParams', function ($stateParams) {
                    this.orgId = $stateParams.orgId;
                }],
                controllerAs: 'ctrl'
            });
        }])
        .component('attendeeListsContainer', {
            controller: ['$stateParams', '$state', ContainerCtrl],
            templateUrl: '../resources/js/admin/feature/attendee-list/all.html',
            bindings: {
                organizations: '<'
            }
        })
        .component('attendeeLists', {
            controller: ['AttendeeListService', ListCtrl],
            templateUrl: '../resources/js/admin/feature/attendee-list/list.html',
            bindings: {
                organizationId: '<'
            }
        }).component('attendeeList', {
            controller: ['AttendeeListService', '$timeout', 'NotificationHandler', DetailCtrl],
            templateUrl: '../resources/js/admin/feature/attendee-list/edit.html',
            bindings: {
                organizationId: '<',
                listId: '<',
                createNew: '<'
            }
        }).service('AttendeeListService', ['$http', 'HttpErrorHandler', '$q', AttendeeListService]);


    function ContainerCtrl($stateParams, $state) {
        var ctrl = this;

        ctrl.$onInit = function() {
            if(ctrl.organizations && ctrl.organizations.length > 0) {
                var orgId = ctrl.organizations[0].id;
                if($stateParams.orgId) {
                    orgId = $stateParams.orgId;
                }
                ctrl.organization = _.find(ctrl.organizations, function(org) {return org.id === orgId});
                $state.go('.all', {orgId: ctrl.organization.id});
            }
        };
    }

    function ListCtrl(AttendeeListService) {
        var ctrl = this;

        ctrl.loadLists = loadLists;

        ctrl.$onInit = function() {
            ctrl.lists = [];
            loadLists();
        };

        function loadLists() {
            ctrl.loading = true;
            AttendeeListService.loadLists(ctrl.organizationId).then(function(result) {
                ctrl.lists = result.data;
                ctrl.loading = false;
            });
        }

    }

    function DetailCtrl(AttendeeListService, $timeout, NotificationHandler, $state) {
        var ctrl = this;
        ctrl.uploadCsv = false;
        ctrl.removeItem = removeItem;
        ctrl.addItem = addItem;
        ctrl.parseFileContent = parseFile;
        ctrl.reinit = init;
        ctrl.toggleUploadCsv = function() {
            ctrl.uploadCsv = !ctrl.uploadCsv;
        };
        ctrl.submit = submit;

        ctrl.$onInit = function() {
            init();
        };

        function removeItem(index) {
            ctrl.list.items.splice(index, 1);
        }

        function addItem() {
            ctrl.list.items.push({ value: null, description: null});
        }

        function init() {
            if(ctrl.createNew) {
                ctrl.list = {
                    name: '',
                    description: '',
                    organizationId: ctrl.organizationId,
                    items: [{
                        value: null,
                        description: null
                    }]
                }
            } else {
                ctrl.loading = true;
                AttendeeListService.loadListDetail(ctrl.organizationId, ctrl.listId).then(function(result) {
                    ctrl.list = result.data;
                    ctrl.loading = false;
                });
            }
        }

        function submit(frm) {
            if(!frm.$valid) {
                return false;
            }
            AttendeeListService.createList(ctrl.organizationId, ctrl.list).then(function(result) {
                NotificationHandler.showSuccess('List '+ctrl.list.name+' successfully created');
                $state.go('attendee-list',{orgId: ctrl.organizationId, listId: result.data});
            });
        }

        function parseFile(content) {
            $timeout(function() {
                ctrl.loading = true;
            });
            var items = [];
            $timeout(function() {
                Papa.parse(content, {
                    header: false,
                    skipEmptyLines: true,
                    chunk: function(results, parser) {
                        var data = results.data;
                        _.forEach(data, function(row) {
                            if(row.length >= 1) {
                                var item = {
                                    value: row[0],
                                    description: row.length > 1 ? row[1] : null
                                };
                                items.push(item);
                            } else {
                                console.log("unable to parse row", row);
                            }
                        });
                    },
                    complete: function() {
                        ctrl.loading = false;
                        ctrl.list.items = items;
                        if(items.length > 0) {
                            ctrl.uploadCsv = false;
                        }
                    }
                });
            }, 100);
        }
    }

    function AttendeeListService($http, HttpErrorHandler, $q) {
        return {
            loadLists: function(orgId) {
                return $http.get('/admin/api/whitelist/'+orgId).error(HttpErrorHandler.handle);
            },
            loadListDetail: function(orgId, listId) {
                return $http.get('/admin/api/whitelist/'+orgId+'/detail/'+listId).error(HttpErrorHandler.handle);
            },
            createList: function(orgId, list) {
                return $http.post('/admin/api/whitelist/'+orgId+'/new', list).error(HttpErrorHandler.handle);
            },
            loadActiveList: function(eventId, categoryId) {
                var url = '/admin/api/whitelist/event/'+eventId + (angular.isDefined(categoryId) ? '/category/'+categoryId : '');
                return $http.get(url).error(HttpErrorHandler.handle);
            },
            linkTo: function(configuration) {
                if(configuration) {
                    return $http.post('/admin/api/whitelist/'+configuration.whitelistId+'/link', configuration).error(HttpErrorHandler.handle);
                } else {
                    return $q.resolve(true);
                }
            },
            unlinkFrom: function(organizationId, whitelistConfigurationId) {
                return $http['delete']('/admin/api/whitelist/'+organizationId+'/link/'+whitelistConfigurationId);
            }
        }
    }
})();