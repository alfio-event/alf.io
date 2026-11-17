import {html, nothing, TemplateResult} from 'lit';
import {classMap} from 'lit/directives/class-map.js';
import {repeat} from 'lit/directives/repeat.js';
import type {SlInput, SlSelect, SlTextarea} from '@shoelace-style/shoelace';
import {SettingsLevel} from '../model/configuration.ts';
import {BoundField, fieldError} from '../components/form-validation.ts';

/**
 * What a control needs to know about a setting, either a setting of alf.io or a parameter of an extension
 */
export interface SettingDescriptor {
    // id of the control, also used by the tests
    id: string;
    label: string;
    componentType: string;
    // the options of a LIST setting
    options?: { value: string; label: string }[];
    // shown when there is no error
    helpText?: string;
    required?: boolean;
    // yes/no settings: the value ("true"/"false") applied when the level doesn't set the setting, if known
    inheritedValue?: string;
    // the level above sets a value, which applies when this level doesn't set the setting
    inheritedSet?: boolean;
    // the value is never shown: the field is empty, and secretStored tells whether a value is saved at this level
    secret?: boolean;
    secretStored?: boolean;
}

/**
 * The value of a secret field whose saved value is to be removed on save (its value is unknown, so the field
 * can't be emptied)
 */
export const REMOVE_SECRET = '\u0000remove-secret';


/**
 * What applies when a setting isn't set at a level
 */
export function notSetLabel(level: SettingsLevel): string {
    if (level.type === 'ORGANIZATION') {
        return 'Same as the system';
    }
    if (level.type === 'EVENT') {
        return 'Same as the organization';
    }
    return 'Default';
}

/**
 * The label of the "not set" choice of a yes/no setting, with the value that then applies (e.g. "Same as the system (Yes)")
 */
function notSetYesNoLabel(level: SettingsLevel, inheritedValue: string | undefined): string {
    const label = notSetLabel(level);
    if (inheritedValue === 'true') {
        return `${label} (Yes)`;
    }
    if (inheritedValue === 'false') {
        return `${label} (No)`;
    }
    return label;
}

/**
 * The control of a setting, bound to a form field whose value is '' when the setting isn't set at this level:
 * - a yes/no setting is a select with a third option, "not set", which names the value that then applies
 * - a text is an input or a textarea, empty when not set
 * - a list is a select with the given options, and "not set"
 * Use with the "form" styles.
 *
 * @param submitted the form has been submitted at least once
 */
export function settingControl(field: BoundField<string>, submitted: boolean, setting: SettingDescriptor,
                               level: SettingsLevel): TemplateResult {
    const error = fieldError(field.state.meta, submitted);
    const classes = classMap({error: error != null, setting: true});
    const value = field.state.value ?? '';
    const helpText = error ?? setting.helpText ?? '';
    const notSet = notSetLabel(level);
    if (setting.componentType === 'BOOLEAN') {
        return html`
            <sl-select id=${setting.id} class=${classes} label=${setting.label} hoist .value=${value}
                       help-text=${helpText}
                       @sl-change=${(e: Event) => field.handleChange(String((e.target as SlSelect).value))}
                       @sl-blur=${() => field.handleBlur()}>
                <sl-option value="">${notSetYesNoLabel(level, setting.inheritedValue)}</sl-option>
                <sl-option value="true">Yes</sl-option>
                <sl-option value="false">No</sl-option>
            </sl-select>
        `;
    }
    if (setting.componentType === 'LIST') {
        return html`
            <sl-select id=${setting.id} class=${classes} label=${setting.label} hoist .value=${value}
                       help-text=${helpText} ?required=${setting.required === true}
                       @sl-change=${(e: Event) => field.handleChange(String((e.target as SlSelect).value))}
                       @sl-blur=${() => field.handleBlur()}>
                <sl-option value="">${notSet}</sl-option>
                ${repeat(setting.options ?? [], (option) => option.value, (option) => html`
                    <sl-option value=${option.value}>${option.label}</sl-option>
                `)}
            </sl-select>
        `;
    }
    if (setting.secret === true) {
        return secretControl(field, setting, level, classes, helpText);
    }
    const placeholder = inheritedPlaceholder(setting, level);
    if (setting.componentType === 'TEXTAREA') {
        return html`
            <sl-textarea id=${setting.id} class=${classes} label=${setting.label} rows="4" resize="auto"
                         placeholder=${placeholder} help-text=${helpText} ?required=${setting.required === true}
                         .value=${value}
                         @sl-input=${(e: Event) => field.handleChange((e.target as SlTextarea).value)}
                         @sl-blur=${() => field.handleBlur()}></sl-textarea>
        `;
    }
    return html`
        <sl-input id=${setting.id} class=${classes} label=${setting.label}
                  placeholder=${placeholder} help-text=${helpText} ?required=${setting.required === true}
                  .value=${value}
                  @sl-input=${(e: Event) => field.handleChange((e.target as SlInput).value)}
                  @sl-blur=${() => field.handleBlur()}></sl-input>
    `;
}

