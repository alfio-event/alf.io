(function() {
    'use strict';

    angular.module('adminApplication').component('reservationView', {
        bindings: {
            event:'<',
            reservationDescriptor: '<',
            onUpdate: '<'
        },
        controller: ReservationViewCtrl,
        templateUrl: '../resources/js/admin/feature/reservation/view/reservation-view.html'
    });


    function ReservationViewCtrl(AdminReservationService, $window, $stateParams) {
        var ctrl = this;

        ctrl.notification = {
            customer: {
                loading: false,
                error: false,
                success: false
            },
            attendees: {
                loading: false,
                error: false,
                success: false
            }
        };

        ctrl.displayCreationWarning = angular.isDefined($stateParams.fromCreation) && $stateParams.fromCreation;

        ctrl.hideCreationWarning = function() {
            ctrl.displayCreationWarning = false;
        };

        ctrl.$onInit = function() {
            var src = ctrl.reservationDescriptor.reservation;
            var currentURL = $window.location.href;
            ctrl.reservationUrl = (currentURL.substring(0, currentURL.indexOf('/admin')) + '/event/'+ ctrl.event.shortName + '/reservation/' + src.id+'?lang='+src.userLanguage);
            ctrl.reservation = {
                id: src.id,
                status: src.status,
                expirationStr: moment(src.validity).format('YYYY-MM-DD HH:mm'),
                expiration: {
                    date: moment(src.validity).format('YYYY-MM-DD'),
                    time: moment(src.validity).format('HH:mm')
                },
                customerData: {
                    firstName: src.firstName,
                    lastName: src.lastName,
                    emailAddress: src.email
                },
                language: src.userLanguage
            };
            var ticketsByCategory = ctrl.reservationDescriptor.ticketsByCategory;
            ctrl.reservation.ticketsInfo = ticketsByCategory.map(function(entry) {
                var category = entry.key;
                return {
                    category: {
                        existingCategoryId: category.id,
                        name: category.name
                    },
                    attendees: entry.value.map(function(ticket) {
                        return {
                            ticketId: ticket.id,
                            firstName: ticket.firstName,
                            lastName: ticket.lastName,
                            emailAddress: ticket.email
                        };
                    })
                }
            });
        };

        ctrl.update = function(frm) {
            if(frm.$valid) {
                AdminReservationService.updateReservation(ctrl.event.shortName, ctrl.reservation.id, ctrl.reservation).then(function() {
                    if(ctrl.onUpdate) {ctrl.onUpdate({eventName: ctrl.event.shortName, reservationId: ctrl.reservation.id});} else {$window.location.reload();}
                })
            }
        };

        var notify = function(customer) {
            ctrl.loading = true;
            var notifyError = function(message) {
                ctrl.loading = false;
                delete ctrl.confirmationMessage;
                ctrl.errorMessage = message || 'An unexpected error has occurred. Please retry';
            };
            AdminReservationService.notify(ctrl.event.shortName, ctrl.reservation.id, {notification: {customer: customer, attendees:(!customer)}}).then(function(r) {
                var result = r.data;
                ctrl.loading = false;
                if(result.success) {
                    ctrl.confirmationMessage = 'Success!';
                    delete ctrl.errorMessage;
                } else {
                    notifyError(result.errors.map(function (e) {
                        return e.description;
                    }).join(', '));
                }
            }, function() {
                notifyError();
            });
        };

        ctrl.hideMessages = function() {
            delete ctrl.errorMessage;
            delete ctrl.confirmationMessage;
        };

        ctrl.notifyCustomer = function() {
            notify(true);
        };

        ctrl.notifyAttendees = function() {
            notify(false);
        };

        ctrl.confirm = function() {
            AdminReservationService.confirm(ctrl.event.shortName, ctrl.reservation.id).then(function() {
                $window.location.reload();
            });
        };
    }

})();