
<h1>Ticket has been successfully paid for {{event.shortName}} - {{customerEmail}}</h1>
<div>{{event.begin}}-{{event.end}} {{event.location}}</div>
<div>{{event.description}}</div>

<input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
<button type="submit">Continue</button>
