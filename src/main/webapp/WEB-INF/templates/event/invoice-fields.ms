<div class="page-header">
    <h2>{{#i18n}}reservation-page.invoice-details{{/i18n}}</h2>
</div>

<div class="row">
    <div class="col-xs-12">
        <div class="form-group">
            <label for="">
                {{#i18n}}invoice.type{{/i18n}}
            </label>
            <div>
                <label class="radio-inline">
                    <input type="radio" name="addCompanyBillingDetails" id="invoiceTypePrivate" value="false" {{^paymentForm.addCompanyBillingDetails}}checked=""{{/paymentForm.addCompanyBillingDetails}}> {{#i18n}}invoice.type.private{{/i18n}}
                </label>
                <label class="radio-inline">
                    <input type="radio" name="addCompanyBillingDetails" id="invoiceTypeBusiness" value="true" {{#paymentForm.addCompanyBillingDetails}}checked=""{{/paymentForm.addCompanyBillingDetails}}> {{#i18n}}invoice.type.business{{/i18n}}
                </label>
            </div>
        </div>
    </div>
</div>

<div class="row invoice-business">
    <div class="col-xs-12">
        <div class="form-group {{#field-has-error}}[billingAddressCompany] has-error{{/field-has-error}}">
            <label for="billingAddressCompany" class="control-label">{{#i18n}}reservation-page-complete.company{{/i18n}}</label>
            <input id="billingAddressCompany" name="billingAddressCompany" class="form-control" type="text" value="{{paymentForm.billingAddressCompany}}" maxlength="255">
            {{#field-has-error}}[billingAddressCompany]<span class="help-block text-danger">{{#i18n}}{{#field-error}}billingAddressCompany{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
        </div>
    </div>
</div>
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
        <div class="col-xs-12 col-md-6 invoice-business">
            <div class="form-group {{#field-has-error}}[customerReference] has-error{{/field-has-error}}">
                <label for="customerReference" class="control-label">{{#i18n}}common.customer-reference{{/i18n}}</label>
                <input type="text" name="customerReference" id="customerReference" class="form-control" maxlength="50" value="{{paymentForm.customerReference}}">
                {{#field-has-error}}[customerReference]<span class="help-block text-danger">{{#i18n}}{{#field-error}}customerReference{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
            </div>
        </div>
    {{/customerReferenceEnabled}}
</div>
<div class="row invoice-business">
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
        {{^vatNumberStrictlyRequired}}
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
        {{/vatNumberStrictlyRequired}}
    </div>
</div>
{{#enabledItalyEInvoicing}}
<div class="row" id="italyEInvoicing">
    <div>
        <div class="col-md-12">
            <div class="form-group {{#field-has-error}}[italyEInvoicingFiscalCode] has-error{{/field-has-error}}">
                <label for="italyEInvoicingFiscalCode">{{#i18n}}invoice-fields.fiscalCode{{/i18n}}</label>
                <input id="italyEInvoicingFiscalCode" type="text" name="italyEInvoicingFiscalCode" value="{{paymentForm.italyEInvoicingFiscalCode}}" class="form-control">
            </div>
        </div>
        <div class="col-md-12">
            <div class="form-group {{#field-has-error}}[italyEInvoicingReferenceType] has-error{{/field-has-error}}">
                <label for="">
                    {{#i18n}}invoice-fields.addresseeItalyEInvoice{{/i18n}}
                </label>
                {{#field-has-error}}[italyEInvoicingReferenceType]<span class="help-block text-danger">{{#i18n}}{{#field-error}}italyEInvoicingReferenceType{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
            </div>
        </div>
        <div class="col-md-12">
            <div class="form-group {{#field-has-error}}[italyEInvoicingReferenceAddresseeCode] has-error{{/field-has-error}}">
                <label><input {{#paymentForm.italyEInvoicingTypeAddresseeCode}}checked{{/paymentForm.italyEInvoicingTypeAddresseeCode}} type="radio" value="ADDRESSEE_CODE" name="italyEInvoicingReferenceType"><span class="label-after-radio">{{#i18n}}invoice-fields.addressee-code{{/i18n}}</span></label>
                <input type="text" maxlength="7" class="form-control" name="italyEInvoicingReferenceAddresseeCode" value="{{paymentForm.italyEInvoicingReferenceAddresseeCode}}">
                {{#field-has-error}}[italyEInvoicingReferenceAddresseeCode]<span class="help-block text-danger">{{#i18n}}{{#field-error}}italyEInvoicingReferenceAddresseeCode{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
            </div>
        </div>
        <div class="col-md-12">
            <div class="form-group {{#field-has-error}}[italyEInvoicingReferencePEC] has-error{{/field-has-error}}">
                <label><input {{#paymentForm.italyEInvoicingTypePEC}}checked{{/paymentForm.italyEInvoicingTypePEC}} type="radio" value="PEC"  name="italyEInvoicingReferenceType"><span class="label-after-radio">{{#i18n}}invoice-fields.pec{{/i18n}}</span></label>
                <input type="email" class="form-control" name="italyEInvoicingReferencePEC" value="{{paymentForm.italyEInvoicingReferencePEC}}">
                {{#field-has-error}}[italyEInvoicingReferencePEC]<span class="help-block text-danger">{{#i18n}}{{#field-error}}italyEInvoicingReferencePEC{{/field-error}}{{/i18n}}</span>{{/field-has-error}}
            </div>
        </div>
        <div class="col-md-12">
            <div class="form-group">
                <label><input {{#paymentForm.italyEInvoicingTypeNone}}checked{{/paymentForm.italyEInvoicingTypeNone}} type="radio" value="NONE"  name="italyEInvoicingReferenceType"><span class="label-after-radio">{{#i18n}}invoice.fields.neither{{/i18n}}</span></label>
            </div>
        </div>
    </div>
</div>
{{/enabledItalyEInvoicing}}