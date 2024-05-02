(function() {
    'use strict';

    angular.module('adminApplication').component('metadataViewer', {
        transclude: true,
        bindings: {
            event: '<',
            metadata: '<',
            availableLanguages: '<',
            level: '<',
            parentId: '<'
        },
        controller: MetadataViewerCtrl,
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/metadata-editor/metadata-viewer.html'
    }).component('metadataEditor', {
        bindings: {
            event: '<',
            metadata: '<',
            availableLanguages: '<',
            level: '<',
            ok: '<',
            cancel: '<',
            parentId: '<'
        },
        controller: MetadataEditorCtrl,
        templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/metadata-editor/metadata-editor-modal.html'
    });

    var ONLINE_EVENT_CAPABILITIES = [
        { id: 'GENERATE_MEETING_LINK', text: 'Generate Meeting link' },
        { id: 'CREATE_VIRTUAL_ROOM', text: 'Init Virtual Room' },
        { id: 'CREATE_GUEST_LINK', text: 'Create Join Link for Guest' },
        { id: 'CREATE_ANONYMOUS_GUEST_LINK', text: 'Create Join Link for Anonymous Guest' }
    ];

    function MetadataViewerCtrl($uibModal, EventService, NotificationHandler, $q, HttpErrorHandler) {
        var ctrl = this;
        function executeCapability(capability, event) {
            event.preventDefault();
            var promise;
            if (capability.id === 'CREATE_GUEST_LINK') {
                promise = requestGuestData($uibModal);
            } else {
                promise = $q.resolve({});
            }
            promise.then(function(params) {
                params.selector = capability.selector;
                EventService.executeCapability(ctrl.event.shortName, capability.id, params)
                    .then(res => {
                        if (capability.id === 'GENERATE_MEETING_LINK') {
                            window.location.reload();
                        } else {
                            ctrl.capabilityResult = res.data;
                        }
                    }, function(err) {
                        if (err.status === 500 && err.headers('Alfio-Extension-Error-Class')) {
                            NotificationHandler.showError(err.data);
                        } else {
                            HttpErrorHandler.handle(err.data, err.status);
                        }
                    });
            });
        }
        ctrl.$onInit = function() {
            ctrl.categoryLevel = ctrl.level === 'category';
            ctrl.languageDescription = languageDescription(ctrl.availableLanguages);
            if(!ctrl.categoryLevel && ctrl.event.supportedCapabilities && ctrl.event.supportedCapabilities.length > 0) {
                ctrl.capabilities = ctrl.event.supportedCapabilities
                    .flatMap(function(supportedCapability) {
                        if (supportedCapability.details && supportedCapability.details.length > 0) {
                            return supportedCapability.details.map(function(details) {
                                return {
                                    id: supportedCapability.capability,
                                    text: details.label,
                                    selector: details.selector
                                };
                            });
                        }
                        var staticCapability = ONLINE_EVENT_CAPABILITIES.find(function(c) { return c.id === supportedCapability.capability });
                        if (staticCapability) {
                            return [{
                                id: supportedCapability.capability,
                                text: staticCapability.text,
                                selector: ''
                            }];
                        }
                        return [];

                    });
                ctrl.showCapabilitiesMenu = ctrl.capabilities.length > 0;
            }
            ctrl.normalLayout = !ctrl.categoryLevel && !ctrl.showCapabilitiesMenu;
            if(ctrl.showCapabilitiesMenu) {
                ctrl.capabilitySelected = executeCapability;
                ctrl.copyCapabilityResult = function() {
                    var listener = function(clipboardEvent) {
                        var clipboard = clipboardEvent.clipboardData || window['clipboadData'];
                        clipboard.setData('text', ctrl.capabilityResult);
                        clipboardEvent.preventDefault();
                        NotificationHandler.showSuccess('Link copied in the clipboard!');
                    };
                    document.addEventListener('copy', listener, false);
                    document.execCommand('copy');
                    document.removeEventListener('copy', listener, false);
                }
            }
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
                template:'<metadata-editor event="$ctrl.event" metadata="$ctrl.metadata" available-languages="$ctrl.availableLanguages" level="$ctrl.level" parent-id="$ctrl.parentId" cancel="$ctrl.cancel" ok="$ctrl.ok"></metadata-editor>',
                backdrop: 'static',
                controllerAs: '$ctrl',
                controller: function($scope) {
                    this.event = ctrl.event;
                    this.metadata = ctrl.metadata;
                    this.availableLanguages = ctrl.availableLanguages;
                    this.level = ctrl.level;
                    this.parentId = ctrl.parentId;
                    this.ok = function() {
                        $scope.$close('OK');
                    };
                    this.cancel = function() {
                        $scope.$dismiss();
                    };
                }
            }).result.then(function() {
                retrieveMetadata(ctrl.event, ctrl.parentId, ctrl.categoryLevel, EventService).then(function (result) {
                    ctrl.metadata = result.data;
                })
            });
        }
    }

    MetadataViewerCtrl.$inject = ['$uibModal', 'EventService', 'NotificationHandler', '$q', 'HttpErrorHandler'];

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
            ctrl.categoryLevel = ctrl.level === 'category';
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
            saveMetadata(ctrl.event, ctrl.parentId, ctrl.categoryLevel, EventService, metadata).then(function() {
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

    function retrieveMetadata(event, parentId, categoryLevel, EventService) {
        var getter;
        if(categoryLevel) {
            getter = EventService.retrieveCategoryMetadata(event.shortName, parentId);
        } else {
            getter = EventService.retrieveMetadata(event.shortName);
        }
        return getter;
    }

    function saveMetadata(event, parentId, categoryLevel, EventService, metadata) {
        var publisher;
        if(categoryLevel) {
            publisher = EventService.updateCategoryMetadata(event.shortName, parentId, metadata);
        } else {
            publisher = EventService.updateEventMetadata(event.shortName, metadata);
        }
        return publisher;
    }

    function requestGuestData($uibModal) {
        return $uibModal.open({
            templateUrl:window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/metadata-editor/join-link-details-modal.html',
            backdrop: 'static',
            controllerAs: '$ctrl',
            controller: function($scope) {
                var ctrl = this;
                ctrl.guest = {
                    firstName: '',
                    lastName: '',
                    email: ''
                };
                ctrl.save = function(form) {
                    if(form.$valid) {
                       $scope.$close(ctrl.guest);
                    }
                };
                ctrl.cancel = function() {
                    $scope.$dismiss();
                };
            }
        }).result;
    }

})();