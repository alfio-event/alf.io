import {AdditionalField, PurchaseContextFieldDescriptionContainer} from "../../model/additional-field.ts";
import {html, nothing} from "lit";
import {repeat} from "lit/directives/repeat.js";

export interface LocalizedAdditionalFieldContent {
    locale: string,
    localeLabel: string,
    description: PurchaseContextFieldDescriptionContainer
}

export function renderPreview(fieldContent: LocalizedAdditionalFieldContent, field: AdditionalField) {
    const localizedConfiguration = fieldContent.description.description;
    switch(field.type) {
        case "checkbox":
            return html`
                <label><strong>${localizedConfiguration.label}</strong></label>
                ${repeat(Object.entries(localizedConfiguration.restrictedValues ?? {}), ([k]) => k, ([value, label]) => html`
                    <div><sl-checkbox value=${value}>${label}</sl-checkbox></div>
                `)}

            `;
        case "radio":
            return html`
                <sl-radio-group name=${field.name}>
                    <div slot="label"><strong>${localizedConfiguration.label}</strong></div>
                    ${repeat(Object.entries(localizedConfiguration.restrictedValues ?? {}), ([k]) => k, ([value, label]) => html`
                        <sl-radio value=${value}> ${label}</sl-radio>
                    `)}
                </sl-radio-group>
            `;
        case "country":
            return html`
                <sl-select hoist label=${localizedConfiguration.label}>
                    <sl-option value="C1">Country 1</sl-option>
                    <sl-option value="C2">Country 2</sl-option>
                    <sl-option value="C3">Country 3</sl-option>
                </sl-select>
            `;
        case "select":
            return html`
                <sl-select hoist label=${localizedConfiguration.label}>
                    ${repeat(Object.entries(localizedConfiguration.restrictedValues ?? {}), ([k]) => k, ([value, label]) => html`
                        <sl-option value=${value}>${label}</sl-option>
                    `)}
                </sl-select>
            `;

        case "textarea":
            return html`
                <sl-textarea label=${localizedConfiguration.label} placeholder=${localizedConfiguration.placeholder ?? nothing}></sl-textarea>
            `;

    }

    let inputType;

    if (field.type === 'input:dateOfBirth') {
        inputType = 'date';
    } else if (field.type === 'vat:eu') {
        inputType = 'text';
    } else {
        inputType = field.type.substring(field.type.indexOf(':') + 1);
    }

    return html`
        <sl-input type=${inputType} label=${localizedConfiguration.label} placeholder=${localizedConfiguration.placeholder ?? nothing}>
    `;
}
