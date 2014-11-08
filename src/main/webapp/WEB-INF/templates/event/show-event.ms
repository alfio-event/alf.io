{{>/event/page-top}}

<div class="page-header">
    <h1>
    	{{#event.imageIsPresent}}
    		<img src="{{event.imageUrl}}" alt="{{event.shortName}}" class="img-responsive img-center">
    	{{/event.imageIsPresent}}
    	{{^event.imageIsPresent}}
    		{{event.shortName}}
    	{{/event.imageIsPresent}}
    </h1>
</div>
<div class="row">
	<div class="col-md-7">
        <div>{{#i18n}}show-event.by{{/i18n}} <strong>{{organizer.name}}</strong> &lt;<a href="mailto:{{organizer.email}}">{{organizer.email}}</a>&gt;</div>
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
        <div>{{event.location}}</div>
        <div class="event-description">{{event.description}}</div>
    </div>
	<div class="col-md-5">
		<img src="{{locationDescriptor.mapUrl}}" class="img-responsive img-center">
	</div>
</div>

<h2>{{#i18n}}show-event.tickets{{/i18n}}</h2>

{{#hasErrors}}
<div class="alert alert-danger" role="alert">

{{#error.globalErrors}}
	<p><strong>{{#i18n}}{{.}}{{/i18n}}</strong></p>
{{/error.globalErrors}}

</div>
{{/hasErrors}}

<form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reserve-tickets" class="form-horizontal">
	<ul class="list-group">
	{{#ticketCategories}}
		<li class="list-group-item {{#expired}}text-muted{{/expired}} ticket-category-restricted-{{accessRestricted}}">
			<div class="form-group">
				<div class="col-sm-7" id="{{id}}-label">
					<label class="control-label">{{name}}</label>
					<div>
						{{#saleInFuture}}
							{{#i18n}}show-event.sales-not-started{{/i18n}}
						{{/saleInFuture}}
						{{^saleInFuture}}
							{{#i18n}}show-event.sales-end [{{#format-date}}{{zonedExpiration}} dd.MM.yyyy HH:mm locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]{{/i18n}}
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
							{{finalPrice}} {{event.currency}}
                            <i class="fa fa-info-circle text-info tooltip-handler" title="{{#event.vatIncluded}}{{#i18n}}show-event.incVat [{{event.vat}}]{{/i18n}}{{/event.vatIncluded}}{{^event.vatIncluded}}{{#i18n}}show-event.excVat [{{event.vat}}]{{/i18n}}{{/event.vatIncluded}}"></i>
						{{/free}}
					</label>
				</div>
				<div class="col-sm-2 text-align-center">
					{{#saleable}}
						<input name="reservation[{{-index}}].ticketCategoryId" value="{{id}}" type="hidden">
						<select aria-labelledby="{{id}}-label" name="reservation[{{-index}}].amount" class="form-control text-align-center" placeholder="0" autocomplete="off">
							{{#accessRestricted}}
								<option value="0">0</option>
								<option value="1">1</option>
							{{/accessRestricted}}
							{{^accessRestricted}}
								{{#amountOfTickets}}<option value="{{this}}">{{this}}</option>{{/amountOfTickets}}
							{{/accessRestricted}}
						</select>
					{{/saleable}}
					{{^saleable}}
						<label class="control-label">
							{{#soldout}}
								{{#i18n}}show-event.soldout{{/i18n}}
							{{/soldout}}
							{{^soldout}}
								{{#i18n}}show-event.not-available{{/i18n}}
							{{/soldout}}
						</label>
					{{/saleable}}
				</div>
			</div>
		</li>
	{{/ticketCategories}}
	</ul>

	<div class="row wMarginBottom">
		<div class="col-sm-8 col-sm-offset-4">
			{{#hasAccessRestrictedCategory}}
                <div class="form-inline text-center">
                    <label>Apply promotional code: <input name="promoCodes" autocomplete="off" class="form-control" id="promo-code"></label> <button class="btn btn-default" type="button" id="apply-promo-codes">Apply</button>
                </div>
			{{/hasAccessRestrictedCategory}}
		</div>
	</div>

	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
	<input type="hidden" name="promoCode" value="{{promoCode}}">
    <a href="{{event.websiteUrl}}" class="btn btn-default pull-left">{{#i18n}}to-event-site{{/i18n}}</a>
    <button type="submit" class="btn btn-success {{#forwardButtonDisabled}}disabled{{/forwardButtonDisabled}} pull-right">{{#i18n}}show-event.continue{{/i18n}}</button>

	<div class="clearfix"></div>
</form>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
{{#hasAccessRestrictedCategory}}
<script src="{{request.contextPath}}/resources/js/event/show-event.js"></script>
{{/hasAccessRestrictedCategory}}
<script src="{{request.contextPath}}/resources/bower_components/bootstrap/dist/js/bootstrap.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/bootstrap-handler.js"></script>
{{>/event/page-bottom}}