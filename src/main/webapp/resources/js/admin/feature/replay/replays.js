(function() {
    'use strict';

    angular.module('adminApplication')
        .component('replays', {
            controller: ['$window', '$uibModal', '$q', 'ReplayService', 'ConfigurationService','Upload', ReplayCtrl],
            templateUrl: '../resources/js/admin/feature/replay/replays.html',
            bindings: {
                forOrganization: '<',
                organizationId: '<'
            }
        })
        .component('replayList', {
            controller: [ReplayListCtrl],
            templateUrl: '../resources/js/admin/feature/replay/list.html',
            bindings: {
                organizationId: '<',
                replays: '<',
                deleteReplay: '<',
                addReplay: '<',
                showReplay: '<',
                uploadVideo: '<',
                fileArray: '<',
                currentProgress: '<',
                isUploading: '<'
            }
        });

    function ReplayListCtrl() {}

    function ReplayCtrl($window, $uibModal, $q, ReplayService, ConfigurationService, Upload) {
        var ctrl = this;
        ctrl.isInternal = isInternal;
        ctrl.deleteReplay = deleteReplay;
        ctrl.addReplay = addReplay;
        ctrl.showReplay = showReplay;
        ctrl.uploadVideo = uploadVideo;
        ctrl.isUploading = false;
        ctrl.currentProgress = '0%';

        ctrl.$onInit = function() {
            loadData();
        };

        function loadData() {
            var loader = function() {
                return ReplayService.getList(ctrl.organizationId)
            };
            ctrl.replayDescription = 'Replay video management';
            ctrl.currentProgress = '0%';
            ctrl.isUploading = false;

            loader().then(function(res) {
                console.log('Replay loaded', res);
                ctrl.replays = res.data;

            });
        }

        function isInternal(event) {
            return true;
        }

        function errorHandler(error) {
            console.error(error.data);
            alert(error.data);
        }

        function deleteReplay(replay) {
            if($window.confirm('Delete ' +ctrl.replayDescription+ ' '+ replay.name +'replay video ?')) {
                ReplayService.remove(ctrl.organizationId, replay.name).then(loadData, errorHandler);
            }
        }

        function showReplay(fileName, previewUrl) {
            var organizationId = ctrl.organizationId;
            //TODO: transform component style
            $uibModal.open({
                size:'lg',
                templateUrl: '../resources/js/admin/feature/replay/show-video.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.fileName = fileName;
                    $scope.organizationId = organizationId;
                    $scope.previewUrl = previewUrl;
                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };

                }
            });
        }

        function addReplay() {
            var organizationId = ctrl.organizationId;
            angular.element(document.querySelector('#file')).click();

        };

        function uploadVideo(file){
            console.log('UploadVideo', file);
            ctrl.isUploading = true;
            Upload.upload({
                        url: '/admin/api/organization/uploadReplayVideo',
                        fields: {'organizationId': ctrl.organizationId }, // additional data to send
                        file: file
                    }).then(function (resp) {
                        console.log('Success ' + resp.config.data + 'uploaded. Response: ' + resp.data);
//                        ctrl.isUploading = false;
//                        ctrl.progress = '0%';
                        loadData();
                    }, function (resp) {
                        console.log('Error status: ' + resp.status);
                        ctrl.isUploading = false;
                        ctrl.currentProgress = 0;
                    }, function (evt) {
                        var progressPercentage = parseInt(100.0 * evt.loaded / evt.total);
                        console.log('progress: ' + progressPercentage + '% ' + evt.config.data);
                        ctrl.currentProgress = progressPercentage+'%';
                    });
//            var reader = new FileReader();
//                reader.onload = function(e) {
//                    $scope.$applyAsync(function() {
//                        var imageBase64 = e.target.result;
//                        $scope.imageBase64 = imageBase64;
//                        FileUploadService.uploadImageWithResize({file : imageBase64.substring(imageBase64.indexOf('base64,') + 7), type : files[0].type, name : files[0].name}).success(function(imageId) {
//                            $scope.obj.fileBlobId = imageId;
//                        })
//                    })
//
//                };
//            ReplayService.uploadVideo({file : files[0], type : files[0].type, name : files[0].name}, ctrl.organizationId)
//                .then(function(){
//                    console.log('Upload completed');
//                } , errorHandler);
        }
    }
})();