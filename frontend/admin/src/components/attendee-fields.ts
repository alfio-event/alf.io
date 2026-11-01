import {html, nothing, TemplateResult} from 'lit';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import type {SlCheckbox, SlInput, SlSelect, SlTextarea} from '@shoelace-style/shoelace';
import {AdditionalField} from '../model/additional-field.ts';

/**
 * Values of the additional fields of an attendee: field name -> values.
 * Checkboxes have one value for each option, null if the option is not selected; the other fields a single value.
 * An empty value removes the stored one.
 */
export type AttendeeFieldValues = Record<string, (string | null)[]>;

export interface Country {
    isoCode: string;
    name: string;
}

export interface AttendeeFieldsOptions {
    // the fields to display, see fieldsForCategory()
    fields: readonly AdditionalField[];
    values: AttendeeFieldValues;
    // needed only by the "country" fields
    countries: readonly Country[];
    // language of the labels; the first available one is used as fallback
    language: string;
    size?: 'small' | 'medium';
}

/**
 * The additional fields describing the attendees of a category, without the ones of the additional items.
 *
 * @param categoryId null for a category that doesn't exist yet, which gets only the fields of all the categories
 */
export function fieldsForCategory(fields: readonly AdditionalField[], categoryId: number | null): AdditionalField[] {
    return attendeeFieldsOfAnyCategory(fields).filter(field => {
        if (field.categoryIds == null || field.categoryIds.length === 0) {
            return true;
        }
        return categoryId != null && field.categoryIds.includes(categoryId);
    });
}

/**
 * The additional fields describing the attendees, of any category, without the ones of the additional items
 */
export function attendeeFieldsOfAnyCategory(fields: readonly AdditionalField[]): AdditionalField[] {
    return fields.filter(field => field.context === 'ATTENDEE' && (field.additionalServiceId == null || field.additionalServiceId < 0));
}

/**
 * The values of the given fields only: a field is sent with an empty value when it has none, so that the stored
 * value is removed.
 */
export function valuesForFields(fields: readonly AdditionalField[], values: AttendeeFieldValues): AttendeeFieldValues {
    const result: AttendeeFieldValues = {};
    for (const field of fields) {
        const current = values[field.name];
        if (current != null) {
            result[field.name] = current;
        } else {
            result[field.name] = [''];
        }
    }
    return result;
}

/**
 * Inputs for the additional fields of an attendee. The organizer can leave any field empty, since the attendee can
 * fill it in later. Use with the "form" and "attendeeFields" styles.
 */
export function attendeeFields(options: AttendeeFieldsOptions,
                               onChange: (values: AttendeeFieldValues) => void): TemplateResult {
    const update = (name: string, values: (string | null)[]) => onChange({ ...options.values, [name]: values });
    return html`
        <div class="attendee-fields">
            ${repeat(options.fields, field => field.name, field => renderField(field, options, values => update(field.name, values)))}
        </div>
    `;
}

function renderField(field: AdditionalField,
                     options: AttendeeFieldsOptions,
                     update: (values: (string | null)[]) => void): TemplateResult {
    const values = options.values[field.name] ?? [];
    const value = values[0] ?? '';
    const label = fieldLabel(field, options.language);
    const size = options.size ?? 'medium';
    switch (field.type) {
        case 'checkbox':
            return renderCheckboxes(field, options, values, update);
        case 'select':
        case 'radio':
            // the index identifies the option: sl-select doesn't support values with spaces
            return html`
                <sl-select class="attendee-field" data-field=${field.name} label=${label} size=${size} hoist clearable
                           .value=${optionIndex(field, value)}
                           @sl-change=${(e: Event) => update([optionValue(field, String((e.target as SlSelect).value))])}>
                    ${repeat(field.restrictedValues ?? [], option => option, (option, index) => html`
                        <sl-option value=${String(index)}>${optionLabel(field, option, options.language)}</sl-option>
                    `)}
                </sl-select>
            `;
        case 'country':
            return renderCountry(field, options, label, value, update);
        case 'textarea':
            return html`
                <sl-textarea class="attendee-field wide" data-field=${field.name} label=${label} size=${size} rows="2" resize="auto"
                             maxlength=${field.maxLength ?? nothing} .value=${value}
                             @sl-input=${(e: Event) => update([(e.target as SlTextarea).value])}></sl-textarea>
            `;
    }
    return html`
        <sl-input class="attendee-field" data-field=${field.name} label=${label} size=${size} type=${inputType(field)}
                  minlength=${field.minLength ?? nothing} maxlength=${field.maxLength ?? nothing} .value=${value}
                  @sl-input=${(e: Event) => update([(e.target as SlInput).value])}></sl-input>
    `;
}

