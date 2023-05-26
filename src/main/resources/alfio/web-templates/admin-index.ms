<!DOCTYPE html>
<html lang="en" data-ng-app="adminApplication" data-ng-csp {{#basicConfigurationNeeded}}data-basic-configuration-needed{{/basicConfigurationNeeded}} {{#devModeEnabled}}dev-mode-enabled{{/devModeEnabled}} {{#prodModeEnabled}}prod-mode-enabled{{/prodModeEnabled}}>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <meta name="_csrf" content="{{_csrf.token}}"/>
    <meta name="_csrf_header" content="{{_csrf.headerName}}"/>
    <link rel="stylesheet" href="{{contextPath}}/resources/css/source-sans-pro.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/css/bootstrap-admin.min.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/chartist/dist/chartist.min.css">
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/chartist-plugin-tooltip/dist/chartist-plugin-tooltip.css">
    <link rel="stylesheet" href="{{contextPath}}/resources/css/admin.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/components-font-awesome/css/font-awesome.min.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/bootstrap-daterangepicker/daterangepicker-bs3.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/angular-bootstrap/ui-bootstrap-csp.css"/>
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/nz-toggle/dist/nz-toggle.min.css"/>
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/angular-growl-v2/build/angular-growl.min.css"/>
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/dragula.js/dist/dragula.min.css"/>
    <script nonce="{{nonce}}">
        window.ALFIO_CONTEXT_PATH = "{{contextPath}}";
        window.USER_IS_OWNER = {{isOwner}};
    </script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/lodash/lodash.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/moment/min/moment-with-locales.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/numeral/min/numeral.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular/angular.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-sanitize/angular-sanitize.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-messages/angular-messages.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-bootstrap/ui-bootstrap-tpls.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-ui-router/release/angular-ui-router.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/ng-file-upload/ng-file-upload-shim.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/ng-file-upload/ng-file-upload.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/chartist/dist/chartist.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/chartist-plugin-accessibility/dist/chartist-plugin-accessibility.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/chartist-plugin-tooltip/dist/chartist-plugin-tooltip.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/nz-toggle/dist/nz-toggle.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-growl-v2/build/angular-growl.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-animate/angular-animate.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/qrcode-generator/js/qrcode.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/qrcode-generator/js/qrcode_UTF8.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-qrcode/angular-qrcode.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/dragula.js/dist/dragula.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angularjs-dragula/dist/angularjs-dragula.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/jsqrcode/jsqrcode.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/ng-app/admin-application.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/directive/admin-directive.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/filter/util.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/service/service.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/service/resource-service.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/service/organization.service.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/service/user.service.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/email/email-log.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/util/file-upload.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/configuration/configuration.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/additional-service/additional-service.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/event-statistic/event-statistic.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/expired-events-list/expired-events-list.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/active-events-list/active-events-list.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/event-data-to-collect/event-data-to-collect.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/promo-codes/promo-codes.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/tickets-remove/tickets-remove.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/ticket-full-data/ticket-full-data.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/reservation-cancel/reservation-cancel.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/users/users.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/user-edit/user-edit.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/apikey-bulk-import/apikey-bulk-import.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/user-edit-current/user-edit-current.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/organizations/organizations.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/organization-edit/organization-edit.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/resources/show/resources-show.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/resources/edit/resources-edit.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/reservation/reservation.service.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/reservation/create/reservation-create.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/reservation/import/reservation-import.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/reservation/view/reservation-view.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/reservations-list/reservations-list.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/payments-list/payments-list.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/tickets-list/tickets-list.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/label-template/label-template.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/extension/extension.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/extension/extension.service.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/extension/add-update/extension-add-update.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/extension/log/extension-log.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/ticket-category/ticket-category.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/group/groups.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/subscriptions/subscriptions.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/util/reservation-identifier.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/internationalization-editor/internationalization-editor.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/copy-event/copy-event.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/metadata-editor/metadata-editor.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/project-banner/project-banner.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/polls/polls.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/export-reservations/export-reservations.js"></script>


    <link rel="stylesheet" href="{{contextPath}}/resources/js/admin/feature/reservation/create/reservation-create.css" />

    <script nonce="{{nonce}}" type="text/javascript" src="{{contextPath}}/resources/bower_components/bootstrap-daterangepicker/daterangepicker.js"></script>
    <script nonce="{{nonce}}" type="text/javascript" src="{{contextPath}}/resources/bower_components/ace-builds/src-min-noconflict/ace.js"></script>
    <script nonce="{{nonce}}" type="text/javascript" src="{{contextPath}}/resources/bower_components/angular-ui-ace/ui-ace.js"></script>
    <script nonce="{{nonce}}" type="text/javascript" src="{{contextPath}}/resources/bower_components/dexie/dist/dexie.min.js"></script>
    <script nonce="{{nonce}}" type="text/javascript" src="{{contextPath}}/resources/bower_components/papaparse/papaparse.min.js"></script>
    <script nonce="{{nonce}}" type="text/javascript" src="{{contextPath}}/resources/bower_components/checklist-model/checklist-model.js"></script>
    <title>Alf.io Admin</title>
</head>
<body>
    <div class="navbar navbar-default navbar-fixed-top" role="navigation" data-ng-controller="MenuController as ctrl">
        <div class="container-fluid">
            <div class="row">
                <div class="col-xs-12 col-lg-11">
                    <div class="navbar-header">
                        <button type="button" class="navbar-toggle" data-ng-click="ctrl.toggleCollapse(ctrl.menuCollapsed)">
                            <span class="sr-only">Toggle navigation</span>
                            <span class="icon-bar"></span>
                            <span class="icon-bar"></span>
                            <span class="icon-bar"></span>
                        </button>
                        <a data-ui-sref="index" style="margin-right: 10px;"><img alt="Alf.io" src="{{contextPath}}/resources/images/alfio-logo.svg" width="100" height="50"></a>
                    </div>
                    <div class="navbar-left">
                        <div class="collapse navbar-collapse" data-uib-collapse="ctrl.menuCollapsed">
                            <ul class="nav navbar-nav">
                                <li data-ui-sref-active="active"><a data-ui-sref="index" ng-click="ctrl.menuCollapsed = true">Events</a></li>
                                {{#isOwner}}
                                <li data-ui-sref-active="active"><a data-ui-sref="subscriptions" ui-sref-opts="{reload: true, notify: true}" ng-click="ctrl.menuCollapsed = true">Subscriptions <span class="label label-xs label-default">beta</span></a></li>
                                <li data-ui-sref-active="active"><a data-ui-sref="organizations" ng-click="ctrl.menuCollapsed = true">Organizations</a></li>
                                {{#isDBAuthentication}}
                                <li data-ui-sref-active="active"><a data-ui-sref="users" ng-click="ctrl.menuCollapsed = true">Users</a></li>
                                {{/isDBAuthentication}}
                                <li data-ui-sref-active="active"><a data-ui-sref="apikey" ng-click="ctrl.menuCollapsed = true">Api Keys</a></li>
                                <li data-ui-sref-active="active"><a data-ui-sref="groups" ui-sref-opts="{reload: true, notify: true}" ng-click="ctrl.menuCollapsed = true">Groups</a></li>
                                <li data-ui-sref-active="active"><a data-ui-sref="configuration.system" ng-click="ctrl.menuCollapsed = true">Configuration</a></li>
                                {{#isAdmin}}<li data-ui-sref-active="active"><a data-ui-sref="extension.list" ng-click="ctrl.menuCollapsed = true">Extension</a></li>{{/isAdmin}}
                                {{/isOwner}}
                            </ul>
                            <ul class="nav navbar-nav visible-sm visible-xs" ng-if="ctrl.eventName">
                                <li class="navbar-header"><h4>Actions</h4></li>
                                <li data-ui-sref-active="active"><a data-ui-sref="events.single.create-reservation({eventName: ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-magic"></i> create reservation</a></li>
                                <li data-ui-sref-active="active"><a data-ui-sref="events.single.configuration({eventName: ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-wrench"></i> edit configuration</a></li>
                                <li data-ui-sref-active="active"><a data-ui-sref="events.single.show-resources({eventName: ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-file-text-o" aria-hidden="true"></i> customize templates</a></li>
                                <li data-ui-sref-active="active"><a data-ui-sref="events.single.compose-custom-message({eventName: ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-envelope"></i> compose message</a></li>
                                <li data-ui-sref-active="active" {{^isOwner}}data-ng-if="!ctrl.event.expired"{{/isOwner}}><a data-ui-sref="events.single.checkIn({eventName: ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-check"></i> Check-in</a></li>
                            </ul>
                            <ul class="nav navbar-nav visible-sm visible-xs" ng-if="ctrl.eventName">
                                <li class="navbar-header"><h4>Download</h4></li>
                                <li role="menuitem"><a data-ng-click="ctrl.openFieldSelectionModal()"><i class="fa fa-users"></i> attendees' data</a></li>
                                <li role="menuitem"><a data-ng-click="ctrl.openWaitingQueueModal()" ><i class="fa fa-group"></i> waiting list</a></li>
                                <li role="menuitem"><a data-ng-click="ctrl.downloadSponsorsScan()" ><i class="fa fa-barcode"></i> sponsors scan</a></li>
                                <li role="menuitem"><a data-ng-click="ctrl.downloadInvoices()" ><i class="fa fa-file-o"></i> all Billing Documents (PDF)</a></li>
                                <li role="menuitem"><a data-ng-click="ctrl.downloadInvoices('xls')" ><i class="fa fa-file-o"></i> all Billing Documents (Excel)</a></li>
                            </ul>

                            <ul class="nav navbar-nav visible-sm visible-xs" ng-if="ctrl.eventName">
                                <li class="navbar-header"><h4>Status</h4></li>
                                <li data-ui-sref-active="active" waiting-queue-display-counter="" data-event-name="ctrl.eventName" data-style-class="navbar-link"></li>
                                <li data-ui-sref-active="active" pending-payments-link="" data-style-class="emulate-nav-pills-link" data-event-name="ctrl.eventName"></li>
                            </ul>

                            <ul class="nav navbar-nav visible-sm visible-xs" ng-if="ctrl.eventName">
                                <li class="navbar-header"><h4>Navigate</h4></li>
                                <li role="presentation" data-ui-sref-active="active">
                                    <a ui-sref="events.single.dataToCollect({eventName: ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-info-circle"></i> Attendees' data to collect</a>
                                </li>
                                <li role="presentation" data-ui-sref-active="active">
                                    <a ui-sref="events.single.promoCodes({eventName: ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-percent"></i> Promo Codes</a>
                                </li>
                                <li role="presentation" data-ui-sref-active="active">
                                    <a ui-sref="events.single.additionalServices({eventName: ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-money"></i> Additional options</a>
                                </li>
                                <li role="presentation" data-ui-sref-active="active">
                                    <a ui-sref="events.single.donations({eventName: ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-gift"></i> Donation options</a>
                                </li>
                                <li role="presentation" data-ui-sref-active="active">
                                    <a data-ui-sref="events.single.reservationsList({eventName:ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-ticket"></i> Reservations</a>
                                </li>
                                <li role="presentation" data-ui-sref-active="active" data-ng-if="!ctrl.event.free">
                                    <a data-ui-sref="events.single.paymentsList({eventName:ctrl.event.shortName})"><i class="fa fa-dollar"></i> Confirmed Payments</a>
                                </li>
                                <li role="presentation" data-ui-sref-active="active">
                                    <a data-ui-sref="events.single.email-log({eventName:ctrl.eventName})" ng-click="ctrl.menuCollapsed = true"><i class="fa fa-paper-plane-o"></i> E-mail log</a>
                                </li>
                            </ul>
                            <ul class="nav navbar-nav visible-sm visible-xs">
                                <li class="nav-divider"></li>
                                <li class="visible-xs visible-sm">
                                    <div class="pull-left"><a href="#" class="navbar-link" ng-click="ctrl.menuCollapsed = true" data-ui-sref="edit-current-user" title="click to update account details"><i class="fa fa-user"></i> <span ng-non-bindable>{{username}}</span></a></div>
                                    <div class="pull-right"><a href="" class="navbar-link" data-ng-click="ctrl.doLogout('{{idpLogoutRedirectionUrl}}')"><i class="fa fa-sign-out"></i> Log out</a></div>
                                </li>
                            </ul>
                        </div>
                    </div>
                    <div class="navbar-right hidden-sm hidden-xs">
                        <ul class="nav navbar-nav">
                            <li role="presentation" class="navbar-text"><i class="fa fa-user"></i> Logged in as <span ng-non-bindable>{{username}}</span></li>
                            {{#isDBAuthentication}}
                            <li role="presentation" data-ui-sref-active="active"> <a data-ui-sref="edit-current-user" title="click to update account details"><i class="fa fa-edit"></i> edit account</a></li>
                            {{/isDBAuthentication}}
                            <li role="presentation"><a href="" data-ng-click="ctrl.doLogout('{{idpLogoutRedirectionUrl}}')"><i class="fa fa-sign-out"></i> Log out</a></li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <div growl></div>
    <div data-ng-controller="LayoutController as ctrl">
        <div class="container-fluid menu-spacer">
            <div class="row">
                <div class="hidden-xs hidden-sm col-md-3 col-lg-2" ng-if="ctrl.displaySidebar">
                    <alfio-sidebar></alfio-sidebar>
                </div>
                <div class="col-sm-12 col-xs-12" ng-class="{'col-md-9': ctrl.displaySidebar}">
                    {{#errorMessage}}
                        <div class="container wMarginTop10px">
                            <div class="alert alert-danger text-center"><i class="fa fa-warning"></i> <strong>{{errorMessage}}</strong></div>
                        </div>
                    {{/errorMessage}}
                    <div data-ui-view></div>
                </div>
            </div>
            <footer class="text-center" ng-if="!ctrl.displaySidebar">
                <project-banner data-full-banner="{{displayProjectBanner}}" data-alfio-version="'{{alfioVersion}}'"></project-banner>
            </footer>
        </div>
    </div>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/common/warning-msg.js" async="true"></script>
</body>
</html>