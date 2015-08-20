(function () {
    "use strict";

    angular.module('alfio-plugins', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider
                .state('plugin-log', {
                    url: '/plugin-log',
                    templateUrl: '/resources/angular-templates/admin/partials/plugin/log.html',
                    controller: PluginLogController,
                    controllerAs: 'ctrl'
                })
        }])
        .service('PluginService', PluginService);


    function PluginLogController(PluginService) {
        var ctrl = this;
        ctrl.logEntries = [];
        PluginService.loadLogEntries().success(function(result) {
            ctrl.logEntries = result;
        })
    }

    PluginLogController.prototype.$inject = ['PluginService'];

    function PluginService($http, HttpErrorHandler) {

        this.loadLogEntries = function() {
            return $http.get('/admin/api/plugin/log').error(HttpErrorHandler.handle);
        };
    }

    PluginService.prototype.$inject = ['$http', 'HttpErrorHandler'];

})();
