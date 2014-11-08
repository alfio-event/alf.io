{{>/event/page-top}}

<h1>{{#i18n}}event-list.title{{/i18n}}</h1>
<ul class="list-group">
{{#events}}
	<li class="list-group-item">
		<a href="{{request.contextPath}}/event/{{shortName}}/">
			<div>
                <div>
					{{#imageIsPresent}}
                        <img src="{{imageUrl}}" alt="{{shortName}}" class="img-responsive">
					{{/imageIsPresent}}
					{{^imageIsPresent}}
					<h2>{{shortName}}</h2>
					{{/imageIsPresent}}
                </div>
				<div>
	            {{#sameDay}}
					{{#i18n}}event-days.same-day 
						[{{#format-date}}{{begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] 
						[{{#format-date}}{{begin}} HH:mm{{/format-date}}] 
						[{{#format-date}}{{end}} HH:mm (z){{/format-date}}]
					{{/i18n}}
				{{/sameDay}}
				{{^sameDay}}
					{{#i18n}}event-days.not-same-day 
						[{{#format-date}}{{begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
						[{{#format-date}}{{begin}} HH:mm{{/format-date}}]
					{{/i18n}} 
					- 
					{{#i18n}}event-days.not-same-day 
						[{{#format-date}}{{end}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
						[{{#format-date}}{{end}} HH:mm (z){{/format-date}}]
					{{/i18n}} 
				{{/sameDay}}
				</div>
				<div>{{location}}</div>
			</div>
		</a>
	</li>
{{/events}}
</ul>
	

{{>/event/page-bottom}}