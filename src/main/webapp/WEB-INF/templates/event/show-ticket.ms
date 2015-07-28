{{>/event/page-top}}
<a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/success">{{#i18n}}show-ticket.go-back-to-reservation{{/i18n}}</a>

<div class="page-header">
<h1 class="center">
    	{{>/event/output-event-image-tag}}
    	{{^event.imageIsPresent}}
    		{{event.displayName}}
    	{{/event.imageIsPresent}}
</h1>
</div>

{{#ticketEmailSent}}
<div class="alert alert-success alert-dismissible" role="alert">
  <button type="button" class="close" data-dismiss="alert"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
  <strong>{{#i18n}}email.ticket-email-sent{{/i18n}}</strong>
</div>
{{/ticketEmailSent}}

<h2>{{#i18n}}ticket.event-info{{/i18n}}</h2>

<table>
	<tr>
		<td class="ticket-header-col">{{#i18n}}ticket.date-time{{/i18n}}</td>
		<td>
			<div>
				{{#event.sameDay}}
					{{#i18n}}event-days.same-day
						[{{#format-date}}{{event.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
						[{{#format-date}}{{event.begin}} HH:mm{{/format-date}}]
						[{{#format-date}}{{event.end}} HH:mm{{/format-date}}]
					{{/i18n}}
				{{/event.sameDay}}
				{{^event.sameDay}}
					{{#i18n}}event-days.not-same-day
						[{{#format-date}}{{event.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
						[{{#format-date}}{{event.begin}} HH:mm{{/format-date}}]
					{{/i18n}}
					-
					{{#i18n}}event-days.not-same-day
						[{{#format-date}}{{event.end}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
						[{{#format-date}}{{event.end}} HH:mm{{/format-date}}]
					{{/i18n}}
				{{/event.sameDay}}
				<i class="fa fa-info-circle text-info tooltip-handler" title="{{#i18n}}show-event.timezone.warn [{{event.timeZone}}]{{/i18n}}"></i>
			</div>
			<div>
			{{#i18n}}show-event.add-to-calendar{{/i18n}} <a href="{{request.contextPath}}/event/{{event.shortName}}/calendar/?type=google">google</a> {{#i18n}}common.or{{/i18n}} <a href="{{request.contextPath}}/event/{{event.shortName}}/calendar/">iCalendar</a></div>
			</div>
		</td>
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
		<td class="ticket-header-col">{{#i18n}}ticket.holder{{/i18n}}</td>
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

{{#deskPaymentRequired}}
	<h3 class="text-center">{{#i18n}}ticket.payment-required{{/i18n}}</h3>
{{/deskPaymentRequired}}


<div><a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{ticket.uuid}}/download-ticket" class="btn btn-link">{{#i18n}}reservation-page-complete.download-ticket{{/i18n}}</a></div> 
<div>
	<form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{ticket.uuid}}/send-ticket-by-email" class="form-inline">
		<button type="submit" class="btn btn-link">{{#i18n}}reservation-page-complete.send-ticket-by-email-to [{{ticket.email}}]{{/i18n}}</button>
		<input type="hidden" name="from" value="ticket">
		<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
	</form>
</div>

<script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/show-ticket.js"></script>

{{>/event/page-bottom}}