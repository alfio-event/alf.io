(function() {
    'use strict';

    angular.module('adminApplication').component('reservationCreate', {
        bindings: {
            event:'<'
        },
        controller: ReservationEditCtrl,
        templateUrl: '../resources/js/admin/feature/reservation/create/reservation-create.html'
    });


    function ReservationEditCtrl(AdminReservationService) {
        var ctrl = this;

        ctrl.$onInit = function() {
            ctrl.languages = ctrl.event.contentLanguages;
            ctrl.reservation = {
                expiration: {},
                customerData: {},
                ticketsInfo: []
            };
            ctrl.addTicketInfo();
            ctrl.categories = ctrl.event.ticketCategories;
        };

        ctrl.addTicketInfo = function() {
            var ticketInfo = {
                category: {},
                attendees: [],
                addSeatsIfNotAvailable: true,
                categoryType: 'existing',
                attendeeStrategy: 'noData'
            };
            ticketInfo.parseFileContent = angular.bind(ticketInfo, internalParseFileContent);
            ctrl.reservation.ticketsInfo.push(ticketInfo);
            ctrl.addAttendee(ticketInfo);
        };

        ctrl.updateAttendeesSize = function(ticketInfo) {
            var requestedLength = ticketInfo.currentAttendeesLength;
            var length = ticketInfo.attendees.length;
            if(requestedLength > length) {
                for(var i = 0; i < (requestedLength - length); i++) {
                    ctrl.addAttendee(ticketInfo, true);
                }
            } else if(requestedLength < length) {
                ticketInfo.attendees.splice(requestedLength - 1);
            }
        };

        ctrl.addAttendee = function(ticketInfo) {
            ticketInfo.attendees.push({
                firstName: null,
                lastName: null,
                emailAddress: null
            });
            ticketInfo.currentAttendeesLength = ticketInfo.attendees.length;
        };

        ctrl.submit = function(frm) {
            if(frm.$valid) {
                AdminReservationService.createReservation(ctrl.event.shortName, ctrl.reservation).then(function(result) {
                    console.log(result.data);
                });
            }
        };

        var internalParseFileContent = function(content) {
            var lines = _.trim(content).split("\n");
            this.attendees = lines.map(function (line) {
                var data = line.split(',');
                if(data.length < 3) {
                    return {
                        firstName: 'data error',
                        lastName: null,
                        emailAddress: null
                    }
                }
                return {
                    firstName: data[0].replace(/["\\]/g, ''),
                    lastName: data[1].replace(/["\\]/g, ''),
                    emailAddress: data[2].replace(/["\\]/g, '')
                }
            });
            this.attendeeStrategy = 'fullData';
        };

    }

})();