/**
 * The placeholder of an empty text: what applies instead, without telling the value of the level above
 */
function inheritedPlaceholder(setting: SettingDescriptor, level: SettingsLevel): string | typeof nothing {
    if (setting.inheritedSet === true) {
        if (level.type === 'SYSTEM') {
            return 'Set in the server configuration';
        }
        return `${notSetLabel(level)} (a value is set)`;
    }
    if (level.type === 'SYSTEM') {
        return nothing;
    }
    return notSetLabel(level);
}

/**
 * A secret is write-only: its saved value is never sent to the browser. The field is empty, says whether a value
 * is saved, and replaces it with what the user types. "Remove" deletes the saved value on save.
 */
function secretControl(field: BoundField<string>, setting: SettingDescriptor, level: SettingsLevel,
                       classes: ReturnType<typeof classMap>, helpText: string): TemplateResult {
    const removing = field.state.value === REMOVE_SECRET;
    let value = field.state.value ?? '';
    let placeholder = inheritedPlaceholder(setting, level);
    if (removing) {
        value = '';
        placeholder = 'Removed when you save';
    } else if (setting.secretStored === true) {
        placeholder = 'Saved. Type a new value to replace it';
    }
    const help = secretHelp(field, setting, removing, helpText);
    if (setting.componentType === 'TEXTAREA') {
        return html`
            <sl-textarea id=${setting.id} class=${classes} label=${setting.label} rows="4" resize="auto"
                         placeholder=${placeholder} .value=${value} ?disabled=${removing}
                         autocomplete="off" spellcheck="false"
                         @sl-input=${(e: Event) => field.handleChange((e.target as SlTextarea).value)}
                         @sl-blur=${() => field.handleBlur()}>${help}</sl-textarea>
        `;
    }
    // new-password: the browser must not fill in the password of the user
    return html`
        <sl-input id=${setting.id} class=${classes} label=${setting.label} type="password" password-toggle
                  autocomplete="new-password" placeholder=${placeholder} .value=${value} ?disabled=${removing}
                  @sl-input=${(e: Event) => field.handleChange((e.target as SlInput).value)}
                  @sl-blur=${() => field.handleBlur()}>${help}</sl-input>
    `;
}

/**
 * The help of a secret: the error, if any, or why the saved value isn't shown, with the action that removes it
 */
function secretHelp(field: BoundField<string>, setting: SettingDescriptor, removing: boolean,
                    helpText: string): TemplateResult | typeof nothing {
    if (removing) {
        return html`
            <div slot="help-text" class="secret-help">
                The saved value will be removed.
                <sl-button class="undo-remove-secret" variant="text" size="small" @click=${() => field.handleChange('')}>Undo</sl-button>
            </div>
        `;
    }
    if (setting.secretStored !== true) {
        if (helpText === '') {
            return nothing;
        }
        return html`<div slot="help-text">${helpText}</div>`;
    }
    let text = 'For security, the saved value is never shown.';
    if (helpText !== '') {
        text = helpText;
    }
    return html`
        <div slot="help-text" class="secret-help">
            ${text}
            <sl-button class="remove-secret" variant="text" size="small" @click=${() => field.handleChange(REMOVE_SECRET)}>
                <sl-icon slot="prefix" name="trash"></sl-icon>
                Remove the saved value
            </sl-button>
        </div>
    `;
}

/**
 * Checks a value that must be valid JSON, when set
 */
export function validJson(value: unknown): string | undefined {
    const text = String(value ?? '').trim();
    if (text === '' || value === REMOVE_SECRET) {
        return undefined;
    }
    try {
        JSON.parse(text);
        return undefined;
    } catch {
        return 'Please enter valid JSON';
    }
}
