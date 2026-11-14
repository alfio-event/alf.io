import {html, nothing, ReactiveController, ReactiveControllerHost, TemplateResult} from 'lit';
import {classMap} from 'lit/directives/class-map.js';
import {when} from 'lit/directives/when.js';
import type {SlInput, SlSelect} from '@shoelace-style/shoelace';

/**
 * Validators and error display for the forms built with TanStackFormController (@tanstack/lit-form).
 *
 * A validator returns the message to display, or undefined if the value is valid. Register it both as onChange and
 * onSubmit validator, so that the errors are updated while the user types and checked again on submit.
 */

export const REQUIRED_MESSAGE = 'Please fill in this field';
export const EMAIL_MESSAGE = 'Please enter a valid e-mail address';

// the rule of <input type="email"> (WHATWG HTML): addresses such as admin@localhost are accepted, as before
const EMAIL_PATTERN = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;

export function requiredValue(value: unknown): string | undefined {
    if (value == null || String(value).trim() === '') {
        return REQUIRED_MESSAGE;
    }
    return undefined;
}

export function emailAddress(value: unknown): string | undefined {
    const missing = requiredValue(value);
    if (missing != null) {
        return missing;
    }
    if (!EMAIL_PATTERN.test(String(value).trim())) {
        return EMAIL_MESSAGE;
    }
    return undefined;
}

/**
 * Required, and at least the given number of characters (spaces at the ends excluded)
 */
export function minLength(value: unknown, length: number): string | undefined {
    const missing = requiredValue(value);
    if (missing != null) {
        return missing;
    }
    if (String(value).trim().length < length) {
        return `Please enter at least ${length} characters`;
    }
    return undefined;
}

/**
 * A URL that must start with https://. Empty is valid: combine with requiredValue for a required URL field
 */
export function httpsUrl(value: unknown): string | undefined {
    const text = String(value ?? '').trim();
    if (text === '') {
        return undefined;
    }
    if (!text.toLowerCase().startsWith('https://')) {
        return 'The URL must start with https://';
    }
    try {
        new URL(text);
        return undefined;
    } catch {
        return 'Please enter a valid URL';
    }
}

/**
 * A required URL that must start with https://
 */
export function requiredHttpsUrl(value: unknown): string | undefined {
    const missing = requiredValue(value);
    if (missing != null) {
        return missing;
    }
    return httpsUrl(value);
}

/**
 * Normalizes a URL so that it starts with https://: prepends the scheme when it is missing,
 * replaces http:// with https://. Applied on blur to every input rendered with type="url"
 */
export function normalizeHttpsUrl(value: string): string {
    const text = value.trim();
    if (text === '') {
        return text;
    }
    if (text.toLowerCase().startsWith('https://')) {
        return text;
    }
    if (text.toLowerCase().startsWith('http://')) {
        return `https://${text.substring('http://'.length)}`;
    }
    return `https://${text}`;
}

export interface ValidatedFieldMeta {
    errors: unknown[];
    isBlurred: boolean;
}

/**
 * The error to display for a field: only once the user has left the field, or has tried to submit the form,
 * so that a field is not marked as wrong while it is being filled in.
 *
 * @param submitted the form has been submitted at least once (formApi.state.submissionAttempts > 0)
 */
export function fieldError(meta: ValidatedFieldMeta, submitted: boolean): string | undefined {
    const error = meta.errors.find(e => e != null && e !== '');
    if (error == null || (!meta.isBlurred && !submitted)) {
        return undefined;
    }
    return String(error);
}

/**
 * The same check on change and on submit, see TanStackFormController.field()
 */
export function validateWith(validate: (value: unknown) => string | undefined) {
    return {
        onChange: ({value}: { value: unknown }) => validate(value),
        onSubmit: ({value}: { value: unknown }) => validate(value),
    };
}

/**
 * The part of a TanStack FieldApi used by the controls of the form
 */
export interface BoundField<T> {
    state: { value: T, meta: ValidatedFieldMeta };
    handleChange: (value: T) => void;
    handleBlur: () => void;
}

/**
 * A text field, possibly nested in a value that can be null (e.g. a section not being edited)
 */
export type TextField = BoundField<string> | BoundField<string | null>;

export interface ValidatedInputOptions {
    id?: string;
    name?: string;
    placeholder?: string;
    // also identifies the input in tests
    className?: string;
    label: string;
    type?: string;
    // e.g. the earliest date of a datetime-local input
    min?: string;
    maxlength?: number;
    // shown when there is no error
    helpText?: string;
    // the input is not required, e.g. an optional field with a format to check
    optional?: boolean;
    prefixIcon?: string;
    // e.g. the currency of a price
    suffix?: string;
    suffixIcon?: string;
    // e.g. a value that can't be changed once saved: the validators should skip it
    readonly?: boolean;
}

/**
 * A required sl-input bound to a validated field. The error is displayed as help text, see fieldError().
 * Use with the "form" styles.
 *
 * @param submitted the form has been submitted at least once (formApi.state.submissionAttempts > 0)
 */
