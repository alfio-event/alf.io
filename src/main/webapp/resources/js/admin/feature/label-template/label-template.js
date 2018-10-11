(function() {
    'use strict';

    angular.module('adminApplication').component('labelTemplate', {
        bindings: {
            config: '<',
            deleteHandler: '&'
        },
        controller: LabelTemplateCtrl,
        templateUrl: '../resources/js/admin/feature/label-template/label-template.html'
    });

    var DEFAULT_CONFIG = {
        qrCode: {
            additionalInfo: [], //UUID is always included
            infoSeparator: "::"
        },
        content: {
            firstRow: "firstName",
            secondRow: "lastName",
            additionalRows: [],
            checkbox: false
        },
        general: {
            printPartialID: true
        }
    };

    function LabelTemplateCtrl() {
        var ctrl = this;
        ctrl.config.value = ctrl.config.value || JSON.stringify(DEFAULT_CONFIG, null, 2);
        ctrl.configValue = JSON.parse(ctrl.config.value);
        ctrl.updateHandler = updateHandler;
        ctrl.qrCodeData = qrCodeData;
        ctrl.getFirstRow = getFirstRow;
        ctrl.getSecondRow = getSecondRow;

        function updateHandler(val) {
            try {
                JSON.parse(val.value);
                ctrl.config.value = val.value;
                ctrl.configValue = JSON.parse(ctrl.config.value);
                ctrl.error = false;
            } catch (e) {
                ctrl.error = true;
            }
        }

        function qrCodeData() {
            return _.join(ctrl.configValue.qrCode.additionalInfo, ctrl.configValue.qrcode.infoSeparator);
        }

        function getFirstRow() {
            return getRowValue(ctrl.configValue.content.firstRow, 'firstName');
        }

        function getSecondRow() {
            return getRowValue(ctrl.configValue.content.secondRow, 'lastName');
        }

        function getRowValue(k, defaultKey) {
            var key = k || defaultKey;
            switch(key) {
                case 'firstName':
                    return 'Homer J';
                case 'lastName':
                    return 'Simpson';
                default:
                    return 'Sample '+key;
            }
        }

    }


})();