{{>/event/page-top}}
{{>/event/header}}

{{#ticketEmailSent}}
<div class="alert alert-success alert-dismissible hidden-print" role="alert">
  <button type="button" class="close" data-dismiss="alert"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
  <strong>{{#i18n}}email.ticket-email-sent{{/i18n}}</strong>
</div>
{{/ticketEmailSent}}
<div class="ticket">
    <div class="row wMarginBottom">
        <div class="col-xs-7">
            {{>/event/event-details}}
        </div>
        <div class="col-xs-5">
            <img src="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{ticket.uuid}}/code.png" class="img-responsive img-center">
        </div>
    </div>

    <div class="wMarginTop30px wMarginBottom30px">
        <h2>{{#i18n}}ticket.ticket{{/i18n}}</h2>
        <dl class="dl-horizontal">
            <dt>{{#i18n}}ticket.holder{{/i18n}}</dt>
            <dd>{{ticket.fullName}} &lt;{{ticket.email}}&gt;</dd>
            <dt>{{#i18n}}ticket.type{{/i18n}}</dt>
            <dd>{{ticketCategory.name}}</dd>
            <dt>{{#i18n}}ticket.reference-number{{/i18n}}</dt>
            <dd>{{ticket.uuid}}</dd>
            <dt>{{#i18n}}ticket.order-information{{/i18n}}</dt>
            <dd>{{#i18n}}ticket.order-information-values [{{reservation.id}}] [{{reservation.fullName}}]{{/i18n}}</dd>
        </dl>
    </div>


    {{#deskPaymentRequired}}
        <div class="text-center wMarginTop30px wMarginBottom30px">
            <h3>{{#i18n}}ticket.payment-required{{/i18n}}</h3>
        </div>
    {{/deskPaymentRequired}}

    <div class="hidden-print wMarginBottom wMarginTop">
        <form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{ticket.uuid}}/send-ticket-by-email">
            <input type="hidden" name="from" value="ticket">
            <input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
            <div class="container-fluid ">
                <div class="row">
                    <div class="col-md-4 col-md-push-8 col-xs-12"><a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{ticket.uuid}}/download-ticket" class="btn btn-success btn-block"><i class="fa fa-download"></i> {{#i18n}}reservation-page-complete.download-ticket{{/i18n}}</a></div>
                    <div class="col-md-4 col-md-pull-4 col-xs-12"><a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{backSuffix}}" class="btn btn-default btn-block go-back"><i class="fa fa-backward"></i> {{#i18n}}show-ticket.go-back-to-reservation{{/i18n}}</a></div>
                </div>
            </div>
        </form>
    </div>
</div>


<script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/show-ticket.js"></script>

{{>/event/page-bottom}}