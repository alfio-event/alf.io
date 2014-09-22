<h1>{{event.shortName}}</h1>
<div>{{event.begin}}-{{event.end}} {{event.location}}</div>
<div>{{event.description}}</div>


<h2>Tickets</h2>

<form method="post" action="reserve-tickets">
	<ul>
	{{#ticketCategories}}
		<li>
			{{name}}, regular price is:  {{event.regularPrice}} {{event.currency}} (vat included: {{event.vatIncluded}}), apply {{discount}}% of discount : 
			<input name="reservation[{{-index}}].ticketCategoryId" value="{{id}}" type="hidden">
			<input name="reservation[{{-index}}].amount" type="text">
		</li>
	{{/ticketCategories}}
	</ul>
	<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
	<button type="submit">Continue</button>
</form>