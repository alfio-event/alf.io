<h1>{{event.shortName}}</h1>
<div>{{event.begin}}-{{event.end}} {{event.location}}</div>
<div>{{event.description}}</div>


<h2>Tickets</h2>
<ul>
{{#ticketCategories}}
	<li>{{description}}, regular price is:  {{event.regularPrice}} {{event.currency}} (vat included: {{event.vatIncluded}}), apply {{discount}}% of discount</li>
{{/ticketCategories}}
</ul>