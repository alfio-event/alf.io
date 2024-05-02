(function() {
    'use strict';

    angular.module('adminApplication').component('reservationView', {
        bindings: {
            purchaseContext:'<',
            purchaseContextType: '<',
            reservationDescriptor: '<',
            onUpdate: '<',
            onClose: '<',
            onConfirm: '<'
        },
        controller: ['AdminReservationService', 'EventService', 'ReservationCancelService', '$window', '$stateParams', 'NotificationHandler', 'CountriesService', '$uibModal', 'ConfigurationService', ReservationViewCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/reservation/view/reservation-view.html'
    });


    function ReservationViewCtrl(AdminReservationService, EventService, ReservationCancelService, $window, $stateParams, NotificationHandler, CountriesService, $uibModal, ConfigurationService) {
        var ctrl = this;
        ctrl.displayPotentialMatch = false;
        ctrl.isOwner = window.USER_IS_OWNER; // check if user is owner _or_ admin

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

        ctrl.documentTypeDescription = {
            'RECEIPT': 'Receipt',
            'INVOICE': 'Invoice',
            'CREDIT_NOTE': 'Credit note'
        };

        var initReservationData = function() {
            var src = ctrl.reservationDescriptor.reservation;
            var configurationPromise;
            if(ctrl.purchaseContextType === 'event') {
                configurationPromise = ConfigurationService.loadSingleConfigForEvent(ctrl.purchaseContext.publicIdentifier, 'BASE_URL')
            } else {
                configurationPromise = ConfigurationService.loadSingleConfigForOrganization(ctrl.purchaseContext.organizationId, 'BASE_URL')
            }
            configurationPromise.then(function(resp) {
                var baseUrl = resp.data;
                var cleanUrl = baseUrl.endsWith('/') ? baseUrl.substring(0, baseUrl.length - 1) : baseUrl;
                ctrl.reservationUrl = cleanUrl + '/'+ctrl.purchaseContextType+'/'+ ctrl.purchaseContext.publicIdentifier + '/reservation/' + src.id+'?lang='+src.userLanguage;
            }, function() {
                var currentURL = $window.location.href;
                ctrl.reservationUrl = (currentURL.substring(0, currentURL.indexOf('/admin')) + '/'+ctrl.purchaseContextType+'/'+ ctrl.purchaseContext.publicIdentifier + '/reservation/' + src.id+'?lang='+src.userLanguage);
            })
            var vatApplied = null;
            if(['INCLUDED', 'NOT_INCLUDED'].indexOf(src.vatStatus) > -1) {
                vatApplied = 'Y';
            } else if(['INCLUDED_EXEMPT', 'NOT_INCLUDED_EXEMPT'].indexOf(src.vatStatus) > -1) {
                vatApplied = 'N';
            }
            var containsCheckedInTickets = ctrl.reservationDescriptor.ticketsByCategory.some(function(category) {
                return category.value.some(function(t) {
                    return t.status === 'CHECKED_IN';
                })
            });
            ctrl.reservation = {
                id: src.id,
                status: src.status,
                showCreditCancel: src.status !== 'CANCELLED' && src.status !== 'CREDIT_NOTE_ISSUED' && !containsCheckedInTickets,
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
                    invoicingAdditionalInfo: angular.copy(ctrl.reservationDescriptor.additionalInfo.invoicingAdditionalInfo)
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

            if(ctrl.purchaseContextType === 'event') {
                // retrieve tickets with additional data
                AdminReservationService.getTicketIdsWithAdditionalData(ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id)
                    .then(function(result) {
                       if(result.data && result.data.length > 0) {
                           ctrl.reservation.ticketsInfo.forEach(function(ticketInfo) {
                               ticketInfo.attendees.forEach(function (attendee) {
                                   attendee.hasAdditionalData = result.data.indexOf(attendee.ticketId) > -1;
                               });
                           });
                       }
                    });
            }

            ctrl.displayConfirmButton = ['PENDING', 'OFFLINE_PAYMENT', 'STUCK'].indexOf(ctrl.reservation.status) > -1;

            CountriesService.getCountries().then(function(countries) {
                ctrl.countries = countries;
            });


            if(ctrl.purchaseContextType !== 'event' || ctrl.purchaseContext.visibleForCurrentUser) {
                ctrl.purchaseContextTitle = ctrl.purchaseContext.title[Object.keys(ctrl.purchaseContext.title)[0]];
                loadEmails();
                loadPaymentInfo();
                loadAudit();
                loadBillingDocuments();
                ctrl.invalidateDocument = function(id) {
                    AdminReservationService.invalidateDocument(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id, id).then(function() {
                        loadBillingDocuments();
                    });
                };
                ctrl.restoreDocument = function(id) {
                    AdminReservationService.restoreDocument(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id, id).then(function() {
                        loadBillingDocuments();
                    });
                };
                ctrl.deleteTransaction = function() {
                    if($window.confirm('About to flag a potential match as not valid. Are you sure?')) {
                        EventService.cancelMatchingPayment(ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id, ctrl.paymentInfo.transaction.id).then(function() {
                            NotificationHandler.showSuccess("Payment discarded");
                            loadPaymentInfo();
                            loadAudit();
                        });
                    }
                };
                if(ctrl.purchaseContextType === 'subscription' && ctrl.reservation.status === 'COMPLETE') {
                    ctrl.subscriptionDetails = ctrl.reservationDescriptor.subscriptionDetails;
                    var validityFrom = null;
                    var validityTo = null;
                    if (ctrl.subscriptionDetails.subscription.formattedValidityFrom) {
                        validityFrom = {
                            date: moment(ctrl.subscriptionDetails.subscription.formattedValidityFrom).format('YYYY-MM-DD'),
                            time: moment(ctrl.subscriptionDetails.subscription.formattedValidityFrom).format('HH:mm')
                        };
                    }
                    if (ctrl.subscriptionDetails.subscription.formattedValidityTo) {
                        validityTo = {
                            date: moment(ctrl.subscriptionDetails.subscription.formattedValidityTo).format('YYYY-MM-DD'),
                            time: moment(ctrl.subscriptionDetails.subscription.formattedValidityTo).format('HH:mm')
                        };
                    }
                    ctrl.reservation.subscriptionDetails = {
                        firstName: ctrl.subscriptionDetails.subscription.firstName,
                        lastName: ctrl.subscriptionDetails.subscription.lastName,
                        email: ctrl.subscriptionDetails.subscription.email,
                        maxAllowed: ctrl.subscriptionDetails.usageDetails.total,
                        validityFrom: validityFrom,
                        validityTo: validityTo
                    };
                }
            }
        };

        ctrl.$onInit = function() {
            EventService.getAllLanguages().then(function(allLangs) {
               ctrl.allLanguages = allLangs.data;
            });
            initReservationData();
        };

        function regenerateBillingDocument() {
            var purchaseContextId = ctrl.purchaseContext.publicIdentifier;
            var reservation = ctrl.reservationDescriptor.reservation;
            var reservationId = reservation.id;
            AdminReservationService.regenerateBillingDocument(ctrl.purchaseContextType, purchaseContextId, reservationId).then(function(res) {
                NotificationHandler.showSuccess("Billing Document regeneration succeeded");
                loadBillingDocuments();
            });
        }

        function loadCheckInLog() {
            var checkInEvents = [
                'CHECK_IN',
                'MANUAL_CHECK_IN',
                'REVERT_CHECK_IN',
                'BADGE_SCAN'
            ];
            return ctrl.audit.filter(function(a) {
                return a.entityType === 'TICKET' && checkInEvents.indexOf(a.eventType) > -1;
            });
        }

        function loadAudit() {
            ctrl.audit = [];
            ctrl.checkInLog = {};
            AdminReservationService.getAudit(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservationDescriptor.reservation.id).then(function(res) {
                ctrl.audit = res.data.data;
                ctrl.checkInLog = _.groupBy(loadCheckInLog(), 'entityId');
            });
        }

        function loadPaymentInfo() {
            ctrl.loadingPaymentInfo = true;
            AdminReservationService.paymentInfo(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservationDescriptor.reservation.id).then(function(res) {
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
            AdminReservationService.loadAllBillingDocuments(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservationDescriptor.reservation.id).then(function(res) {
                ctrl.billingDocuments = {
                    count: res.data.data.length,
                    valid: res.data.data.filter(function(x) { return x.status === 'VALID'; }),
                    notValid: res.data.data.filter(function(x) { return x.status === 'NOT_VALID'; })
                };
            });
        }

        function loadEmails() {
            AdminReservationService.emailList(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservationDescriptor.reservation.id).then(function(res) {
                ctrl.emails = res.data.data;
            });
        }

        ctrl.update = function(frm) {
            if(frm.$valid) {
                AdminReservationService.updateReservation(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id, ctrl.reservation).then(function() {
                    if(ctrl.onUpdate) {ctrl.onUpdate({eventName: ctrl.purchaseContext.publicIdentifier, reservationId: ctrl.reservation.id});} else {$window.location.reload();}
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
            AdminReservationService.notify(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id, {notification: {customer: customer, attendees:(!customer)}}).then(evaluateNotificationResponse, function() {
                notifyError();
            });
        };

        ctrl.notifyCustomer = function() {
            notify(true);
        };

        function openCheckInLog(attendee) {
            $uibModal.open({
                size: 'md',
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/reservation/view/check-in-log-modal.html',
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
                            case "BADGE_SCAN":
                                return "Badge scanned";
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
                templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/reservation/view/send-ticket-email.html',
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
                    AdminReservationService.notifyAttendees(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id, ids).then(evaluateNotificationResponse, function() {
                        notifyError();
                    });
                }
            });
        };

        ctrl.confirm = function() {
            var promise;
            if(ctrl.reservation.status === 'OFFLINE_PAYMENT') {
                promise = EventService.registerPayment(ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id);
            } else {
                promise = AdminReservationService.confirm(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id);
            }
            promise.then(function() {
                if(ctrl.onConfirm) {
                    ctrl.onConfirm({eventName: ctrl.purchaseContext.publicIdentifier, reservationId: ctrl.reservation.id})
                } else {
                    $window.location.reload();
                }
            });
        };

        ctrl.cancelReservationModal = function(credit) {
            ReservationCancelService.cancelReservationModal(ctrl.purchaseContextType, ctrl.purchaseContext, ctrl.reservation, credit).then(function() {
                var message = credit ? 'Credit note generated.' : 'Reservation has been cancelled.';
                if(!credit && ctrl.reservationDescriptor.reservation.status === 'CREDIT_NOTE_ISSUED') {
                    message += ' A credit note has been generated. Please check the Billing Documents tab.';
                }
                reloadReservation(message);
            });
        };

        ctrl.openFullData = function(ticket) {
            AdminReservationService.openFullDataView(ctrl.purchaseContext, ctrl.reservation.id, ticket.uuid)
                .then(function() {
                    console.log('modal closed.');
                })
        };

        ctrl.removeTicket = function(ticket) {
            if (ticket.status !== 'CHECKED_IN') {
                EventService.removeTicketModal(ctrl.purchaseContext, ctrl.reservation.id, ticket.ticketId, ctrl.reservation.customerData.invoiceRequested).then(function(billingDocumentRequested) {
                    var message = 'Ticket has been cancelled.';
                    if(billingDocumentRequested) {
                        message += ' A credit note has been generated. Please check the Billing Documents tab.';
                    }
                    reloadReservation(message);
                });
            } else {
                NotificationHandler.showError('Cannot remove a checked-in ticket');
            }
        };

        ctrl.confirmRefund = function() {
            if(ctrl.amountToRefund != null && ctrl.amountToRefund > 0) {
                if ($window.confirm('Are you sure to refund ' + ctrl.amountToRefund + ctrl.paymentInfo.transaction.currency + ' ?')) {
                    ctrl.refundInProgress = true;
                    AdminReservationService.refund(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id, ctrl.amountToRefund).then(function () {
                        ctrl.amountToRefund = null;
                        ctrl.refundInProgress = false;
                        var message = 'Refund successful.';
                        if(ctrl.reservation.customerData.invoiceRequested) {
                            message += ' A credit note has been generated. Please check the Billing Documents tab.';
                        }
                        reloadReservation(message);
                    })
                }
            }
        }

        function reloadReservation(message) {
            AdminReservationService.load(ctrl.purchaseContextType, ctrl.purchaseContext.publicIdentifier, ctrl.reservation.id).then(function(res) {
                ctrl.reservationDescriptor = res.data.data;
                initReservationData();
                NotificationHandler.showSuccess(message);
            });
        }
    }

})();