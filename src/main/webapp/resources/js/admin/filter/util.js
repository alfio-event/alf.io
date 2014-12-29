(function () {
    "use strict";
    var filters = angular.module('utilFilters', []);

    filters.filter('printSelectedOrganization', function() {
        return function(organizations, id) {
            var existing = _.find(organizations, function(organization) {
                return id && organization.id == id;
            }) || {};
            return existing.name;
        }
    });

    filters.filter('formatDate' , function() {
        return function(dateAsString, pattern) {
            var formatPattern = angular.isDefined(pattern) ? pattern : 'DD.MM.YYYY HH:mm';
            var date = moment(dateAsString);
            if(date.isValid()) {
                return date.format(formatPattern);
            }
            return dateAsString;
        };
    });

    filters.filter('statusText', function() {
        return function(status) {
            if(!status) {
                return '';
            }
            return status.replace(/_/g, ' ').toLowerCase();
        };
    });
})();