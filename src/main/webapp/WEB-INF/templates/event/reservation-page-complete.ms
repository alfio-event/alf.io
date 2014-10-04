<h1>Your Ticket(s)</h1>
<ul>
{{#ticketsByCategory}}
	{{#value}}
		<li>Category: {{key}} -	ticket: 
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
				<p><button type="submit">update</button><input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}"></p>
			</form>
		</li>
	{{/value}}
{{/ticketsByCategory}}
</ul>