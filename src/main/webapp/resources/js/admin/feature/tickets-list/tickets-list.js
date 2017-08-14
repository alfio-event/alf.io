(function() {
    'use strict';

    angular.module('adminApplication').component('ticketsList', {
        bindings: {
            event: '<',
            categoryId: '<'
        },
        controller: ['EventService', '$filter', TicketsListCtrl],
        templateUrl: '../resources/js/admin/feature/tickets-list/tickets-list.html'
    });
    
    
    
    function TicketsListCtrl(EventService, $filter) {
        var ctrl = this;

        ctrl.currentPage = 1;
        ctrl.itemsPerPage = 30;
        ctrl.statusFilter = '';
        ctrl.toSearch = '';
        ctrl.loading = false;
        ctrl.formatFullName = formatFullName;
        ctrl.updateFilteredData = updateFilteredData;
        ctrl.evaluateTicketStatus = evaluateTicketStatus;
        ctrl.truncateReservationId = truncateReservationId;
        ctrl.removeTicket = removeTicket;
        ctrl.toggleLocking = toggleLocking;

        var filter = $filter('filter');

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

        function truncateReservationId(id) {
            return id.substring(0,8).toUpperCase();
        }

        function updateFilteredData() {
            ctrl.filteredTickets = filter(ctrl.tickets, ctrl.toSearch);
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
            EventService.removeTicketModal(event, ticket.ticketReservation.id, ticket.id).then(function() {
                loadData();
            });
        }

        function loadData() {
            ctrl.loading = true;
            EventService.getTicketsForCategory(ctrl.event, ctrl.ticketCategory).then(function(res) {
                ctrl.tickets = res.data;
                updateFilteredData();
            })['finally'](function() {ctrl.loading = false;});
        }

        function toggleLocking(event, ticket, category) {
            EventService.toggleTicketLocking(event, ticket, category).then(function() {
                loadData();
            });
        }
    }
})();