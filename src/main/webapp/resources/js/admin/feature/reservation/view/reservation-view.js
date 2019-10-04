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
        controller: ['AdminReservationService', 'EventService', '$window', '$stateParams', 'NotificationHandler', 'CountriesService', '$uibModal', ReservationViewCtrl],
        templateUrl: '../resources/js/admin/feature/reservation/view/reservation-view.html'
    });


    function ReservationViewCtrl(AdminReservationService, EventService, $window, $stateParams, NotificationHandler, CountriesService, $uibModal) {
        var ctrl = this;
        ctrl.displayPotentialMatch = false;

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
           'NONE': 'VAT/GST not supported',
           'INCLUDED': 'Included in the sale price',
           'NOT_INCLUDED': 'Not included in the sale price',
           'INCLUDED_EXEMPT': 'VAT/GST voided',
           'NOT_INCLUDED_EXEMPT': 'VAT/GST voided'
        };

        ctrl.displayCreationWarning = angular.isDefined($stateParams.fromCreation) && $stateParams.fromCreation;
        ctrl.regenerateBillingDocument = regenerateBillingDocument;

        ctrl.openCheckInLog = openCheckInLog;

        ctrl.hideCreationWarning = function() {
            ctrl.displayCreationWarning = false;
        };

        ctrl.displayPaymentInfo = function() {
            return ctrl.reservation != null
                && !ctrl.displayPotentialMatch
                && ['PENDING', 'OFFLINE_PAYMENT'].indexOf(ctrl.reservation.status) === -1;
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
                    invoiceRequested: src.invoiceRequested,
                    invoicingAdditionalInfo: angular.copy(src.invoicingAdditionalInfo)
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
                            uuid: ticket.uuid,
                            status: ticket.status,
                            firstName: ticket.firstName,
                            lastName: ticket.lastName,
                            emailAddress: ticket.email
                        };
                    })
                }
            });

            ctrl.displayConfirmButton = ['PENDING', 'OFFLINE_PAYMENT', 'STUCK'].indexOf(ctrl.reservation.status) > -1;

            CountriesService.getCountries().then(function(countries) {
                ctrl.countries = countries;
            });


            if(ctrl.event.visibleForCurrentUser) {
                loadEmails();
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
                };
                ctrl.deleteTransaction = function() {
                    if($window.confirm('About to flag a potential match as not valid. Are you sure?')) {
                        EventService.cancelMatchingPayment(ctrl.event.shortName, ctrl.reservation.id, ctrl.paymentInfo.transaction.id).then(function() {
                            NotificationHandler.showSuccess("Payment discarded");
                            loadPaymentInfo();
                            loadAudit();
                        });
                    }
                };
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

        function loadCheckInLog() {
            var checkInEvents = [
                'CHECK_IN',
                'MANUAL_CHECK_IN',
                'REVERT_CHECK_IN'
            ];
            return ctrl.audit.filter(function(a) {
                return a.entityType === 'TICKET' && checkInEvents.indexOf(a.eventType) > -1;
            });
        }

        function loadAudit() {
            ctrl.audit = [];
            ctrl.checkInLog = {};
            AdminReservationService.getAudit(ctrl.event.shortName, ctrl.reservationDescriptor.reservation.id).then(function(res) {
                ctrl.audit = res.data.data;
                ctrl.checkInLog = _.groupBy(loadCheckInLog(), 'entityId');
            });
        }

        function loadPaymentInfo() {
            ctrl.loadingPaymentInfo = true;
            AdminReservationService.paymentInfo(ctrl.event.shortName, ctrl.reservationDescriptor.reservation.id).then(function(res) {
                ctrl.paymentInfo = res.data.data;
                ctrl.displayPotentialMatch = ctrl.paymentInfo.transaction && ctrl.paymentInfo.transaction.potentialMatch;
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

        function loadEmails() {
            AdminReservationService.emailList(ctrl.event.shortName, ctrl.reservationDescriptor.reservation.id).then(function(res) {
                ctrl.emails = res.data.data;
            });
        }

        ctrl.update = function(frm) {
            if(frm.$valid) {
                AdminReservationService.updateReservation(ctrl.event.shortName, ctrl.reservation.id, ctrl.reservation).then(function() {
                    if(ctrl.onUpdate) {ctrl.onUpdate({eventName: ctrl.event.shortName, reservationId: ctrl.reservation.id});} else {$window.location.reload();}
                })
            }
        };

        var notifyError = function(message) {
            ctrl.loading = false;
            NotificationHandler.showError(message || 'An unexpected error has occurred. Please retry');
        };

        var evaluateNotificationResponse = function(r) {
            var result = r.data;
            ctrl.loading = false;
            if(result.success) {
                NotificationHandler.showSuccess('Success!');
            } else {
                notifyError(result.errors.map(function (e) {
                    return e.description;
                }).join(', '));
            }
        };

        var notify = function(customer) {
            ctrl.loading = true;
            AdminReservationService.notify(ctrl.event.shortName, ctrl.reservation.id, {notification: {customer: customer, attendees:(!customer)}}).then(evaluateNotificationResponse, function() {
                notifyError();
            });
        };

        ctrl.notifyCustomer = function() {
            notify(true);
        };

        function openCheckInLog(attendee) {
            $uibModal.open({
                size: 'md',
                templateUrl: '../resources/js/admin/feature/reservation/view/check-in-log-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.cancel = function() {$scope.$dismiss('cancelled');};
                    $scope.attendee = attendee;
                    $scope.entries = ctrl.checkInLog[attendee.ticketId];

                    $scope.translateType = function(type) {
                        switch (type) {
                            case "MANUAL_CHECK_IN":
                                return "Manual check-in";
                            case "CHECK_IN":
                                return "Check-in";
                            case "REVERT_CHECK_IN":
                                return "Check-in Reverted";
                            default:
                                return type;
                        }
                    };

                }
            })
        }

        ctrl.notifyAttendees = function() {
            var m = $uibModal.open({
                size: 'lg',
                templateUrl: '../resources/js/admin/feature/reservation/view/send-ticket-email.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.cancel = function() {$scope.$dismiss('cancelled');};
                    $scope.ticketsInfo = ctrl.reservation.ticketsInfo.map(function(ti) {
                        var nTi = _.cloneDeep(ti);
                        _.forEach(nTi.attendees, function(a) { a.selected = true; });
                        return nTi;
                    });
                    $scope.sendEmail = function() {
                        var flatten = _.flatten(_.map($scope.ticketsInfo, 'attendees'));
                        $scope.$close(_.pluck(_.filter(flatten, {'selected': true}), 'ticketId'));
                    }

                    var updateSelection = function(select) {
                        $scope.ticketsInfo.forEach(function(ti) {
                            _.forEach(ti.attendees, function(a) {
                                a.selected = select;
                            });
                        });
                    };

                    $scope.selectAll = function() {
                        updateSelection(true);
                    };

                    $scope.selectNone = function() {
                        updateSelection(false);
                    };



                }
            });
            m.result.then(function(ids) {
                if(ids.length > 0) {
                    AdminReservationService.notifyAttendees(ctrl.event.shortName, ctrl.reservation.id, ids).then(evaluateNotificationResponse, function() {
                        notifyError();
                    });
                }
            });
        };

        ctrl.confirm = function() {
            var promise;
            if(ctrl.reservation.status === 'OFFLINE_PAYMENT') {
                promise = EventService.registerPayment(ctrl.event.shortName, ctrl.reservation.id);
            } else {
                promise = AdminReservationService.confirm(ctrl.event.shortName, ctrl.reservation.id);
            }
            promise.then(function() {
                if(ctrl.onConfirm) {
                    ctrl.onConfirm({eventName: ctrl.event.shortName, reservationId: ctrl.reservation.id})
                } else {
                    $window.location.reload();
                }
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