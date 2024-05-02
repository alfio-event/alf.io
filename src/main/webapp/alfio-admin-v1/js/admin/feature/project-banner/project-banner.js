(function () {
    "use strict";

    angular.module('adminApplication')
        .component('projectBanner', {
            bindings: {
                fullBanner: '<',
                alfioVersion: '<'
            },
            controller: ['ConfigurationService', '$window', ProjectBannerController],
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/project-banner/project-banner.html'
        });

    function ProjectBannerController(ConfigurationService, $window) {
        var ctrl = this;
        ctrl.$onInit = function() {
            ctrl.dismiss = function() {
                ConfigurationService.update({
                    key: 'SHOW_PROJECT_BANNER',
                    value: 'false'
                }).then(function() {
                    ctrl.fullBanner = false;
                    $window.location.reload();
                })
            };
        };
    }
})();