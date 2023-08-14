(function() {
    'use strict';

    angular.module('adminApplication').component('openGeneratedLink', {
        controller: ['EventService', 'NotificationHandler', OpenGeneratedLinkCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/open-generated-link/open-generated-link.html',
        bindings: {
            event: '<',
            link: '<',
            onSuccess: '&'
        }
    });

    function OpenGeneratedLinkCtrl(EventService, NotificationHandler) {
        var ctrl = this;
        ctrl.loading = true;
        ctrl.ok = ctrl.onSuccess;
        EventService.executeCapability(ctrl.event.shortName, ctrl.link.capability, {})
            .then(res => {
                ctrl.loading = false;
                ctrl.generatedLink = res.data;
            }, function(err) {
                if (err.status === 500 && err.headers('Alfio-Extension-Error-Class')) {
                    NotificationHandler.showError(err.data);
                } else {
                    HttpErrorHandler.handle(err.data, err.status);
                }
            });
        ctrl.onClick = function() {
            if (ctrl.onSuccess) {
                ctrl.onSuccess();
            }
        }
    }
})();