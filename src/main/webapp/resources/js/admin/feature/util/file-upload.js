(function () {
    "use strict";

    angular.module('alfio-util', ['adminServices', 'ngFileUpload'])
        .directive('fileUpload', function() {
            return {
                restrict: 'E',
                scope: {
                    accept: '=',
                    targetUrl: '=',
                    successCallback: '='
                },
                bindToController: true,
                controller: FileUploadController,
                controllerAs: 'fup',
                template: '<div class="drop-file-zone wMarginBottom well text-center" data-accept="{{fup.accept}}" data-ngf-pattern="fup.accept" data-ng-model="fup.selectedFile" data-ngf-drop="fup.uploadFile($file)" data-ngf-multiple="false" data-ngf-allow-dir="false" data-ngf-drag-over-class="\'drop-file-zone-hover\'">Drop file or <a href="#" data-ngf-select="fup.uploadFile($file)">click here</a> to upload</div>'
            }
        });


    function FileUploadController($http) {
        var ctrl = this;
        ctrl.selectedFile = undefined;

        ctrl.uploadFile = function(file) {
            if(!angular.isDefined(file) || file === null) {
                return;
            }
            var reader = new FileReader();
            reader.onload = function(e) {
                var fileBase64 = e.target.result;
                $http['post'](ctrl.targetUrl, {file : fileBase64.substring(fileBase64.indexOf('base64,') + 7), type : file.type, name : file.name})
                    .success(function(data) {
                        ctrl.successCallback(data);
                    });
            };
            reader.readAsDataURL(file);
        };

    }

    FileUploadController.prototype.$inject = ['$http'];

})();