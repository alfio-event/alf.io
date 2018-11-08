<div class="row">
    <div class="col-xs-12">
        <div class="form-group {{#field-has-error}}[billingAddressLine1] has-error{{/field-has-error}}">
            <label for="billingAddressLine1" class="control-label">{{#i18n}}reservation-page.address-line-1{{/i18n}} *</label>
            <input id="billingAddressLine1" name="billingAddressLine1" class="form-control" type="text" required value="{{paymentForm.billingAddressLine1}}" maxlength="255">
            {{#field-has-error}}[billingAddressLine1]<span class="help-block text-danger">{{#i18n}}{{#field-error}}billingAddressLine1{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
        </div>
    </div>
</div>
<div class="row">
    <div class="col-xs-12">
        <div class="form-group">
            <label for="billingAddressLine2" class="control-label">{{#i18n}}reservation-page.address-line-2{{/i18n}}</label>
            <input id="billingAddressLine2" name="billingAddressLine2" class="form-control" type="text" value="{{paymentForm.billingAddressLine2}}" maxlength="255">
        </div>
    </div>
</div>
<div class="row">
    <div class="col-xs-12 col-md-3">
        <div class="form-group {{#field-has-error}}[billingAddressZip] has-error{{/field-has-error}}">
            <label for="billingAddressZip" class="control-label">{{#i18n}}reservation-page.zip-postal-code{{/i18n}} *</label>
            <input id="billingAddressZip" name="billingAddressZip" class="form-control" type="text" required value="{{paymentForm.billingAddressZip}}" maxlength="50">
            {{#field-has-error}}[billingAddressZip]<span class="help-block text-danger">{{#i18n}}{{#field-error}}billingAddressZip{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
        </div>
    </div>
    <div class="col-xs-12 col-md-9">
        <div class="form-group {{#field-has-error}}[billingAddressCity] has-error{{/field-has-error}}">
            <label for="billingAddressCity" class="control-label">{{#i18n}}reservation-page.city{{/i18n}} *</label>
            <input id="billingAddressCity" name="billingAddressCity" class="form-control" type="text" required value="{{paymentForm.billingAddressCity}}" maxlength="255">
            {{#field-has-error}}[billingAddressCity]<span class="help-block text-danger">{{#i18n}}{{#field-error}}billingAddressCity{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
        </div>
    </div>
</div>
<div class="row">
    <div class="col-xs-12 {{#customerReferenceEnabled}}col-md-6{{/customerReferenceEnabled}}">
        <div class="form-group {{#field-has-error}}[vatCountryCode] has-error{{/field-has-error}}">
            <label for="vatCountry" class="control-label">{{#i18n}}reservation-page-complete.country{{/i18n}} *</label>
            <select name="vatCountryCode" id="vatCountry" value="{{paymentForm.vatCountryCode}}" class="form-control field-required" required>
                <option value="">{{#i18n}}reservation-page.country.select{{/i18n}}</option>
                {{#euVatCheckingEnabled}}
                    <optgroup label="{{#i18n}}reservation-page.eu-countries{{/i18n}}" id="optgroup-eu-countries-list">
                        {{#euCountriesForVat}}
                            <option value="{{left}}" data-description="{{right}}">{{left}} - {{right}}</option>
                        {{/euCountriesForVat}}
                    </optgroup>
                <optgroup label="{{#i18n}}reservation-page.all-countries{{/i18n}}">
                {{/euVatCheckingEnabled}}
                {{#countriesForVat}}
                    <option value="{{left}}" data-description="{{right}}">{{left}} - {{right}}</option>
                {{/countriesForVat}}
                {{#euVatCheckingEnabled}}
                </optgroup>
                {{/euVatCheckingEnabled}}
            </select>
            {{#field-has-error}}[vatCountryCode]<span class="help-block text-danger">{{#i18n}}{{#field-error}}vatCountryCode{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
        </div>
    </div>
    {{#customerReferenceEnabled}}
        <div class="col-xs-12 col-md-6">
            <div class="form-group {{#field-has-error}}[customerReference] has-error{{/field-has-error}}">
                <label for="customerReference" class="control-label">{{#i18n}}common.customer-reference{{/i18n}}</label>
                <input type="text" name="customerReference" id="customerReference" class="form-control" maxlength="255" value="{{paymentForm.customerReference}}">
                {{#field-has-error}}[customerReference]<span class="help-block text-danger">{{#i18n}}{{#field-error}}customerReference{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
            </div>
        </div>
    {{/customerReferenceEnabled}}
</div>
<input type="hidden" name="invoiceRequested" id="invoice-requested" value="true">
<div class="row">
    <div id="vat-number-container">
        <div class="col-md-6">
            <div class="form-group {{#field-has-error}}[vatNr] has-error{{/field-has-error}}">
                <label for="vatNr">{{#i18n}}invoice.vat [{{vatTranslation}}]{{/i18n}}</label>
                <div class="input-group">
                    <div class="input-group-addon" id="selected-country-code"></div>
                    <input id="vatNr" type="text" name="vatNr" class="form-control" value="{{paymentForm.vatNr}}"/>
                </div>
                {{#field-has-error}}[vatNr]<span class="help-block text-danger">{{#i18n}}{{#field-error}}vatNr{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
            </div>
        </div>
        <div class="col-md-6">
            <div class="form-group">
                <div class="form-check checkbox checkbox-in-form-group">
                    <label>
                        <input class="form-check-input" name="skipVatNr" id="skip-vat-nr" value="true" type="checkbox" {{#paymentForm.skipVatNr}}checked{{/paymentForm.skipVatNr}}>
                        {{#i18n}}reservation-page.skipVatNr [{{vatTranslation}}]{{/i18n}}
                    </label>
                </div>
            </div>
        </div>
    </div>
</div>