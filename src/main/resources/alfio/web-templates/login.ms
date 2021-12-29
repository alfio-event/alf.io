<!DOCTYPE html>
<html lang="en" data-ng-app="adminApplication" data-ng-csp {{#devModeEnabled}}dev-mode-enabled{{/devModeEnabled}} {{#prodModeEnabled}}prod-mode-enabled{{/prodModeEnabled}}>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <link rel="stylesheet" href="{{contextPath}}/resources/css/source-sans-pro.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/css/bootstrap-admin.min.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/css/admin.css" />
    <link rel="stylesheet" href="{{contextPath}}/resources/bower_components/components-font-awesome/css/font-awesome.min.css" />
    {{#demoModeEnabled}}
        <title>Alf.io Demo: Open Source Event Ticketing System</title>
        <meta name="description" content="You can try Alf.io for free. Use a demo version of Alf.io's ticketing system to experience this great way to control your event and your sensitive attendee data!">
    {{/demoModeEnabled}}
    {{^demoModeEnabled}}
        <title>Authentication</title>
    {{/demoModeEnabled}}
    {{#hasRecaptchaApiKey}}
        <script src='https://www.google.com/recaptcha/api.js' nonce="{{nonce}}"></script>
    {{/hasRecaptchaApiKey}}
</head>
<body>
    <div class="container">
        <div class="row">
            <div class="col-sm-10 col-sm-push-1 col-xs-12 col-md-6 col-md-push-3">
                <form role="form" name="loginForm" action="{{request.contextPath}}/authenticate" method="post">
                    <input name="_csrf" type="hidden" value="{{_csrf.token}}" />
                        <div class="page-header">
                            <h2>Authentication requested</h2>
                            {{^demoModeEnabled}}
                                <span>Please login with Username and Password</span>
                            {{/demoModeEnabled}}
                        </div>
                    {{#demoModeEnabled}}
                        <div class="alert alert-info">
                            <div class="row">
                                <div class="col-xs-1"><i class="fa fa-info-circle fa-3x"></i></div>
                                <div class="col-xs-11">This instance is in <strong>demo mode</strong>, please enter your email address, a password and click on login</div>
                            </div>
                        </div>
                    {{/demoModeEnabled}}

                    {{#failed}}
                        <div class="alert alert-danger">Unknown user or wrong password. Please try again.</div>
                    {{/failed}}
                    {{#recaptchaFailed}}
                        <div class="alert alert-danger">Recaptcha check failed. Are you a human?</div>
                    {{/recaptchaFailed}}
                    <div class="form-group">
                        <label for="username">{{#demoModeEnabled}}Email address{{/demoModeEnabled}}{{^demoModeEnabled}}Username{{/demoModeEnabled}}</label>
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
    <script src="{{contextPath}}/resources/js/common/warning-msg.js" async="true" nonce="{{nonce}}"></script>
</body>
</html>