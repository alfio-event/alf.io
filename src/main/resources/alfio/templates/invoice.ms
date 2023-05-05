<?xml version="1.0" encoding="UTF-8"?>
<html lang="{{#i18n}}locale{{/i18n}}">
	<head>
	    <title>
	        {{#proforma}}{{#i18n}}invoice.proforma [{{vatTranslation}}]{{/i18n}}{{/proforma}}
	        {{^proforma}}{{#i18n}}invoice.vat-invoice [{{vatTranslation}}]{{/i18n}}{{/proforma}}
        </title>
        <meta name="subject" content="{{#i18n}}invoice.vat-invoice [{{vatTranslation}}]{{/i18n}}" />
        <meta name="author" content="{{organization.name}}" />
        <meta name="description" content="{{#i18n}}invoice.vat-invoice [{{vatTranslation}}]{{/i18n}}" />
		<style>

		    @font-face {
		        font-family: 'DejaVu Sans';
		        src: url('alfio-internal:/DejaVuSans.ttf');
            }

            @page {
                page: A4;
                margin-bottom: 200px;
                @bottom-center{content:element(pf);}
            }

            .pageFooter {
                border-top: 1px solid black;
                padding-top: 10px;
                position:running(pf);
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

            table.summary, table.border {
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

            table.summary, table.summary th, table.summary td,
            table.border th, table.border td {
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
                <td style="text-align: center; vertical-align: middle;">
                    <h1>
                        {{#proforma}}{{#i18n}}invoice.proforma [{{vatTranslation}}]{{/i18n}}{{/proforma}}
                        {{^proforma}}{{#i18n}}invoice.vat-invoice [{{vatTranslation}}]{{/i18n}}{{/proforma}}
                    </h1>
                </td>
            </tr>
        </table>

        <hr>

        <table style="width:100%;" class="mb">
            <tr>
                <td colspan="2" style="text-align: left" class="small">
                    <p>{{invoiceAddress}}</p>
                    <p>{{#i18n}}invoice.vat [{{vatTranslation}}]{{/i18n}} {{vatNr}}</p>
                    <p class="strong">{{#i18n}}invoice.number{{/i18n}} {{ticketReservation.invoiceNumber}}</p>
                    <p>{{#i18n}}invoice.date{{/i18n}} {{#format-date}}{{confirmationDate}} dd/MM/yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}</p>
                    {{#hasCustomerReference}}<p><strong>{{#i18n}}common.customer-reference{{/i18n}}</strong>: {{ticketReservation.customerReference}}</p>{{/hasCustomerReference}}
                    {{#isOfflinePayment}}<p class="strong">{{#i18n}}reservation-page-waiting.title [{{#format-date}}{{expirationDate}} {{#i18n}}datetime.pattern{{/i18n}} locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]{{/i18n}}</p>{{/isOfflinePayment}}
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
                    <td class="text-right">{{subTotalBeforeVat}}</td>
                </tr>
            {{/taxDetail}}
            {{#taxDetail}}
                <tr>
                    <th colspan="3">{{#i18n}}reservation-page.vat [{{taxPercentage}}] [{{vatTranslation}}]{{/i18n}}</th>
                    <th class="text-right">{{subTotalBeforeVat}}</th>
                </tr>
            {{/taxDetail}}
        {{/orderSummary.summary}}
        {{^orderSummary.free}}
            {{#orderSummary.displayVat}}
                <tr>
                    <th colspan="3">{{#i18n}}reservation-page.vat [{{ticketReservation.usedVatPercent}}] [{{vatTranslation}}]{{/i18n}}</th>
                    <th class="text-right">{{orderSummary.totalVAT}}</td>
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
                <tr><th colspan="3" class="strong" class="no-padding">{{#i18n}}reservation-page.total{{/i18n}}</th><th class="text-right strong">{{purchaseContext.currency}} {{orderSummary.totalPrice}}</th></tr>
                {{#orderSummary.displaySplitPaymentNote}}
                    <tr><th colspan="4" class="strong no-padding">{{#i18n}}invoice.vat-not-added [{{vatTranslation}}]{{/i18n}}</th></tr>
                {{/orderSummary.displaySplitPaymentNote}}
            </tfoot>
        </table>

        {{#isOfflinePayment}}
            <div style="margin-top: 1em">
                <p>{{#i18n}}invoice.payment-instruction-no-later-than [{{#format-date}}{{expirationDate}} {{#i18n}}datetime.pattern{{/i18n}} locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]{{/i18n}}</p>
                <br/>
                <p class="strong">{{#i18n}}invoice.payment-instruction{{/i18n}}</p>
                {{#hasBankAccountNr}}
                <p>{{#i18n}}invoice.account-number [{{bankAccountNr}}]{{/i18n}}</p>
                {{/hasBankAccountNr}}
                {{#hasBankAccountOnwer}}
                    <p style="margin-bottom:0.5em">{{#i18n}}invoice.payment-to{{/i18n}}</p>
                    {{#bankAccountOnwerAsList}}
                        <p style="padding-left:1em">{{.}}</p>
                    {{/bankAccountOnwerAsList}}
                {{/hasBankAccountOnwer}}
                <p style="margin-top: 0.5em">{{#i18n}}invoice.payment-reason [{{paymentReason}}]{{/i18n}}</p>
                <p style="margin-top:0.5em">{{#i18n}}invoice.payment-contact [{{organization.email}}]{{/i18n}}</p>
            </div>
        {{/isOfflinePayment}}
        <div style="margin-top: 1em">
            <p>{{#i18n}}invoice.regards{{/i18n}}</p>
            <p>{{organization.name}}</p>
        </div>

    <div class="pageFooter">
        <div class="small">
            <p><strong>{{#i18n}}invoice.order-information{{/i18n}}</strong>: {{ticketReservation.id}}, {{#i18n}}invoice.buyer{{/i18n}} {{ticketReservation.fullName}} &lt;{{ticketReservation.email}}&gt;</p>
            {{#hasRefund}}
            <p class="strong">{{#i18n}}invoice.refund [{{purchaseContext.currency}} {{orderSummary.refundedAmount}}]{{/i18n}}</p>
            {{/hasRefund}}
        </div>
        {{#invoicingAdditionalInfo}}
          <table style="width:100%;margin-top: 1em" class="border">
            {{#italianEInvoicing}}
                <thead>
                    <tr>
                        <th class="small">{{#i18n}}invoice.vat [{{vatTranslation}}]{{/i18n}}</th>
                        <th class="small">{{#i18n}}invoice-fields.fiscalCode{{/i18n}}</th>
                        <th class="small">{{#i18n}}invoice-fields.addresseeItalyEInvoice{{/i18n}}</th>
                        <th class="small">{{#i18n}}invoice-fields.addressee-code{{/i18n}}</th>
                        <th class="small">{{#i18n}}invoice-fields.pec{{/i18n}}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td class="small">{{ticketReservation.vatNr}}</td>
                        <td class="small">{{fiscalCode}}</td>
                        <td class="small">{{referenceType}}</td>
                        <td class="small">{{addresseeCode}}</td>
                        <td class="small">{{pec}}</td>
                    </tr>
                </tbody>
            {{/italianEInvoicing}}
          </table>
          {{#italianEInvoicing}}
              <!-- the following text is in Italian and is required by law. It will only appear if the italian "e-invoice" data collection is active -->
              <p class="small">Documento privo di valenza fiscale ai sensi dell’art. 21 Dpr 633/72. L’originale è disponibile all’indirizzo telematico da Lei fornito oppure nella Sua area riservata dell’Agenzia delle Entrate.</p>
          {{/italianEInvoicing}}
        {{/invoicingAdditionalInfo}}
    </div>
    </body>
</html>