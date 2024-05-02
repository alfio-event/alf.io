(function () {
    "use strict";

    angular.module('alfio-util', ['adminServices', 'ngFileUpload'])
        .directive('fileUpload', function() {
            return {
                restrict: 'E',
                scope: {
                    accept: '=',
                    targetUrl: '=',
                    successCallback: '=',
                    errorCallback: '=',
                    directHandling: '=',
                    readAsText:'='
                },
                bindToController: true,
                controller: FileUploadController,
                controllerAs: 'fup',
                template: '<div class="drop-file-zone wMarginBottom well text-center" data-accept="{{fup.accept}}" data-ngf-pattern="fup.accept" data-ng-model="fup.selectedFile" data-ngf-drop="fup.uploadFile($file)" data-ngf-multiple="false" data-ngf-allow-dir="false" data-ngf-drag-over-class="\'drop-file-zone-hover\'">Drop file or <a href="#" data-ngf-select="fup.uploadFile($file)">click here</a> to upload</div>'
            }
        });


    function FileUploadController($http, $window) {
        var ctrl = this;
        ctrl.selectedFile = undefined;

        ctrl.uploadFile = function(file) {
            if(!angular.isDefined(file) || file === null) {
                return;
            }
            var reader = new FileReader();
            reader.onload = function(e) {

                var fileContent = e.target.result;
                if(!ctrl.readAsText) {
                    fileContent = fileContent.substring(fileContent.indexOf('base64,') + 7);
                    if(ctrl.directHandling) {
                        fileContent = $window.atob(fileContent)
                    }
                }

                if(ctrl.directHandling) {
                    ctrl.successCallback(fileContent);
                } else {
                    $http['post'](ctrl.targetUrl, {file : fileContent, type : file.type, name : file.name})
                        .success(function(data) {
                            ctrl.successCallback(data);
                        }).error(function(data) {
                            if(ctrl.errorCallback) {
                                ctrl.errorCallback(data);
                            }
                        });
                }
            };

            if(ctrl.readAsText) {
                reader.readAsText(file);
            } else {
                reader.readAsDataURL(file);
            }

        };

    }

    FileUploadController.prototype.$inject = ['$http', '$window'];

})();