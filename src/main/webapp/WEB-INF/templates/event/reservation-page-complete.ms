{{>/event/page-top}}


	<h1>{{#i18n}}reservation-page-complete.your-tickets{{/i18n}} {{event.shortName}}</h1>
	<ul class="list-group">
	{{#ticketsByCategory}}
		{{#value}}
			<li class="list-group-item">Category: {{key}} -	ticket:
				{{#assigned}}
					<p><a href="{{reservationId}}/download-ticket/{{uuid}}">{{#i18n}}reservation-page-complete.download-ticket{{/i18n}}</a>.</p>
					<div>TODO: send by email</div>
					<p>You can update the owner of the ticket: a new email will be sent (reminder: the previous sent ticket will be invalidated)</p>
				{{/assigned}}
				{{^assigned}}
					<p>You need to fill fullname/email to download the ticket:</p>
				{{/assigned}}
				<form method="post" action="{{reservationId}}/{{uuid}}" class="form-horizontal">
					<div class="form-group">
						<label class="col-sm-2 control-label" for="full-name-{{uuid}}">{{#i18n}}common.full-name{{/i18n}}</label>
						<div class="col-sm-10">
							<input type="text" name="fullName" value="{{fullName}}" required id="full-name-{{uuid}}" class="form-control">
						</div>
					</div>
					<div class="form-group">
						 <label class="col-sm-2 control-label" for="email-{{uuid}}">{{#i18n}}common.email{{/i18n}}</label>
						 <div class="col-sm-10">
							<input type="email" name="email" value="{{email}}" required id="email-{{uuid}}" class="form-control">
						</div>
					</div>
					<div class="form-group">
    					<div class="col-sm-offset-2 col-sm-10">	
							<button type="submit" class="btn btn-primary">{{#i18n}}reservation-page-complete.update{{/i18n}}</button>
							<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
						</div>
					</div>
				</form>
			</li>
		{{/value}}
	{{/ticketsByCategory}}
	</ul>
	
{{>/event/page-bottom}}