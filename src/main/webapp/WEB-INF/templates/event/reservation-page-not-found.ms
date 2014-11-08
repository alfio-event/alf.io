{{>/event/page-top}}

<div>
    {{#event.imageIsPresent}}
        <img src="{{event.imageUrl}}" alt="{{event.shortName}}" class="img-responsive img-center">
    {{/event.imageIsPresent}}
</div>
<div class="clearfix"></div>
<p>{{#i18n}}reservation-page-not-found.reservation-with-id-does-not-exist [{{reservationId}}]{{/i18n}}</p>
<p>{{#i18n}}reservation-page-not-found.go-back-to-event [{{request.contextPath}}/event/{{event.shortName}}/]{{/i18n}}</p>

{{>/event/page-bottom}}