(function() {
    'use strict';

    angular.module('adminApplication').component('metadataViewer', {
        bindings: {
            event: '<',
            metadata: '<',
            availableLanguages: '<',
            level: '<'
        },
        controller: MetadataViewerCtrl,
        templateUrl: '../resources/js/admin/feature/metadata-editor/metadata-viewer.html'
    }).component('metadataEditor', {
        bindings: {
            event: '<',
            metadata: '<',
            availableLanguages: '<',
            level: '<',
            ok: '<',
            cancel: '<'
        },
        controller: MetadataEditorCtrl,
        templateUrl: '../resources/js/admin/feature/metadata-editor/metadata-editor-modal.html'
    });

    function MetadataViewerCtrl($uibModal, EventService) {
        var ctrl = this;
        ctrl.$onInit = function() {
            ctrl.categoryLevel = ctrl.level === 'category';
            ctrl.languageDescription = languageDescription(ctrl.availableLanguages);
        };
        ctrl.metadataPresent = function() {
            return ctrl.metadata.onlineConfiguration
                && ctrl.metadata.onlineConfiguration.callLinks
                && ctrl.metadata.onlineConfiguration.callLinks.length > 0;
        };
        ctrl.showMetadataWarning = function() {
            return ctrl.level === 'event' && !ctrl.metadataPresent();
        };
        ctrl.showRequirements = function() {
            return ctrl.metadata && ctrl.metadata.requirementsDescriptions && Object.keys(ctrl.metadata.requirementsDescriptions).length > 0;
        };
        ctrl.edit = function() {
            $uibModal.open({
                size:'lg',
                template:'<metadata-editor event="$ctrl.event" metadata="$ctrl.metadata" available-languages="$ctrl.availableLanguages" level="$ctrl.level" cancel="$ctrl.cancel" ok="$ctrl.ok"></metadata-editor>',
                backdrop: 'static',
                controllerAs: '$ctrl',
                controller: function($scope) {
                    this.event = ctrl.event;
                    this.metadata = ctrl.metadata;
                    this.availableLanguages = ctrl.availableLanguages;
                    this.level = ctrl.level;
                    this.ok = function() {
                        $scope.$close('OK');
                    };
                    this.cancel = function() {
                        $scope.$dismiss();
                    };
                }
            }).result.then(function() {
                EventService.retrieveMetadata(ctrl.event.shortName).then(function (result) {
                    ctrl.metadata = result.data;
                })
            });
        }
    }

    MetadataViewerCtrl.$inject = ['$uibModal', 'EventService'];

    function MetadataEditorCtrl(EventService) {
        var ctrl = this;
        var syncSelectedLanguages = function() {
            ctrl.notSelectedLanguages = ctrl.availableLanguages.filter(function(lang) {
                return !angular.isDefined(ctrl.prerequisites[lang.language]);
            });
        };
        ctrl.$onInit = function() {
            var callLinks;
            if(!ctrl.metadata.onlineConfiguration) {
                callLinks = [{
                    link: '',
                    validFrom: moment(ctrl.event.formattedBegin),
                    validTo: moment(ctrl.event.formattedEnd)
                }];
            } else {
                callLinks = _.clone(ctrl.metadata.onlineConfiguration.callLinks, true);
            }
            ctrl.callLinks = callLinks.map(function(callLink) {
                return {
                    link: callLink.link,
                    validFrom: createDateTimeObject(callLink.validFrom),
                    validTo: createDateTimeObject(callLink.validTo)
                }
            });

            ctrl.prerequisites = ctrl.metadata.requirementsDescriptions || {};
            syncSelectedLanguages();
            ctrl.languageDescription = languageDescription(ctrl.availableLanguages);
        };

        ctrl.buttonClick = function(index) {
            if(index === ctrl.callLinks.length - 1) {
                ctrl.addLink();
            } else {
                ctrl.deleteLink(index);
            }
        };

        ctrl.addLink = function() {
            var last = ctrl.callLinks[ctrl.callLinks.length - 1].validTo;
            ctrl.callLinks.push({
                link: '',
                validFrom: createDateTimeObject(moment(last.date + 'T' + last.time)),
                validTo: createDateTimeObject(moment(ctrl.event.formattedEnd))
            });
        };

        ctrl.deleteLink = function(index) {
            ctrl.callLinks.splice(index, 1);
        };

        ctrl.addPrerequisite = function(language) {
            ctrl.prerequisites[language] = '';
            syncSelectedLanguages();
        };

        ctrl.save = function(form) {
            if(form.$invalid) {
                return;
            }
            delete ctrl.error;
            var metadata = {
                callLinks: ctrl.callLinks,
                requirementsDescriptions: ctrl.prerequisites
            };
            EventService.updateEventMetadata(ctrl.event.shortName, metadata).then(function() {
                ctrl.ok();
            });
        };

        function createDateTimeObject(srcDate) {
            if(!srcDate) {
                return null;
            }
            var m = moment(srcDate);
            return {
                date: m.format('YYYY-MM-DD'),
                time: m.format('HH:mm')
            };
        }

    }

    MetadataEditorCtrl.$inject = ['EventService'];

    function languageDescription(availableLanguages) {
        return function(lang) {
            var element = _.find(availableLanguages, function(l) {
                return l.language === lang;
            });
            return element ? element.displayLanguage : lang;
        };
    }

})();