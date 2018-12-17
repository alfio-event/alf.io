(function() {
    'use strict';

    angular.module('adminApplication').component('reservationView', {
        bindings: {
            event:'<',
            reservationDescriptor: '<',
            onUpdate: '<',
            onClose: '<',
            onConfirm: '<'
        },
        controller: ['AdminReservationService', 'EventService', '$window', '$stateParams', 'NotificationHandler', 'CountriesService', ReservationViewCtrl],
        templateUrl: '../resources/js/admin/feature/reservation/view/reservation-view.html'
    });


    function ReservationViewCtrl(AdminReservationService, EventService, $window, $stateParams, NotificationHandler, CountriesService) {
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

        ctrl.amountToRefund = null;
        ctrl.refundInProgress = false;
        ctrl.vatStatusDescriptions = {
           /*
           'NONE': 'VAT/GST not supported',
           'INCLUDED': 'Included in the sale price',
           'NOT_INCLUDED': 'Not included in the sale price',
           'INCLUDED_EXEMPT': 'VAT/GST voided',
           'NOT_INCLUDED_EXEMPT': '
            */
        };

        ctrl.displayCreationWarning = angular.isDefined($stateParams.fromCreation) && $stateParams.fromCreation;
        ctrl.regenerateBillingDocument = regenerateBillingDocument;

        ctrl.hideCreationWarning = function() {
            ctrl.displayCreationWarning = false;
        };

        ctrl.$onInit = function() {
            EventService.getAllLanguages().then(function(allLangs) {
               ctrl.allLanguages = allLangs.data;
            });
            var src = ctrl.reservationDescriptor.reservation;
            var currentURL = $window.location.href;
            ctrl.reservationUrl = (currentURL.substring(0, currentURL.indexOf('/admin')) + '/event/'+ ctrl.event.shortName + '/reservation/' + src.id+'?lang='+src.userLanguage);
            var vatApplied = null;
            if(['INCLUDED', 'NOT_INCLUDED'].indexOf(src.vatStatus) > -1) {
                vatApplied = 'Y';
            } else if(['INCLUDED_EXEMPT', 'NOT_INCLUDED_EXEMPT'].indexOf(src.vatStatus) > -1) {
                vatApplied = 'N';
            }
            ctrl.reservation = {
                id: src.id,
                status: src.status,
                showCreditCancel: src.status !== 'CANCELLED' && src.status !== 'CREDIT_NOTE_ISSUED',
                expirationStr: moment(src.validity).format('YYYY-MM-DD HH:mm'),
                expiration: {
                    date: moment(src.validity).format('YYYY-MM-DD'),
                    time: moment(src.validity).format('HH:mm')
                },
                customerData: {
                    firstName: src.firstName,
                    lastName: src.lastName,
                    emailAddress: src.email,
                    billingAddress: src.billingAddress,
                    userLanguage: src.userLanguage,
                    vatNr: src.vatNr,
                    vatCountryCode: src.vatCountryCode,
                    invoiceRequested: src.invoiceRequested
                },
                advancedBillingOptions: {
                    vatApplied: vatApplied
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

            CountriesService.getCountries().then(function(countries) {
                ctrl.countries = countries;
            });

            if(ctrl.event.visibleForCurrentUser) {
                loadPaymentInfo();
                loadAudit();
                loadBillingDocuments();
                ctrl.invalidateDocument = function(id) {
                    AdminReservationService.invalidateDocument(ctrl.event.shortName, ctrl.reservation.id, id).then(function() {
                        loadBillingDocuments();
                    });
                };
                ctrl.restoreDocument = function(id) {
                    AdminReservationService.restoreDocument(ctrl.event.shortName, ctrl.reservation.id, id).then(function() {
                        loadBillingDocuments();
                    });
                }
            }

        };

        function regenerateBillingDocument() {
            var eventName = ctrl.event.shortName;
            var reservation = ctrl.reservationDescriptor.reservation;
            var reservationId = reservation.id;
            AdminReservationService.regenerateBillingDocument(eventName, reservationId).then(function(res) {
                NotificationHandler.showSuccess("Billing Document regeneration succeeded");
                loadBillingDocuments();
            });
        }

        function loadAudit() {
            ctrl.audit = [];
            AdminReservationService.getAudit(ctrl.event.shortName, ctrl.reservationDescriptor.reservation.id).then(function(res) {
                ctrl.audit = res.data.data;
            });
        }

        function loadPaymentInfo() {
            ctrl.loadingPaymentInfo = true;
            AdminReservationService.paymentInfo(ctrl.event.shortName, ctrl.reservationDescriptor.reservation.id).then(function(res) {
                ctrl.paymentInfo = res.data.data;
                ctrl.loadingPaymentInfo = false;
            }, function() {
                ctrl.loadingPaymentInfo = false;
            });
        }

        function loadBillingDocuments() {
            ctrl.billingDocuments = {
                count: 0,
                valid: [],
                notValid: []
            };
            AdminReservationService.loadAllBillingDocuments(ctrl.event.shortName, ctrl.reservationDescriptor.reservation.id).then(function(res) {
                ctrl.billingDocuments = {
                    count: res.data.data.length,
                    valid: res.data.data.filter(function(x) { return x.status === 'VALID'; }),
                    notValid: res.data.data.filter(function(x) { return x.status === 'NOT_VALID'; })
                };
            });
        }

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
                NotificationHandler.showError(message || 'An unexpected error has occurred. Please retry');
            };
            AdminReservationService.notify(ctrl.event.shortName, ctrl.reservation.id, {notification: {customer: customer, attendees:(!customer)}}).then(function(r) {
                var result = r.data;
                ctrl.loading = false;
                if(result.success) {
                    NotificationHandler.showSuccess('Success!');
                } else {
                    notifyError(result.errors.map(function (e) {
                        return e.description;
                    }).join(', '));
                }
            }, function() {
                notifyError();
            });
        };

        ctrl.notifyCustomer = function() {
            notify(true);
        };

        ctrl.notifyAttendees = function() {
            notify(false);
        };

        ctrl.confirm = function() {
            AdminReservationService.confirm(ctrl.event.shortName, ctrl.reservation.id).then(function() {
                if(ctrl.onConfirm) {ctrl.onConfirm({eventName: ctrl.event.shortName, reservationId: ctrl.reservation.id})} else {$window.location.reload();}
            });
        };

        ctrl.cancelReservationModal = function(credit) {
            EventService.cancelReservationModal(ctrl.event, ctrl.reservation.id, credit).then(function() {
                $window.location.reload();
            });
        };

        ctrl.removeTicket = function(ticket) {
            EventService.removeTicketModal(ctrl.event, ctrl.reservation.id, ticket.ticketId).then(function() {
                //not a beautiful solution...
                $window.location.reload();
            });
        };

        ctrl.confirmRefund = function() {
            if(ctrl.amountToRefund != null && ctrl.amountToRefund.length > 0) {
                if ($window.confirm('Are you sure to refund ' + ctrl.amountToRefund + ctrl.paymentInfo.transaction.currency + ' ?')) {
                    ctrl.refundInProgress = true;
                    AdminReservationService.refund(ctrl.event.shortName, ctrl.reservation.id, ctrl.amountToRefund).then(function () {
                        ctrl.amountToRefund = null;
                        ctrl.refundInProgress = false;
                        loadPaymentInfo();
                        loadAudit();
                    })
                }
            }
        }
    }

})();