{{>/event/page-top}}

<div>
    {{#event.imageIsPresent}}
        <img src="{{event.imageUrl}}" alt="{{event.shortName}}" class="img-responsive img-center">
    {{/event.imageIsPresent}}
</div>

<h1>{{#i18n}}reservation-page-error-status.title{{/i18n}}</h1>
<p>{{#i18n}}reservation-page-error-status.contact-text [{{organizer.email}}] [{{reservation.id}}] [{{organizer.email}}]{{/i18n}}</p>
<p>{{#i18n}}reservation-page-error-status.your-reservation-identifier [{{reservation.id}}]{{/i18n}}</p>

{{>/event/page-bottom}}