{{>/event/page-top}}


<h1>
	{{#ticketsAreAllAssigned}}
		{{#i18n}}reservation-page-complete.your-tickets [{{event.shortName}}]{{/i18n}} 
	{{/ticketsAreAllAssigned}}
	{{^ticketsAreAllAssigned}}
		{{#i18n}}reservation-page-complete.assign-your-tickets [{{event.shortName}}]{{/i18n}}
	{{/ticketsAreAllAssigned}}
</h1>

{{#confirmationEmailSent}}
<div class="alert alert-success alert-dismissible" role="alert">
  <button type="button" class="close" data-dismiss="alert"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
  <strong>{{#i18n}}email.confirmation-email-sent{{/i18n}}</strong>
</div>
{{/confirmationEmailSent}}
{{#ticketEmailSent}}
<div class="alert alert-success alert-dismissible" role="alert">
  <button type="button" class="close" data-dismiss="alert"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
  <strong>{{#i18n}}email.ticket-email-sent{{/i18n}}</strong>
</div>
{{/ticketEmailSent}}

<form action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/re-send-email" method="post">
	<button type="submit" class="btn btn-link">{{#i18n}}reservation-page-complete.resend-reservation-email [{{reservation.email}}]{{/i18n}}</button>
	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
</form>

{{#ticketsAreAllAssigned}}
<p class="bg-info ticket-info">{{#i18n}}reservation-page-complete.info-update{{/i18n}}</p>
{{/ticketsAreAllAssigned}}
{{^ticketsAreAllAssigned}}
<p class="bg-info ticket-info">{{#i18n}}reservation-page-complete.info-assign{{/i18n}}</p>
{{/ticketsAreAllAssigned}}

<ul class="list-group">
{{#ticketsByCategory}}
	{{#value}}
		<li class="list-group-item">{{#i18n}}reservation-page-complete.ticket-type{{/i18n}} <strong>{{key.name}}</strong>
			{{#assigned}}
				<div>
					<p>{{#i18n}}reservation-page-complete.assigned-to [{{fullName}}] [{{email}}]{{/i18n}}</p>
					<ul>
						<li> <a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}" class="btn btn-link">{{#i18n}}reservation-page-complete.show-ticket{{/i18n}}</a>
						<li> <a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}/download-ticket" class="btn btn-link">{{#i18n}}reservation-page-complete.download-ticket{{/i18n}}</a>
						<li> <form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}/send-ticket-by-email" class="form-inline"><button type="submit" class="btn btn-link">{{#i18n}}reservation-page-complete.send-ticket-by-email-to [{{email}}]{{/i18n}}</button><input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}"></form>
						<li> <a href="#ticket-{{uuid}}" class="btn btn-link update-ticket-owner">{{#i18n}}reservation-page-complete.update-ticket-owner{{/i18n}}</a>
					</ul>
					
				</div>
			{{/assigned}}
			{{^assigned}}
				<p>You need to fill fullname/email to download the ticket:</p>
			{{/assigned}}
			<form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}" class="form-horizontal {{#assigned}}hide-by-default{{/assigned}}" id="ticket-{{uuid}}">
				<div class="form-group">
					<label class="col-sm-2 control-label" for="full-name-{{uuid}}">{{#i18n}}common.full-name{{/i18n}}</label>
					<div class="col-sm-10">
						<input type="text" name="fullName" value="{{fullName}}" required id="full-name-{{uuid}}" class="form-control"  maxlength="255">
					</div>
				</div>
				<div class="form-group">
					 <label class="col-sm-2 control-label" for="email-{{uuid}}">{{#i18n}}common.email{{/i18n}}</label>
					 <div class="col-sm-10">
						<input type="email" name="email" value="{{email}}" required id="email-{{uuid}}" class="form-control"  maxlength="255">
					</div>
				</div>
				<div class="form-group">
					<div class="col-sm-offset-2 col-sm-10">	
						<button type="submit" class="btn btn-primary pull-right">
							{{#i18n}}{{#assigned}}reservation-page-complete.update{{/assigned}}{{^assigned}}reservation-page-complete.assign{{/assigned}}{{/i18n}}
						</button>

						{{#assigned}}
							<button type="button" class="btn btn-default pull-left cancel-update" data-for-form="ticket-{{uuid}}">{{#i18n}}reservation-page-complete.cancel{{/i18n}}</button>
						{{/assigned}}
						
						<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
					</div>
				</div>
			</form>
		</li>
	{{/value}}
{{/ticketsByCategory}}
</ul>

<div class="text-center">
	{{#i18n}}reservation-page-complete.order-information [{{reservation.id}}] [{{reservation.fullName}}]{{/i18n}}
</div>

<script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/reservation-page-complete.js"></script>
	
{{>/event/page-bottom}}