(function() {
    'use strict';

    angular.module('adminApplication').component('apiKeyBulkImport', {
        controller: ['$q', '$state', 'OrganizationService', 'UserService', '$timeout', BulkImportCtrl],
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/apikey-bulk-import/import.html',
        bindings: {},
        require: {
            usersCtrl: '^?users'
        }
    });


    function BulkImportCtrl($q, $state, OrganizationService, UserService, $timeout) {
        var ctrl = this;

        ctrl.cancel = cancel;
        ctrl.save = save;
        ctrl.parseFileContent = parseFile;

        ctrl.$onInit = function() {
            ctrl.apiKeys = {
            };

            ctrl.organizations = [];
            ctrl.roles = [];

            $q.all([OrganizationService.getAllOrganizations(), UserService.getAllRoles()]).then(function(results) {
                ctrl.organizations = results[0].data;
                ctrl.roles = _.filter(results[1].data, function(r) { return r.target.indexOf('API_KEY') > -1; });
            });
        };


        function cancel() {
            $state.go('^');
        }

        function save(form) {
            if(!form.$valid) {
                return;
            }

            if(ctrl.apiKeys.descriptions && ctrl.apiKeys.descriptions.length === 0) {
                return;
            }

            UserService.bulkImportApiKeys(ctrl.apiKeys).success(function() {
                if (ctrl.usersCtrl) {
                    ctrl.usersCtrl.loadUsers();
                    $state.go('^');
                }
            });
        }

        function parseFile(content) {
            $timeout(function() {
                ctrl.loading = true;
            });
            var items = [];
            $timeout(function() {
                Papa.parse(content, {
                    header: false,
                    skipEmptyLines: true,
                    chunk: function(results) {
                        var data = results.data;
                        _.forEach(data, function(row) {
                            if(row.length >= 1) {
                                items.push(row[0]);
                            } else {
                                console.log("unable to parse row", row);
                            }
                        });
                    },
                    complete: function() {
                        ctrl.loading = false;
                        ctrl.apiKeys.descriptions = items;
                    }
                });
            }, 100);
        }
    }
})();