/**
 * One checkbox for each option. The values are stored by position, as the attendees' form does.
 */
function renderCheckboxes(field: AdditionalField,
                          options: AttendeeFieldsOptions,
                          values: (string | null)[],
                          update: (values: (string | null)[]) => void): TemplateResult {
    const restrictedValues = field.restrictedValues ?? [];
    const toggle = (index: number, checked: boolean) => {
        const updated = restrictedValues.map((option, i) => {
            if (i === index) {
                if (checked) {
                    return option;
                }
                return null;
            }
            return values[i] ?? null;
        });
        update(updated);
    };
    return html`
        <fieldset class="attendee-field wide" data-field=${field.name}>
            <legend>${fieldLabel(field, options.language)}</legend>
            ${repeat(restrictedValues, option => option, (option, index) => html`
                <sl-checkbox size=${options.size ?? 'medium'} value=${option} .checked=${values[index] === option}
                             @sl-change=${(e: Event) => toggle(index, (e.target as SlCheckbox).checked)}>
                    ${optionLabel(field, option, options.language)}
                </sl-checkbox>
            `)}
        </fieldset>
    `;
}

function renderCountry(field: AdditionalField,
                       options: AttendeeFieldsOptions,
                       label: string,
                       value: string,
                       update: (values: (string | null)[]) => void): TemplateResult {
    // sl-select ignores a value set before its options: without the countries, the ISO code can be typed
    return html`${when(options.countries.length > 0,
        () => html`
            <sl-select class="attendee-field" data-field=${field.name} label=${label} size=${options.size ?? 'medium'} hoist clearable
                       .value=${value} @sl-change=${(e: Event) => update([String((e.target as SlSelect).value)])}>
                ${repeat(options.countries, country => country.isoCode, country => html`
                    <sl-option value=${country.isoCode}>${country.name}</sl-option>
                `)}
            </sl-select>
        `,
        () => html`
            <sl-input class="attendee-field" data-field=${field.name} label=${label} size=${options.size ?? 'medium'} maxlength="2"
                      help-text="ISO country code" .value=${value}
                      @sl-input=${(e: Event) => update([(e.target as SlInput).value.toUpperCase()])}></sl-input>
        `)}`;
}

function inputType(field: AdditionalField): string {
    if (field.type === 'input:tel') {
        return 'tel';
    }
    if (field.type === 'input:dateOfBirth') {
        return 'date';
    }
    return 'text';
}

function optionIndex(field: AdditionalField, value: string): string {
    const index = (field.restrictedValues ?? []).indexOf(value);
    if (index < 0) {
        return '';
    }
    return String(index);
}

function optionValue(field: AdditionalField, index: string): string {
    if (index === '') {
        return '';
    }
    return (field.restrictedValues ?? [])[Number(index)] ?? '';
}

function localizedDescription(field: AdditionalField, language: string) {
    const descriptions = field.description ?? {};
    return descriptions[language]?.description ?? Object.values(descriptions)[0]?.description;
}

function fieldLabel(field: AdditionalField, language: string): string {
    return localizedDescription(field, language)?.label || field.name;
}

function optionLabel(field: AdditionalField, option: string, language: string): string {
    return localizedDescription(field, language)?.restrictedValues?.[option] || option;
}
