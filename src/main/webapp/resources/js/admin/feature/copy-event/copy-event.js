(function() {

'use strict';


angular.module('adminApplication').component('copyEvent', {
    templateUrl: '/resources/js/admin/feature/copy-event/copy-event.html',
    controller: copyEventCtrl
});


function copyEventCtrl(EventService, $q, $templateCache) {

    $templateCache.put('copy-event-typeahead-event.html', '<a>{{match.label.displayName}} - {{match.label.formattedBegin | formatDate}} / {{match.label.formattedEnd | formatDate}}</a>');

    var ctrl = this;

    ctrl.newEvent = {
    };

    ctrl.$onInit = function() {
        $q.all([EventService.getAllActiveEvents(), EventService.getAllExpiredEvents()]).then(function(res) {
            ctrl.events = res[0].data.concat(res[1].data);
        });
    }

    ctrl.match = function(criteria) {
        var c = criteria.toLowerCase();
        return function(item) {
            return item.shortName.toLowerCase().indexOf(c) >= 0 || item.displayName.toLowerCase().indexOf(c) >= 0;
        }
    }

    ctrl.onSelect = function($item, $model, $label) {
        ctrl.selectedEvent = $item;
    }
}

copyEventCtrl.$inject = ['EventService', '$q', '$templateCache'];

})();