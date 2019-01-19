<div class="form-group {{#field-has-error}}[{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]] has-error{{/field-has-error}}">
    <label class="col-sm-3 control-label" for="{{name}}-title-{{uuid}}">{{labelDescription}}{{#isRequired}}*{{/isRequired}}</label>
    <div class="col-sm-9">
        {{#inputField}}
            {{^lockedAssignment}}
            <input type="{{inputType}}" name="{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control {{#isRequired}}field-required{{/isRequired}}"
                   {{#placeholderDescriptionDefined}}placeholder="{{placeholderDescription}}"{{/placeholderDescriptionDefined}}
                {{#maxLengthDefined}}maxlength="{{maxLength}}"{{/maxLengthDefined}}
                {{#minLengthDefined}}minlength="{{minLength}}"{{/minLengthDefined}}
                {{#isRequired}}required{{/isRequired}}
            >
            {{/lockedAssignment}}
            {{#lockedAssignment}}
            <p id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control-static">{{fieldValue}}</p>
            {{/lockedAssignment}}
        {{/inputField}}
        {{#euVat}}
            {{^lockedAssignment}}
            <input type="text" name="{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control {{#isRequired}}field-required{{/isRequired}}"
                   {{#placeholderDescriptionDefined}}placeholder="{{placeholderDescription}}"{{/placeholderDescriptionDefined}}
                {{#maxLengthDefined}}maxlength="{{maxLength}}"{{/maxLengthDefined}}
                {{#minLengthDefined}}minlength="{{minLength}}"{{/minLengthDefined}}
                {{#isRequired}}required{{/isRequired}}
            >
            {{/lockedAssignment}}
            {{#lockedAssignment}}
            <p id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control-static">{{fieldValue}}</p>
            {{/lockedAssignment}}
        {{/euVat}}
        {{#textareaField}}
            {{^lockedAssignment}}
            <textarea name="{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control {{#isRequired}}field-required{{/isRequired}}"
                      {{#placeholderDescriptionDefined}}placeholder="{{placeholderDescription}}"{{/placeholderDescriptionDefined}}
                {{#maxLengthDefined}}maxlength="{{maxLength}}"{{/maxLengthDefined}}
                {{#minLengthDefined}}minlength="{{minLength}}"{{/minLengthDefined}}
                {{#isRequired}}required{{/isRequired}}
            >{{fieldValue}}</textarea>
            {{/lockedAssignment}}
            {{#lockedAssignment}}
            <p id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control-static like-pre">{{fieldValue}}</p>
            {{/lockedAssignment}}
        {{/textareaField}}
        {{#countryField}}
            {{^lockedAssignment}}
            <select name="{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control {{#isRequired}}field-required{{/isRequired}}" {{#isRequired}}required{{/isRequired}}>
                <option value=""></option>
                {{#countries}}
                    <option value="{{left}}">{{right}}</option>
                {{/countries}}
            </select>
            {{/lockedAssignment}}
            {{#lockedAssignment}}
            <div>
                <p id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control-static select-placeholder">{{fieldValue}}</p>
                <select disabled value="{{fieldValue}}" class="read-only-select hidden">
                    <option value=""></option>
                    {{#countries}}
                        <option value="{{left}}">{{right}}</option>
                    {{/countries}}
                </select>
            </div>
            {{/lockedAssignment}}
        {{/countryField}}
        {{#selectField}}
            {{^lockedAssignment}}
            <select name="{{elementNamePrefix}}additional[{{name}}][{{fieldIndex}}]" value="{{fieldValue}}" id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control {{#isRequired}}field-required{{/isRequired}}" {{#isRequired}}required{{/isRequired}}>
                <option value=""></option>
                {{#translatedRestrictedValue}}<option value="{{left}}" {{^right}}disabled{{/right}}>{{middle}}</option>{{/translatedRestrictedValue}}
            </select>
            {{/lockedAssignment}}
            {{#lockedAssignment}}
            <p id="{{name}}-title-{{uuid}}{{^-first}}-{{fieldCounter}}{{/-first}}" class="form-control-static">{{valueDescription}}</p>
            {{/lockedAssignment}}
        {{/selectField}}
    </div>
</div>