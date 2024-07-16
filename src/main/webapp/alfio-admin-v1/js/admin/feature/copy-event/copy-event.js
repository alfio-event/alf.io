(function() {

'use strict';


angular.module('adminApplication').component('copyEvent', {
    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/copy-event/copy-event.html',
    controller: copyEventCtrl,
    bindings: {
        dismiss: '&',
        onCopy:'<',
        event:'<',
        eventNameToPreselect: '<'
    }
});


function copyEventCtrl(EventService, AdditionalFieldsService, $q, $templateCache, $filter, $http) {

    $templateCache.put('copy-event-typeahead-event.html', '<a>{{match.label.displayName}} - {{match.label.formattedBegin | formatDate}} / {{match.label.formattedEnd | formatDate}}</a>');

    var ctrl = this;
    ctrl.loading = false;

    ctrl.newEvent = {
        begin: ctrl.event.begin,
        end: ctrl.event.end
    };

    ctrl.$onInit = function() {
        ctrl.loading = true;
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
            ctrl.loading = false;
        });
    }

    ctrl.cancel = function() {
        ctrl.dismiss();
    };

    ctrl.submit = function() {
        var selectedAdditionalFields = (ctrl.additionalFields||[]).filter(function(af) {return ctrl.selectedAdditionalFields[af.name]});
        var selectedAdditionalServices = (ctrl.additionalServices||[]).filter(function(as) {return ctrl.selectedAdditionalServices[as.id]});
        ctrl.onCopy([ctrl.newEvent, ctrl.selectedEvent, selectedAdditionalFields, selectedAdditionalServices]);
    };

    ctrl.match = function(criteria) {
        var criteriaLower = criteria.toLowerCase();
        var splitted = criteriaLower.split(/\s+/);
        return function(item) {
            for(var i = 0; i < splitted.length; i++) {
                var c = splitted[i];
                if(item.shortName.toLowerCase().indexOf(c) >= 0
                               || (item.displayName && item.displayName.toLowerCase().indexOf(c) >= 0)
                               || $filter('formatDate')(item.formattedBegin).indexOf(c) >= 0
                               || $filter('formatDate')(item.formattedEnd).indexOf(c) >= 0) {
                               return true;
                }
            }
            return false;
        }
    }

    ctrl.clearSelection = function() {
        ctrl.selectedEvent = undefined;
    }

    ctrl.onSelect = function($item) {
        ctrl.selectedEvent = $item;

        $http.get('/admin/api/event/'+$item.id+'/additional-services').then(function(res) {
            ctrl.additionalServices = res.data;
            ctrl.selectedAdditionalServices = {};
            angular.forEach(res.data, function(r) {
                ctrl.selectedAdditionalServices[r.id] = true;
            });
        });

        AdditionalFieldsService.getAdditionalFields('event', ctrl.selectedEvent.shortName).then(function(res) {
            ctrl.additionalFields = res.data;
            ctrl.selectedAdditionalFields = {};
            angular.forEach(res.data, function(r) {
                ctrl.selectedAdditionalFields[r.name] = true;
            });
        });
    }
}

copyEventCtrl.$inject = ['EventService', 'AdditionalFieldsService', '$q', '$templateCache', '$filter', '$http'];

})();