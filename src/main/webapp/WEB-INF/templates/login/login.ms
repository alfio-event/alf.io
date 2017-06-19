<!DOCTYPE html>
<html lang="en" data-ng-app="adminApplication" data-ng-csp>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/bootstrap/dist/css/bootstrap.min.css" />
    <link rel="stylesheet" href="{{request.contextPath}}/resources/css/admin.css" />
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/components-font-awesome/css/font-awesome.min.css" />
    <title>Authentication</title>
    {{#demo}}
        <script src='https://www.google.com/recaptcha/api.js'></script>
    {{/demo}}
</head>
<body>
    <div class="container">
        <div class="row">
            <div class="col-sm-10 col-sm-push-1 col-xs-12 col-md-6 col-md-push-3">
                <form role="form" name="loginForm" action="{{request.contextPath}}/authenticate" method="post">
                    <input name="_csrf" type="hidden" value="{{_csrf.token}}" />
                    {{#demo}}
                        <div class="page-header">
                            <h2>Authentication requested</h2>
                            <span>This instance is in demo mode, please enter your email address, a password and click on login</span>
                        </div>
                    {{/demo}}
                    {{^demo}}
                        <div class="page-header">
                            <h2>Authentication requested</h2>
                            <span>Please login with Username and Password</span>
                        </div>
                    {{/demo}}
                    {{#failed}}
                        <div class="alert alert-danger">Unknown user or wrong password. Please try again.</div>
                    {{/failed}}
                    {{#recaptchaFailed}}
                        <div class="alert alert-danger">Recaptcha check failed. Are you a human?</div>
                    {{/recaptchaFailed}}
                    <div class="form-group">
                        <label for="username">{{#demo}}Email address{{/demo}}{{^demo}}Username{{/demo}}</label>
                        <input id="username" class="form-control" type="text" name="username" value="" required="required">
                    </div>
                    <div class="form-group">
                        <label for="password">Password</label>
                        <input id="password" class="form-control" type="password" name="password" value="" required="required">
                    </div>
                    <div class="row">
                        <div class="col-md-4 col-md-push-8 col-xs-12 wMarginBottom"><button type="submit" class="btn btn-success btn-block">Login</button></div>
                        <div class="col-md-4 col-md-pull-4 col-xs-12"><button type="reset" class="btn btn-default btn-block">Cancel</button></div>
                    </div>
                    {{#hasRecaptchaApiKey}}
                        <div class="g-recaptcha" data-sitekey="{{recaptchaApiKey}}"></div>
                    {{/hasRecaptchaApiKey}}
                </form>
            </div>
        </div>
    </div>
</body>
</html>