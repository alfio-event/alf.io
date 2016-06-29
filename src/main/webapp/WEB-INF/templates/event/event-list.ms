{{>/event/page-top}}

<h1>{{#i18n}}event-list.title{{/i18n}}</h1>
<ul class="list-group">
{{#events}}
    <li class="list-group-item">
        <div class="row">
            <div class="col-xs-12 col-sm-7 col-md-4">
                <a href="{{request.contextPath}}/event/{{shortName}}/" class="no-decoration">
                    {{>/event/output-event-image-tag}}
                </a>
            </div>
            <div class="col-xs-12 col-sm-5 col-md-8">
              {{#internal}}
              <a href="{{request.contextPath}}/event/{{shortName}}/" class="no-decoration">
              {{/internal}}
              {{^internal}}
              <a href="{{externalUrl}}" target="_blank" class="no-decoration">
              {{/internal}}

                  <h2>{{displayName}}{{^internal}} <small><i class="fa fa-external-link"></i></small>{{/internal}}</h2>
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
              </a>
            </div>
        </div>
    </li>
{{/events}}
</ul>

{{>/event/page-bottom}}