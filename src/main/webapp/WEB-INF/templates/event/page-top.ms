<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/bootstrap/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="{{request.contextPath}}/resources/bower_components/components-font-awesome/css/font-awesome.min.css">
    <link rel="stylesheet" href="{{request.contextPath}}/resources/css/application.css">
    <title>{{#i18n}}{{pageTitle}}[{{event.displayName}}]{{/i18n}}</title>
</head>
<body>


<div class="container">
	<div class="row">
		<div class="col-md-8 col-md-offset-2">
			<div>

				<!-- lang bar -->
				<div class="row">
                    <div class="col-xs-4 col-xs-push-8 col-sm-2 col-sm-push-10 text-center">
                        <ol class="alfio-lang breadcrumb wMarginBottom wMarginTop">
                            {{#availableLanguages}}
                                <li><a href="?lang={{language}}">{{language}}</a></li>
                            {{/availableLanguages}}
                        </ol>
                    </div>
                </div>
				<!-- -->