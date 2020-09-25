(function() {
    'use strict';

    angular.module('adminApplication')
        .service('PollService', ['$http', 'HttpErrorHandler', '$uibModal', PollService])
        .component('polls', {
            bindings: {
                event:'<'
            },
            controller: function() {},
            templateUrl: '../resources/js/admin/feature/polls/index.html'
        })
        .component('pollsList', {
            bindings: {
                event:'<'
            },
            controller: ['$uibModal', 'PollService', PollListCtrl],
            templateUrl: '../resources/js/admin/feature/polls/polls-list.html'
        })
        .component('pollEdit', {
            bindings: {
                event:'<'
            },
            controller: ['PollService', 'EventService', '$state', '$stateParams', '$q', PollEditCtrl],
            templateUrl: '../resources/js/admin/feature/polls/poll-edit.html'
        })
        .component('pollDetail', {
            bindings: {
                event:'<'
            },
            controller: ['PollService', 'EventService', '$q', '$stateParams', PollDetailCtrl],
            templateUrl: '../resources/js/admin/feature/polls/poll-detail.html'
        }).component('pollParticipants', {
            bindings: {
                event:'<'
            },
            controller: ['PollService', 'EventService', '$q', '$stateParams', PollParticipantsCtrl],
            templateUrl: '../resources/js/admin/feature/polls/poll-participants.html'
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
            PollService.createNew(ctrl.event.shortName, poll).then(function(resp) {
                $state.go('events.single.polls-detail', { eventName: ctrl.event.shortName, pollId: resp.data })
            });
        };
    }

    function PollDetailCtrl(PollService, EventService, $q, $stateParams) {
        var ctrl = this;
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
            };

            ctrl.changePollStatus = function() {
                var newStatus = ctrl.open ? 'CLOSED' : 'OPEN';
                PollService.updateStatus(ctrl.event.shortName, ctrl.poll.id, newStatus).then(function(res) {
                    initPollObj(res.data);
                });
            };
        }

    }

    function PollParticipantsCtrl(PollService, EventService, $q, $stateParams) {
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
                PollService.selectParticipants(ctrl.event.shortName, ctrl.poll.id).then(function(participants) {
                    PollService.addParticipants(ctrl.event.shortName, ctrl.poll.id, participants.map(function(p) { return p.id; })).then(function(res) {
                        ctrl.participants = res.data;
                    })
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

    function PollService($http, HttpErrorHandler, $uibModal) {
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
            searchParticipant: function(eventName, pollId, term) {
                return $http.get('/admin/api/'+eventName+'/poll/'+pollId+'/filter-tickets?filter='+term).error(HttpErrorHandler.handle);
            },
            selectParticipants: function(eventName, pollId) {
                var modal = $uibModal.open({
                    size: 'lg',
                    templateUrl: '../resources/js/admin/feature/polls/select-participants-modal.html',
                    backdrop: 'static',
                    controllerAs: '$ctrl',
                    controller: function ($scope) {
                        var ctrl = this;
                        ctrl.participants = [];
                        ctrl.results = [];
                        ctrl.updateSelection = function(participant) {
                            if(participant.selected) {
                                ctrl.remove(participant);
                            } else {
                                ctrl.add(participant);
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
                            ctrl.participants = _.remove(ctrl.participants, function(p) {
                                return p === participant;
                            });
                        }
                        ctrl.save = function() {
                            if(ctrl.participants.length > 0) {
                                $scope.$close(ctrl.participants);
                            } else {
                                $scope.$dismiss();
                            }
                        };
                        ctrl.cancel = function() {
                            $scope.$dismiss();
                        };
                    }
                });
                return modal.result;
            },
            editOption: function(option, languages) {
                var modal = $uibModal.open({
                    size: 'lg',
                    templateUrl: '../resources/js/admin/feature/polls/edit-option-modal.html',
                    backdrop: 'static',
                    controllerAs: '$ctrl',
                    controller: function ($scope) {
                        this.option = angular.copy(option);
                        this.languages = languages;
                        this.addNew = !option.id || option.title.length === 0;
                        this.editOption = function(form, obj) {
                            if(form.$valid && option.id) {
                                console.log('TODO update');
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