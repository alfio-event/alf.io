(function() {
    'use strict';

    angular.module('adminApplication').component('extensionLog', {
        controller: ['$http', ExtensionLogCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/extension/log/extension-log.html',
        bindings: {
        }
    });



    function ExtensionLogCtrl($http) {

        var ctrl = this;
        ctrl.itemsPerPage = 50;
        ctrl.currentPage = 1;

        ctrl.$onInit = function() {
            loadLogs();
        }

        ctrl.updateFilteredData = updateFilteredData;

        function loadLogs() {
            $http.get('/admin/api/extensions/log', {params:{page: ctrl.currentPage -1}}).then(function(res) {
                ctrl.logs = res.data.left;
                ctrl.totalItems = res.data.right;
            })
        }
        
        function updateFilteredData() {
            loadLogs();
        }
    }

})();