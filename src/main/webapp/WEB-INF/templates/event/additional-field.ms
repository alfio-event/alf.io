<div class="form-group {{#field-has-error}}[{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]] has-error{{/field-has-error}}">
    <label class="col-sm-3 control-label" for="{{name}}-title-{{uuid}}">{{labelDescription}}{{#isRequired}}*{{/isRequired}}</label>
    <div class="col-sm-9">
        {{#inputField}}
            <input type="{{inputType}}" name="{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control {{#isRequired}}field-required{{/isRequired}}"
                   {{#placeholderDescriptionDefined}}placeholder="{{placeholderDescription}}"{{/placeholderDescriptionDefined}}
                {{#maxLengthDefined}}maxlength="{{maxLength}}"{{/maxLengthDefined}}
                {{#minLengthDefined}}minlength="{{minLength}}"{{/minLengthDefined}}
                {{#isRequired}}required{{/isRequired}}
            >
        {{/inputField}}
        {{#euVat}}
            <input type="text" name="{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control {{#isRequired}}field-required{{/isRequired}}"
                   {{#placeholderDescriptionDefined}}placeholder="{{placeholderDescription}}"{{/placeholderDescriptionDefined}}
                {{#maxLengthDefined}}maxlength="{{maxLength}}"{{/maxLengthDefined}}
                {{#minLengthDefined}}minlength="{{minLength}}"{{/minLengthDefined}}
                {{#isRequired}}required{{/isRequired}}
            >
        {{/euVat}}
        {{#textareaField}}
            <textarea name="{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control"
                      {{#placeholderDescriptionDefined}}placeholder="{{placeholderDescription}}"{{/placeholderDescriptionDefined}}
                {{#maxLengthDefined}}maxlength="{{maxLength}}"{{/maxLengthDefined}}
                {{#minLengthDefined}}minlength="{{minLength}}"{{/minLengthDefined}}
                {{#isRequired}}required{{/isRequired}}
            >{{fieldValue}}</textarea>
        {{/textareaField}}
        {{#countryField}}
            <select name="{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control" {{#isRequired}}required{{/isRequired}}>
                <option value=""></option>
                {{#countries}}
                    <option value="{{left}}">{{right}}</option>
                {{/countries}}
            </select>
        {{/countryField}}
        {{#selectField}}
            <select name="{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control" {{#isRequired}}required{{/isRequired}}>
                <option value=""></option>
                {{#translatedRestrictedValue}}<option value="{{left}}" {{^right}}disabled{{/right}}>{{middle}}</option>{{/translatedRestrictedValue}}
            </select>
        {{/selectField}}
    </div>
</div>