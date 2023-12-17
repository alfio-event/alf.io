(function() {
    'use strict';

    angular.module('adminApplication').component('ticketFullData', {
        controller: ['AdminReservationService', 'EventService', TicketsFullDataCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/ticket-full-data/ticket-full-data.html',
        bindings: {
            event: '<',
            reservationId: '<',
            ticketId:'<',
            canGenerateCreditNote: '<',
            onSuccess: '&',
            onCancel:'&'
        }
    });


    function TicketsFullDataCtrl(AdminReservationService) {
        var ctrl = this;
        var defaultLang = ctrl.event.contentLanguages[0].locale;
        var textFields = ['input:text', 'input:tel', 'textarea', 'vat:eu', 'input:dateOfBirth'];
        ctrl.$onInit = function() {
            AdminReservationService.getFullTicketData(ctrl.event, ctrl.reservationId, ctrl.ticketId).then(function(res) {
                ctrl.fullData = res.data;
            });
        };
        ctrl.getFieldValue = function(field) {
            if(textFields.indexOf(field.type) > -1) {
                return field.value;
            } else if (field.description[defaultLang]) {
                return field.description[defaultLang].restrictedValuesDescription[field.value] || field.value;
            }
            return field.value;
        };
        ctrl.getFieldLabel = function(field) {
            if (field.description[defaultLang]) {
                return field.description[defaultLang].label;
            }
            return field.name;
        };
    }
})();