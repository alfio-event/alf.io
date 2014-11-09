{{>/event/page-top}}

<div>
	{{#event.imageIsPresent}}
        <img src="{{event.imageUrl}}" alt="{{event.shortName}}" class="img-responsive img-center">
	{{/event.imageIsPresent}}
</div>

{{^ticketsAreAllAssigned}}
<ol class="breadcrumb wMarginBottom wMarginTop">
    <li class="active">{{#i18n}}breadcrumb.step1{{/i18n}}</li>
    <li class="active">{{#i18n}}breadcrumb.step2{{/i18n}}</li>
    <li><i class="fa fa-download"></i> {{#i18n}}breadcrumb.step3{{/i18n}}</li>
</ol>
{{/ticketsAreAllAssigned}}

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

{{#ticketsAreAllAssigned}}
<p class="bg-info ticket-info">{{#i18n}}reservation-page-complete.info-update{{/i18n}}</p>
{{/ticketsAreAllAssigned}}

{{^ticketsAreAllAssigned}}
<p class="alert alert-success">{{#i18n}}reservation-page-complete.info-assign [{{event.shortName}}]{{/i18n}}</p>
{{/ticketsAreAllAssigned}}

<ul class="list-group">
{{#ticketsByCategory}}
	{{#value}}
		<li class="list-group-item">{{#i18n}}reservation-page-complete.ticket-type{{/i18n}} <strong>{{key.name}}</strong>
			{{#assigned}}
				<div class="wMarginBottom">
					<p>{{#i18n}}reservation-page-complete.assigned-to [{{fullName}}] [{{email}}]{{/i18n}}</p>
					<ul class="list-inline center">
						<li><a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}" class="btn btn-primary"><i class="fa fa-ticket"></i> {{#i18n}}reservation-page-complete.show-ticket{{/i18n}}</a>
						<li><a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}/download-ticket" class="btn btn-primary"><i class="fa fa-download"></i> {{#i18n}}reservation-page-complete.download-ticket{{/i18n}}</a>
					</ul>
					<ul class="list-inline wMarginTop center">
						<li><form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}/send-ticket-by-email" class="form-inline"><button type="submit" class="btn btn-primary"><i class="fa fa-envelope-o"></i> {{#i18n}}reservation-page-complete.send-ticket-by-email-to [{{email}}]{{/i18n}}</button><input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}"></form>
						{{^lockedAssignment}}
						<li><a href="#ticket-{{uuid}}" class="btn btn-primary update-ticket-owner"><i class="fa fa-mail-forward"></i> {{#i18n}}reservation-page-complete.update-ticket-owner{{/i18n}}</a>
						{{/lockedAssignment}}
					</ul>

				</div>
			{{/assigned}}

			<form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}" class="form-horizontal {{#assigned}}hide-by-default{{/assigned}}" id="ticket-{{uuid}}">
				<div class="form-group">
					<label class="col-sm-3 control-label" for="full-name-{{uuid}}">{{#i18n}}common.full-name{{/i18n}} *</label>
					<div class="col-sm-9">
						<input type="text" name="fullName" value="{{fullName}}" required id="full-name-{{uuid}}" class="form-control"  maxlength="255" autocomplete="name">
					</div>
				</div>
				<div class="form-group">
					 <label class="col-sm-3 control-label" for="email-{{uuid}}">{{#i18n}}common.email{{/i18n}} *</label>
					 <div class="col-sm-9">
						<input type="email" name="email" value="{{email}}" required id="email-{{uuid}}" class="form-control"  maxlength="255" autocomplete="email">
					</div>
				</div>
				<!-- -->
				<div class="form-group">
					 <label class="col-sm-3 control-label" for="job-title-{{uuid}}">{{#i18n}}reservation-page-complete.job-title{{/i18n}}</label>
					 <div class="col-sm-9">
						<input type="text" name="jobTitle" value="{{jobTitle}}" id="job-title-{{uuid}}" class="form-control"  maxlength="255">
					</div>
				</div>
				<div class="form-group">
					 <label class="col-sm-3 control-label" for="company-{{uuid}}">{{#i18n}}reservation-page-complete.company{{/i18n}}</label>
					 <div class="col-sm-9">
						<input type="text" name="company" value="{{company}}" id="company-{{uuid}}" class="form-control"  maxlength="255">
					</div>
				</div>
				<div class="form-group">
					 <label class="col-sm-3 control-label" for="phone-number-{{uuid}}">{{#i18n}}reservation-page-complete.phone-number{{/i18n}}</label>
					 <div class="col-sm-9">
						<input type="tel" name="phoneNumber" value="{{phoneNumber}}" id="phone-number-{{uuid}}" class="form-control"  maxlength="255">
					</div>
				</div>
				<div class="form-group">
					 <label class="col-sm-3 control-label" for="address-{{uuid}}">{{#i18n}}reservation-page-complete.address{{/i18n}}</label>
					 <div class="col-sm-9">
						<textarea name="address" id="address-{{uuid}}" class="form-control"  maxlength="450">{{address}}</textarea>
					</div>
				</div>
				<div class="form-group">
					 <label class="col-sm-3 control-label" for="country-{{uuid}}">{{#i18n}}reservation-page-complete.country{{/i18n}}</label>
					 <div class="col-sm-9">
					 	<select name="country" value="{{country}}" id="country-{{uuid}}" class="form-control">
					 		<option value=""></option>
					 		{{#countries}}
					 			<option value="{{left}}">{{right}}</option>
					 		{{/countries}}
					 	</select>
					</div>
				</div>
				<div class="form-group">
					 <label class="col-sm-3 control-label" for="t-shirt-size-{{uuid}}">{{#i18n}}reservation-page-complete.t-shirt-size{{/i18n}}</label>
					 <div class="col-sm-9">
					 	<select name="tShirtSize" value="{{tshirtSize}}" id="t-shirt-size-{{uuid}}" class="form-control">
					 		<option value=""></option>
					 		<option value="SMALL">Small</option>
					 		<option value="MEDIUM">Medium</option>
					 		<option value="LARGE">Large</option>
					 		<option value="X-LARGE">X-Large</option>
					 		<option value="2X-LARGE">2X-Large</option>
					 	</select>
					</div>
				</div>
				<!-- -->
				<div class="form-group">
					<div class="col-sm-offset-3 col-sm-9">	
						<button type="submit" class="btn btn-success pull-right">
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