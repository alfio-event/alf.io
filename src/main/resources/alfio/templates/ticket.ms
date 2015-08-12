<?xml version="1.0" encoding="UTF-8"?>
<html>
	<head>
		<style>
			body {
				font-family:"DejaVu Sans Mono";
			}
			h1,h2,p {margin:0;}
			h1 {
				font-size:160%;
			}
			
			h2 {
				font-size:120%;
				font-weight:normal;
				border-bottom:1px solid black;
				margin-bottom:10px;
			}
			
			table {
				margin-bottom:30px;
			}
		</style>
	</head>
	<body>
		<table style="width:100%">
			<tr>
                {{#eventImage}}
				<td style="width:{{imageWidth}}px; height:{{imageHeight}}px"><img style="margin:auto; width:{{imageWidth}}px; height:{{imageHeight}}px" src="{{eventImage}}"/></td>
                {{/eventImage}}
				<td style="text-align: center; vertical-align: middle;"><h1>{{event.displayName}}</h1></td>
			</tr>
		</table>
		<h2>{{#i18n}}ticket.event-info{{/i18n}}</h2>
		<table>
            <tr>
                <td>
                    <table>
                        <tr>
                            <td style="width:180px">{{#i18n}}ticket.date-time{{/i18n}}</td>
                            <td>			{{#event.sameDay}}
                                {{#i18n}}event-days.same-day
                                    [{{#format-date}}{{event.begin}} EEE dd MMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
                                    [{{#format-date}}{{event.begin}} HH:mm{{/format-date}}]
                                    [{{#format-date}}{{event.end}} HH:mm (z){{/format-date}}]
                                {{/i18n}}
                            {{/event.sameDay}}
                                {{^event.sameDay}}
                                    {{#i18n}}event-days.not-same-day
                                        [{{#format-date}}{{event.begin}} EE dd MMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
                                        [{{#format-date}}{{event.begin}} HH:mm{{/format-date}}]
                                    {{/i18n}}
                                    -
                                    {{#i18n}}event-days.not-same-day
                                        [{{#format-date}}{{event.end}} EE dd MMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
                                        [{{#format-date}}{{event.end}} HH:mm (z){{/format-date}}]
                                    {{/i18n}}
                                {{/event.sameDay}}</td>
                        </tr>
                        <tr>
                            <td>{{#i18n}}ticket.location{{/i18n}}</td>
                            <td>{{event.location}}</td>
                        </tr>
                        <tr>
                            <td>{{#i18n}}ticket.organized-by{{/i18n}}</td>
                            <td>{{organization.name}} &lt;{{organization.email}}&gt;</td>
                        </tr>
                    </table>
                </td>
                <td style="width:200px;"><div style="text-align:right"><img src="{{qrCodeDataUri}}"/></div></td>
            </tr>
		</table>
		
		<h2>{{#i18n}}ticket.ticket{{/i18n}}</h2>
		<table>
			<tr>
				<td style="width:180px">{{#i18n}}ticket.holder{{/i18n}}</td>
				<td>{{ticket.fullName}} &lt;{{ticket.email}}&gt;</td>
			</tr>
			<tr>
				<td>{{#i18n}}ticket.type{{/i18n}}</td>
				<td>{{ticketCategory.name}}</td>
			</tr>
			<tr>
				<td>{{#i18n}}ticket.reference-number{{/i18n}}</td>
				<td>{{ticket.uuid}}</td>
			</tr>
			<tr>
				<td>{{#i18n}}ticket.order-information{{/i18n}}</td>
				<td>{{reservation.id}} by {{reservation.fullName}}</td>
			</tr>
		</table>
		{{#deskPaymentRequired}}
			<h3 style="text-align:center"><strong>{{#i18n}}ticket.payment-required{{/i18n}}</strong></h3>
		{{/deskPaymentRequired}}
	</body>
</html>