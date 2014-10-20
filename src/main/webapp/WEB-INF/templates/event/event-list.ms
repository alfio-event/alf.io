{{>/event/page-top}}

<h1>{{#i18n}}event-list.title{{/i18n}}</h1>
<ul class="list-group">
{{#events}}
	<li class="list-group-item">
		<a href="event/{{shortName}}/">
			<div>
				<h2>{{shortName}}</h2>
				<div>
					{{#sameDay}}
						{{#i18n}}event-days.same-day 
							[{{#format-date}}{{begin}}EEEEE dd MMMMM yyyy timezone: {{timeZone}} locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] 
							[{{#format-date}}{{begin}} HH:mm timezone: {{timeZone}}{{/format-date}}] 
							[{{#format-date}}{{end}} HH:mm timezone: {{timeZone}}{{/format-date}}]
						{{/i18n}}
					{{/sameDay}}
					{{^sameDay}}
						{{#i18n}}event-days.not-same-day 
							[{{#format-date}}{{begin}}EEEEE dd MMMMM yyyy timezone: {{timeZone}} locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
							[{{#format-date}}{{begin}} HH:mm timezone: {{timeZone}}{{/format-date}}]
						{{/i18n}} 
						- 
						{{#i18n}}event-days.not-same-day 
							[{{#format-date}}{{end}}EEEEE dd MMMMM yyyy timezone: {{timeZone}} locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
							[{{#format-date}}{{end}} HH:mm timezone: {{timeZone}}{{/format-date}}]
						{{/i18n}} 
					{{/sameDay}} ({{timeZone}})
				</div>
				<div>{{location}}</div>
			</div>
		</a>
	</li>
{{/events}}
</ul>
	

{{>/event/page-bottom}}