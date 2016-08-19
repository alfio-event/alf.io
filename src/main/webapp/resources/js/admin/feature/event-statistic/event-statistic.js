(function() {
    "use strict";
    angular.module('alfio-event-statistic', ['adminServices'])
        .directive('eventOverview', [function() {
            return {
                scope: {
                    event: '='
                },
                bindToController: true,
                templateUrl: '/resources/angular-templates/admin/partials/event/statistic/event-overview.html',
                controllerAs: 'ctrl',
                controller: function() {
                    var ctrl = this;
                    ctrl.counter = 1;
                    ctrl.increment = function() {
                        ctrl.counter++;
                    };
                    ctrl.isEven = function() {
                        return ctrl.counter % 2 == 0;
                    };
                    ctrl.isOdd = function() {
                        return ctrl.counter % 2 != 0;
                    };
                    ctrl.ticketsConfirmed = ctrl.event.soldTickets + ctrl.event.checkedInTickets;
                    ctrl.allTickets = _.chain(ctrl.event.ticketCategories)
                        .map('tickets')
                        .flatten()
                        .value();
                    ctrl.confirmedTickets = _.filter(ctrl.allTickets, function(t) {
                        return t.ticketReservation.confirmationTimestamp;
                    });
                    ctrl.pendingReservations = _.chain(ctrl.allTickets)
                        .filter(function(t) {
                            return !t.ticketReservation.confirmationTimestamp;
                        })
                        .map(function(t) {
                            return t.ticketReservation.id;
                        })
                        .uniq().value().length;
                }
            }
        }])
        .directive('eventTicketsPie', [function() {
            return {
                scope: {
                    event: '='
                },
                bindToController: true,
                templateUrl: '/resources/angular-templates/admin/partials/event/statistic/tickets-pie.html',
                controllerAs: 'ctrl',
                controller: function() {},
                link: function($scope, element, attrs) {
                    var event = $scope.ctrl.event;
                    var series = _.filter([{
                        value: event.checkedInTickets,
                        name: 'Checked in',
                        className: 'slice-success',
                        meta: 'Checked in tickets  ('+event.checkedInTickets+')'
                    },{
                        value: event.soldTickets,
                        name: 'Sold',
                        className: 'slice-warning',
                        meta: 'Sold tickets ('+event.soldTickets+')'
                    },{
                        value: event.pendingTickets,
                        name: 'Reservation in progress',
                        className: 'slice-pending',
                        meta: 'Reservation in progress ('+event.pendingTickets+')'
                    },{
                        value: event.notSoldTickets,
                        name: 'Still Available',
                        className: 'slice-info',
                        meta: 'Tickets still available ('+event.notSoldTickets+')'
                    }, {
                        value: event.notAllocatedTickets,
                        name: 'Not yet allocated',
                        className: 'slice-danger',
                        meta: 'Tickets not yet allocated ('+event.notAllocatedTickets+')'
                    }, {
                        value: event.dynamicAllocation,
                        name: 'Dinamically allocated',
                        className: 'slice-default',
                        meta: 'Tickets dinamically allocated ('+event.dynamicAllocation+')'
                    }], function(s) {return s.value > 0});
                    $scope.ctrl.series = series;

                    new Chartist.Pie(element.find('.event-pie').get(0), {
                        series: series
                    }, {
                        total: _.reduce(series, function(x, y) {return x + y.value;} , 0),
                        fullWidth: true,
                        showLabel: false,
                        plugins: [
                            Chartist.plugins.ctAccessibility({
                                caption: 'Event Tickets status',
                                seriesHeader: 'Ticket Category',
                                summary: 'Represents the current status of the event. How many seats still available, how many tickets sold and so on',
                                valueTransform: function(value) {
                                    return value + ' tickets';
                                }
                            }),
                            Chartist.plugins.tooltip({
                                tooltipOffset: {
                                    x: 0,
                                    y: -20
                                },
                                appendToBody: true
                            })
                        ]
                    });
                }
            }
        }])
        .directive("eventTwoWeeksBar", ['$filter', function($filter) {
            return {
                scope: {
                    tickets: '='
                },
                bindToController: true,
                templateUrl: '/resources/angular-templates/admin/partials/event/statistic/two-weeks-bar.html',
                controller: function() {},
                controllerAs: 'ctrl',
                link: function ($scope, element, attrs) {
                    var dateFilter = $filter('formatDate');
                    var start = moment().subtract(15, 'days').startOf('day');
                    var end = moment().endOf('day');
                    var tickets = _.chain($scope.ctrl.tickets).filter(function(t) {
                        var reservationTimestamp = t.ticketReservation.confirmationTimestamp;
                        return reservationTimestamp && moment(dateFilter(reservationTimestamp, 'YYYY-MM-DD HH:mm:ss')).isBetween(start, end);
                    }).value();
                    var labels = _.range(0, end.diff(start, 'days') + 1).map(function(d) {
                        return start.clone().add(d, 'days');
                    });
                    var ticketsByDay = _.countBy(tickets, function(t) {
                        var translatedDate = dateFilter(t.ticketReservation.confirmationTimestamp, 'YYYY-MM-DD HH:mm:ss');
                        return moment(translatedDate).format('YYYY-MM-DD');
                    });

                    new Chartist.Bar(element.find('.event-tw').get(0), {
                        //labels: _.map(labels, (function(m) {return m.format('Do')})),
                        series: [_.map(labels, function(l) {
                            return {
                                value: ticketsByDay[l.format('YYYY-MM-DD')],
                                name: l.format('MMM Do'),
                                meta: l.format('MMM Do')
                            };
                        })]
                    }, {
                        ignoreEmptyValues:true,
                        showLabels: false,
                        fullWidth: true,
                        plugins: [
                            Chartist.plugins.ctAccessibility({
                                caption: 'Last two weeks trend',
                                seriesHeader: 'Day',
                                summary: 'How many tickets have been confirmed during the last two weeks',
                                valueTransform: function(value) {
                                    return value + ' tickets';
                                }
                            }),
                            Chartist.plugins.tooltip({
                                tooltipOffset: {
                                    x: 0,
                                    y: -40
                                },
                                appendToBody: true
                            })
                        ]
                    });
                }
            };
        }])
        .directive("eventAllTicketsByDay", ['$filter', function($filter) {
            return {
                scope: {
                    tickets: '='
                },
                bindToController: true,
                templateUrl: '/resources/angular-templates/admin/partials/event/statistic/tickets-by-day.html',
                controller: function() {

                },
                controllerAs: 'ctrl',
                link: function ($scope, element, attrs) {
                    var dateFilter = $filter('formatDate');
                    var ticketsConfirmationTs = _.chain($scope.ctrl.tickets)
                        .map(function(t) {
                            return moment(dateFilter(t.ticketReservation.confirmationTimestamp, 'YYYY-MM-DD HH:mm:ss') || 'invalid');
                        }).filter(function(ts) {return ts.isValid();})
                        .sortBy()
                        .map(function(ts) {return moment(ts);})
                        .value();

                        var ticketsByDay = _.chain(ticketsConfirmationTs).countBy(function(t) {
                            return t.format('YYYY-MM-DD');
                        }).value();
                    var start = ticketsConfirmationTs.length > 0 ? ticketsConfirmationTs[0] : moment().subtract(1, 'days');
                    var labelMutableStart = undefined;

                    var labels = _.range(0, Math.abs(start.diff(moment(), 'days'))).map(function(t) {return moment(start).add(t, 'd');});

                    new Chartist.Line(element.find('.event-tl').get(0), {
                        labels: labels,
                        series: [_.map(labels, function(l) {
                            var n = ticketsByDay[l.format('YYYY-MM-DD')] || null;
                            return {
                                value: n,
                                name: l.format('MMM Do YYYY'),
                                meta: l.format('MMM Do YYYY')
                            };
                        })]
                    }, {
                        ignoreEmptyValues:true,
                        showLabels: false,
                        fullWidth: true,
                        lineSmooth: Chartist.Interpolation.cardinal({
                            fillHoles: true
                        }),
                        chartPadding: {
                            right: 10
                        },
                        low: 0,
                        axisX: {
                            labelInterpolationFnc: function(l, index) {
                                if(!labelMutableStart) {
                                    labelMutableStart = moment(start);
                                    return labelMutableStart.format('MMMM');
                                }
                                if(labelMutableStart.month() != l.month()) {
                                    var sameMonth = function(m) {
                                        return l.month() === m.month();
                                    };
                                    var first = _.findIndex(labels, sameMonth);
                                    var last = _.findLastIndex(labels, sameMonth);
                                    if(index > first && index < last && index >= (last - first)/2) {
                                        return labelMutableStart.add(1, 'months').format('MMMM');
                                    }
                                }
                                return "";
                            }
                        },
                        plugins: [
                            Chartist.plugins.ctAccessibility({
                                caption: 'Today',
                                seriesHeader: 'Hour',
                                summary: 'How many tickets have been confirmed today',
                                valueTransform: function(value) {
                                    return value + ' tickets';
                                }
                            }),
                            Chartist.plugins.tooltip({
                                tooltipOffset: {
                                    x: 0,
                                    y: -40
                                },
                                appendToBody: true
                            })
                        ]
                    });
                }
            };
        }]);
})();