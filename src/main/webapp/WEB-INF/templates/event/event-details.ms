<h2>{{#i18n}}ticket.event-info{{/i18n}}</h2>
<div>{{#i18n}}show-event.by{{/i18n}} <strong>{{organization.name}}</strong> <span class="hidden-print">&lt;<a href="mailto:{{organization.email}}">{{organization.email}}</a>&gt;</span></div>
<div>
    <ul class="list-unstyled">
    <li>
        <i class="fa fa-calendar"></i>
        {{#event.sameDay}}
            {{#format-date}}{{event.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}<br/>
        </li>
        <li>
            <i class="fa fa-clock-o"></i>
            {{#i18n}}event-days.single-day.hours
                [{{#format-date}}{{event.begin}} HH:mm{{/format-date}}]
                [{{#format-date}}{{event.end}} HH:mm{{/format-date}}]
            {{/i18n}}
        {{/event.sameDay}}
        {{^event.sameDay}}
            {{#i18n}}event-days.not-same-day
                [{{#format-date}}{{event.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
                [{{#format-date}}{{event.begin}} HH:mm{{/format-date}}]
            {{/i18n}}
            -
            {{#i18n}}event-days.not-same-day
                [{{#format-date}}{{event.end}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]
                [{{#format-date}}{{event.end}} HH:mm{{/format-date}}]
            {{/i18n}}
        {{/event.sameDay}}
        <i class="fa fa-info-circle text-info tooltip-handler hidden-print" title="{{#i18n}}show-event.timezone.warn [{{event.timeZone}}]{{/i18n}}"></i>
    </li>
        <li class="hidden-print">
            <i class="fa fa-calendar-plus-o"></i> <span>{{#i18n}}show-event.add-to-calendar{{/i18n}}</span> <a title="{{#i18n}}show-event.add-to-calendar{{/i18n}} google" href="{{request.contextPath}}/event/{{event.shortName}}/calendar/locale/{{userLanguage}}?type=google"><i class="fa fa-google-plus-square"></i></a> <a title="{{#i18n}}show-event.add-to-calendar{{/i18n}} iCalendar" href="{{request.contextPath}}/event/{{event.shortName}}/calendar/locale/{{userLanguage}}"><i class="fa fa-calendar-check-o"></i></a>
        </li>
        <li>
            <i class="fa fa-compass"></i> {{event.location}}
        </li>
    </ul>
</div>