export function validatedInput(field: TextField, submitted: boolean, options: ValidatedInputOptions): TemplateResult {
    const error = fieldError(field.state.meta, submitted);
    const classes: Record<string, boolean> = { error: error != null };
    if (options.className != null) {
        classes[options.className] = true;
    }
    return html`
        <sl-input id=${options.id ?? nothing} name=${options.name ?? nothing} placeholder=${options.placeholder ?? nothing}
                  class=${classMap(classes)} label=${options.label} type=${options.type ?? 'text'}
                  ?required=${options.optional !== true && options.readonly !== true} ?readonly=${options.readonly === true}
                  min=${options.min ?? nothing} maxlength=${options.maxlength ?? nothing}
                  help-text=${error ?? options.helpText ?? ''}
                  .value=${field.state.value ?? ''}
                  @sl-input=${(e: Event) => field.handleChange((e.target as SlInput).value)}
                  @sl-blur=${() => {
                      if (options.type === 'url') {
                          const normalized = normalizeHttpsUrl(String(field.state.value ?? ''));
                          if (normalized !== String(field.state.value ?? '')) {
                              field.handleChange(normalized);
                          }
                      }
                      field.handleBlur();
                  }}>
            ${when(options.prefixIcon, () => html`<sl-icon slot="prefix" name=${options.prefixIcon!}></sl-icon>`)}
            ${when(options.suffix, () => html`<span slot="suffix">${options.suffix}</span>`)}
            ${when(options.suffixIcon, () => html`<sl-icon slot="suffix" name=${options.suffixIcon!}></sl-icon>`)}
        </sl-input>
    `;
}

export interface ValidatedSelectOptions {
    id?: string;
    className?: string;
    label: string;
    placeholder?: string;
    // shown when there is no error
    helpText?: string;
    // the select is not required, e.g. an optional choice with a default "automatic" option
    optional?: boolean;
}

/**
 * A required sl-select bound to a validated field, with the given options (sl-option). The error is displayed as
 * help text, see fieldError(). Use with the "form" styles.
 */
export function validatedSelect(field: BoundField<string>, submitted: boolean, options: ValidatedSelectOptions,
                                content: TemplateResult): TemplateResult {
    const error = fieldError(field.state.meta, submitted);
    const classes: Record<string, boolean> = { error: error != null };
    if (options.className != null) {
        classes[options.className] = true;
    }
    return html`
        <sl-select id=${options.id ?? nothing} class=${classMap(classes)} label=${options.label}
                   placeholder=${options.placeholder ?? nothing} ?required=${options.optional !== true} hoist
                   help-text=${error ?? options.helpText ?? ''}
                   .value=${field.state.value ?? ''}
                   @sl-change=${(e: Event) => field.handleChange(String((e.target as SlSelect).value))}
                   @sl-blur=${() => field.handleBlur()}>
            ${content}
        </sl-select>
    `;
}

/**
 * Mounts the form of a TanStackFormController while the host is connected. TanStackFormController (lit-form 1.1.0)
 * doesn't: without it, the values of the form are not updated until a field is rendered.
 *
 * Usage, in the constructor of the host: `new FormMountController(this, this.#form.api);`
 */
export class FormMountController implements ReactiveController {
    #unmount: (() => void) | null = null;

    constructor(host: ReactiveControllerHost, private readonly api: { mount: () => () => void }) {
        host.addController(this);
    }

    hostConnected(): void {
        this.#unmount = this.api.mount();
    }

    hostDisconnected(): void {
        this.#unmount?.();
        this.#unmount = null;
    }
}

export const INVALID_FORM_MESSAGE = 'Please fill in the fields highlighted in red.';

/**
 * Validates all the fields and, if they are valid, calls the onSubmit of the form. The errors returned by the server
 * on the previous submit (see setServerError()) are removed.
 * The fields are validated again first: a field may have been validated before its control was hidden, or before
 * another value changed what it requires, and handleSubmit() alone stops at the errors already known.
 *
 * @return true if the form was valid, and onSubmit has been called
 */
export async function submitValidated(api: ServerErrorsApi & {
    validateAllFields: (cause: 'submit') => Promise<unknown>,
    handleSubmit: () => Promise<void>,
    state: { isValid: boolean },
}): Promise<boolean> {
    // the server checks the values again
    clearServerErrors(api);
    await api.validateAllFields('submit');
    await api.handleSubmit();
    return api.state.isValid;
}

/**
 * Scrolls to the first control with an error, and focuses it
 */
export function focusFirstError(root: ParentNode): void {
    const invalid = root.querySelector<HTMLElement>('.error');
    if (invalid == null) {
        return;
    }
    invalid.scrollIntoView({ block: 'center' });
    invalid.focus();
}

interface ServerErrorsApi {
    fieldInfo: Record<string, unknown>;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    setFieldMeta: (field: any, updater: (meta: any) => any) => void;
}

/**
 * Displays an error returned by the server on a field, like a validation error, until the next submit.
 */
export function setServerError(api: ServerErrorsApi, field: string, message: string): void {
    api.setFieldMeta(field, meta => ({ ...meta, errorMap: { ...meta.errorMap, onServer: message } }));
}

function clearServerErrors(api: ServerErrorsApi): void {
    for (const field of Object.keys(api.fieldInfo)) {
        api.setFieldMeta(field, meta => {
            if (meta?.errorMap?.onServer == null) {
                return meta;
            }
            return { ...meta, errorMap: { ...meta.errorMap, onServer: undefined } };
        });
    }
}
