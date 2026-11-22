<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    {{#devModeEnabled}}
    <link rel="stylesheet" href="http://localhost:5173/src/login.css" />
    {{/devModeEnabled}}
    {{#loginStylesheet}}
    <link rel="stylesheet" href="{{contextPath}}/frontend-admin/{{loginStylesheet}}" />
    {{/loginStylesheet}}
    {{#demoModeEnabled}}
        <title>Alf.io Demo: Open Source Event Ticketing System</title>
        <meta name="description" content="You can try Alf.io for free. Use a demo version of Alf.io's ticketing system to experience this great way to control your event and your sensitive attendee data!">
    {{/demoModeEnabled}}
    {{^demoModeEnabled}}
        <title>Log in · alf.io Admin</title>
    {{/demoModeEnabled}}
    {{#hasRecaptchaApiKey}}
        <script src='https://www.google.com/recaptcha/api.js' nonce="{{nonce}}"></script>
    {{/hasRecaptchaApiKey}}
</head>
<body>
    <main class="login">
        <img class="login-logo" alt="alf.io" src="{{contextPath}}/resources/images/alfio-logo.svg" width="100" height="50">
        <div class="login-card">
            <form name="loginForm" action="{{request.contextPath}}/authenticate" method="post">
                <input name="_csrf" type="hidden" value="{{_csrf.token}}" />
                <h1>Log in</h1>
                {{^demoModeEnabled}}
                <p class="description">Enter your username and password.</p>
                {{/demoModeEnabled}}
                {{#demoModeEnabled}}
                <div class="message primary">This instance is in demo mode: enter your email address and a password, then log in.</div>
                {{/demoModeEnabled}}
                {{#failed}}
                <div class="message danger" role="alert">Unknown user or wrong password. Please try again.</div>
                {{/failed}}
                {{#recaptchaFailed}}
                <div class="message danger" role="alert">The reCAPTCHA check failed. Please try again.</div>
                {{/recaptchaFailed}}
                <div class="field">
                    <label for="username">{{#demoModeEnabled}}Email address{{/demoModeEnabled}}{{^demoModeEnabled}}Username{{/demoModeEnabled}}</label>
                    <input id="username" type="text" name="username" value="" autocomplete="username" required="required" autofocus>
                </div>
                <div class="field">
                    <label for="password">Password</label>
                    <input id="password" type="password" name="password" value="" autocomplete="current-password" required="required">
                </div>
                {{#hasRecaptchaApiKey}}
                <div class="g-recaptcha" data-sitekey="{{recaptchaApiKey}}"></div>
                {{/hasRecaptchaApiKey}}
                <div class="actions">
                    <button type="submit" class="submit">Log in</button>
                </div>
            </form>
        </div>
    </main>
    {{#devModeEnabled}}
    <div class="message primary mode-notice">You are running alf.io in development mode</div>
    {{/devModeEnabled}}
    {{#prodModeEnabled}}
    <div class="message danger mode-notice" id="insecure-notice" hidden>You are running alf.io in production mode not over https. This is not a recommended configuration.</div>
    <script nonce="{{nonce}}">
        if (location.protocol !== 'https:') {
            document.getElementById('insecure-notice').hidden = false;
        }
    </script>
    {{/prodModeEnabled}}
</body>
</html>
