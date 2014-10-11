{{>/event/page-top}}

	<h1>{{#i18n}}event-list.title{{/i18n}}</h1>
	<ul class="list-group">
	{{#events}}
		<li class="list-group-item">
			<a href="event/{{shortName}}/">
				<div>
					<h2>{{shortName}}</h2>
					<div>{{begin}}-{{end}}</div>
					<div>{{location}}</div>
				</div>
			</a>
		</li>
	{{/events}}
	</ul>
	

{{>/event/page-bottom}}