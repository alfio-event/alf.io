(function() {

'use strict';


angular.module('adminApplication').component('expiredEventsList', {
    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/expired-events-list/expired-events-list.html',
    controller: expiredEventsListCtrl

});


function expiredEventsListCtrl(EventService) {
    var ctrl = this;

    ctrl.$onInit = function() {
        ctrl.open = false;
    }

    ctrl.loadExpiredEvents = loadExpiredEvents;



    function loadExpiredEvents() {
        ctrl.loading = true;
        ctrl.open = true;
        EventService.getAllExpiredEvents().success(function(data) {
            ctrl.events = data;
            ctrl.loading = false;
        });
    }
}

expiredEventsListCtrl.$inject = ['EventService'];






})();