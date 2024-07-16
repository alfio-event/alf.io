(function () {
    "use strict";

    angular.module('alfio-email', ['adminServices'])
        .config(['$stateProvider', function($stateProvider) {
            $stateProvider
                .state('events.single.email-log', {
                    url: '/email-log',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/email/log.html',
                    controller: EmailLogController,
                    controllerAs: 'ctrl'
                })
                .state('subscriptions.single.email-log', {
                    url: '/email-log',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/email/log.html',
                    controller: EmailLogController,
                    controllerAs: 'ctrl'
                })
                .state('events.single.email-log-detail', {
                    url: '/email-log/:messageId',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/email/entry-detail.html',
                    controller: EmailDetailController,
                    controllerAs: 'detailCtrl'
                })
                .state('subscriptions.single.email-log-detail', {
                    url: '/email-log/:messageId',
                    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/angular-templates/admin/partials/email/entry-detail.html',
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


    function EmailLogController(EmailService, $location, $stateParams) {
        var ctrl = this;

        var currentSearch = $location.search();
        ctrl.currentPage = currentSearch.page || 1;
        ctrl.toSearch = currentSearch.search || '';

        ctrl.emailMessages = [];
        ctrl.publicIdentifier = $stateParams.eventName || $stateParams.subscriptionId;
        ctrl.contextType = $stateParams.eventName ? 'event' : 'subscription';
        ctrl.itemsPerPage = 50;
        ctrl.loadData = loadData();
        ctrl.updateFilteredData = function() {
            loadData();
        }

        loadData();

        function loadData() {
            $location.search({page: ctrl.currentPage, search: ctrl.toSearch});
            EmailService.loadEmailLog(ctrl.contextType, ctrl.publicIdentifier, ctrl.currentPage - 1, ctrl.toSearch).success(function(results) {
                ctrl.emailMessages = results.left;
                ctrl.totalItems = results.right;
            });
        }


    }

    EmailLogController.prototype.$inject = ['EmailService', '$location', '$stateParams'];

    function EmailDetailController(EmailService, $stateParams) {
        var self = this;
        self.publicIdentifier = $stateParams.eventName || $stateParams.subscriptionId;
        self.contextType = $stateParams.eventName ? 'event' : 'subscription';
        EmailService.loadEmailDetail(self.contextType, self.publicIdentifier, $stateParams.messageId).success(function(result) {
            self.message = result;
        });
    }

    EmailDetailController.prototype.$inject = ['EmailService', '$stateParams'];

    function EmailService($http, HttpErrorHandler) {

        this.loadEmailLog = function(type, publicIdentifier, page, search) {
            return $http.get('/admin/api/'+type+'/'+publicIdentifier+'/email', {params: {page: page, search: search}}).error(HttpErrorHandler.handle);
        };

        this.loadEmailDetail = function(type, publicIdentifier, messageId) {
            return $http.get('/admin/api/'+type+'/'+publicIdentifier+'/email/'+messageId).error(HttpErrorHandler.handle);
        }
    }

    EmailService.prototype.$inject = ['$http', 'HttpErrorHandler'];

})();
