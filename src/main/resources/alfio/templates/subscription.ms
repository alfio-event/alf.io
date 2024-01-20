<html lang="{{#i18n}}locale{{/i18n}}">
	<head>
	    <title>{{#i18n}}purchase-context.subscription{{/i18n}}</title>
	    <meta name="subject" content="{{#i18n}}purchase-context.subscription{{/i18n}}" />
        <meta name="author" content="{{organization.name}}" />
        <meta name="description" content="{{#i18n}}purchase-context.subscription{{/i18n}}" />
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

			table {
				margin-bottom:30px;
			}
		</style>
	</head>
	<body>
		<table style="width:100%">
			<tr>
                {{#logo}}
				<td style="width:{{imageWidth}}px; height:{{imageHeight}}px"><img style="margin:auto; width:{{imageWidth}}px; height:{{imageHeight}}px" src="{{logo}}" alt=""/></td>
                {{/logo}}
				<td style="text-align: center; vertical-align: middle;"><h1>{{title}}</h1></td>
			</tr>
		</table>

		<h2>{{#i18n}}reservation-page-complete.subscription{{/i18n}}</h2>
		<table>
			<tr>
				<td style="width:180px">{{#i18n}}reservation-page-complete.subscription.owner{{/i18n}}:</td>
				<td>{{subscription.firstName}} {{subscription.lastName}} &lt;{{subscription.email}}&gt;</td>
			</tr>
			{{#print-additional-fields}}
			<tr>
			    <td style="width:180px">{{fieldName}}:</td>
                <td>{{fieldValue}}</td>
			</tr>
			{{/print-additional-fields}}
		</table>
		<h4>{{#subscriptionDescription}}{{/subscriptionDescription}}</h4>

        {{#displayPin}}
		<p>{{#i18n}}reservation-page-complete.subscription.pin-description{{/i18n}}</p>
		<h3>{{subscription.pin}}</h3>

		<p>{{#i18n}}reservation-page-complete.subscription.id-description{{/i18n}}</p>
		<p>{{subscription.id}}</p>
        <br><br>
        {{/displayPin}}

        <table>
			<tr>
				<td>{{#i18n}}ticket.order-information{{/i18n}}</td>
				<td>{{#i18n}}ticket.order-information-values [{{reservationId}}] [{{reservation.fullName}}]{{/i18n}}</td>
			</tr>
		</table>
	</body>
</html>