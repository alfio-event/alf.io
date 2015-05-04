{{>/event/page-top}}

<div class="page-header">
    <h1 class="center">
    	{{>/event/output-event-image-tag}}
    	{{^event.imageIsPresent}}
    		{{event.shortName}}
    	{{/event.imageIsPresent}}
    </h1>
</div>
<div class="row">
	<div class="col-md-7">
        <div>{{#i18n}}show-event.by{{/i18n}} <strong>{{organizer.name}}</strong> &lt;<a href="mailto:{{organizer.email}}">{{organizer.email}}</a>&gt;</div>
        <div>
			<i class="fa fa-calendar"></i>
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
        <div><i class="fa fa-compass"></i> {{event.location}}</div>
        <div class="event-description">{{event.description}}</div>
    </div>
	<div class="col-md-5">
		<img src="{{locationDescriptor.mapUrl}}" class="img-responsive img-center">
	</div>
</div>

<ol class="breadcrumb wMarginBottom wMarginTop">
    <li><i class="fa fa-check-square-o"></i> {{#i18n}}breadcrumb.step1{{/i18n}}</li>
    <li class="active">{{#i18n}}breadcrumb.step2{{/i18n}}</li>
    <li class="active">{{#i18n}}breadcrumb.step3{{/i18n}}</li>
</ol>

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
					{{^expired}}
					<div>
						{{#saleInFuture}}
							{{#i18n}}show-event.sales-not-started{{/i18n}}
						{{/saleInFuture}}
						{{^saleInFuture}}
							{{#i18n}}show-event.sales-end [{{#format-date}}{{zonedExpiration}} dd.MM.yyyy HH:mm locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]{{/i18n}}
						{{/saleInFuture}}
					</div>
					{{/expired}}
					{{#expired}}
					<div>
						{{#i18n}}show-event.sales-ended [{{#format-date}}{{zonedExpiration}} dd.MM.yyyy HH:mm locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]{{/i18n}}
					</div>
					{{/expired}}
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
							{{#expired}}
                                {{finalPrice}} {{event.currency}}
                            {{/expired}}
							{{^expired}}
                                {{#hasPromoCodeDiscount}}
                                    <del>{{finalPrice}}</del>
                                    <mark>{{discountedPrice}}</mark>
                                    {{event.currency}}
                                {{/hasPromoCodeDiscount}}
                                {{^hasPromoCodeDiscount}}
                                    {{finalPrice}} {{event.currency}}
                                {{/hasPromoCodeDiscount}}
                                <i class="fa fa-info-circle text-info tooltip-handler" title="{{#event.vatIncluded}}{{#i18n}}show-event.incVat [{{event.vat}}]{{/i18n}}{{/event.vatIncluded}}{{^event.vatIncluded}}{{#i18n}}show-event.excVat [{{event.vat}}]{{/i18n}}{{/event.vatIncluded}}"></i>
							{{/expired}}
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
							{{#soldOut}}
								{{#i18n}}show-event.sold-out{{/i18n}}
							{{/soldOut}}
							{{^soldOut}}
								{{#i18n}}show-event.not-available{{/i18n}}
							{{/soldOut}}
						</label>
					{{/saleable}}
				</div>
			</div>
		</li>
	{{/ticketCategories}}
	</ul>

{{#hasPromoCodeDiscount}}

	<hr/>

	<div>
		<input type="hidden" name="promoCodeDiscount" value="{{promoCodeDiscount.promoCode}}">
		<ul class="list-group">
			<li class="list-group-item">
				<div class="row">
					<div class="col-sm-7">{{#i18n}}show-event.promo-code-applied{{/i18n}} {{promoCodeDiscount.promoCode}}</div>
					<div class="col-sm-5">
						{{#promoCodeDiscount.fixedAmount}}
							{{#i18n}}show-event.promo-code-fixed-amount-discount [{{promoCodeDiscount.formattedDiscountAmount}} {{event.currency}}]{{/i18n}}
						{{/promoCodeDiscount.fixedAmount}}
						{{^promoCodeDiscount.fixedAmount}}
							{{#i18n}}show-event.promo-code-percentage-discount [{{promoCodeDiscount.discountAmount}}%]{{/i18n}}
						{{/promoCodeDiscount.fixedAmount}}
					</div>
				</div>
			</li>
		</ul>
	</div>
{{/hasPromoCodeDiscount}}

<div class="wMarginBottom"></div>
{{#hasAccessPromotions}}
<div id="accessRestrictedTokens" class="wMarginBottom">
	<div class="panel panel-default">
		<div class="panel-heading" role="tab" id="headingOne">
			<h4 class="panel-title">
				<i class="fa fa-gift"></i>
				<a data-toggle="collapse" href="#collapseOne" aria-expanded="false" aria-controls="collapseOne">
					{{#i18n}}show-event.promo-code{{/i18n}}
				</a>
			</h4>
		</div>
		<div id="collapseOne" class="panel-collapse collapse" role="tabpanel" aria-labelledby="headingOne">
			<div class="panel-body">
				<div class="hidden text-danger text-center wMarginBottom" id="error-code-not-found">{{#i18n}}error.STEP_1_CODE_NOT_FOUND{{/i18n}}</div>
				<div class="form-inline text-center">
					<label>{{#i18n}}show-event.promo-code-insert{{/i18n}} <input name="promoCodes" autocomplete="off" class="form-control" id="promo-code"></label>
					<button class="btn btn-success" type="button" id="apply-promo-codes">{{#i18n}}show-event.promo-code.apply{{/i18n}}</button>
				</div>
			</div>
		</div>
	</div>
</div>
{{/hasAccessPromotions}}

	<hr/>

	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
	<input type="hidden" name="promoCode" value="{{promoCode}}">

	<div class="row">
		<div class="col-md-4 col-md-push-8 col-xs-12 wMarginBottom"><button type="submit" class="btn btn-success {{#forwardButtonDisabled}}disabled{{/forwardButtonDisabled}} btn-block">{{#i18n}}show-event.continue{{/i18n}}</button></div>
		<div class="col-md-4 col-md-pull-4 col-xs-12"><a href="{{event.websiteUrl}}" class="btn btn-default btn-block">{{#i18n}}to-event-site{{/i18n}}</a></div>
	</div>

	<div class="clearfix"></div>
</form>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
{{#hasAccessPromotions}}
<script src="{{request.contextPath}}/resources/js/event/show-event.js"></script>
{{/hasAccessPromotions}}
<script src="{{request.contextPath}}/resources/bower_components/bootstrap/dist/js/bootstrap.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/bootstrap-handler.js"></script>
{{>/event/page-bottom}}