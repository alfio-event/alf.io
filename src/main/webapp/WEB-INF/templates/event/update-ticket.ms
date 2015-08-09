{{>/event/page-top}}

{{>/event/header}}

<div class="wMarginBottom"></div>

<h1>
    {{#i18n}}show-ticket.header.title [{{event.displayName}}]{{/i18n}}
</h1>

{{#ticketEmailSent}}
    <div class="alert alert-success alert-dismissible" role="alert">
        <button type="button" class="close" data-dismiss="alert"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
        <strong>{{#i18n}}email.ticket-email-sent{{/i18n}}</strong>
    </div>
{{/ticketEmailSent}}

<p class="bg-info ticket-info">{{#i18n}}reservation-page-complete.info-update{{/i18n}}</p>

<ul class="list-group">
    {{#ticketAndCategory}}
        {{#value}}
            <li class="list-group-item">
                <div class="wMarginBottom">
                    <h3><i class="fa fa-ticket"></i> {{#i18n}}reservation-page-complete.ticket-nr{{/i18n}}<span class="ticket-counter"></span></h3>
                </div>
                {{#i18n}}reservation-page-complete.ticket-type{{/i18n}} <strong>{{key.name}}</strong>
                <div id="ticket-detail-{{uuid}}" data-collapse-enabled="true" class="collapsible-container">
                    {{> /event/assign-ticket-form}}
                </div>
            </li>
        {{/value}}
    {{/ticketAndCategory}}
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

<script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
<script src="{{request.contextPath}}/resources/js/h5f/h5f.min.js"></script>
<script src="{{request.contextPath}}/resources/bower_components/bootstrap/dist/js/bootstrap.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/reservation-page-complete.js"></script>

{{>/event/page-bottom}}