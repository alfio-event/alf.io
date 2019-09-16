(function() {

'use strict';


angular.module('adminApplication').component('copyEvent', {
    templateUrl: '/resources/js/admin/feature/copy-event/copy-event.html',
    controller: copyEventCtrl,
    bindings: {
        dismiss: '&',
        event:'<'
    }
});


function copyEventCtrl(EventService, $q, $templateCache, $filter) {

    $templateCache.put('copy-event-typeahead-event.html', '<a>{{match.label.displayName}} - {{match.label.formattedBegin | formatDate}} / {{match.label.formattedEnd | formatDate}}</a>');

    var ctrl = this;

    ctrl.newEvent = {
        begin: ctrl.event.begin,
        end: ctrl.event.end
    };

    ctrl.$onInit = function() {
        $q.all([EventService.getAllActiveEvents(), EventService.getAllExpiredEvents()]).then(function(res) {
            ctrl.events = res[0].data.concat(res[1].data);
        });
    }

    ctrl.cancel = function() {
        ctrl.dismiss();
    };

    ctrl.match = function(criteria) {
        var criteriaLower = criteria.toLowerCase();

        var splitted = criteriaLower.split(/\s+/);

        return function(item) {
            for(var i = 0; i < splitted.length; i++) {
                var c = splitted[i];
                console.log(c);
                if(item.shortName.toLowerCase().indexOf(c) >= 0
                               || item.displayName.toLowerCase().indexOf(c) >= 0
                               || $filter('formatDate')(item.formattedBegin).indexOf(c) >= 0
                               || $filter('formatDate')(item.formattedEnd).indexOf(c) >= 0) {
                               return true;
                }
            }
            return false;
        }
    }

    ctrl.onSelect = function($item, $model, $label) {
        ctrl.selectedEvent = $item;
    }
}

copyEventCtrl.$inject = ['EventService', '$q', '$templateCache', '$filter'];

})();