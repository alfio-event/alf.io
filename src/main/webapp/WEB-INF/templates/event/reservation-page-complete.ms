<h1>the reservation is complete</h1>

name/email + button for sending to registered email the ticket

<h2>Tickets</h2>

<ul>
{{#ticketsByCategory}}
	{{#value}}
		<li>{{key}} - {{.}} <a href="{{reservationId}}/download-ticket/{{uuid}}">Download ticket</a></li>
	{{/value}}
{{/ticketsByCategory}}
</ul>