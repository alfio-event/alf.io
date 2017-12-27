(function() {
    'use strict';

    angular.module('adminApplication').component('scriptingAddUpdate', {
        controller: ['$http', ScriptingAddUpdateCtrl],
        templateUrl: '../resources/js/admin/feature/scripting/add-update/scripting-add-update.html'
    });

    function ScriptingAddUpdateCtrl($http) {
        var ctrl = this;
    }
})();