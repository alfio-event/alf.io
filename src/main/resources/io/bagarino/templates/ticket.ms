<html>
	<body>
		<h1>{{event.shortName}}</h1>
		<p>{{event.begin}}-{{event.end}}</p>
		<p>{{event.location}}</p>
		
		<p>Ticket holder:</p> 
		<p>{{ticket.fullName}}</p>
		<p>{{ticket.email}}</p>
		<p><img src="{{qrCodeDataUri}}"/></p>
	</body>
</html>