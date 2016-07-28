<!DOCTYPE html>
<html lang="en" data-ng-app="adminApplication" data-ng-csp {{#basicConfigurationNeeded}}data-basic-configuration-needed{{/basicConfigurationNeeded}}>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <meta name="_csrf" content="{{_csrf.token}}"/>
    <meta name="_csrf_header" content="{{_csrf.headerName}}"/>
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/bootstrap/dist/css/bootstrap.min.css" />
    <link rel="stylesheet" href="{{request.contextPath}}/resources/css/admin.css" />
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/components-font-awesome/css/font-awesome.min.css" />
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/bootstrap-daterangepicker/daterangepicker-bs3.css" />
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/angular-bootstrap/ui-bootstrap-csp.css"/>
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/nz-toggle/dist/nz-toggle.min.css"/>
    <script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/lodash/lodash.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/moment/min/moment-with-locales.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/numeral/min/numeral.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular/angular.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular-sanitize/angular-sanitize.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular-messages/angular-messages.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular-bootstrap/ui-bootstrap-tpls.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular-ui-router/release/angular-ui-router.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/ng-file-upload/ng-file-upload-shim.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/ng-file-upload/ng-file-upload.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/Chart.js/Chart.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular-chart.js/angular-chart.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/nz-toggle/dist/nz-toggle.min.js"></script>
    <script src="{{request.contextPath}}/resources/js/jsqrcode/jsqrcode.min.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/ng-app/admin-application.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/directive/admin-directive.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/filter/util.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/service/service.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/feature/plugin/plugin-log.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/feature/email/email-log.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/feature/util/file-upload.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/feature/user/user.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/feature/configuration/configuration.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/feature/additional-service/additional-service.js"></script>
    <script type="text/javascript" src="{{request.contextPath}}/resources/bower_components/bootstrap-daterangepicker/daterangepicker.js"></script>
    <title>{{#i18n}}admin.title{{/i18n}}</title>
</head>
<body>
    <div class="navbar navbar-default navbar-fixed-top" role="navigation" data-ng-controller="MenuController as ctrl">
        <div class="container-fluid">
            <div class="row">
                <div class="col-xs-12 col-md-10 col-md-push-1">
                    <div class="navbar-header">
                        <button type="button" class="navbar-toggle" data-ng-click="ctrl.toggleCollapse(ctrl.menuCollapsed)">
                            <span class="sr-only">Toggle navigation</span>
                            <span class="icon-bar"></span>
                            <span class="icon-bar"></span>
                            <span class="icon-bar"></span>
                        </button>
                        <a class="navbar-brand logo title" data-ui-sref="index">Alf.io</a>
                    </div>
                    <div class="navbar-left">
                        <div class="collapse navbar-collapse" data-uib-collapse="ctrl.menuCollapsed">
                            <ul class="nav navbar-nav">
                                <li data-ui-sref-active="active"><a data-ui-sref="index">Dashboard</a></li>
                                <li data-ui-sref-active="active"><a data-ui-sref="configuration.system">Configuration</a></li>
                                <li class="nav-divider"></li>
                                <li class="visible-xs navbar-text"><i class="fa fa-user"></i> Logged in as {{username}}</li>
                                <li class="visible-xs"><a href="#" data-ui-sref="edit-current-user" title="click to update account details"><i class="fa fa-edit"></i> edit account</a></li>
                                <li class="visible-xs"><a href="" data-ng-click="ctrl.doLogout()">Logout</a></li>
                            </ul>
                        </div>
                    </div>
                    <div class="navbar-right hidden-xs">
                        <ul class="nav navbar-nav">
                            <li role="presentation" class="navbar-text"><i class="fa fa-user"></i> Logged in as {{username}}</li>
                            <li role="presentation" data-ui-sref-active="active"> <a data-ui-sref="edit-current-user" title="click to update account details"><i class="fa fa-edit"></i> edit account</a></li>
                            <li role="presentation"><a href="" data-ng-click="ctrl.doLogout()"><i class="fa fa-sign-out"></i> Log out</a></li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <div class="container-fluid menu-spacer">
        <div class="row">
            <div class="hidden-xs hidden-sm col-md-2 col-md-push-1">
                <alfio-sidebar></alfio-sidebar>
            </div>
            <div class="col-sm-12 col-xs-12 col-md-8 col-md-push-1">
                <div data-ui-view></div>
            </div>
        </div>
    </div>
</body>
</html>