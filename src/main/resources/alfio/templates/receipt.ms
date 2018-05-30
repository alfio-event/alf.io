<?xml version="1.0" encoding="UTF-8"?>
<html>
	<head>
		<style>
			body {
				font-family:"DejaVu Sans Mono";
			}
			h1,h2,p {margin:0;}
			h1 {
				font-size:160%;
			}

            h2 {
				font-size:120%;
				font-weight:normal;
				border-bottom:1px solid black;
				margin-bottom:10px;
			}

			h3 {
			    font-weight:normal;
			}

			table {
				margin-bottom:30px;
			}

			.text-center {
			    text-align:center;
			}

			.text-right {
                text-align:right;
            }
		</style>
	</head>
	<body>
<table style="width:100%">
    <tr>
        {{#eventImage}}<td style="width:{{imageWidth}}px; height:{{imageHeight}}px"><img style="margin:auto; width:{{imageWidth}}px; height:{{imageHeight}}px" src="{{eventImage}}"/></td>{{/eventImage}}
        <td style="text-align: center; vertical-align: middle;"><h1>{{event.displayName}}</h1></td>
    </tr>
</table>
<h2>{{#i18n}}receipt.receipt{{/i18n}}</h2>

<h3>{{#i18n}}receipt.order-information{{/i18n}} {{ticketReservation.id}}</h3>

<div>{{#i18n}}receipt.date{{/i18n}} {{#format-date}}{{confirmationDate}} EEE dd MMM yyyy HH:mm (z) locale:{{#i18n}}locale{{/i18n}}{{/format-date}}</div>


<div>{{#i18n}}receipt.buyer{{/i18n}}</div>
<pre style="font-size: 110%">
{{ticketReservation.fullName}}
{{#ticketReservation.hasBillingAddress}}
{{ticketReservation.billingAddress}}
{{/ticketReservation.hasBillingAddress}}
{{ticketReservation.email}}
</pre>

{{#hasCustomerReference}}
<div>{{#i18n}}common.customer-reference{{/i18n}}</div>
<pre>{{ticketReservation.customerReference}}</pre>
{{/hasCustomerReference}}

<h3>{{#i18n}}email-confirmation.reservation-summary{{/i18n}}</h3>
<table style="width:100%">
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
        {{^ticketReservation.vatIncluded}}
            <tr><td colspan="3">{{#i18n}}reservation-page.vat [{{ticketReservation.usedVatPercent}}] [{{vatTranslation}}]{{/i18n}}</td><td class="text-right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
        {{/ticketReservation.vatIncluded}}
        {{/orderSummary.free}}
        <tr><th colspan="3">{{#i18n}}reservation-page.total{{/i18n}}</th><td class="text-right"><strong>{{orderSummary.totalPrice}} {{event.currency}}</strong></td></tr>

        {{^orderSummary.free}}
        {{#ticketReservation.vatIncluded}}
            <tr><td colspan="3">{{#i18n}}reservation-page.vat-included [{{ticketReservation.usedVatPercent}}] [{{vatTranslation}}]{{/i18n}}</td><td class="text-right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
        {{/ticketReservation.vatIncluded}}
        {{/orderSummary.free}}
    </tfoot>
</table>
<p>{{#hasVat}}{{#i18n}}email-confirmation.vatNr [{{vatNr}}] [{{vatTranslation}}]{{/i18n}}{{/hasVat}}</p>


    </body>
</html>