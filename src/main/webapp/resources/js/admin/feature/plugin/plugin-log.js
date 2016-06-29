(function () {
    "use strict";

    angular.module('alfio-plugins', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider
                .state('events.single.plugin-log', {
                    url: '/plugin-log',
                    templateUrl: '/resources/angular-templates/admin/partials/plugin/log.html',
                    controller: PluginLogController,
                    controllerAs: 'ctrl'
                })
        }])
        .service('PluginService', PluginService);


    function PluginLogController(PluginService, EventService, $stateParams) {
        var ctrl = this;
        ctrl.logEntries = [];
        EventService.getEvent($stateParams.eventName).success(function(result) {
            ctrl.event = result.event;
        });
        PluginService.loadLogEntries($stateParams.eventName).success(function(result) {
            ctrl.logEntries = result;
        });
    }

    PluginLogController.prototype.$inject = ['PluginService', 'EventService', '$stateParams'];

    function PluginService($http, HttpErrorHandler) {

        this.loadLogEntries = function(eventName) {
            return $http.get('/admin/api/events/'+eventName+'/plugin/log').error(HttpErrorHandler.handle);
        };
    }

    PluginService.prototype.$inject = ['$http', 'HttpErrorHandler'];

})();
