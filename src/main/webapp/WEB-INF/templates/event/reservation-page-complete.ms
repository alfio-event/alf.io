<h1>the reservation is complete</h1>

name/email + button for sending to registered email the ticket

<h2>Tickets</h2>

<ul>
{{#ticketsByCategory}}
	{{#value}}
		<li>{{key}} - {{.}} <a href="{{reservationId}}/download-ticket/{{uuid}}">Download ticket</a>
			<form method="post" action="{{reservationId}}/{{uuid}}">
				<p>TODO: ticket can be downloaded only if full name and email are set. Updating the values invalidate the ticket. (a new one will be sent by email)</p>
				<p>Full name: <input type="text" name="fullName"></p>
				<p>Email: <input type="text" name="email"></p>
				<p><button type="submit">update</button><input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}"></p>
			</form>
		</li>
	{{/value}}
{{/ticketsByCategory}}
</ul>