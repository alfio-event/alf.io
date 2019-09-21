(function() {

'use strict';


angular.module('adminApplication').component('copyEvent', {
    templateUrl: '/resources/js/admin/feature/copy-event/copy-event.html',
    controller: copyEventCtrl,
    bindings: {
        dismiss: '&',
        onCopy:'<',
        event:'<',
        eventNameToPreselect: '<'
    }
});


function copyEventCtrl(EventService, $q, $templateCache, $filter, $http) {

    $templateCache.put('copy-event-typeahead-event.html', '<a>{{match.label.displayName}} - {{match.label.formattedBegin | formatDate}} / {{match.label.formattedEnd | formatDate}}</a>');

    var ctrl = this;

    ctrl.newEvent = {
        begin: ctrl.event.begin,
        end: ctrl.event.end
    };

    ctrl.$onInit = function() {
        $q.all([EventService.getAllActiveEvents(), EventService.getAllExpiredEvents()]).then(function(res) {
            ctrl.events = res[0].data.concat(res[1].data);
            if(ctrl.eventNameToPreselect) {
                for (var i = 0; i < ctrl.events.length; i++) {
                    if(ctrl.events[i].shortName === ctrl.eventNameToPreselect) {
                        ctrl.onSelect(ctrl.events[i]);
                        break;
                    }
                }
            }
        });
    }

    ctrl.cancel = function() {
        ctrl.dismiss();
    };

    ctrl.submit = function() {
        var selectedAdditionalFields = ctrl.additionalFields.filter(function(af) {return ctrl.selectedAdditionalFields[af.name]});
        var selectedAdditionalServices = ctrl.additionalServices.filter(function(as) {return ctrl.selectedAdditionalServices[as.id]});
        ctrl.onCopy([ctrl.newEvent, ctrl.selectedEvent, selectedAdditionalFields, selectedAdditionalServices]);
    };

    ctrl.match = function(criteria) {
        var criteriaLower = criteria.toLowerCase();
        var splitted = criteriaLower.split(/\s+/);
        return function(item) {
            for(var i = 0; i < splitted.length; i++) {
                var c = splitted[i];
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

    ctrl.onSelect = function($item) {
        ctrl.selectedEvent = $item;

        $http.get('/admin/api/event/'+$item.id+'/additional-services/').then(function(res) {
            ctrl.additionalServices = res.data;
            console.log(res.data);
            ctrl.selectedAdditionalServices = {};
            angular.forEach(res.data, function(r) {
                ctrl.selectedAdditionalServices[r.id] = true;
            });
        });

        EventService.getAdditionalFields(ctrl.selectedEvent.shortName).then(function(res) {
            ctrl.additionalFields = res.data;
            ctrl.selectedAdditionalFields = {};
            angular.forEach(res.data, function(r) {
                ctrl.selectedAdditionalFields[r.name] = true;
            });
        });
    }
}

copyEventCtrl.$inject = ['EventService', '$q', '$templateCache', '$filter', '$http'];

})();