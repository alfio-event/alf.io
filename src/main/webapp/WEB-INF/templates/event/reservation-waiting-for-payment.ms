{{>/event/page-top}}

{{>/event/header}}

<div class="alert alert-warning wMarginTop wMarginBottom">
    <h2><i class="fa fa-warning"></i> {{#i18n}}reservation-page-waiting.title [{{#format-date}}{{expires}} {{#i18n}}datetime.pattern{{/i18n}} locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]{{/i18n}}</h2>
</div>

<div class="wMarginBottom wMarginTop">&nbsp;</div>

<div class="wMarginBottom wMarginTop center-block">
    <h4>{{#i18n}}reservation-page-waiting.required-steps{{/i18n}}</h4>
    <ol>
        {{#hasBankAccountOwnerSet}}
        <li>
            <p class="no-margin-bottom">{{#i18n}}reservation-page-waiting.required-steps.1.with-bank-account-owner [{{event.currency}} {{totalPrice}}] [{{bankAccount}}]{{/i18n}}</p>
            {{#bankAccountOwner}}<p class="bank-account-owner-info">{{.}}</p>{{/bankAccountOwner}}
            <p class="no-margin-bottom">{{#i18n}}reservation-page-waiting.required-steps.1.with-bank-account-owner.2 [<mark>{{paymentReason}}</mark>]{{/i18n}}</p>
        </li>
        {{/hasBankAccountOwnerSet}}
        {{^hasBankAccountOwnerSet}}
        <li>{{#i18n}}reservation-page-waiting.required-steps.1 [{{event.currency}} {{totalPrice}}] [{{bankAccount}}] [<mark>{{paymentReason}}</mark>]{{/i18n}}</li>
        {{/hasBankAccountOwnerSet}}
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
    {{#userCanDownloadReceiptOrInvoice}}
    {{#reservation.hasInvoiceNumber}}
        <div class="pull-right">
            <a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/invoice" class="btn btn-success" target="_blank">{{#i18n}}reservation-page-complete.download-your-invoice{{/i18n}}</a>
        </div>
    {{/reservation.hasInvoiceNumber}}
    {{/userCanDownloadReceiptOrInvoice}}
    {{^userCanDownloadReceiptOrInvoice}}
        <div class="pull-right">
            {{#i18n}}reservation-page-waiting.invoice-will-be-sent{{/i18n}}
        </div>
    {{/userCanDownloadReceiptOrInvoice}}
</div>
<div class="clearfix wMarginBottom"></div>
<div class="text-center">
    {{#i18n}}reservation-page-complete.order-information [{{reservation.id}}] [{{reservation.fullName}}]{{/i18n}}
</div>
{{>/event/page-bottom}}
