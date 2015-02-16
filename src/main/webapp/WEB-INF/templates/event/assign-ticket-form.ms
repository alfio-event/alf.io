{{#assigned}}
    <div class="wMarginBottom">
        <p>{{#i18n}}reservation-page-complete.assigned-to [{{fullName}}] [{{email}}]{{/i18n}}</p>
        <div class="row">
            <div class="col-lg-3 col-sm-6 col-xs-12 wMarginBottom"><a href="{{request.contextPath}}/event/{{event.shortName}}/reservation/{{reservationId}}/{{uuid}}" class="btn btn-primary btn-block"><i class="fa fa-search-plus"></i> {{#i18n}}reservation-page-complete.show-ticket{{/i18n}}</a></div>
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
    <!-- -->
    <div class="form-group">
        <label class="col-sm-3 control-label" for="job-title-{{uuid}}">{{#i18n}}reservation-page-complete.job-title{{/i18n}}</label>
        <div class="col-sm-9">
            <input type="text" name="jobTitle" value="{{jobTitle}}" id="job-title-{{uuid}}" class="form-control"  maxlength="255">
        </div>
    </div>
    <div class="form-group">
        <label class="col-sm-3 control-label" for="company-{{uuid}}">{{#i18n}}reservation-page-complete.company{{/i18n}}</label>
        <div class="col-sm-9">
            <input type="text" name="company" value="{{company}}" id="company-{{uuid}}" class="form-control"  maxlength="255">
        </div>
    </div>
    <div class="form-group">
        <label class="col-sm-3 control-label" for="phone-number-{{uuid}}">{{#i18n}}reservation-page-complete.phone-number{{/i18n}}</label>
        <div class="col-sm-9">
            <input type="tel" name="phoneNumber" value="{{phoneNumber}}" id="phone-number-{{uuid}}" class="form-control"  maxlength="255">
        </div>
    </div>
    <div class="form-group">
        <label class="col-sm-3 control-label" for="address-{{uuid}}">{{#i18n}}reservation-page-complete.address{{/i18n}}</label>
        <div class="col-sm-9">
            <textarea name="address" id="address-{{uuid}}" class="form-control"  maxlength="450">{{address}}</textarea>
        </div>
    </div>
    <div class="form-group">
        <label class="col-sm-3 control-label" for="country-{{uuid}}">{{#i18n}}reservation-page-complete.country{{/i18n}}</label>
        <div class="col-sm-9">
            <select name="country" value="{{country}}" id="country-{{uuid}}" class="form-control">
                <option value=""></option>
                {{#countries}}
                    <option value="{{left}}">{{right}}</option>
                {{/countries}}
            </select>
        </div>
    </div>
    <div class="form-group">
        <label class="col-sm-3 control-label" for="t-shirt-size-{{uuid}}">{{#i18n}}reservation-page-complete.t-shirt-size{{/i18n}}</label>
        <div class="col-sm-9">
            <select name="tShirtSize" value="{{tshirtSize}}" id="t-shirt-size-{{uuid}}" class="form-control">
                <option value=""></option>
                <option value="SMALL">Small</option>
                <option value="MEDIUM">Medium</option>
                <option value="LARGE">Large</option>
                <option value="X-LARGE">X-Large</option>
                <option value="2X-LARGE">2X-Large</option>
            </select>
        </div>
    </div>
    <div class="form-group">
        <label class="col-sm-3 control-label" for="notes-{{uuid}}">{{#i18n}}reservation-page-complete.notes{{/i18n}}</label>
        <div class="col-sm-9">
            <textarea name="notes" id="notes-{{uuid}}" class="form-control" maxlength="1024" placeholder="{{#i18n}}reservation-page-complete.notes.placeholder{{/i18n}}">{{notes}}</textarea>
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
        </div>
    </div>
    <div class="text-center loading" id="loading-{{uuid}}"><i class="fa fa-cog fa-2x fa-spin"></i></div>
    <div class="alert alert-danger wMarginTop wMarginBottom hide" id="generic-{{uuid}}-error">{{#i18n}}reservation-page-complete.please-check-input-fields{{/i18n}}</div>
</form>