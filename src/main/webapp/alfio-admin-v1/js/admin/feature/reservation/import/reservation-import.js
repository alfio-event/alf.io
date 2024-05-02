(function() {
    'use strict';

    angular.module('adminApplication').component('reservationImport', {
        bindings: {
            event:'<',
            onCancel:'<',
            onCreation:'<',
            fastCreation: '<'
        },
        controller: ReservationImportCtrl,
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/reservation/import/reservation-import.html'
    }).component('reservationImportProgress', {
        bindings: {
            event: '<'
        },
        controller: ImportProgressCtrl,
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/reservation/import/import-progress.html'

    });

    function ImportProgressCtrl(AdminImportService, $interval, $stateParams) {
        var ctrl = this;
        ctrl.loading = true;
        ctrl.total = 0;
        ctrl.processed = 0;
        ctrl.$onInit = function() {
            var interval = $interval(function() {
                AdminImportService.retrieveStats(ctrl.event.shortName, $stateParams.requestId)
                    .then(function(res) {
                        var data = res.data.data;
                        ctrl.countSuccess = data.countSuccess;
                        ctrl.countError = data.countError;
                        ctrl.processed = data.countSuccess + data.countError;
                        ctrl.total = data.countSuccess + data.countError + data.countPending;
                        if(ctrl.countSuccess + ctrl.countError === ctrl.total) {
                            $interval.cancel(interval);
                            ctrl.success = ctrl.countError === 0;
                        }
                        ctrl.loading = false;
                    });
            }, 1000);

        };
    }

    function ReservationImportCtrl(AdminImportService, PriceCalculator, $timeout, $state, ConfigurationService, $q) {
        var ctrl = this;

        var handleError = function(error) {
            if($.isArray(error)) {
                ctrl.errorMessage = error.map(function(e) {return e.description;}).join(',');
            } else {
                ctrl.errorMessage = angular.isDefined(error.description) ? error.description : error;
            }
        };

        var init = function() {
            ctrl.createSingleReservations = false;
            ctrl.reassignmentForbidden = false;
            ctrl.singleReservationsAllowed = true;
            var expiration = moment().add(1, 'days').endOf('day');
            ctrl.reservation = {
                expiration: {
                    date: expiration.format('YYYY-MM-DD'),
                    time: expiration.format('HH:mm')
                },
                customerData: {},
                ticketsInfo: [],
                notification: {
                    customer: false,
                    attendees: false
                }
            };
            ctrl.addTicketInfo();
        };

        ctrl.$onInit = function() {
            ctrl.languages = ctrl.event.contentLanguages;
            init();

            $q.all([ConfigurationService.loadSingleConfigForEvent(ctrl.event.shortName, 'SEND_TICKETS_AFTER_IMPORT_ATTENDEE'), ConfigurationService.loadSingleConfigForEvent(ctrl.event.shortName, 'CREATE_RESERVATION_FOR_EACH_IMPORTED_ATTENDEE')])
                .then(function(result) {
                    ctrl.reservation.notification.attendees = ('true' === result[0].data); //default is false
                    ctrl.createSingleReservations = ('true' === result[1].data); //default is false
                });
            if(ctrl.languages.length === 1) {
                ctrl.reservation.language = ctrl.languages[0].locale;
            }

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
            ctrl.singleReservationsAllowed = ctrl.reservation.ticketsInfo.every(function(ti) {
                return ti.categoryType === 'existing';
            });
            if(!ctrl.singleReservationsAllowed) {
                ctrl.createSingleReservations = false;
            }
        };

        ctrl.removeTicketInfo = function(index) {
            ctrl.reservation.ticketsInfo.splice(index, 1);
        };

        ctrl.removeAttendee = function(ticketInfo, index) {
            ticketInfo.attendees.splice(index, 1);
        };

        if(ctrl.onCancel) {
            ctrl.reinit = ctrl.onCancel;
        } else {
            ctrl.reinit = function() {init();};
        }



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

                if(ctrl.fastCreation) {

                    var firstTicket = ctrl.reservation.ticketsInfo[0].attendees[0];
                    ctrl.reservation.customerData.firstName = firstTicket.firstName;
                    ctrl.reservation.customerData.lastName = firstTicket.lastName;
                    ctrl.reservation.customerData.emailAddress = firstTicket.emailAddress;
                }


                _.forEach(ctrl.reservation.ticketsInfo, function(ti) {
                    _.forEach(ti.attendees, function(attendee) {
                        attendee.reassignmentForbidden = ctrl.reassignmentForbidden;
                    });
                });

                AdminImportService.importAttendees(ctrl.event.shortName, ctrl.reservation, ctrl.createSingleReservations).then(function(r) {
                    var result = r.data;
                    if(result.success) {
                        $state.go('events.single.import-status', {eventName: ctrl.event.shortName, requestId: result.data});
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
                var filtered = ctrl.categories.filter(function(c) {return c.id === ticketInfo.category.existingCategoryId;});
                return filtered.length > 0 ? filtered[0].name : index;
            }
            return ticketInfo.category['name'] || index;
        };

        ctrl.hideMessages = function() {
            delete ctrl.errorMessage;
        };

        ctrl.countParsedAttendees = function(ticketsInfo) {
            var count = 0;
            if(ticketsInfo) {
                for(var i = 0; i < ticketsInfo.length; i++) {
                    if(ticketsInfo[i] && ticketsInfo[i].attendees) {
                        count += ticketsInfo[i].attendees.length;
                    }
                }
            }
            return count;
        }

        var internalParseFileContent = function(content) {
            var self = this;
            self.attendees = [];
            $timeout(function() {
                ctrl.parsing = true;
            });
            var attendees = [];
            $timeout(function() {
                Papa.parse(content, {
                    header: true,
                    skipEmptyLines: true,
                    chunk: function(results, parser) {
                        var data = results.data;
                        var header = results.meta.fields;
                        _.forEach(data, function(row) {
                            if(header.length >= 4) {
                                var attendee = {
                                    firstName: row[header[0]],
                                    lastName: row[header[1]],
                                    emailAddress: row[header[2]],
                                    language: row[header[3]]
                                };
                                if(header.length > 4) {
                                    var map = {};
                                    for(var i = 4; i < header.length; i++) {
                                        var key = header[i];
                                        if(key.toLowerCase() === 'reference') {
                                            attendee.reference = row[key];
                                        } else {
                                            map[key] = [row[key]];
                                        }
                                    }
                                    attendee.additionalInfo = map;
                                }
                                attendees.push(attendee);
                            } else {
                                console.log("unable to parse row", row);
                            }

                        })

                    },
                    complete: function() {
                        ctrl.parsing = false;
                        self.attendees = attendees;
                        if(attendees.length > 100) {
                            ctrl.createSingleReservations = true;
                        }
                    }
                });
            }, 100);
        };

    }

})();