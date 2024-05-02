(function() {

'use strict';


angular.module('adminApplication').component('exportReservationsButton', {
    templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/export-reservations/export-reservations-button.html',
    controller: exportReservationsButton
});


function exportReservationsButton(EventService, $uibModal) {
    var ctrl = this;

    ctrl.isOwner = window.USER_IS_OWNER;

    ctrl.$onInit = function() {
        EventService.getEventsCount().then(function(response) {
            ctrl.eventsCount = response.data;
        });
    }

    ctrl.openModal = function() {
        $uibModal.open({
            size: 'lg',
            templateUrl: window.ALFIO_CONTEXT_PATH + '/resources/js/admin/feature/export-reservations/export-reservations-form.html',
            backdrop: 'static',
            controllerAs: '$ctrl',
            bindToController: true,
            controller: function($scope) {
                var modalCtrl = this;
                modalCtrl.maxDate = moment().endOf('day');
                modalCtrl.searchFrom = null;
                modalCtrl.searchTo = null;
                modalCtrl.ready = false;
                modalCtrl.formattedSearchFrom = null;
                modalCtrl.formattedSearchTo = null;

                modalCtrl.close = function() {
                    $scope.$dismiss();
                }

                $scope.$watch('$ctrl.searchFrom', function(newValue) {
                    if(newValue) {
                        modalCtrl.ready = moment(modalCtrl.searchTo).isAfter(moment(newValue));
                        modalCtrl.formattedSearchFrom = moment(newValue).format('YYYY-MM-DD');
                    }
                });
                $scope.$watch('$ctrl.searchTo', function(newValue) {
                    if(newValue) {
                        modalCtrl.ready = moment(newValue).isAfter(moment(modalCtrl.searchFrom));
                        modalCtrl.formattedSearchTo = moment(newValue).format('YYYY-MM-DD');
                    }
                });
            }
        });
    }
}

exportReservationsButton.$inject = ['EventService', '$uibModal'];

})();