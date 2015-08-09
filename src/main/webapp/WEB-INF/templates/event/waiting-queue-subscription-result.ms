{{#error}}
    <div class="alert alert-danger" role="alert">
        <h4>{{#i18n}}show-event.sold-out.subscription-error{{/i18n}}</h4>
    </div>
{{/error}}
{{^error}}
    <div class="alert alert-success" role="alert">
        <h4>{{#i18n}}show-event.sold-out.subscription-complete{{/i18n}}</h4>
    </div>
{{/error}}
