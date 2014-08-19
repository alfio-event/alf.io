<!DOCTYPE html>
<html lang="en" data-ng-app="adminApplication">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <link rel="stylesheet" href="/resources/bower_components/bootstrap/dist/css/bootstrap.min.css" />
    <link rel="stylesheet" href="/resources/css/admin.css" />
    <link rel="stylesheet" href="/resources/bower_components/components-font-awesome/css/font-awesome.min.css" />
    <script src="/resources/bower_components/jquery/dist/jquery.min.js"></script>
    <script src="/resources/bower_components/angular/angular.min.js"></script>
    <script src="/resources/bower_components/angular-bootstrap/ui-bootstrap.min.js"></script>
    <script src="/resources/bower_components/angular-ui-router/release/angular-ui-router.min.js"></script>
    <script src="/resources/js/admin/ng-app/admin-application.js"></script>
    <script src="/resources/js/admin/directive/admin-directive.js"></script>
    <script src="/resources/js/admin/service/service.js"></script>
    <title>{{#i18n}}application.title{{/i18n}}</title>
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
                <a class="navbar-brand logo" href="#">{{#i18n}}application.title{{/i18n}}</a>
            </div>
            <div class="navbar-left">
                <div class="collapse navbar-collapse">
                    <ul class="nav navbar-nav">
                        <li class="active"><a href="#">Home</a></li>
                        <li><a href="#">second</a></li>
                        <li><a href="#">third</a></li>
                    </ul>
                </div>
            </div>
        </div>
    </div>
    <div data-ui-view></div>
</body>
</html>