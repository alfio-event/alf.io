{{>/event/page-top}}

{{>/event/header}}

{{^ticketsAreAllAssigned}}
<div class="wizard wMarginBottom wMarginTop center">
    <a><span class="badge">1</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step1{{/i18n}}</span></a>
    <a><span class="badge">2</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step2{{/i18n}}</span></a>
    <a><span class="badge">3</span> <span class="hidden-xs">{{#i18n}}breadcrumb.step3{{/i18n}}</span></a>
    <a class="current"><span class="badge badge-inverse">4</span> {{#i18n}}breadcrumb.step4{{/i18n}}</a>
</div>
{{/ticketsAreAllAssigned}}

<div class="wMarginBottom"></div>

<h1>
    {{^additionalServicesOnly}}
        {{#ticketsAreAllAssigned}}
            {{#i18n}}reservation-page-complete.your-tickets [{{event.displayName}}]{{/i18n}}
        {{/ticketsAreAllAssigned}}
        {{^ticketsAreAllAssigned}}
            {{#i18n}}reservation-page-complete.assign-your-tickets [{{event.displayName}}]{{/i18n}}
        {{/ticketsAreAllAssigned}}
    {{/additionalServicesOnly}}
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


<div class="alert alert-success">
    {{#additionalServicesOnly}}
    <div class="row">
        <div class="col-sm-12">
            <h3>{{#i18n}}reservation-page-complete.thanks-for-your-support{{/i18n}}</h3>
        </div>
    </div>
    {{/additionalServicesOnly}}
    {{^additionalServicesOnly}}
    <div class="row">
        <div class="col-sm-12">
            <h3>{{#i18n}}reservation-page-complete.info-assign [{{event.displayName}}]{{/i18n}}</h3>
        </div>
    </div>
    {{/additionalServicesOnly}}
    <div class="row">
        <div class="col-sm-12">
            {{#i18n}}reservation-page-complete.info-assign-email [{{reservation.email}}]{{/i18n}}
        </div>
    </div>
    <div class="row">
        <div class="col-sm-12">
        <form action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/re-send-email" method="post" class="form-inline text-align-right">
            <button type="submit" class="btn btn-default"><i class="fa fa-envelope-o"></i> {{#i18n}}reservation-page-complete.resend-reservation-email{{/i18n}}</button>
            {{#reservation.hasBeenPaid}}
                {{#reservation.hasInvoiceOrReceiptDocument}}
                    {{#reservation.hasInvoiceNumber}}
                        <a class="btn btn-default" href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/invoice" target="_blank"><i class="fa fa-download"></i> {{#i18n}}reservation-page-complete.download-your-invoice{{/i18n}}</a>
                    {{/reservation.hasInvoiceNumber}}
                    {{^reservation.hasInvoiceNumber}}
                        <a class="btn btn-default" href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/receipt" target="_blank"><i class="fa fa-download"></i> {{#i18n}}reservation-page-complete.download-your-receipt{{/i18n}}</a>
                    {{/reservation.hasInvoiceNumber}}
                {{/reservation.hasInvoiceOrReceiptDocument}}
            {{/reservation.hasBeenPaid}}
            <input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
        </form>

        </div>
    </div>
</div>

{{^additionalServicesOnly}}
    {{#ticketsAreAllAssigned}}
    <p class="bg-info ticket-info">{{#i18n}}reservation-page-complete.info-update{{/i18n}}</p>
    {{/ticketsAreAllAssigned}}
{{/additionalServicesOnly}}

<ul class="list-group">
{{#ticketsByCategory}}
    {{#value}}
    <li class="list-group-item">
        <div class="wMarginBottom">
            <h3><i class="fa fa-ticket"></i> {{#i18n}}reservation-page-complete.ticket-nr{{/i18n}}<span class="ticket-counter"></span></h3>
        </div>
        {{#i18n}}reservation-page-complete.ticket-type{{/i18n}} <strong>{{key.name}}</strong>
        <div id="ticket-detail-{{uuid}}" {{#collapseEnabled}}data-collapse-enabled="true" class="collapsible-container"{{/collapseEnabled}}>
                {{> /event/assign-ticket-form}}
        </div>
    </li>
    {{/value}}
{{/ticketsByCategory}}
</ul>
<div class="container-fluid hidden" id="back-to-event-site">
    <div class="row">
        <div class="col-md-4 col-md-push-8 col-xs-12 wMarginBottom"><a href="{{request.contextPath}}/event/{{event.shortName}}/" class="form-control-static btn btn-success btn-block">{{#i18n}}buy-other-tickets{{/i18n}}</a></div>
        <div class="col-md-4 col-md-pull-4 col-xs-12"><a href="{{event.websiteUrl}}" class="form-control-static btn btn-default btn-block">{{#i18n}}to-event-site{{/i18n}}</a></div>
    </div>
</div>
<div class="clearfix wMarginBottom"></div>
<div class="text-center">
    {{#i18n}}reservation-page-complete.order-information [{{reservation.id}}] [{{reservation.fullName}}]{{/i18n}}
</div>
<div class="text-center wMarginTop30px">
    <span class="text-muted"><a href="https://alf.io" title="{{#i18n}}alfio.credits{{/i18n}}" target="_blank" rel="noreferrer noopener">{{#i18n}}alfio.credits{{/i18n}}</a></span>
</div>

<script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
<script src="{{request.contextPath}}/resources/js/h5f/h5f.min.js"></script>
<script src="{{request.contextPath}}/resources/bower_components/bootstrap/dist/js/bootstrap.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/reservation-page-complete.js"></script>
<script src="{{request.contextPath}}/resources/js/event/attendee-form.js"></script>

{{>/event/page-bottom}}