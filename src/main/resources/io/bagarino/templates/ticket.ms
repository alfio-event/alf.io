<html>
	<body>
		<h1>{{event.shortName}}</h1>
		<p>{{#event.sameDay}}
				{{#i18n}}event-days.same-day 
					[{{#format-date}}{{event.begin}}EEEEE dd MMMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] 
					[{{#format-date}}{{event.begin}} HH:mm{{/format-date}}] 
					[{{#format-date}}{{event.end}} HH:mm{{/format-date}}]
				{{/i18n}}
			{{/event.sameDay}}
			{{^event.sameDay}}
				{{#i18n}}event-days.not-same-day 
					[{{#format-date}}{{event.begin}}EEEEE dd MMMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
					[{{#format-date}}{{event.begin}} HH:mm{{/format-date}}]
				{{/i18n}} 
				- 
				{{#i18n}}event-days.not-same-day 
					[{{#format-date}}{{event.end}}EEEEE dd MMMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
					[{{#format-date}}{{event.end}} HH:mm{{/format-date}}]
				{{/i18n}} 
			{{/event.sameDay}}</p>
		<p>{{event.location}}</p>
		
		<p>Ticket holder:</p> 
		<p>{{ticket.fullName}}</p>
		<p>{{ticket.email}}</p>
		<p><img src="{{qrCodeDataUri}}"/></p>
	</body>
</html>