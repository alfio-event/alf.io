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


<h1>{{event.shortName}}</h1>
<div>{{event.begin}}-{{event.end}} {{event.location}}</div>
<div>{{event.description}}</div>


<h2>Tickets</h2>



<form method="post" action="reserve-tickets">
	<ul class="list-group">
	{{#ticketCategories}}
		<li class="list-group-item">
			{{name}}, regular price is:  {{event.regularPrice}} {{event.currency}} (vat included: {{event.vatIncluded}}), category price {{price}} :
			<input name="reservation[{{-index}}].ticketCategoryId" value="{{id}}" type="hidden">
			<input name="reservation[{{-index}}].amount" type="text">
		</li>
	{{/ticketCategories}}
	</ul>
	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
	<button type="submit" class="btn btn-primary pull-right">Continue</button>


</form>

</div>
</div>
</div>

</body>
</html>