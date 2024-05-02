(function() {
    'use strict';

    angular.module('adminApplication').component('ticketsList', {
        bindings: {
            event: '<',
            categoryId: '<'
        },
        controller: ['EventService', '$location', 'NotificationHandler', TicketsListCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/tickets-list/tickets-list.html'
    });
    
    
    
    function TicketsListCtrl(EventService, $location, NotificationHandler) {
        var ctrl = this;

        var currentSearch = $location.search();

        ctrl.currentPage = currentSearch.page || 1;
        ctrl.itemsPerPage = 30;
        ctrl.statusFilter = '';
        ctrl.toSearch = currentSearch.search || '';
        ctrl.loading = false;
        ctrl.formatFullName = formatFullName;
        ctrl.updateFilteredData = updateFilteredData;
        ctrl.evaluateTicketStatus = evaluateTicketStatus;
        ctrl.removeTicket = removeTicket;
        ctrl.toggleLocking = toggleLocking;

        this.$onInit = function() {
            ctrl.ticketCategory = _.find(ctrl.event.ticketCategories, function(c) {
                return ""+c.id === ctrl.categoryId;
            });
            if(ctrl.ticketCategory) {
                ctrl.otherCategories = _.filter(ctrl.event.ticketCategories, function(c) {
                    return ""+c.id !== ctrl.categoryId;
                });
                loadData();
            }
        };

        function formatFullName(r) {
            if(r.firstName && r.lastName) {
                return r.firstName + ' ' + r.lastName;
            } else {
                return r.fullName;
            }
        }

        function updateFilteredData() {
            loadData()
        }

        function evaluateTicketStatus(status) {
            var cls = 'fa ';

            switch(status) {
                case 'PENDING':
                    return cls + 'fa-warning text-warning';
                case 'ACQUIRED':
                    return cls + 'fa-bookmark text-success';
                case 'TO_BE_PAID':
                    return cls + 'fa-bookmark-o text-success';
                case 'CHECKED_IN':
                    return cls + 'fa-check-circle text-success';
                case 'CANCELLED':
                    return cls + 'fa-close text-danger';
            }

            return cls + 'fa-cog';
        }

        function removeTicket(event, ticket) {
            if (ticket.status !== 'CHECKED_IN') {
                EventService.removeTicketModal(event, ticket.ticketReservation.id, ticket.id, ticket.ticketReservation.invoiceRequested).then(function() {
                    NotificationHandler.showSuccess('Ticket removed');
                    loadData();
                });
            } else {
                NotificationHandler.showError('Cannot remove a checked-in ticket');
            }
        }

        function loadData() {
            ctrl.loading = true;

            $location.search({page: ctrl.currentPage, search: ctrl.toSearch});

            EventService.getTicketsForCategory(ctrl.event, ctrl.ticketCategory, ctrl.currentPage - 1, ctrl.toSearch).then(function(res) {
                ctrl.tickets = res.data.left;
                ctrl.totalItems = res.data.right;
            })['finally'](function() {ctrl.loading = false;});
        }

        function toggleLocking(event, ticket, category) {
            EventService.toggleTicketLocking(event, ticket, category).then(function() {
                loadData();
            });
        }
    }
})();