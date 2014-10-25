{{>/event/page-top}}
<a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}">{{#i18n}}show-ticket.go-back-to-reservation{{/i18n}}</a>

<h1>{{event.shortName}}</h1>
<h2>{{#i18n}}ticket.event-info{{/i18n}}</h2>

<table>
	<tr>
		<td style="width:150px">{{#i18n}}ticket.date-time{{/i18n}}</td>
		<td>{{event.formattedEventDates}} ({{event.timeZone}})</td>
	</tr>
	<tr>
		<td>{{#i18n}}ticket.location{{/i18n}}</td>
		<td>{{event.location}}</td>
	</tr>
	<tr>
		<td>{{#i18n}}ticket.organized-by{{/i18n}}</td>
		<td>{{organization.name}} &lt;<a href="mailto:{{organization.email}}">{{organization.email}}</a>&gt;</td>
	</tr>
</table>
		
<h2>{{#i18n}}ticket.ticket{{/i18n}}</h2>
<table>
	<tr>
		<td style="width:150px">{{#i18n}}ticket.holder{{/i18n}}</td>
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
		<td>{{#i18n}}ticket.order-information-values [{{reservation.id}}] [{{reservation.fullName}}]{{/i18n}}</td>
	</tr>
</table>

<p class="text-center"><img src="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{ticket.uuid}}/code.png"></p>


<div><a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{ticket.uuid}}/download-ticket" class="btn btn-link">{{#i18n}}reservation-page-complete.download-ticket{{/i18n}}</a></div> 
<div>
	<form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{ticket.uuid}}/send-ticket-by-email" class="form-inline">
		<button type="submit" class="btn btn-link">{{#i18n}}reservation-page-complete.send-ticket-by-email-to [{{ticket.email}}]{{/i18n}}</button>
		<input type="hidden" name="from" value="ticket">
		<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
	</form>
</div>

{{>/event/page-bottom}}