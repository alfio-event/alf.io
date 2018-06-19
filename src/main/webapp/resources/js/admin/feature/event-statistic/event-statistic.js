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
                        return ctrl.counter % 2 === 0;
                    };
                    ctrl.isOdd = function() {
                        return ctrl.counter % 2 !== 0;
                    };
                    ctrl.ticketsConfirmed = ctrl.event.soldTickets + ctrl.event.checkedInTickets;

                    // FIXME remove
                    ctrl.allTickets = [];
                    //

                    ctrl.pendingReservations = ctrl.event.pendingTickets;
                }
            }
        }])
        .directive('eventTicketsPie', ['$rootScope', function($rootScope) {
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
                    var buildSeries = function(event) {
                        return _.filter([{
                            value: event.checkedInTickets,
                            name: 'Checked in',
                            className: 'slice-success',
                            meta: 'Checked in ('+event.checkedInTickets+')'
                        },{
                            value: event.soldTickets,
                            name: 'Sold',
                            className: 'slice-warning',
                            meta: 'Sold ('+event.soldTickets+')'
                        },{
                            value: event.pendingTickets,
                            name: 'Reservation in progress',
                            className: 'slice-pending',
                            meta: 'Reservation in progress ('+event.pendingTickets+')'
                        },{
                            value: event.notSoldTickets,
                            name: 'Reserved for categories',
                            className: 'slice-info',
                            meta: 'Reserved for categories ('+event.notSoldTickets+')'
                        }, {
                            value: event.notAllocatedTickets,
                            name: 'Not yet allocated',
                            className: 'slice-danger',
                            meta: 'Not yet allocated ('+event.notAllocatedTickets+')'
                        }, {
                            value: event.dynamicAllocation,
                            name: 'Available',
                            className: 'slice-default',
                            meta: 'Available ('+event.dynamicAllocation+')'
                        }], function(s) {return s.value > 0});
                    };
                    var buildOptions = function(series) {
                        return {
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
                        };
                    };
                    var series = buildSeries(event);
                    $scope.ctrl.series = series;

                    var pie = new Chartist.Pie(element.find('.event-pie').get(0), {
                        series: series
                    }, buildOptions(series));
                    var clearListener = $rootScope.$on('ReloadEventPie', function(e, event) {
                        var s = buildSeries(event);
                        pie.update({series: s}, {total: _.reduce(s, function(x, y) {return x + y.value;} , 0)}, true);
                        $scope.ctrl.series = s;
                    });
                    $scope.$on('$destroy', clearListener);
                }
            }
        }])
        .directive("eventTwoWeeksBar", ['$filter', 'EventService', 'SeriesSplitter', '$window', function($filter, EventService, SeriesSplitter, $window) {
            return {
                scope: {
                    tickets: '=',
                    event: '='
                },
                bindToController: true,
                templateUrl: '/resources/angular-templates/admin/partials/event/statistic/two-weeks-bar.html',
                controller: function($element) {
                    var element = $element;
                    var ctrl = this;

                    var twoWeeksAgo = moment().subtract(15, 'days').format('YYYY-MM-DD');
                    var now = moment().format('YYYY-MM-DD');

                    EventService.getTicketsStatistics(ctrl.event.shortName, twoWeeksAgo, now).then(function(res) {

                        var split = SeriesSplitter.split(res.data);
                        var labels = split.labels;
                        var soldSeries = split.soldSeries;
                        var reservedSeries = split.reservedSeries;
                        var mobileView = $window.matchMedia('screen and (max-width: 991px)').matches;

                        new Chartist.Bar(element.find('.event-tw').get(0), {
                            labels: !mobileView ? _.map(labels, function(l) {return moment(l).format('MMM D')}) : [],
                            series: [reservedSeries, soldSeries]
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
                    });
                },
                controllerAs: 'ctrl',
                link: function () {}
            };
        }])
        .directive("eventAllTicketsByDay", ['$filter', 'EventService', 'SeriesSplitter', function($filter, EventService, SeriesSplitter) {
            return {
                scope: {
                    tickets: '=',
                    event: '='
                },
                bindToController: true,
                templateUrl: '/resources/angular-templates/admin/partials/event/statistic/tickets-by-day.html',
                controller: function($element) {
                    var element = $element;
                    var ctrl = this;

                    EventService.getTicketsStatistics(ctrl.event.shortName).then(function(res) {
                        var split = SeriesSplitter.split(res.data);
                        var labels = split.labels;
                        var soldSeries = split.soldSeries;
                        var reservedSeries = split.reservedSeries;
                        var start = split.start;
                        var labelMutableStart = undefined;


                        new Chartist.Line(element.find('.event-tl').get(0), {
                            labels: labels,
                            series: [reservedSeries, soldSeries]
                        }, {
                            ignoreEmptyValues:true,
                            showLabels: false,
                            fullWidth: true,
                            showArea: false,
                            lineSmooth: Chartist.Interpolation.cardinal({
                                fillHoles: true
                            }),
                            chartPadding: {
                                right: 10
                            },
                            low: 0,
                            axisX: {
                                labelInterpolationFnc: function(lStr, index) {
                                    var l = moment.utc(lStr);
                                    if(!labelMutableStart) {
                                        labelMutableStart = moment.utc(start);
                                        return labelMutableStart.format('MMMM');
                                    }
                                    if(labelMutableStart.month() !== l.month()) {
                                        var sameMonth = function(m) {
                                            return l.month() === moment.utc(m).month();
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
                                    caption: 'So Far',
                                    seriesHeader: 'Date',
                                    summary: 'How many tickets have been reserved and confirmed so far',
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

                    });
                },
                controllerAs: 'ctrl',
                link: function () {}
            };
        }])
        .service('SeriesSplitter', function() {
            return {
                split: function(stats) {
                    var soldByDay = stats.sold;
                    var reservedByDay = stats.reserved;
                    var labels = _.chain(soldByDay)
                        .concat(reservedByDay)
                        .map('date')
                        .sortBy()
                        .uniq(true)
                        .value();

                    function generateSeries(byDay, labels) {
                        return _.map(labels, function(label) {
                            var stat = _.find(byDay, function(d) { return d.date === label; });
                            var count = 0;
                            if(stat) {
                                count = stat.count;
                            }
                            var mDate = moment.utc(label);
                            return {
                                value: count,
                                name: mDate.format('MMM Do YYYY'),
                                meta: mDate.format('MMM Do YYYY')
                            };
                        });
                    }

                    return {
                        labels: labels,
                        soldSeries: generateSeries(soldByDay, labels),
                        reservedSeries: generateSeries(reservedByDay, labels),
                        start: soldByDay.length > 0 ? moment.utc(soldByDay[0].date) : undefined
                    };
                }
            }
        });
})();
