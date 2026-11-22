<!DOCTYPE html>
<html lang="en" data-ng-app="adminApplication" data-ng-csp {{#devModeEnabled}}dev-mode-enabled{{/devModeEnabled}} {{#prodModeEnabled}}prod-mode-enabled{{/prodModeEnabled}}>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <meta name="_csrf" content="{{_csrf.token}}"/>
    <meta name="_csrf_header" content="{{_csrf.headerName}}"/>
    {{#devModeEnabled}}
    <!-- see https://vitejs.dev/guide/backend-integration.html -->
    <script type="module" src="http://localhost:5173/@vite/client" nonce="{{nonce}}"></script>
    <script type="module" src="http://localhost:5173/src/main.ts" nonce="{{nonce}}"></script>
    {{/devModeEnabled}}
    {{#litAdminStatic}}
        {{#litPolyfills}}
        <!-- module scripts run in document order: the polyfills must come first -->
        <script type="module" src="{{contextPath}}/frontend-admin/{{lit-polyfills-js}}" nonce="{{nonce}}"></script>
        {{/litPolyfills}}
        <script type="module" src="{{contextPath}}/frontend-admin/{{lit-js}}" id="lit-js" nonce="{{nonce}}"></script>
        {{#lit-css}}
        <link rel="stylesheet" href="{{contextPath}}/frontend-admin/{{.}}" />
        {{/lit-css}}
    {{/litAdminStatic}}

    <link rel="stylesheet" href="{{contextPath}}/resources/css/source-sans-pro.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/css/bootstrap-admin.min.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/chartist/dist/chartist.min.css">
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/chartist-plugin-tooltip/dist/chartist-plugin-tooltip.css">
    <link rel="stylesheet" href="{{contextPath}}/resources/css/admin.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/components-font-awesome/css/font-awesome.min.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/bootstrap-daterangepicker/daterangepicker-bs3.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/angular-bootstrap/ui-bootstrap-csp.css"/>
    <script nonce="{{nonce}}">
        window.ALFIO_CONTEXT_PATH = "{{contextPath}}";
        window.USER_IS_OWNER = {{isOwner}};
        window.isAdmin = {{isAdmin}}; //backwards compatibility
        window.IS_ADMIN = {{isAdmin}};
        window.SUPPORTED_LANGUAGES = {{#render-json}}supportedLanguages{{/render-json}};
        window.BASE_URL = "{{baseUrl}}"
    </script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/lodash/lodash.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/moment/min/moment-with-locales.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/numeral/min/numeral.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular/angular.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-messages/angular-messages.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-bootstrap/ui-bootstrap-tpls.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-ui-router/release/angular-ui-router.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/ng-file-upload/ng-file-upload-shim.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/ng-file-upload/ng-file-upload.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/chartist/dist/chartist.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/chartist-plugin-accessibility/dist/chartist-plugin-accessibility.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/chartist-plugin-tooltip/dist/chartist-plugin-tooltip.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/bower_components/angular-animate/angular-animate.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/jsqrcode/jsqrcode.min.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/ng-app/admin-application.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/directive/admin-directive.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/filter/util.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/service/service.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/service/organization.service.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/service/user.service.js"></script>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/util/file-upload.js"></script>






    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/additional-fields-to-collect/additional-fields-to-collect.js"></script>

    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/admin/feature/group/groups.js"></script>



    <script nonce="{{nonce}}" type="text/javascript" src="{{contextPath}}/resources/bower_components/bootstrap-daterangepicker/daterangepicker.js"></script>
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
    <div data-ng-controller="LayoutController as ctrl">
        <div class="container-fluid menu-spacer">
            <div class="row">
                <div class="col-xs-12 col-md-3 col-lg-2 event-sidebar-column" ng-if="ctrl.eventName">
                    <alfio-event-sidebar></alfio-event-sidebar>
                </div>
                <div class="col-sm-12 col-xs-12" ng-class="{'col-md-9': ctrl.eventName, 'col-lg-10': ctrl.eventName}">
                    {{#errorMessage}}
                        <div class="container wMarginTop10px">
                            <div class="alert alert-danger text-center"><i class="fa fa-warning"></i> <strong>{{errorMessage}}</strong></div>
                        </div>
                    {{/errorMessage}}
                    <div data-ui-view></div>
                </div>
            </div>
            <footer class="text-center" ng-if="!ctrl.displaySidebar">
                <alfio-project-banner data-full-banner="{{displayProjectBanner}}" data-alfio-version="{{alfioVersion}}" class="container alert" style="display:block"></alfio-project-banner>
            </footer>
        </div>
    </div>
    <script nonce="{{nonce}}" src="{{contextPath}}/resources/js/common/warning-msg.js" async="true"></script>
    <alfio-feedback-visualizer></alfio-feedback-visualizer>
    {{#basicConfigurationNeeded}}<alfio-basic-settings-dialog></alfio-basic-settings-dialog>{{/basicConfigurationNeeded}}
</body>
</html>
