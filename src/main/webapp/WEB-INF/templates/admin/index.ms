<!DOCTYPE html>
<html lang="en" data-ng-app="adminApplication" data-ng-csp>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <meta name="_csrf" content="{{_csrf.token}}"/>
    <meta name="_csrf_header" content="{{_csrf.headerName}}"/>
    <link rel="stylesheet" href="/resources/bower_components/bootstrap/dist/css/bootstrap.min.css" />
    <link rel="stylesheet" href="/resources/css/admin.css" />
    <link rel="stylesheet" href="/resources/bower_components/components-font-awesome/css/font-awesome.min.css" />
    <link rel="stylesheet" href="/resources/bower_components/bootstrap-daterangepicker/daterangepicker-bs3.css" />
    <script src="/resources/bower_components/jquery/dist/jquery.js"></script>
    <script src="/resources/bower_components/lodash/dist/lodash.min.js"></script>
    <script src="/resources/bower_components/moment/min/moment-with-locales.js"></script>
    <script src="/resources/bower_components/numeral/min/numeral.min.js"></script>
    <script src="/resources/bower_components/angular/angular.min.js"></script>
    <script src="/resources/bower_components/angular-messages/angular-messages.min.js"></script>
    <script src="/resources/bower_components/angular-bootstrap/ui-bootstrap-tpls.js"></script>
    <script src="/resources/bower_components/angular-ui-router/release/angular-ui-router.min.js"></script>
    <script src="/resources/js/admin/ng-app/admin-application.js"></script>
    <script src="/resources/js/admin/directive/admin-directive.js"></script>
    <script src="/resources/js/admin/filter/util.js"></script>
    <script src="/resources/js/admin/service/service.js"></script>
    <script type="text/javascript" src="/resources/bower_components/bootstrap-daterangepicker/daterangepicker.js"></script>
    <title>{{#i18n}}admin.title{{/i18n}}</title>
</head>
<body>
    <div class="navbar navbar-default navbar-fixed-top" role="navigation">
        <div class="container">
            <div class="navbar-header">
                <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-collapse">
                    <span class="sr-only">Toggle navigation</span>
                    <span class="icon-bar"></span>
                    <span class="icon-bar"></span>
                    <span class="icon-bar"></span>
                </button>
                <a class="navbar-brand logo" href="#">{{#i18n}}admin.title{{/i18n}}</a>
            </div>
            <div class="navbar-left">
                <div class="collapse navbar-collapse">
                    <ul class="nav navbar-nav">
                        <li class="active"><a href="#">Dashboard</a></li>
                        <li><a href="#">My Profile</a></li>
                        <li><a href="#">About</a></li>
                    </ul>
                </div>
            </div>
        </div>
    </div>
    <div data-ui-view></div>
</body>
</html>