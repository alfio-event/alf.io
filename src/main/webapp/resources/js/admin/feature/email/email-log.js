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


    function EmailLogController(EmailService, $location, getEvent) {
        var ctrl = this;

        var currentSearch = $location.search();
        ctrl.currentPage = currentSearch.page || 1;
        ctrl.toSearch = currentSearch.search || '';

        ctrl.emailMessages = [];
        ctrl.eventName = getEvent.data.event.shortName;
        ctrl.itemsPerPage = 50;
        ctrl.loadData = loadData();
        ctrl.updateFilteredData = function() {
            loadData();
        }

        loadData();

        function loadData() {
            $location.search({page: ctrl.currentPage, search: ctrl.toSearch});
            EmailService.loadEmailLog(ctrl.eventName, ctrl.currentPage - 1, ctrl.toSearch).success(function(results) {
                ctrl.emailMessages = results.left;
                ctrl.totalItems = results.right;
            });
        }


    }

    EmailLogController.prototype.$inject = ['EmailService', '$location', 'EventService'];

    function EmailDetailController(EmailService, $stateParams, getEvent) {
        var self = this;
        var shortName = getEvent.data.event.shortName;
        EmailService.loadEmailDetail(shortName, $stateParams.messageId).success(function(result) {
            self.message = result;
        });
        this.eventName = shortName;
    }

    EmailDetailController.prototype.$inject = ['EmailService', '$stateParams'];

    function EmailService($http, HttpErrorHandler) {

        this.loadEmailLog = function(eventName, page, search) {
            return $http.get('/admin/api/events/'+eventName+'/email/', {params: {page: page, search: search}}).error(HttpErrorHandler.handle);
        };

        this.loadEmailDetail = function(eventName, messageId) {
            return $http.get('/admin/api/events/'+eventName+'/email/'+messageId).error(HttpErrorHandler.handle);
        }
    }

    EmailService.prototype.$inject = ['$http', 'HttpErrorHandler'];

})();
