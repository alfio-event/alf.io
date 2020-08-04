(function() {
    'use strict';

    angular.module('adminApplication')
        .component('replays', {
            controller: ['$window', '$uibModal', '$q', 'ReplayService', 'ConfigurationService', ReplayCtrl],
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
                showReplay: '<'
            }
        });

    function ReplayListCtrl() {}

    function ReplayCtrl($window, $uibModal, $q, ReplayService, ConfigurationService) {
        var ctrl = this;
        ctrl.isInternal = isInternal;
        ctrl.deleteReplay = deleteReplay;
        ctrl.addReplay = addReplay;
        ctrl.showReplay = showReplay;

        ctrl.$onInit = function() {
            loadData();
        };

        function loadData() {
            var loader = function() {
                return ReplayService.getList(ctrl.organizationId)
            };
            ctrl.replayDescription = 'Replay video management';

            loader().then(function(res) {
                console.log('Replay loaded', res);
                ctrl.replays = res.data;

            });
        }

        function isInternal(event) {
            return true;
        }

        function errorHandler(error) {
            $log.error(error.data);
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
            //TODO: transform component style
            $uibModal.open({
                size:'lg',
                templateUrl: '../resources/js/admin/feature/replay/edit-promo-code-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.replayDescription = ctrl.replayDescription;

                    var now = moment();
                    var eventBegin = moment().add(1,'d').endOf('d');

                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, promocode) {
                        if(!form.$valid) {
                            return;
                        }
                        $scope.$close(true);

//                        ReplayService.add(promocode).then(function(result) {
//                            validationErrorHandler(result, form, form.promocode).then(function() {
//                                $scope.$close(true);
//                            });
//                        }, errorHandler).then(loadData);
                    };

                }
            });
        };
    }
})();