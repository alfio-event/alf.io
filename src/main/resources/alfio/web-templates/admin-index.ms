<!DOCTYPE html>
<html lang="en">
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

    <title>Alf.io Admin</title>
</head>
<body>
    <alfio-admin-app data-config="{{adminConfig}}"></alfio-admin-app>
</body>
</html>
