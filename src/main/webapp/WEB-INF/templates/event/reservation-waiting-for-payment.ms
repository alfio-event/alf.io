{{>/event/page-top}}
<div class="alert alert-warning">
    <h3 class="text-center"><i class="fa fa-warning fa-2x"></i> {{#i18n}}reservation-page-waiting.title{{/i18n}}</h3>
</div>
<div class="wMarginBottom wMarginTop center-block">{{#i18n}}reservation-page-waiting.description [{{event.currency}} {{totalPrice}}] [{{#format-date}}{{expires}} dd MMMM yyyy HH:mm locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{bankAccount}}] [{{reservationId}}]{{/i18n}}</div>
{{>/event/page-bottom}}
