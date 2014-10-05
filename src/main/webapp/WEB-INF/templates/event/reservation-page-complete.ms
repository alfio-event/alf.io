<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <link rel="stylesheet" href="/resources/bower_components/bootstrap/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="/resources/bower_components/components-font-awesome/css/font-awesome.min.css">
</head>
<body>

<div class="container">
<div class="row">
<div class="col-md-8 col-md-offset-2">


	<h1>Your Ticket(s)</h1>
	<ul class="list-group">
	{{#ticketsByCategory}}
		{{#value}}
			<li class="list-group-item">Category: {{key}} -	ticket: 
				{{assigned}}
				{{#assigned}}
					<p><a href="{{reservationId}}/download-ticket/{{uuid}}">Download ticket</a>.</p>
					<div>TODO: send by email</div>
					<p>You can update the owner of the ticket: a new email will be sent (reminder: the previous sent ticket will be invalidated)</p>
				{{/assigned}}
				{{^assigned}}
					<p>You need to fill fullname/email to download the ticket:</p>
				{{/assigned}}
				<form method="post" action="{{reservationId}}/{{uuid}}">
					<p>Full name: <input type="text" name="fullName" value="{{fullName}}" required></p>
					<p>Email: <input type="email" name="email" value="{{email}}" required></p>
					<p><button type="submit" class="btn btn-primary">update</button><input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}"></p>
				</form>
			</li>
		{{/value}}
	{{/ticketsByCategory}}
	</ul>
	
</div>
</div>
</div>
	
</body>
</html>