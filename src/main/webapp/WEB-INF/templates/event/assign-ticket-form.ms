{{#assigned}}
    <div class="wMarginBottom">
        <p>{{#i18n}}reservation-page-complete.assigned-to [{{fullName}}] [{{email}}]{{/i18n}} {{#cancellationEnabled}}<a href="#unbind-form-{{uuid}}" id="unbind-{{uuid}}" class="btn btn-xs btn-danger unbind-btn" data-ticket-uid="{{uuid}}"><i class="fa fa-eraser"></i> {{#i18n}}reservation-page-complete.release-button.text{{/i18n}}</a>{{/cancellationEnabled}}</p>
        {{#cancellationEnabled}}
        <div class="hide-by-default" id="unbind-form-{{uuid}}" tabindex="-1" role="dialog" aria-labelledby="modalTitle" aria-hidden="true" data-cancel-ticket="">
            <form name="cancel-ticket" method="POST" action="{{request.contextPath}}/event/{{event.shortName}}/cancel-ticket">
                <input type="hidden" name="ticketId" value="{{uuid}}" />
                <input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}" />
                <div class="alert alert-danger">
                    <h4>{{#i18n}}reservation-page-complete.confirm-cancellation.text{{/i18n}}
                    <button type="button" class="btn btn-default cancel-update" data-for-form="unbind-form-{{uuid}}">{{#i18n}}reservation-page-complete.confirm-cancellation.button.no{{/i18n}}</button>
                    <button type="submit" class="btn btn-danger">{{#i18n}}reservation-page-complete.confirm-cancellation.button.yes{{/i18n}}</button>
                    </h4>
                </div>
            </form>
        </div>
        {{/cancellationEnabled}}
        <div class="row">
            <div class="col-lg-3 col-sm-6 col-xs-12 wMarginBottom"><a href="{{request.contextPath}}/event/{{event.shortName}}/ticket/{{uuid}}/view" target="_blank" class="btn btn-primary btn-block"><i class="fa fa-search-plus"></i> {{#i18n}}reservation-page-complete.show-ticket{{/i18n}}</a></div>
            <div class="col-lg-3 col-sm-6 col-xs-12 wMarginBottom"><a href="{{request.contextPath}}/event/{{event.shortName}}/ticket/{{uuid}}/download-ticket" class="btn btn-primary btn-block"><i class="fa fa-download"></i> {{#i18n}}reservation-page-complete.download-ticket{{/i18n}}</a></div>
            {{#if-config-flag}}[SEND_TICKETS_AUTOMATICALLY]
            <div class="col-lg-3 col-sm-6 col-xs-12 wMarginBottom"><form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/ticket/{{uuid}}/send-ticket-by-email" class="form-inline" data-ticket-uuid="{{uuid}}"><button type="button" class="btn btn-primary btn-block send-ticket-by-email"><i class="fa fa-envelope-o"></i> {{#i18n}}reservation-page-complete.send-ticket-by-email-to [{{email}}]{{/i18n}}</button><input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}"></form></div>
            {{/if-config-flag}}
            <div class="col-lg-3 col-sm-6 col-xs-12 wMarginBottom"><a href="#ticket-{{uuid}}" class="btn btn-primary btn-block update-ticket-owner"><i class="fa fa-edit"></i> {{#i18n}}reservation-page-complete.update-ticket-owner{{/i18n}}</a></div>
        </div>
        <div id="success-{{uuid}}" class="alert alert-success wMarginTop wMarginBottom hidden alert-dismissible" role="alert">
            <button type="button" class="close" data-dismiss="alert"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
            <strong>{{#i18n}}email.ticket-email-sent{{/i18n}}</strong>
        </div>
        <div id="error-{{uuid}}" class="alert alert-danger wMarginTop wMarginBottom hidden alert-dismissible" role="alert">
            <button type="button" class="close" data-dismiss="alert"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
            <strong>{{#i18n}}email.ticket-email-sent{{/i18n}}</strong>
        </div>
    </div>
{{/assigned}}
{{#validationResult}}
    {{^isSuccess}}
        <div class="alert alert-danger wMarginTop wMarginBottom">{{#i18n}}reservation-page-complete.please-check-input-fields{{/i18n}}</div>
    {{/isSuccess}}
{{/validationResult}}
<form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/ticket/{{uuid}}/assign" class="form-horizontal {{#displayAssignForm}}show-by-default{{/displayAssignForm}} {{^displayAssignForm}}hide-by-default{{/displayAssignForm}} {{^assigned}}not-assigned{{/assigned}}" id="ticket-{{uuid}}" data-ticket-uuid="{{uuid}}">

{{> /event/attendee-fields }}

    <div class="text-center hide alert alert-danger" id="error-{{uuid}}"><i class="fa fa-exclamation"></i> {{#i18n}}error.generic{{/i18n}}</div>
    <!-- -->
    <div class="form-group" id="buttons-bar-{{uuid}}">
        <div class="col-sm-offset-3 col-sm-9">
            <button type="submit" class="btn btn-success pull-right submit-assignee-data">
                {{#i18n}}{{#assigned}}reservation-page-complete.update{{/assigned}}{{^assigned}}reservation-page-complete.assign{{/assigned}}{{/i18n}}
            </button>

            {{#assigned}}
                <button type="button" class="btn btn-default pull-left cancel-update" data-for-form="ticket-{{uuid}}">{{#i18n}}reservation-page-complete.cancel{{/i18n}}</button>
            {{/assigned}}

            <input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
            <input type="hidden" name="single-ticket" value="false" />
        </div>
    </div>
    <div class="text-center loading" id="loading-{{uuid}}"><i class="fa fa-cog fa-2x fa-spin"></i></div>
    <div class="alert alert-danger wMarginTop wMarginBottom hide" id="generic-{{uuid}}-error">{{#i18n}}reservation-page-complete.please-check-input-fields{{/i18n}}</div>
</form>
