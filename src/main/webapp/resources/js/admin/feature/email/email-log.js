(function () {
    "use strict";

    angular.module('alfio-email', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider
                .state('events.single.email-log', {
                    url: '/email-log',
                    templateUrl: '/resources/angular-templates/admin/partials/email/log.html',
                    controller: EmailLogController,
                    controllerAs: 'ctrl'
                })
                .state('events.single.email-log-detail', {
                    url: '/email-log/:messageId',
                    templateUrl: '/resources/angular-templates/admin/partials/email/entry-detail.html',
                    controller: EmailDetailController,
                    controllerAs: 'detailCtrl'
                })
        }])
        .service('EmailService', EmailService)
        .filter('truncateString', function() {
            return function(string, maxLength) {
                if(!angular.isDefined(string)) {
                    return "";
                }
                var l = angular.isDefined(maxLength) ? maxLength : 50;
                return string.length > l ? (string.substring(0, l-4) + '...') : string;
            }
        });


    function EmailLogController(EmailService, $filter, $scope, getEvent) {
        var ctrl = this;
        ctrl.emailMessages = [];
        ctrl.eventName = getEvent.data.event.shortName;
        var evaluateResultsLength = function(results) {
            ctrl.emailMessages = results;
            ctrl.filteredResults = $filter('filter')(results, ctrl.searchFilter);
            ctrl.tooManyResults = (ctrl.filteredResults.length > 50);
        };
        EmailService.loadEmailLog(ctrl.eventName).success(function(results) {
            evaluateResultsLength(results);
        });

        $scope.$watch(function() {
            return ctrl.searchFilter;
        }, function(val) {
            if(angular.isDefined(val)) {
                evaluateResultsLength(ctrl.emailMessages);
            }
        });
    }

    EmailLogController.prototype.$inject = ['EmailService', '$filter', '$scope', 'EventService', '$stateParams'];

    function EmailDetailController(EmailService, $stateParams, event) {
        var self = this;
        EmailService.loadEmailDetail(event.shortName, $stateParams.messageId).success(function(result) {
            self.message = result;
        });
        this.eventName = event.shortName;
    }

    EmailDetailController.prototype.$inject = ['EmailService', '$stateParams'];

    function EmailService($http, HttpErrorHandler) {

        this.loadEmailLog = function(eventName) {
            return $http.get('/admin/api/events/'+eventName+'/email/').error(HttpErrorHandler.handle);
        };

        this.loadEmailDetail = function(eventName, messageId) {
            return $http.get('/admin/api/events/'+eventName+'/email/'+messageId).error(HttpErrorHandler.handle);
        }
    }

    EmailService.prototype.$inject = ['$http', 'HttpErrorHandler'];

})();
