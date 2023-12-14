(function() {

'use strict';


angular.module('adminApplication').component('activeEventsList', {
    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/active-events-list/active-events-list.html',
    controller: activeEventsListCtrl

});


function activeEventsListCtrl(EventService) {
    var ctrl = this;

    ctrl.supportsOfflinePayments = supportsOfflinePayments;
    ctrl.loading = true;
    ctrl.isOwner = window.USER_IS_OWNER;

    ctrl.$onInit = function() {
        EventService.getAllActiveEvents().success(function(data) {
            ctrl.displayImage = data.length <= 10; // temporary fix issues if there are a lot of events in the list
            ctrl.events = data;
            ctrl.loading = false;
        });
    }


    function supportsOfflinePayments (event) {
        return _.contains(event.allowedPaymentProxies, 'OFFLINE');
    }
}

activeEventsListCtrl.$inject = ['EventService'];

})();