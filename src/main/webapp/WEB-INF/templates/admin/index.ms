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
    <script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/lodash/lodash.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/moment/min/moment-with-locales.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/numeral/min/numeral.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular/angular.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular-messages/angular-messages.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular-bootstrap/ui-bootstrap-tpls.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular-ui-router/release/angular-ui-router.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/ng-file-upload/ng-file-upload-shim.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/ng-file-upload/ng-file-upload.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/Chart.js/Chart.min.js"></script>
    <script src="{{request.contextPath}}/resources/bower_components/angular-chart.js/angular-chart.js"></script>
    <script src="{{request.contextPath}}/resources/js/jsqrcode/jsqrcode.min.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/ng-app/admin-application.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/directive/admin-directive.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/filter/util.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/service/service.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/feature/plugin/plugin-log.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/feature/email/email-log.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/feature/util/file-upload.js"></script>
    <script src="{{request.contextPath}}/resources/js/admin/feature/configuration/configuration.js"></script>
    <script type="text/javascript" src="{{request.contextPath}}/resources/bower_components/bootstrap-daterangepicker/daterangepicker.js"></script>
    <title>{{#i18n}}admin.title{{/i18n}}</title>
</head>
<body>
    <div class="navbar navbar-default navbar-fixed-top" role="navigation" data-ng-controller="MenuController">
        <div class="container">
            <div class="navbar-header">
                <button type="button" class="navbar-toggle" data-ng-click="toggleCollapse(menuCollapsed)">
                    <span class="sr-only">Toggle navigation</span>
                    <span class="icon-bar"></span>
                    <span class="icon-bar"></span>
                    <span class="icon-bar"></span>
                </button>
                <a class="navbar-brand logo" data-ui-sref="index">{{#i18n}}admin.title{{/i18n}}</a>
            </div>
            <div class="navbar-left">
                <div class="collapse navbar-collapse" data-collapse="menuCollapsed">
                    <ul class="nav navbar-nav">
                        <li data-ui-sref-active="active"><a data-ui-sref="index">Dashboard</a></li>
                        <li data-ui-sref-active="active"><a data-ui-sref="configuration">Configuration</a></li>
                        <li data-ui-sref-active="active"><a data-ui-sref="plugin-log">Plugin Log</a></li>
                        <li class="nav-divider"></li>
                        <li class="visible-xs"><span class="navbar-brand"><i class="fa fa-user"></i> {{username}}</span></li>
                    </ul>
                </div>
            </div>
            <div class="navbar-right hidden-xs">
                <span class="navbar-brand"><i class="fa fa-user"></i> {{username}}</span>
            </div>
        </div>
    </div>
    <div data-ui-view></div>
    <footer>
        <div class="text-center"><span class="text-muted">Powered by <a href="http://alf.io">Alf.io</a> v.{{alfioVersion}}</span></div>
    </footer>
</body>
</html>