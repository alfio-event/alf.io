{{>/event/page-top}}

{{>/event/header}}

<div class="alert alert-warning wMarginTop wMarginBottom">
    <h2><i class="fa fa-warning"></i> {{#i18n}}reservation-page-waiting.title [{{#format-date}}{{expires}} {{#i18n}}datetime.pattern{{/i18n}} locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]{{/i18n}}</h2>
</div>

<div class="wMarginBottom wMarginTop">&nbsp;</div>

<div class="wMarginBottom wMarginTop center-block">
    <h4>{{#i18n}}reservation-page-waiting.required-steps{{/i18n}}</h4>
    <ol>
        <li>{{#i18n}}reservation-page-waiting.required-steps.1 [{{event.currency}} {{totalPrice}}] [{{bankAccount}}] [<mark>{{paymentReason}}</mark>]{{/i18n}}</li>
        <li>{{#i18n}}reservation-page-waiting.required-steps.2 [{{emailAddress}}] [{{reservation.id}}]{{/i18n}}</li>
        <li>{{#i18n}}reservation-page-waiting.required-steps.3{{/i18n}}</li>
    </ol>
</div>
<div class="text-center wMarginTop">
    <h4>{{#i18n}}reservation-page-waiting.questions [{{emailAddress}}] [{{reservation.id}}]{{/i18n}}</h4>
</div>
<div class="wMarginBottom">&nbsp;</div>
<div class="container-fluid" id="back-to-event-site">
    <div class="pull-left"><a href="{{event.websiteUrl}}" class="btn btn-default">{{#i18n}}to-event-site{{/i18n}}</a></div>
</div>
<div class="clearfix wMarginBottom"></div>
<div class="text-center">
    {{#i18n}}reservation-page-complete.order-information [{{reservation.id}}] [{{reservation.fullName}}]{{/i18n}}
</div>
{{>/event/page-bottom}}
