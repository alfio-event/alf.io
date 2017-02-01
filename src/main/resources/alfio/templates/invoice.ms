<?xml version="1.0" encoding="UTF-8"?>
<html>
	<head>
		<style>
			body {
				font-family:"DejaVu Sans Mono";
				font-size:11pt;
			}
			h1,h2,p {margin:0;}

			h2 {
				font-size:120%;
				font-weight:normal;
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

            .margin-bottom-line {
                margin-bottom:1em;
            }

            .border-bottom {
                border-bottom:1px solid black;
            }

            table.summary {
                border-collapse:collapse;
            }

            table.summary, table.summary th, table.summary td {
                border: 1px solid #aaa;
                padding:0;
                margin:0;
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


<table style="width:100%">
    <tr>
        <td style="width:60%">
            <p style="font-size:80%;margin-bottom:2em;">{{invoiceAddress}}</p>
            <p class="margin-bottom-line">{{#i18n}}invoice.to{{/i18n}}</p>
            {{#ticketReservation.hasBillingAddress}}
            {{#ticketReservation.lineSplittedBillingAddress}}
            <p>{{.}}</p>
            {{/ticketReservation.lineSplittedBillingAddress}}
            {{/ticketReservation.hasBillingAddress}}
        </td>
        <td style="width:40%">
            <p class="margin-bottom-line">{{#i18n}}invoice.from{{/i18n}}</p>
            {{#invoiceAddressAsList}}
            <p>{{.}}</p>
            {{/invoiceAddressAsList}}
            <p>{{#i18n}}invoice.vat{{/i18n}} {{vatNr}}</p>
        </td>
    </tr>
</table>

<h2 class="border-bottom">{{#i18n}}invoice.vat-invoice{{/i18n}}</h2>

<table style="width:100%">
    <tr>
        <td style="width:60%">
            {{#i18n}}invoice.number{{/i18n}} {{ticketReservation.invoiceNumber}}
        </td>
        <td>{{#i18n}}invoice.date{{/i18n}} {{#format-date}}{{confirmationDate}} dd MMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}</td>
    </tr>
</table>

<h3 class="border-bottom">{{#i18n}}invoice.buyer{{/i18n}}</h3>
<p>{{ticketReservation.fullName}}</p>
<p>{{ticketReservation.email}}</p>

<h3  class="border-bottom">{{#i18n}}email-confirmation.reservation-summary{{/i18n}}</h3>
<table style="width:100%" class="summary">
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
        {{^event.vatIncluded}}
            <tr><td colspan="3">{{#i18n}}reservation-page.vat [{{event.vat}}]{{/i18n}}</td><td class="text-right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
        {{/event.vatIncluded}}
        {{/orderSummary.free}}
        <tr><th colspan="3">{{#i18n}}reservation-page.total{{/i18n}}</th><td class="text-right"><strong>{{orderSummary.totalPrice}} {{event.currency}}</strong></td></tr>

        {{^orderSummary.free}}
        {{#event.vatIncluded}}
            <tr><td colspan="3">{{#i18n}}reservation-page.vat-included [{{event.vat}}]{{/i18n}}</td><td class="text-right">{{orderSummary.totalVAT}} {{event.currency}}</td></tr>
        {{/event.vatIncluded}}
        {{/orderSummary.free}}
    </tfoot>
</table>


<p>{{#i18n}}invoice.order-information{{/i18n}} {{ticketReservation.id}}</p>

    </body>
</html>