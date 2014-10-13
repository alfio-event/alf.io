{{>/event/page-top}}

<h1>{{event.shortName}}</h1>
<div>
	<i class="fa fa-calendar width-13-center"></i> 
	{{#event.sameDay}}
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
	{{/event.sameDay}}
</div>
<div><i class="fa fa-map-marker width-13-center"></i> {{event.location}}</div>
<div>{{event.description}}</div>


<h2>{{#i18n}}show-event.tickets{{/i18n}}</h2>

{{#hasErrors}}
<div class="alert alert-danger" role="alert">

{{#error.globalErrors}}
	<p><strong>{{#i18n}}{{code}}{{/i18n}}</strong></p>
{{/error.globalErrors}}

</div>
{{/hasErrors}}


<form method="post" action="reserve-tickets" class="form-horizontal">
	<ul class="list-group">
	{{#ticketCategories}}
		<li class="list-group-item {{#expired}}event-expired{{/expired}}">
			<div class="form-group">
				<div class="col-sm-7">
					<label class="control-label">{{name}}</label>
					<div>
						{{#saleInFuture}}
							{{#i18n}}show-event.sales-not-started{{/i18n}}
						{{/saleInFuture}}
						{{^saleInFuture}}
							{{#i18n}}show-event.sales-end [{{#format-date}}{{expiration}} dd.MM.yyyy{{/format-date}}]{{/i18n}}
						{{/saleInFuture}}
					</div>
					<div>
						{{description}}
					</div>
				</div>
				<div class="col-sm-3">
					<label class="control-label">
						{{#free}}
							{{#i18n}}common.free{{/i18n}}	
						{{/free}}
						{{^free}}
							{{price}} {{event.currency}}
						{{/free}}
					</label>
					<div>
						{{^free}}
							{{#event.vatIncluded}}
								{{#i18n}}show-event.incVat [{{event.vat}}]{{/i18n}}
							{{/event.vatIncluded}}
							{{^event.vatIncluded}}
								{{#i18n}}show-event.excVat [{{event.vat}}]{{/i18n}}
							{{/event.vatIncluded}}
						{{/free}}
					</div>
				</div>
				<div class="col-sm-2 text-align-center">
					{{#saleable}}
						<input name="reservation[{{-index}}].ticketCategoryId" value="{{id}}" type="hidden">
						<input name="reservation[{{-index}}].amount" type="text" class="form-control text-align-center" placeholder="0" autocomplete="off" pattern="\d+">
					{{/saleable}}
					{{^saleable}}
						<label class="control-label">N/A</label>
					{{/saleable}}
				</div>
			</div>
		</li>
	{{/ticketCategories}}
	</ul>
	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
	<button type="submit" class="btn btn-primary pull-right">{{#i18n}}show-event.continue{{/i18n}}</button>
	
	<div class="clearfix"></div>
</form>

{{>/event/page-bottom}}