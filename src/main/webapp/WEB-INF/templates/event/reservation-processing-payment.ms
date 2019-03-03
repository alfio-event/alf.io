{{>/event/page-top}}
<script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/reservation-processing-payment-page.js"
    id="config-element"
    data-event-name="{{event.shortName}}"
    data-reservation-id="{{reservation.id}}"
    data-payment-method="{{paymentMethod}}"></script>
{{>/event/header}}

<div class="wizard2 center text-muted">
    <div>
        <div><span class="badge badge-success"><i class="fa fa-check"></i></span><span>{{#i18n}}breadcrumb.step1{{/i18n}}</span></div>
        <div class="wizard2-line"></div>
    </div>
    <div>
        <div><span class="badge badge-success"><i class="fa fa-check"></i></span><span>{{#i18n}}breadcrumb.step2{{/i18n}}</span></div>
        <div class="wizard2-line"></div>
    </div>
    <div class="wizard2-current">
        <div><span class="badge"><i class="fa {{#event.free}}fa-lightbulb-o{{/event.free}}{{^event.free}}fa-cog fa-spin{{/event.free}}"></i></span><span>{{#i18n}}breadcrumb.step3{{#event.free}}.free{{/event.free}}{{/i18n}}</span></div>
        <div class="wizard2-line"></div>
    </div>
    <div>
        <div><span class="badge"><i class="fa fa-thumbs-up"></i></span><span>{{#i18n}}breadcrumb.step4{{/i18n}}</span></div>
    </div>
</div>

<div class="page-header">
    <h2>{{#i18n}}reservation-page.title{{/i18n}}</h2>
</div>

<table class="table">
    <thead>
    <tr>
        <th>{{#i18n}}reservation-page.category{{/i18n}}</th>
        <th class="text-center">{{#i18n}}reservation-page.amount{{/i18n}}</th>
        <th class="text-right">{{#i18n}}reservation-page.price{{/i18n}}</th>
        <th class="text-right">{{#i18n}}reservation-page.subtotal{{/i18n}}</th>
    </tr>
    </thead>
    <tbody>
    {{#orderSummary.summary}}
        <tr>
            <td>{{name}}</td>
            <td class="text-center">{{amount}}</td>
            <td class="text-right">{{price}}</td>
            <td class="text-right">{{subTotal}} {{event.currency}}</td>
        </tr>
    {{/orderSummary.summary}}
    </tbody>
    <tfoot>
    {{^orderSummary.free}}
        {{#orderSummary.displayVat}}
            {{^event.vatIncluded}}
                <tr><td colspan="3">{{#i18n}}reservation-page.vat [{{event.vat}}] [{{vatTranslation}}]{{/i18n}}</td><td class="text-right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
            {{/event.vatIncluded}}
        {{/orderSummary.displayVat}}
    {{/orderSummary.free}}
    <tr><th colspan="3">{{#i18n}}reservation-page.total{{/i18n}}</th><td class="text-right"><strong>{{orderSummary.totalPrice}} {{event.currency}}</strong></td></tr>

    {{^orderSummary.free}}
        {{#orderSummary.displayVat}}
            {{#event.vatIncluded}}
                <tr><td colspan="3">{{#i18n}}reservation-page.vat-included [{{event.vat}}] [{{vatTranslation}}]{{/i18n}}</td><td class="text-right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
            {{/event.vatIncluded}}
        {{/orderSummary.displayVat}}
        {{^orderSummary.displayVat}}
            <tr><td colspan="4">{{#i18n}}invoice.vat-voided [{{vatTranslation}}]{{/i18n}}</td></tr>
        {{/orderSummary.displayVat}}
    {{/orderSummary.free}}
    </tfoot>
</table>

<div class="alert alert-info text-center">
    <h4><i class="fa fa-cog fa-spin"></i> {{#i18n}}reservation.payment-in-progress{{/i18n}}</h4>
</div>


{{>/event/page-bottom}}
