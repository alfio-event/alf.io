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


<h2>{{#i18n}}show-event.tickets{{/i18n}}</h2>



<form method="post" action="reserve-tickets" class="form-horizontal">
	<ul class="list-group">
	{{#ticketCategories}}
		<li class="list-group-item">
			<div class="form-group">
				<div class="col-sm-8">
					<label class="control-label">{{name}}</label>
					<input name="reservation[{{-index}}].ticketCategoryId" value="{{id}}" type="hidden">
					<div>
						(vat included: {{event.vatIncluded}})
						{{description}}
					</div>
				</div>
				<div class="col-sm-2">
					<label class="control-label">
						{{#free}}
							{{#i18n}}common.free{{/i18n}}	
						{{/free}}
						{{^free}}
							{{price}} {{event.currency}}
						{{/free}}
					</label>
				</div>
				<div class="col-sm-2">
					<input name="reservation[{{-index}}].amount" type="text" class="form-control text-align-center" placeholder="0" autocomplete="off">
				</div>
			</div>
		</li>
	{{/ticketCategories}}
	</ul>
	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
	<button type="submit" class="btn btn-primary pull-right">{{#i18n}}show-event.continue{{/i18n}}</button>


</form>

</div>
</div>
</div>

</body>
</html>