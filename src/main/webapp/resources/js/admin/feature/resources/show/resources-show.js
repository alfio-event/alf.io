(function() {
    'use strict';

    angular.module('adminApplication').component('resourcesShow', {
        bindings: {
            event:'<'
        },
        controller: ResourcesShowCtrl,
        templateUrl: '../resources/js/admin/feature/resources/show/resources-show.html'
    });


function ResourcesShowCtrl(ResourceService) {
    var ctrl = this;

    ctrl.$onInit = function() {
        ResourceService.listTemplates().then(function(res) {
            ctrl.availableTemplates = res.data;
        })
    };
}

})();