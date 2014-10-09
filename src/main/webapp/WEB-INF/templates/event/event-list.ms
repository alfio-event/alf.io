<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <link rel="stylesheet" href="/resources/bower_components/bootstrap/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="/resources/bower_components/components-font-awesome/css/font-awesome.min.css">
    <link rel="stylesheet" href="/resources/css/application.css">
</head>
<body>


<div class="container">
<div class="row">
<div class="col-md-8 col-md-offset-2">

	<h1>{{#i18n}}event-list.title{{/i18n}}</h1>
	<ul class="list-group">
	{{#events}}
		<li class="list-group-item">
			<a href="event/{{shortName}}/">
				<div>
					<h2>{{shortName}}</h2>
					<div>{{begin}}-{{end}}</div>
					<div>{{location}}</div>
				</div>
			</a>
		</li>
	{{/events}}
	</ul>
	

</div>
</div>
</div>

</body>
</html>