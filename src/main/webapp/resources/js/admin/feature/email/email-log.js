(function () {
    "use strict";

    angular.module('alfio-email', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider
                .state('events.email-log', {
                    url: '/:eventName/email-log',
                    templateUrl: '/resources/angular-templates/admin/partials/email/log.html',
                    controller: EmailLogController,
                    controllerAs: 'ctrl'
                })
                .state('events.email-log-detail', {
                    url: '/:eventName/email-log/:messageId',
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


    function EmailLogController(EmailService, $stateParams, $state, $filter, $scope) {
        var ctrl = this;
        ctrl.emailMessages = [];
        var evaluateResultsLength = function(results) {
            ctrl.emailMessages = results;
            ctrl.filteredResults = $filter('filter')(results, ctrl.searchFilter);
            ctrl.tooManyResults = (ctrl.filteredResults.length > 50);
        };
        EmailService.loadEmailLog($stateParams.eventName).success(function(results) {
            evaluateResultsLength(results);
        });

        $scope.$watch(function() {
            return ctrl.searchFilter;
        }, function(val) {
            if(angular.isDefined(val)) {
                evaluateResultsLength(ctrl.emailMessages);
            }
        });

        ctrl.openDetail = function(message) {
            $state.go('events.email-log-detail', {eventName: $stateParams.eventName, messageId: message.id})
        }
    }

    EmailLogController.prototype.$inject = ['EmailService','$stateParams', '$state', '$filter', '$scope'];

    function EmailDetailController(EmailService, $stateParams) {
        var self = this;
        EmailService.loadEmailDetail($stateParams.eventName, $stateParams.messageId).success(function(result) {
            self.message = result;
        });
        this.eventName = $stateParams.eventName;
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
