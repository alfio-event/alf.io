(function() {
    'use strict';

    angular.module('group', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider.state('groups', {
                url: '/group',
                template: '<groups-container organizations="ctrl.organizations"></groups-container>',
                controller: ['loadOrganizations', function (loadOrganizations) {
                    this.organizations = loadOrganizations.data;
                }],
                controllerAs: 'ctrl',
                resolve: {
                    'loadOrganizations': function(OrganizationService) {
                        return OrganizationService.getAllOrganizations();
                    }
                }
            }).state('groups.all', {
                url: '/:orgId/all',
                template: '<groups organization-id="ctrl.orgId"></groups>',
                controller: ['$stateParams', function ($stateParams) {
                    this.orgId = $stateParams.orgId;
                }],
                controllerAs: 'ctrl'
            }).state('groups.edit', {
                url: '/:orgId/edit/:groupId',
                template: '<group group-id="ctrl.groupId" organization-id="ctrl.orgId"></group>',
                controller: ['$stateParams', function ($stateParams) {
                    this.orgId = $stateParams.orgId;
                    this.groupId = $stateParams.groupId;
                }],
                controllerAs: 'ctrl'
            }).state('groups.new', {
                url: '/:orgId/new',
                template: '<group create-new="true" organization-id="ctrl.orgId"></group>',
                controller: ['$stateParams', function ($stateParams) {
                    this.orgId = $stateParams.orgId;
                }],
                controllerAs: 'ctrl'
            });
        }])
        .component('groupsContainer', {
            controller: ['$stateParams', '$state', '$scope', ContainerCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/group/all.html',
            bindings: {
                organizations: '<'
            }
        })
        .component('groups', {
            controller: ['GroupService', GroupCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/group/list.html',
            bindings: {
                organizationId: '<'
            }
        }).component('group', {
            controller: ['GroupService', '$timeout', 'NotificationHandler', '$state', '$window', DetailCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/group/edit.html',
            bindings: {
                organizationId: '<',
                groupId: '<',
                createNew: '<'
            }
        }).service('GroupService', ['$http', 'HttpErrorHandler', '$q', 'NotificationHandler', GroupService]);


    function ContainerCtrl($stateParams, $state, $scope) {
        var ctrl = this;

        $scope.$watch(function(){
            return $stateParams.orgId
        }, function(newVal, oldVal){
            var orgId = parseInt(newVal, 10);
            ctrl.organization = _.find(ctrl.organizations, function(org) {return org.id === orgId});
        });

        ctrl.$onInit = function() {
            if(ctrl.organizations && ctrl.organizations.length > 0) {
                var orgId = ctrl.organizations[0].id;
                if($stateParams.orgId) {
                    orgId = parseInt($stateParams.orgId, 10);
                }
                ctrl.organization = _.find(ctrl.organizations, function(org) {return org.id === orgId});
                if($state.current.name === 'groups') {
                    $state.go('.all', {orgId: ctrl.organization.id});
                }
            }
        };
    }

    function GroupCtrl(GroupService) {
        var ctrl = this;

        ctrl.loadGroups = loadGroups;

        ctrl.$onInit = function() {
            ctrl.groups = [];
            if (window.USER_IS_OWNER) {
                loadGroups();
            }
        };

        function loadGroups() {
            ctrl.loading = true;
            GroupService.loadGroups(ctrl.organizationId).then(function(result) {
                ctrl.groups = result.data;
                ctrl.loading = false;
            });
        }

    }

    function DetailCtrl(GroupService, $timeout, NotificationHandler, $state, $window) {
        var ctrl = this;
        ctrl.uploadCsv = false;
        ctrl.removeItem = removeItem;
        ctrl.addItem = addItem;
        ctrl.parseFileContent = parseFile;
        ctrl.reinit = init;
        ctrl.deactivateGroup = deactivateGroup;
        ctrl.deactivateMember = deactivateMember;
        ctrl.toggleUploadCsv = function() {
            ctrl.uploadCsv = !ctrl.uploadCsv;
        };
        ctrl.submit = submit;

        ctrl.$onInit = function() {
            init();
        };

        function removeItem(index) {
            ctrl.group.items.splice(index, 1);
        }

        function addItem() {
            ctrl.group.items.push({ value: null, description: null, editable: true});
        }

        function init() {
            if(ctrl.createNew) {
                ctrl.group = {
                    name: '',
                    description: '',
                    organizationId: ctrl.organizationId,
                    items: []
                };
                addItem();
            } else {
                ctrl.loading = true;
                GroupService.loadGroupDetail(ctrl.organizationId, ctrl.groupId).then(function(result) {
                    ctrl.group = result.data;
                    ctrl.loading = false;
                });
            }
        }

        function submit(frm) {
            if(!frm.$valid) {
                return false;
            }
            if(angular.isDefined(ctrl.group.id)) {
                GroupService.updateGroup(ctrl.organizationId, ctrl.group).then(function(result) {
                    NotificationHandler.showSuccess('Group '+ctrl.group.name+' successfully updated');
                    $state.go('^.all', {orgId: ctrl.organizationId});
                }, function(err) {
                    if(err.data) {
                        NotificationHandler.showError('Duplicates found: ' + err.data);
                    } else {
                        NotificationHandler.showError('Error while updating group');
                    }
                });
            } else {
                GroupService.createGroup(ctrl.organizationId, ctrl.group).then(function(result) {
                    NotificationHandler.showSuccess('Group '+ctrl.group.name+' successfully created');
                    $state.go('^.all', {orgId: ctrl.organizationId});
                });
            }
        }

        function deactivateGroup() {
            if($window.confirm('About to delete ['+ctrl.group.name+'] group. Are you sure?')) {
                GroupService.deactivateGroup(ctrl.organizationId, ctrl.group.id).then(function() {
                    NotificationHandler.showSuccess('Group '+ctrl.group.name+' successfully deleted');
                    $state.go('^.all', {orgId: ctrl.organizationId});
                });
            }
        }

        function deactivateMember(member) {
            if($window.confirm('About to remove ['+member.value+']. Are you sure?')) {
                GroupService.deactivateMember(ctrl.organizationId, ctrl.group.id, member.id).then(function() {
                    NotificationHandler.showSuccess(member.value+' successfully removed');
                    init();
                });
            }
        }

        function parseFile(content) {
            $timeout(function() {
                ctrl.loading = true;
            });
            var items = _.filter(ctrl.group.items, function(i) {return !i.editable;});
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
                                    description: row.length > 1 ? row[1] : null,
                                    editable: true
                                };
                                items.push(item);
                            } else {
                                console.log("unable to parse row", row);
                            }
                        });
                    },
                    complete: function() {
                        ctrl.loading = false;
                        ctrl.group.items = items;
                        if(items.length > 0) {
                            ctrl.uploadCsv = false;
                        }
                    }
                });
            }, 100);
        }
    }

    function GroupService($http, HttpErrorHandler, $q, NotificationHandler) {
        return {
            loadGroups: function(orgId) {
                if (!window.USER_IS_OWNER) {
                    return $q.reject('not authorized');
                }
                return $http.get('/admin/api/group/for/'+orgId).error(HttpErrorHandler.handle);
            },
            loadGroupDetail: function(orgId, groupId) {
                return $http.get('/admin/api/group/for/'+orgId+'/detail/'+groupId).error(HttpErrorHandler.handle);
            },
            createGroup: function(orgId, group) {
                return $http.post('/admin/api/group/for/'+orgId+'/new', group)
                    .error(function(body,status) {
                        if(status === 400 && body) {
                            NotificationHandler.showError("Please remove duplicates: "+body);
                        } else {
                            HttpErrorHandler.handle(body, status);
                        }
                    });
            },
            updateGroup: function(orgId, group) {
                return $http.post('/admin/api/group/for/'+orgId+'/update/'+group.id, group);
            },
            loadActiveGroup: function(eventName, categoryId) {
                var url = '/admin/api/group/for/event/'+eventName + (angular.isDefined(categoryId) ? '/category/'+categoryId : '');
                return $http.get(url).error(HttpErrorHandler.handle);
            },
            loadActiveLinks: function(eventName) {
                return $http.get('/admin/api/group/for/event/'+eventName+'/all').error(HttpErrorHandler.handle);
            },
            linkTo: function(configuration) {
                if(configuration) {
                    return $http.post('/admin/api/group/'+configuration.groupId+'/link', configuration).error(HttpErrorHandler.handle);
                } else {
                    return $q.resolve(true);
                }
            },
            unlinkFrom: function(organizationId, groupLinkId, conf) {
                return $http['delete']('/admin/api/group/for/'+organizationId+'/event/'+conf.event.id+'/link/'+groupLinkId, {
                    params: {
                        categoryId: conf.category ? conf.category.id : null
                    }
                });
            },
            deactivateGroup: function(organizationId, groupId) {
                return $http['delete']('/admin/api/group/for/'+organizationId+'/id/'+groupId);
            },
            deactivateMember: function(organizationId, groupId, memberId) {
                return $http['delete']('/admin/api/group/for/'+organizationId+'/id/'+groupId+'/member/'+memberId);
            }
        }
    }
})();