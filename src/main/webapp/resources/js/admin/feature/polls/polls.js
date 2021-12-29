(function() {
    'use strict';

    angular.module('adminApplication')
        .service('PollService', ['$http', 'HttpErrorHandler', '$uibModal', 'NotificationHandler', PollService])
        .component('polls', {
            bindings: {
                event:'<'
            },
            controller: function() {},
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/polls/index.html'
        })
        .component('pollsList', {
            bindings: {
                event:'<'
            },
            controller: ['$uibModal', 'PollService', PollListCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/polls/polls-list.html'
        })
        .component('pollEdit', {
            bindings: {
                event:'<'
            },
            controller: ['PollService', 'EventService', '$state', '$stateParams', '$q', PollEditCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/polls/poll-edit.html'
        })
        .component('pollDetail', {
            bindings: {
                event:'<'
            },
            controller: ['PollService', 'EventService', '$q', '$stateParams', '$interval', '$uibModal', '$scope', '$window', '$state', PollDetailCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/polls/poll-detail.html'
        }).component('pollParticipants', {
            bindings: {
                event:'<'
            },
            controller: ['PollService', 'EventService', '$q', '$stateParams', 'NotificationHandler', PollParticipantsCtrl],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/polls/poll-participants.html'
        }).component('pollVotesTable', {
            bindings: {
                votes: '<',
                event: '<'
            },
            controller: function() {
                var keys = Object.keys(this.event.description);
                this.getFirstLang = function(option) {
                    if(!option) {
                        return "";
                    }
                    return option[keys[0]];
                };
            },
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/polls/poll-votes-table.html'
        });


    function PollListCtrl($uibModal, PollService) {
        var ctrl = this;
        ctrl.polls = [];
        ctrl.$onInit = function() {
            PollService.loadListForEvent(ctrl.event.shortName).then(function(resp) {
                ctrl.polls = resp.data;
                ctrl.getFirstLang = function(obj) {
                    if(!obj) {
                        return "";
                    }
                    var keys = Object.keys(obj);
                    return obj[keys[0]];
                };
            });

        }
    }

    function PollEditCtrl(PollService, EventService, $state, $stateParams, $q) {
        var ctrl = this;
        var keys;
        ctrl.$onInit = function() {
            var promises = [EventService.getSupportedLanguages()];
            if($stateParams.pollId) {
                promises.push(PollService.loadForEvent(ctrl.event.shortName, $stateParams.pollId))
            } else {
                promises.push($q.resolve({
                    data: {
                        accessRestricted: false,
                        title: {},
                        description: {},
                        order: 0,
                        options: [
                        ]
                    }
                }));
            }
            $q.all(promises).then(function(results) {
                keys = Object.keys(ctrl.event.description)
                ctrl.languages = results[0].data.filter(function(l) {
                    return _.includes(keys, l.locale);
                });
                ctrl.poll = results[1].data;
            });
        };

        ctrl.getFirstLang = function(option) {
            if(!option) {
                return "";
            }
            return option[keys[0]];
        };

        ctrl.getAdditionalTranslations = function() {
            return keys.length - 1;
        };

        ctrl.editOption = function(option, index) {
            PollService.editOption(option, ctrl.languages).then(function(newVersion) {
                ctrl.poll.options[index] = newVersion;
            });
        };

        ctrl.addOption = function() {
            PollService.editOption({}, ctrl.languages).then(function(newVersion) {
                ctrl.poll.options.push(newVersion);
            });
        };

        ctrl.save = function(form, poll) {
            if(!form.$valid || poll.options.length === 0) {
                return;
            }
            if(poll.id) {
                PollService.update(ctrl.event.shortName, poll).then(function(resp) {
                    $state.go('events.single.polls-detail', { eventName: ctrl.event.shortName, pollId: poll.id })
                });
            } else {
                PollService.createNew(ctrl.event.shortName, poll).then(function(resp) {
                    $state.go('events.single.polls-detail', { eventName: ctrl.event.shortName, pollId: resp.data })
                });
            }
        };

        ctrl.cancel = function() {
            if(ctrl.poll.id) {
                $state.go('events.single.polls-detail', { eventName: ctrl.event.shortName, pollId: ctrl.poll.id })
            } else {
                $state.go('events.single.polls-list', { eventName: ctrl.event.shortName })
            }
        }
    }

    function PollDetailCtrl(PollService, EventService, $q, $stateParams, $interval, $uibModal, $scope, $window, $state) {
        var ctrl = this;
        var timer = null;

        ctrl.$onInit = function() {
            $q.all([PollService.loadForEvent(ctrl.event.shortName, $stateParams.pollId), EventService.getSupportedLanguages()]).then(function(res) {
                var keys = Object.keys(ctrl.event.description)
                ctrl.languages = res[1].data.filter(function(l) {
                    return _.includes(keys, l.locale);
                });
                initPollObj(res[0].data);

                ctrl.getFirstLang = function(option) {
                    if(!option) {
                        return "";
                    }
                    return option[keys[0]];
                };

                ctrl.getAdditionalTranslations = function() {
                    return keys.length - 1;
                };
            });

            var initPollObj = function(poll) {
                ctrl.poll = poll;
                ctrl.draft = poll.status === 'DRAFT';
                ctrl.closed = poll.status === 'CLOSED';
                ctrl.open = poll.status === 'OPEN';

                function initTimer() {
                    timer = $interval(function () {
                        loadPollStatistics();
                    }, 2000);
                }

                function loadPollStatistics() {
                    PollService.getStatistics(ctrl.event.shortName, poll.id).then(function (result) {
                        var data = result.data;
                        var options = result.data.optionStatistics.map(function (d) {
                            var percentage = d.percentage;
                            if(percentage.endsWith(".00")) {
                                percentage = percentage.substring(0, percentage.length - 3);
                            }
                            return {
                                id: d.optionId,
                                option: _.find(ctrl.poll.options, function (o) {
                                    return o.id === d.optionId;
                                }),
                                numVotes: d.votes,
                                percentage: percentage//(d.votes / data.totalVotes) * 100.0
                            }
                        });
                        poll.options.forEach(function(o) {
                            var existing = _.find(options, function(opt) {
                                return opt.id === o.id
                            });
                            if(!existing) {
                                options.push({
                                    id: o.id,
                                    option: o,
                                    numVotes: 0,
                                    percentage: 0
                                })
                            }
                        })
                        ctrl.statistics = angular.extend({}, data, {optionStatistics: options});
                    });
                }

                if(ctrl.open) {
                    initTimer();
                }
                if(!ctrl.draft) {
                    loadPollStatistics();
                }
            };

            ctrl.changePollStatus = function() {
                var newStatus = ctrl.open ? 'CLOSED' : 'OPEN';
                PollService.updateStatus(ctrl.event.shortName, ctrl.poll.id, newStatus).then(function(res) {
                    initPollObj(res.data);
                });
            };

            ctrl.removeOption = function(option) {
                if($window.confirm('Are you sure you want to remove '+ctrl.getFirstLang(option.title)+'?\nThis operation cannot be undone.')) {
                    PollService.removeOption(ctrl.event.shortName, ctrl.poll.id, option.id).then(function(res) {
                        initPollObj(res.data);
                    });
                }
            };

            ctrl.deletePoll = function() {
                if($window.confirm('Are you sure you want to delete this Poll? This operation cannot be undone.')) {
                    PollService.deletePoll(ctrl.event.shortName, ctrl.poll.id).then(function(res) {
                        $state.go('events.single.detail', {eventName: ctrl.event.shortName});
                    });
                }
            };

            ctrl.openPresentationView = function() {
                var parentScope = $scope;
                var parent = ctrl;
                $uibModal.open({
                    size: 'lg',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/polls/poll-result-modal.html',
                    backdrop: 'static',
                    controllerAs: '$ctrl',
                    controller: function ($scope) {
                        var ctrl = this;
                        ctrl.poll = parent.poll;
                        ctrl.event = parent.event;
                        ctrl.getFirstLang = parent.getFirstLang;
                        ctrl.statistics = parent.statistics;
                        var chart;
                        var refreshStatistics = function() {
                            ctrl.participationPercentage = ctrl.statistics.participationPercentage;
                            if(ctrl.participationPercentage.endsWith(".00")) {
                                ctrl.participationPercentage = ctrl.participationPercentage.substring(0, ctrl.participationPercentage.length - 3)
                            }
                            ctrl.votesSeries = ctrl.statistics.optionStatistics.map(function(s) { return s.numVotes; });
                        };
                        refreshStatistics();
                        var data = {
                            labels: ctrl.statistics.optionStatistics.map(function(s) {
                                return parent.getFirstLang(s.option.title);
                            }),
                            series: ctrl.votesSeries
                        };
                        parentScope.$watch('$ctrl.statistics', function(newVal, oldVal) {
                            ctrl.statistics = newVal;
                            refreshStatistics();
                            if(chart) {
                                chart.update({series: ctrl.votesSeries, labels: data.labels})
                            }
                        });

                        ctrl.dismiss = function() {
                            $scope.$dismiss();
                        };

                        setTimeout(function() {
                            chart = new Chartist.Bar('.ct-chart', data, {
                                fullWidth: true,
                                axisY: {
                                    showGrid: false,
                                    showLabel: false
                                },
                                axisX: {
                                    showGrid: false,
                                    showLabel: true,
                                    labelInterpolationFnc: function (lstr, index) {
                                        return lstr+ "<br>("+ctrl.votesSeries[index]+")";
                                    }
                                },
                                distributeSeries: true,
                                chartPadding: {
                                    bottom: 40
                                },
                                height: '250px'
                            }).on('draw', function(data) {
                                if(data.type === 'bar') {
                                    data.element.attr({
                                        style: 'stroke-width: 40px'
                                    });
                                }
                            });
                        }, 100);

                    }
                });
            };
        }

        function destroyTimer() {
            if (timer) {
                $interval.cancel(timer);
            }
        }

        ctrl.$onDestroy = function() {
            destroyTimer();
        };

    }

    function PollParticipantsCtrl(PollService, EventService, $q, $stateParams, NotificationHandler) {
        var ctrl = this;
        ctrl.$onInit = function() {
            $q.all([PollService.loadForEvent(ctrl.event.shortName, $stateParams.pollId), PollService.loadParticipants(ctrl.event.shortName, $stateParams.pollId)]).then(function(res) {
                ctrl.poll = res[0].data;
                ctrl.participants = res[1].data;
                ctrl.filteredParticipants = res[1].data;
                var keys = Object.keys(ctrl.event.description)
                ctrl.getFirstLang = function(option) {
                    if(!option) {
                        return "";
                    }
                    return option[keys[0]];
                };
            });
            ctrl.addParticipants = function() {
                var load = function() {
                    PollService.loadParticipants(ctrl.event.shortName, ctrl.poll.id).then(function(res) {
                        ctrl.participants = res.data;
                        ctrl.filteredParticipants = res.data;
                    });
                };
                PollService.selectParticipants(ctrl.event.shortName, ctrl.poll.id).then(load, load);
            };
            ctrl.removeParticipant = function(participant) {
                PollService.removeParticipant(ctrl.event.shortName, ctrl.poll.id, participant.id).then(function(res) {
                    ctrl.participants = res.data;
                    ctrl.filteredParticipants = res.data;
                    NotificationHandler.showSuccess(participant.firstName + ' ' + participant.lastName + ' removed.');
                });
            };
            ctrl.updateFilteredData = function() {
                ctrl.filteredParticipants = ctrl.participants.filter(function(p) {
                    return !ctrl.toSearch || ctrl.toSearch === ''
                        || _.include(p.firstName.toLowerCase(), ctrl.toSearch.toLowerCase())
                        || _.include(p.lastName.toLowerCase(), ctrl.toSearch.toLowerCase())
                        || _.include(p.emailAddress.toLowerCase(), ctrl.toSearch.toLowerCase())
                        || _.include(p.categoryName.toLowerCase(), ctrl.toSearch.toLowerCase());
                });
            }
        }
    }

    function PollService($http, HttpErrorHandler, $uibModal, NotificationHandler) {
        var service = {
            loadListForEvent: function(eventName) {
                return $http.get('/admin/api/'+eventName+'/poll').error(HttpErrorHandler.handle);
            },
            loadForEvent: function(eventName, pollId) {
                return $http.get('/admin/api/'+eventName+'/poll/'+pollId).error(HttpErrorHandler.handle);
            },
            createNew: function(eventName, poll) {
                return $http.post('/admin/api/'+eventName+'/poll', poll).error(HttpErrorHandler.handle);
            },
            update: function(eventName, poll) {
                return $http.post('/admin/api/'+eventName+'/poll/'+poll.id, poll).error(HttpErrorHandler.handle);
            },
            updateStatus: function(eventName, pollId, newStatus) {
                return $http['put']('/admin/api/'+eventName+'/poll/'+pollId, { status: newStatus })
                    .error(HttpErrorHandler.handle);
            },
            loadParticipants: function(eventName, pollId) {
                return $http.get('/admin/api/'+eventName+'/poll/'+pollId+"/allowed").error(HttpErrorHandler.handle);
            },
            addParticipants: function(eventName, pollId, ids) {
                return $http.post('/admin/api/'+eventName+'/poll/'+pollId+'/allow', {
                    ticketIds: ids
                }).error(HttpErrorHandler.handle);
            },
            removeParticipant: function(eventName, pollId, id) {
                return $http['delete']('/admin/api/'+eventName+'/poll/'+pollId+'/allowed', { data: {
                    ticketIds: [id]
                }}).error(HttpErrorHandler.handle);
            },
            removeOption: function(eventName, pollId, optionId) {
                return $http['delete']('/admin/api/'+eventName+'/poll/'+pollId+'/option/'+optionId).error(HttpErrorHandler.handle);
            },
            deletePoll: function(eventName, pollId) {
                return $http['delete']('/admin/api/'+eventName+'/poll/'+pollId).error(HttpErrorHandler.handle);
            },
            searchParticipant: function(eventName, pollId, term) {
                return $http.get('/admin/api/'+eventName+'/poll/'+pollId+'/filter-tickets?filter='+term).error(HttpErrorHandler.handle);
            },
            getStatistics: function(eventName, pollId) {
                return $http.get('/admin/api/'+eventName+'/poll/'+pollId+'/stats').error(HttpErrorHandler.handle);
            },
            selectParticipants: function(eventName, pollId) {
                var modal = $uibModal.open({
                    size: 'lg',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/polls/select-participants-modal.html',
                    backdrop: 'static',
                    controllerAs: '$ctrl',
                    controller: function ($scope) {
                        var ctrl = this;
                        ctrl.participants = [];
                        ctrl.results = [];
                        ctrl.updateSelection = function(participant) {
                            if(participant.selected) {
                                ctrl.select(participant);
                            } else {
                                ctrl.remove(participant);
                            }
                        };
                        ctrl.toggleSelection = function(all) {
                            ctrl.results.forEach(function(p) { p.selected = all; });
                            if(all) {
                                ctrl.participants = ctrl.results.slice();
                            } else {
                                ctrl.participants = [];
                            }
                        }
                        ctrl.select = function(participant) {
                            ctrl.participants.push(participant);
                        };
                        ctrl.search = function() {
                            if(ctrl.searchTerm) {
                                ctrl.loading = true;
                                service.searchParticipant(eventName, pollId, ctrl.searchTerm).then(function(result) {
                                    ctrl.results = result.data;
                                    ctrl.loading = false;
                                }, function() {
                                    ctrl.loading = false;
                                });
                            }
                        };
                        ctrl.remove = function(participant) {
                            ctrl.participants = ctrl.participants.filter(function(p) {
                                return p.id !== participant.id
                            });
                        }
                        ctrl.save = function() {
                            if(ctrl.participants.length > 0) {
                                service.addParticipants(eventName, pollId, ctrl.participants.map(function(p) { return p.id; }))
                                    .then(function() {
                                        NotificationHandler.showSuccess(ctrl.participants.length + " participants added.");
                                        ctrl.participants = [];
                                        ctrl.results = [];
                                        ctrl.searchTerm = '';
                                        setTimeout(function() {
                                            document.getElementById('searchParticipants').focus();
                                        }, 10);

                                    })
                            } else {
                                $scope.$close();
                            }
                        };
                        ctrl.cancel = function() {
                            $scope.$close();
                        };
                    }
                });
                return modal.result;
            },
            editOption: function(option, languages) {
                var modal = $uibModal.open({
                    size: 'lg',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/polls/edit-option-modal.html',
                    backdrop: 'static',
                    controllerAs: '$ctrl',
                    controller: function ($scope) {
                        this.option = angular.copy(option);
                        this.languages = languages;
                        this.addNew = !option.id || option.title.length === 0;
                        this.editOption = function(form, obj) {
                            if(form.$valid && option.id) {
                                $scope.$close(obj);
                            } else if(form.$valid) {
                                $scope.$close(obj);
                            }
                        };
                        this.cancel = function() {
                            $scope.$dismiss();
                        };
                    }
                });
                return modal.result;
            }
        };
        return service;
    }

})();