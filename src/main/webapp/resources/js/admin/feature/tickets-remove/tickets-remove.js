(function() {
    'use strict';

    angular.module('adminApplication').component('ticketsRemove', {
        controller: [TicketsRemoveCtrl],
        templateUrl: '../resources/js/admin/feature/tickets-remove/tickets-remove.html',
        bindings: {
            event: '<',
            ticketIds:'<',
            onCancel:'&'
        }
    });


    function TicketsRemoveCtrl() {
        var ctrl = this;
    }
})();