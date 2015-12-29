{{#assigned}}
    <div class="wMarginBottom">
        <p>{{#i18n}}reservation-page-complete.assigned-to [{{fullName}}] [{{email}}]{{/i18n}} {{#cancellationEnabled}}<a href="#unbind-form-{{uuid}}" id="unbind-{{uuid}}" class="btn btn-xs btn-danger unbind-btn" data-ticket-uid="{{uuid}}"><i class="fa fa-eraser"></i> {{#i18n}}reservation-page-complete.release-button.text{{/i18n}}</a>{{/cancellationEnabled}}</p>
        {{#cancellationEnabled}}
        <div class="hide-by-default" id="unbind-form-{{uuid}}" tabindex="-1" role="dialog" aria-labelledby="modalTitle" aria-hidden="true" data-cancel-ticket="">
            <form name="cancel-ticket" method="POST" action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/cancel-ticket">
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
            <div class="col-lg-3 col-sm-6 col-xs-12 wMarginBottom"><a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{urlSuffix}}" class="btn btn-primary btn-block"><i class="fa fa-search-plus"></i> {{#i18n}}reservation-page-complete.show-ticket{{/i18n}}</a></div>
            <div class="col-lg-3 col-sm-6 col-xs-12 wMarginBottom"><a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}/download-ticket" class="btn btn-primary btn-block"><i class="fa fa-download"></i> {{#i18n}}reservation-page-complete.download-ticket{{/i18n}}</a></div>
            <div class="col-lg-3 col-sm-6 col-xs-12 wMarginBottom"><form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}/send-ticket-by-email" class="form-inline"><button type="submit" class="btn btn-primary btn-block"><i class="fa fa-envelope-o"></i> {{#i18n}}reservation-page-complete.send-ticket-by-email-to [{{email}}]{{/i18n}}</button><input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}"></form></div>
            <div class="col-lg-3 col-sm-6 col-xs-12 wMarginBottom"><a href="#ticket-{{uuid}}" class="btn btn-primary btn-block update-ticket-owner"><i class="fa fa-edit"></i> {{#i18n}}reservation-page-complete.update-ticket-owner{{/i18n}}</a></div>
        </div>
    </div>
{{/assigned}}
{{#validationResult}}
    {{^isSuccess}}
        <div class="alert alert-danger wMarginTop wMarginBottom">{{#i18n}}reservation-page-complete.please-check-input-fields{{/i18n}}</div>
    {{/isSuccess}}
{{/validationResult}}
<form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/ticket/{{uuid}}/assign" class="form-horizontal {{^assigned}}show-by-default{{/assigned}} {{#assigned}}hide-by-default{{/assigned}}" id="ticket-{{uuid}}" data-ticket-uuid="{{uuid}}">
    <div class="form-group">
        <label class="col-sm-3 control-label" for="full-name-{{uuid}}">{{#i18n}}common.full-name{{/i18n}} *</label>
        <div class="col-sm-9">
            {{^lockedAssignment}}
                <input type="text" name="fullName" value="{{fullName}}" required id="full-name-{{uuid}}" class="form-control"  maxlength="255" autocomplete="name">
            {{/lockedAssignment}}
            {{#lockedAssignment}}
                <p class="form-control-static">{{fullName}}</p>
            {{/lockedAssignment}}
        </div>
    </div>
    <div class="form-group">
        <label class="col-sm-3 control-label" for="email-{{uuid}}">{{#i18n}}common.email{{/i18n}} *</label>
        <div class="col-sm-9">
            {{^lockedAssignment}}
                <input type="email" name="email" value="{{email}}" required id="email-{{uuid}}" class="form-control"  maxlength="255" autocomplete="email">
            {{/lockedAssignment}}
            {{#lockedAssignment}}
                <p class="form-control-static">{{email}}</p>
            {{/lockedAssignment}}
        </div>
    </div>
    <!-- optional values -->
    <div class="hidden toggle-collapse center wMarginBottom">
        <span class="collapse-more"><a class="btn btn-primary btn-xs toggle-collapse"><i class="fa fa-arrow-circle-o-down"></i> {{#i18n}}show.optional.fields{{/i18n}}</a></span>
        <span class="collapse-less collapse-toggle hidden"><a class="btn btn-primary btn-xs toggle-collapse"><i class="fa fa-arrow-circle-o-up"></i> {{#i18n}}hide.optional.fields{{/i18n}}</a></span>
    </div>
    <div {{^assigned}}class="collapsible"{{/assigned}}>
        {{#ticketFieldConfiguration}}
            <div class="form-group">
                 <label class="col-sm-3 control-label" for="{{key.name}}-title-{{uuid}}">{{key.labelDescription}}</label>
                 <div class="col-sm-9">
                    {{#key.inputField}}
                        <input type="{{key.inputType}}" name="additional['{{key.name}}']" value="{{value}}" id="{{key.name}}-title-{{uuid}}" class="form-control"
                            {{#key.placeholderDescriptionDefined}}placeholder="{{key.placeholderDescription}}"{{/key.placeholderDescriptionDefined}}
                            {{#key.maxLengthDefined}}maxlength="{{key.maxLength}}"{{/key.maxLengthDefined}}
                            {{#key.minLengthDefined}}minlength="{{key.minLength}}"{{/key.minLengthDefined}}
                        >
                    {{/key.inputField}}
                    {{#key.textareaField}}
                        <textarea name="additional['{{key.name}}']" id="{{key.name}}-{{uuid}}" class="form-control"
                            {{#key.placeholderDescriptionDefined}}placeholder="{{key.placeholderDescription}}"{{/key.placeholderDescriptionDefined}}
                            {{#key.maxLengthDefined}}maxlength="{{key.maxLength}}"{{/key.maxLengthDefined}}
                            {{#key.minLengthDefined}}minlength="{{key.minLength}}"{{/key.minLengthDefined}}
                        >{{value}}</textarea>
                    {{/key.textareaField}}
                    {{#key.countryField}}
                        <select name="additional['{{key.name}}']" value="{{value}}" id="{{key.name}}-{{uuid}}" class="form-control">
                            <option value=""></option>
                            {{#countries}}
                                <option value="{{left}}">{{right}}</option>
                            {{/countries}}
                        </select>
                    {{/key.countryField}}
                    {{#key.selectField}}
                        <select name="additional['{{key.name}}']" value="{{value}}" id="{{key.name}}-{{uuid}}" class="form-control">
                            <option value=""></option>
                            {{#key.translatedRestrictedValue}}<option value="{{key}}">{{value}}</option>{{/key.translatedRestrictedValue}}
                        </select>
                    {{/key.selectField}}
                 </div>
            </div>
        {{/ticketFieldConfiguration}}
        <div class="form-group">
            <label class="col-sm-3 control-label" for="language-{{uuid}}">{{#i18n}}reservation-page-complete.language{{/i18n}}</label>
            <div class="col-sm-9">
                <select name="userLanguage" value="{{userLanguage}}" id="language-{{uuid}}" class="form-control">
                    <option value=""></option>
                    {{#availableLanguages}}
                        <option value="{{language}}">{{displayLanguage}}</option>
                    {{/availableLanguages}}
                </select>
            </div>
        </div>
    </div>
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