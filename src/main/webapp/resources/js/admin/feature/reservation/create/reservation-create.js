(function() {
    'use strict';

    angular.module('adminApplication').component('reservationCreate', {
        bindings: {
            event:'<'
        },
        controller: ReservationEditCtrl,
        templateUrl: '../resources/js/admin/feature/reservation/create/reservation-create.html'
    });


    function ReservationEditCtrl(AdminReservationService, $state, PriceCalculator) {
        var ctrl = this;

        var handleError = function(error) {
            if($.isArray(error)) {
                ctrl.errorMessage = error.map(function(e) {return e.description;}).join(',');
            } else {
                ctrl.errorMessage = angular.isDefined(error.description) ? error.description : error;
            }
        };

        var init = function() {
            var expiration = moment().add(1, 'days').endOf('day');
            ctrl.reservation = {
                expiration: {
                    date: expiration.format('YYYY-MM-DD'),
                    time: expiration.format('HH:mm')
                },
                customerData: {},
                ticketsInfo: []
            };
            ctrl.addTicketInfo();
        };

        ctrl.$onInit = function() {
            ctrl.languages = ctrl.event.contentLanguages;
            init();
            ctrl.categories = ctrl.event.ticketCategories;
        };

        ctrl.addTicketInfo = function() {
            var ticketInfo = {
                category: {},
                attendees: [],
                addSeatsIfNotAvailable: false,
                categoryType: 'existing',
                attendeeStrategy: 'fullData'
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

        ctrl.resetCategory = function(ticketInfo) {
            ticketInfo.category = {};
        };

        ctrl.removeTicketInfo = function(index) {
            ctrl.reservation.ticketsInfo.splice(index, 1);
        };

        ctrl.removeAttendee = function(ticketInfo, index) {
            ticketInfo.attendees.splice(index, 1);
        };

        ctrl.reinit = function() {
            init();
        };

        ctrl.calculateTotalPrice = function(price) {
            if(angular.isDefined(price)) {
                if(!ctrl.event.vatIncluded) {
                    var vat = PriceCalculator.applyPercentage(price, ctrl.event.vatPercentage);
                    return numeral(vat.add(price).format('0.00')).value()
                }
                return numeral(price).format('0.00');
            }
        };

        ctrl.submit = function(frm) {
            if(frm.$valid) {
                ctrl.loading = true;
                AdminReservationService.createReservation(ctrl.event.shortName, ctrl.reservation).then(function(r) {
                    var result = r.data;
                    if(result.success) {
                        $state.go('events.single.view-reservation', {'reservationId': result.data, 'eventName': ctrl.event.shortName});
                    } else {
                        handleError(result.errors);
                    }
                    ctrl.loading = false;
                }, function(error) {
                    handleError('Unexpected error. Please retry and/or check the logs.');
                    ctrl.loading = false;
                });
            }
        };

        ctrl.getCategoryDescription = function(ticketInfo, index) {
            if(angular.isDefined(ticketInfo.category.existingCategoryId)) {
                var filtered = ctrl.categories.filter(function(c) {return c.id == ticketInfo.category.existingCategoryId;});
                return filtered.length > 0 ? filtered[0].name : index;
            }
            return ticketInfo.category['name'] || index;
        };

        ctrl.hideMessages = function() {
            delete ctrl.errorMessage;
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