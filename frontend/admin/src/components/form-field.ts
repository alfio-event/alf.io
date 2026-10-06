import {html, TemplateResult} from 'lit';
import {when} from 'lit/directives/when.js';
import {repeat} from 'lit/directives/repeat.js';
import type {SlInput, SlSelect, SlTextarea} from '@shoelace-style/shoelace';

export interface FormFieldOptions {
    label: string;
    type?: 'text' | 'email' | 'number' | 'datetime-local' | 'textarea';
    icon?: string;
    required?: boolean;
    helpText?: string;
}

/**
 * Input bound to a property of an editable copy ("draft") of the data: every change is written to the draft,
 * then onChange is called (e.g. to re-render the host). Use with the "form" styles.
 */
export function formField<T extends object>(draft: T,
                                            key: keyof T & string,
                                            options: FormFieldOptions,
                                            onChange: () => void): TemplateResult {
    const value = String(draft[key] ?? '');
    const update = (e: Event) => {
        (draft as Record<string, unknown>)[key] = (e.target as SlInput | SlTextarea).value;
        onChange();
    };
    return html`${when(options.type === 'textarea',
        () => html`
            <sl-textarea name=${key} label=${options.label} rows="3" resize="auto" ?required=${options.required}
                         help-text=${options.helpText ?? ''} .value=${value} @sl-input=${update}></sl-textarea>`,
        () => html`
            <sl-input name=${key} label=${options.label} type=${options.type ?? 'text'} ?required=${options.required}
                      help-text=${options.helpText ?? ''} .value=${value} @sl-input=${update}>
                ${when(options.icon, () => html`<sl-icon slot="prefix" name=${options.icon!}></sl-icon>`)}
            </sl-input>`)}`;
}

export interface SelectFieldOptions {
    label: string;
    options: { value: string, label: string }[];
    icon?: string;
}

/**
 * Select bound to a property of a draft, see formField(). Use with the "form" styles.
 */
export function selectField<T extends object>(draft: T,
                                             key: keyof T & string,
                                             options: SelectFieldOptions,
                                             onChange: () => void): TemplateResult {
    const update = (e: Event) => {
        (draft as Record<string, unknown>)[key] = (e.target as SlSelect).value;
        onChange();
    };
    return html`
        <sl-select name=${key} label=${options.label} hoist .value=${String(draft[key] ?? '')} @sl-change=${update}>
            ${when(options.icon, () => html`<sl-icon slot="prefix" name=${options.icon!}></sl-icon>`)}
            ${repeat(options.options, (option) => option.value, (option) => html`
                <sl-option value=${option.value}>${option.label}</sl-option>
            `)}
        </sl-select>
    `;
}
