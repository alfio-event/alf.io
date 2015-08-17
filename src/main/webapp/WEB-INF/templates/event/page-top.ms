<!DOCTYPE html>
<html lang="{{#i18n}}locale{{/i18n}}" prefix="og: http://ogp.me/ns#">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/bootstrap/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/components-font-awesome/css/font-awesome.min.css">
    <link rel="stylesheet" href="{{request.contextPath}}/resources/css/application.css">
    <title>{{#i18n}}{{pageTitle}}[{{event.displayName}}]{{/i18n}}</title>
    <meta property="og:type" content="website" />
{{#event.imageIsPresent}}
    {{#event.fileBlobIdIsPresent}}
    <meta property="og:image" content="{{request.contextPath}}/file/{{event.fileBlobId}}" />
    {{/event.fileBlobIdIsPresent}}
    {{^event.fileBlobIdIsPresent}}
    <meta property="og:image" content="{{event.imageUrl}}" />
    {{/event.fileBlobIdIsPresent}}
{{/event.imageIsPresent}}
    <meta property="og:description" content="{{#i18n}}{{pageTitle}}[{{event.displayName}}]{{/i18n}}" />
</head>
<body>


<div class="container">
    <div class="row">
        <div class="col-md-8 col-md-offset-2">
            <div>

                <!-- lang bar -->
                <div class="row hidden-print">
                    <div class="col-xs-12 text-right">
                    {{#showAvailableLanguagesInPageTop}}
                        <ol class="alfio-lang breadcrumb wMarginBottom wMarginTop">
                            {{#availableLanguages}}
                                <li><a href="?lang={{language}}" lang="{{language}}">{{language}}</a></li>
                            {{/availableLanguages}}
                        </ol>
                    {{/showAvailableLanguagesInPageTop}}
                    </div>
                </div>
                <!-- -->