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
        templateUrl: '../resources/js/admin/feature/metadata-editor/metadata-viewer.html'
    }).component('metadataEditor', {
        bindings: {
            event: '<',
            metadata: '<',
            availableLanguages: '<',
            level: '<',
            ok: '<',
            cancel: '<',
            parentId: '<',
            enableVideoStream: '<',
            videoList: '<',
            showVideoList: '<',
            videoListLoading: '<',
            selectedVideo: '<',
            selectedCallLink: '<'
        },
        controller: MetadataEditorCtrl,
        templateUrl: '../resources/js/admin/feature/metadata-editor/metadata-editor-modal.html'
    }).component('showlink', {
          bindings: {
              title: '<',
              link: '<',
              availableLanguages: '<',
              level: '<',
              ok: '<',
              cancel: '<',
              copy: '<',
              guest: '<'
          },
          controller: MetadataEditorCtrl,
          templateUrl: '../resources/js/admin/feature/metadata-editor/showlink-modal.html'
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
                template:'<metadata-editor event="$ctrl.event" metadata="$ctrl.metadata" available-languages="$ctrl.availableLanguages" level="$ctrl.level" parent-id="$ctrl.parentId" cancel="$ctrl.cancel" ok="$ctrl.ok"></metadata-editor>',
                backdrop: 'static',
                controllerAs: '$ctrl',
                controller: function($scope) {
                    this.event = ctrl.event;
                    this.metadata = ctrl.metadata;
                    this.availableLanguages = ctrl.availableLanguages;
                    this.level = ctrl.level;
                    this.parentId = ctrl.parentId;
                    this.enableVideoStream = ctrl.enableVideoStream;
                    this.ok = function() {
                        $scope.$close('OK');
                    };
                    this.cancel = function() {
                        $scope.$dismiss();
                    };
                }
            }).result.then(function() {
                console.log('EditMetadata');
                retrieveMetadata(ctrl.event, ctrl.parentId, ctrl.categoryLevel, EventService).then(function (result) {
                    ctrl.metadata = result.data;

                })
            });
        };
        ctrl.createRoom = function(callLink) {
            console.log('Create Room', callLink);
            EventService.createRoom(ctrl.event.shortName, callLink).then(function (result) {
                console.log('createRoom result', result);
                $uibModal.open({
                        size:'lg',
                        template:'<showlink title="$ctrl.title" link="$ctrl.link" available-languages="$ctrl.availableLanguages" cancel="$ctrl.cancel" ok="$ctrl.ok" copy="$ctrl.copy"></showlink>',
                        backdrop: 'static',
                        controllerAs: '$ctrl',
                        controller: function($scope) {
                            this.title = 'Room creation'
                            this.link = result.data;
                            this.availableLanguages = ctrl.availableLanguages;
                            this.copied = ctrl.copied;
                            this.ok = function() {
                                $scope.$close('OK');
                            };
                            this.cancel = function() {
                                $scope.$dismiss();
                            };
                            this.copy = function(copied) {
                                /* Get the text field */
                                  var copyText = document.getElementById("linkText");
                                  copyText.type = 'text';
                                  /* Select the text field */
                                  copyText.select();
                                  copyText.setSelectionRange(0, 99999); /*For mobile devices*/

                                  /* Copy the text inside the text field */
                                  document.execCommand("copy");
                                  copyText.type = 'hidden';
                                  /* Alert the copied text */
                                  //alert("Copied the text: " + copyText.value);
                                  document.getElementById("spancopied").textContent="Copied to clipboard";
                            }
                        }
                    }).result.then(function() {
                        console.log('Game over');
                    });
            } )
        }
        ctrl.createGuestAccess = function(callLink) {
            console.log('Create GuestAccess', callLink);
            EventService.createGuestAccess(ctrl.event.shortName, callLink).then(function (result) {
                console.log('guestLink', result);
                $uibModal.open({
                        size:'lg',
                        template:'<showlink title="$ctrl.title" link="$ctrl.link" available-languages="$ctrl.availableLanguages" cancel="$ctrl.cancel" ok="$ctrl.ok" copy="$ctrl.copy"></showlink>',
                        backdrop: 'static',
                        controllerAs: '$ctrl',
                        controller: function($scope) {
                            this.title = 'Enable guest access'
                            this.link = result.data;
                            this.availableLanguages = ctrl.availableLanguages;
                            this.ok = function() {
                                $scope.$close('OK');
                            };
                            this.cancel = function() {
                                $scope.$dismiss();
                            };
                            this.copy = function(copied)
                            {
                                /* Get the text field */
                                  var copyText = document.getElementById("linkText");
                                  copyText.type = 'text';
                                  /* Select the text field */
                                  copyText.select();
                                  copyText.setSelectionRange(0, 99999); /*For mobile devices*/

                                  /* Copy the text inside the text field */
                                  document.execCommand("copy");
                                  copyText.type = 'hidden';
                                  /* Alert the copied text */
                                  //alert("Copied the text: " + copyText.value);
                                  document.getElementById("spancopied").textContent="Copied to clipboard";
                            }
                        }
                    }).result.then(function() {
                        console.log('Game over guest access');
                    });
            } )
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
            getEnableVideoStream(ctrl.event, EventService).then(function (result) {
                ctrl.enableVideoStream = result.data;
            })
            var callLinks;
            if(!ctrl.metadata?.onlineConfiguration) {
                callLinks = [{
                    link: '',
                    validFrom: moment(ctrl.event?.formattedBegin),
                    validTo: moment(ctrl.event?.formattedEnd)
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

            ctrl.prerequisites = ctrl.metadata?.requirementsDescriptions || {};
            syncSelectedLanguages();
            ctrl.languageDescription = languageDescription(ctrl.availableLanguages);
            ctrl.categoryLevel = ctrl.level === 'category';
            ctrl.showVideoList = false;
            ctrl.videoList = [];
            ctrl.videoListLoading = false;
            ctrl.selectedVideo = null;
            ctrl.selectedCallLink = null;
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

         ctrl.chooseVideo = function(callLink){
            console.log('chooseVideo');
            ctrl.showVideoList = true;
            ctrl.videoListLoading = true;
            ctrl.selectedCallLink = callLink;

            getAvailableVideoList(ctrl.event, EventService).then(function (result) {
                ctrl.videoList = result.data;
                ctrl.videoListLoading = false;
            })
         };

         ctrl.previewVideo = function(video, callLink){
            ctrl.selectedVideo = null;
            setTimeout(function(){ ctrl.selectedVideo = video; }, 0);
         }

         ctrl.selectVideo = function(callLink){
             callLink.link = ctrl.selectedVideo.link;
             ctrl.showVideoList = false;
             ctrl.selectedCallLink = null;
             ctrl.videoList = [];
          }

        ctrl.save = function(form) {
            if(form.$invalid) {
                return;
            }
            delete ctrl.error;
            console.log('Controller Metadata', ctrl.metadata);
            // FIX Don't override event tags and others metadata infos, please
            var metadata = ctrl.metadata;
            metadata.callLinks = ctrl.callLinks;
            metadata.requirementsDescriptions = ctrl.prerequisites;

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
    console.log('retrieveMetadata');
        var getter;
        if(categoryLevel) {
            getter = EventService.retrieveCategoryMetadata(event.shortName, parentId);
        } else {
            getter = EventService.retrieveMetadata(event.shortName);
        }
        return getter;
    }

    function getEnableVideoStream(event, EventService) {
        console.log('getEnableVideoStream');
        var getter = EventService.getEnableVideoStream(event.shortName);
        return getter;
    }

    function getAvailableVideoList(event, EventService) {
        console.log('getAvailableVideoList');
        var getter = EventService.getAvailableVideoList(event.shortName);
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

})();