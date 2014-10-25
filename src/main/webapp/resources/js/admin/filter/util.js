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
        return function(dateAsString) {
            var date = moment(dateAsString);
            if(date.isValid()) {
                return date.format('DD.MM.YYYY HH:mm');
            }
            return dateAsString;
        };
    });
})();