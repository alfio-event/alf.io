(function() {
    'use strict';

    // The groups admin screens (list, create, edit) have been migrated to Lit
    // (frontend/admin/src/groups); their routes are in ng-app/admin-application.js.
    // This service remains for the group linking done from the event/category configuration.
    angular.module('group', ['adminServices'])
        .service('GroupService', ['$http', 'HttpErrorHandler', '$q', GroupService]);

    function GroupService($http, HttpErrorHandler, $q) {
        return {
            loadGroups: function(orgId) {
                if (!window.USER_IS_OWNER) {
                    return $q.reject('not authorized');
                }
                return $http.get('/admin/api/group/for/'+orgId).error(HttpErrorHandler.handle);
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
            }
        }
    }
})();
