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
                <div id="ticket-detail-{{uuid}}" data-collapse-enabled="false" class="collapsible-container" data-ticket-update-page="true">
                    {{> /event/assign-ticket-form}}
                </div>
            </li>
        {{/value}}
    {{/ticketAndCategory}}
</ul>
<div class="clearfix wMarginBottom"></div>
<div class="text-center">
    {{#i18n}}reservation-page-complete.order-information [{{reservation.id}}] [{{reservation.fullName}}]{{/i18n}}
</div>

<script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
<script src="{{request.contextPath}}/resources/js/h5f/h5f.min.js"></script>
<script src="{{request.contextPath}}/resources/bower_components/bootstrap/dist/js/bootstrap.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/reservation-page-complete.js"></script>
<script src="{{request.contextPath}}/resources/js/event/update-ticket.js"></script>

{{>/event/page-bottom}}