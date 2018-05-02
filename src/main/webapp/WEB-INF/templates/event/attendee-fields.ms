{{#useFirstAndLastName}}
    <div class="form-group">
        <label class="col-sm-3 control-label" for="first-name-{{uuid}}">{{#i18n}}common.first-name{{/i18n}} *</label>
        <div class="col-sm-9">
            {{^lockedAssignment}}
                <input type="text" name="{{elementNamePrefix}}firstName" value="{{firstName}}" required id="first-name-{{uuid}}" class="form-control attendee-first-name untouched field-required"  maxlength="255" autocomplete="fname">
            {{/lockedAssignment}}
            {{#lockedAssignment}}
                <p class="form-control-static">{{firstName}}</p>
            {{/lockedAssignment}}
        </div>
    </div>
    <div class="form-group">
        <label class="col-sm-3 control-label" for="last-name-{{uuid}}">{{#i18n}}common.last-name{{/i18n}} *</label>
        <div class="col-sm-9">
            {{^lockedAssignment}}
                <input type="text" name="{{elementNamePrefix}}lastName" value="{{lastName}}" required id="last-name-{{uuid}}" class="form-control  attendee-last-name untouched field-required"  maxlength="255" autocomplete="lname">
            {{/lockedAssignment}}
            {{#lockedAssignment}}
                <p class="form-control-static">{{lastName}}</p>
            {{/lockedAssignment}}
        </div>
    </div>
{{/useFirstAndLastName}}
{{^useFirstAndLastName}}
    <div class="form-group">
        <label class="col-sm-3 control-label" for="full-name-{{uuid}}">{{#i18n}}common.full-name{{/i18n}} *</label>
        <div class="col-sm-9">
            {{^lockedAssignment}}
                <input type="text" name="{{elementNamePrefix}}fullName" value="{{fullName}}" required id="full-name-{{uuid}}" class="form-control attendee-full-name untouched field-required"  maxlength="255" autocomplete="name">
            {{/lockedAssignment}}
            {{#lockedAssignment}}
                <p class="form-control-static">{{fullName}}</p>
            {{/lockedAssignment}}
        </div>
    </div>
{{/useFirstAndLastName}}
<div class="form-group">
    <label class="col-sm-3 control-label" for="email-{{uuid}}">{{#i18n}}common.email{{/i18n}} *</label>
    <div class="col-sm-9">
        {{^lockedAssignment}}
            <input type="email" name="{{elementNamePrefix}}email" value="{{email}}" required id="email-{{uuid}}" class="form-control attendee-email untouched field-required"  maxlength="255" autocomplete="email">
        {{/lockedAssignment}}
        {{#lockedAssignment}}
            <p class="form-control-static">{{email}}</p>
        {{/lockedAssignment}}
    </div>
</div>
<div>
    {{#ticketFieldConfiguration}}
        {{#fields}}
            <div class="form-group">
                <label class="col-sm-3 control-label" for="{{name}}-title-{{uuid}}">{{labelDescription}}{{#isRequired}}*{{/isRequired}}</label>
                <div class="col-sm-9">
                    {{#inputField}}
                        <input type="{{inputType}}" name="{{elementNamePrefix}}additional['{{name}}'][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control {{#isRequired}}field-required{{/isRequired}}"
                               {{#placeholderDescriptionDefined}}placeholder="{{placeholderDescription}}"{{/placeholderDescriptionDefined}}
                            {{#maxLengthDefined}}maxlength="{{maxLength}}"{{/maxLengthDefined}}
                            {{#minLengthDefined}}minlength="{{minLength}}"{{/minLengthDefined}}
                            {{#isRequired}}required{{/isRequired}}
                        >
                    {{/inputField}}
                    {{#textareaField}}
                        <textarea name="{{elementNamePrefix}}additional['{{name}}'][{{fieldIndex}}]" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control"
                                  {{#placeholderDescriptionDefined}}placeholder="{{placeholderDescription}}"{{/placeholderDescriptionDefined}}
                            {{#maxLengthDefined}}maxlength="{{maxLength}}"{{/maxLengthDefined}}
                            {{#minLengthDefined}}minlength="{{minLength}}"{{/minLengthDefined}}
                            {{#isRequired}}required{{/isRequired}}
                        >{{fieldValue}}</textarea>
                    {{/textareaField}}
                    {{#countryField}}
                        <select name="{{elementNamePrefix}}additional['{{name}}'][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control" {{#isRequired}}required{{/isRequired}}>
                            <option value=""></option>
                            {{#countries}}
                                <option value="{{left}}">{{right}}</option>
                            {{/countries}}
                        </select>
                    {{/countryField}}
                    {{#selectField}}
                        <select name="{{elementNamePrefix}}additional['{{name}}'][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control" {{#isRequired}}required{{/isRequired}}>
                            <option value=""></option>
                            {{#translatedRestrictedValue}}<option value="{{key}}">{{value}}</option>{{/translatedRestrictedValue}}
                        </select>
                    {{/selectField}}
                </div>
            </div>
        {{/fields}}
    {{/ticketFieldConfiguration}}
    {{#showAvailableLanguagesInPageTop}}
    <div class="form-group">
        <label class="col-sm-3 control-label" for="language-{{uuid}}">{{#i18n}}reservation-page-complete.language{{/i18n}}</label>
        <div class="col-sm-9">
            <select name="{{elementNamePrefix}}userLanguage" value="{{userLanguage}}" id="language-{{uuid}}" class="form-control">
                <option value=""></option>
                {{#availableLanguages}}
                    <option value="{{language}}">{{displayLanguage}}</option>
                {{/availableLanguages}}
            </select>
        </div>
    </div>
    {{/showAvailableLanguagesInPageTop}}
    {{^showAvailableLanguagesInPageTop}}
        <div><input type="hidden" name="{{elementNamePrefix}}userLanguage" value="{{userLanguage}}"></div>
    {{/showAvailableLanguagesInPageTop}}

</div>