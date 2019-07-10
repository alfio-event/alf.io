(function() {
    'use strict';

    angular.module('adminApplication').component('internationalizationEditor', {
        bindings: {
            organizationId: '<',
            eventId: '<'
        },
        controller: InternationalizationEditorCtrl,
        templateUrl: '../resources/js/admin/feature/internationalization-editor/internationalization-editor.html'
    });



    function InternationalizationEditorCtrl() {
        var ctrl = this;

        ctrl.$onInit = function() {
            console.log(this);
        }
    }

})();