<?xml version="1.0" encoding="UTF-8"?>
<html lang="{{#i18n}}locale{{/i18n}}">
	<head>
        <title>{{#i18n}}invoice.credit-note{{/i18n}}</title>
        <meta name="subject" content="{{#i18n}}invoice.credit-note{{/i18n}}" />
        <meta name="author" content="{{organization.name}}" />
        <meta name="description" content="{{#i18n}}invoice.credit-note{{/i18n}}" />
		<style>

		    @font-face {
		        font-family: 'DejaVu Sans';
		        src: url('alfio-internal:/DejaVuSans.ttf');
            }

			body {
				font-family: "DejaVu Sans";
                font-size:10pt;
                line-height:12pt;
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

            table.summary {
                border-collapse:collapse;
                margin-bottom: 20px;
                border-spacing: 0;
            }

            table.summary > tbody > tr:nth-child(even) > td,
            table.summary > tbody > tr:nth-child(even) > th {
                background-color: #f9f9f9;
            }

            table.summary > tbody > tr:last-child > td {
                border-bottom: 1px solid #aaa;
            }

            table.summary, table.summary th, table.summary td {
                border: 1px solid #aaa;
                padding:5px;
                margin:0;
            }

            table.summary > tfoot,
            table.summary > tfoot > tr > th {
                border-color: #ffffff;
            }

            table.summary > tfoot > tr:first-child > th {
                border-top: 1px solid #aaa;
            }

            table.summary > thead > tr > th {
                background-color: #f9f9f9;
            }

            table.summary td, table.summary th {
                min-height: 25px;
            }

            .mb {
                margin-bottom: 3em;
            }

            .mt {
                margin-top: 3em;
            }

            .mb2 {
                margin-bottom: 6em;
            }

            .no-padding {
                padding: 0 !important;
            }
            .small {
                font-size:80%;
            }

		</style>
	</head>
	<body>
        <table style="width:100%">
            <tr>
                {{#eventImage}}<td style="width:{{imageWidth}}px; height:{{imageHeight}}px"><img style="margin:auto; width:{{imageWidth}}px; height:{{imageHeight}}px" src="{{eventImage}}" alt="{{purchaseContext.displayName}}"/></td>{{/eventImage}}
                {{^eventImage}}<td style="text-align: center; vertical-align: middle;"><h1>{{purchaseContext.displayName}}</h1></td>{{/eventImage}}
                <td style="text-align: center; vertical-align: middle;"><h1>{{#i18n}}invoice.credit-note{{/i18n}}</h1></td>
            </tr>
        </table>

        <hr>

        <table style="width:100%;" class="mb">
            <tr>
                <td colspan="2" style="text-align: left" class="small">
                    <p>{{invoiceAddress}}</p>
                    <p>{{#i18n}}invoice.vat [{{vatTranslation}}]{{/i18n}} {{vatNr}}</p>
                    {{#creditNoteNumber}}<p class="strong">{{#i18n}}invoice.credit-note.number{{/i18n}} {{creditNoteNumber}}</p>{{/creditNoteNumber}}
                    <p class="strong">{{#i18n}}invoice.number{{/i18n}} {{ticketReservation.invoiceNumber}}</p>
                    <p>{{#i18n}}invoice.date{{/i18n}} {{#format-date}}{{now}} dd/MM/yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}</p>
                    {{#hasCustomerReference}}<p><strong>{{#i18n}}common.customer-reference{{/i18n}}</strong>: {{ticketReservation.customerReference}}</p>{{/hasCustomerReference}}
                </td>
            </tr>
            <tr class="mt">
                <td style="width:60%">
                    &nbsp;
                </td>
                <td style="width:40%">
                    {{#ticketReservation.hasBillingAddress}}
                    {{#ticketReservation.lineSplittedBillingAddress}}
                    <p>{{.}}</p>
                    {{/ticketReservation.lineSplittedBillingAddress}}
                    {{/ticketReservation.hasBillingAddress}}
                    {{#ticketReservation.hasVatNumber}}
                        <p>{{#i18n}}invoice.vat [{{vatTranslation}}]{{/i18n}} {{ticketReservation.vatNr}}</p>
                        <p>{{#country-name}}{{ticketReservation.vatCountryCode}} locale:{{#i18n}}locale{{/i18n}}{{/country-name}}</p>
                    {{/ticketReservation.hasVatNumber}}
                </td>
            </tr>
        </table>


        <h3><span class="strong">{{purchaseContext.displayName}}</span>{{#isEvent}},
            <i>
                {{#purchaseContext.sameDay}}{{#i18n}}event-days.same-day [{{#format-date}}{{purchaseContext.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{purchaseContext.begin}} HH:mm{{/format-date}}] [{{#format-date}}{{purchaseContext.end}} HH:mm (z){{/format-date}}] {{/i18n}}{{/purchaseContext.sameDay}}
                {{^purchaseContext.sameDay}}{{#i18n}}event-days.not-same-day [{{#format-date}}{{purchaseContext.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{purchaseContext.begin}} HH:mm{{/format-date}}]{{/i18n}} - {{#i18n}}event-days.not-same-day [{{#format-date}}{{purchaseContext.end}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{purchaseContext.end}} HH:mm (z){{/format-date}}]{{/i18n}} {{/purchaseContext.sameDay}}
            </i>{{/isEvent}}
        </h3>
        <table style="width:100%;" class="summary">
            <thead>
                <tr>
                    <th class="text-center" style="width:10%">{{#i18n}}reservation-page.amount{{/i18n}}</th>
                    <th class="text-center" style="width:60%">{{#i18n}}reservation-page.category{{/i18n}}</th>
                    <th class="text-center" style="width:15%">{{#i18n}}reservation-page.price{{/i18n}} ({{purchaseContext.currency}})</th>
                    <th class="text-center" style="width:15%">{{#i18n}}reservation-page.subtotal{{/i18n}} ({{purchaseContext.currency}})</th>
                </tr>
            </thead>
            <tbody>
        {{#orderSummary.summary}}
            {{^taxDetail}}
                <tr>
                    <td class="text-center">{{amount}}</td>
                    <td>{{name}}</td>
                    <td class="text-right">{{priceBeforeVat}}</td>
                    <td class="text-right">- {{subTotalBeforeVat}}</td>
                </tr>
            {{/taxDetail}}
            {{#taxDetail}}
                <tr>
                    <th colspan="3">{{#i18n}}reservation-page.vat [{{taxPercentage}}] [{{vatTranslation}}]{{/i18n}}</th>
                    <th class="text-right">- {{subTotalBeforeVat}}</th>
                </tr>
            {{/taxDetail}}
        {{/orderSummary.summary}}
        {{^orderSummary.free}}
            {{#orderSummary.displayVat}}
                <tr>
                    <th colspan="3">{{#i18n}}reservation-page.vat [{{ticketReservation.usedVatPercent}}] [{{vatTranslation}}]{{/i18n}}</th>
                    <th class="text-right">- {{orderSummary.totalVAT}}</td>
                </tr>
            {{/orderSummary.displayVat}}
        {{/orderSummary.free}}
            </tbody>
            <tfoot>
                {{^orderSummary.free}}
                    {{^orderSummary.displayVat}}
                        <tr><th colspan="4" class="no-padding">{{#i18n}}invoice.vat-voided [{{vatTranslation}}]{{/i18n}}</th></tr>
                    {{/orderSummary.displayVat}}
                {{/orderSummary.free}}
                <tr><th colspan="3" class="strong" class="no-padding">{{#i18n}}reservation-page.total{{/i18n}} {{purchaseContext.currency}}</th><th class="text-right strong">- {{orderSummary.totalPrice}}</th></tr>
            </tfoot>
        </table>

        <div class="small {{^isOfflinePayment}}mb{{/isOfflinePayment}}" style="margin-top:1em">
            <p class="strong">{{#i18n}}invoice.order-information{{/i18n}}</p>
            <p>{{ticketReservation.id}}</p>
            <p>{{#i18n}}invoice.buyer{{/i18n}} {{ticketReservation.fullName}} &lt;{{ticketReservation.email}}&gt;</p>
        </div>

        <p>{{#i18n}}invoice.regards{{/i18n}}</p>
        <p>{{organization.name}}</p>

    </body>
</html>