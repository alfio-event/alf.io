import {html, nothing, TemplateResult} from 'lit';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import type {SlInput, SlSelect} from '@shoelace-style/shoelace';
import {BoundField} from '../components/form-validation.ts';
import type {SettingDescriptor} from './setting-control.ts';

/**
 * An invoice number, as the parts that a person can edit: the server stores it as a Java format pattern,
 * e.g. "INV-%05d" for INV-00001
 */
export interface InvoiceNumberParts {
    prefix: string;
    // minimum number of digits, padded with zeros; '' = as many as needed
    digits: string;
    suffix: string;
}

// a text without format codes ("%%" is a literal "%"), the number, then a text without format codes
const SIMPLE_PATTERN = /^((?:[^%]|%%)*)%(?:0([1-9]\d?))?d((?:[^%]|%%)*)$/;

const DIGITS_OPTIONS = ['3', '4', '5', '6', '8'];

// what the server uses when no pattern is set
const DEFAULT_PATTERN = '%d';

/**
 * The parts of a pattern, or null if the pattern uses format codes that the simple editor doesn't support
 */
export function parseInvoiceNumberPattern(pattern: string): InvoiceNumberParts | null {
    if (pattern.trim() === '') {
        return {prefix: '', digits: '', suffix: ''};
    }
    const match = SIMPLE_PATTERN.exec(pattern);
    if (match == null) {
        return null;
    }
    return {prefix: unescape(match[1]), digits: match[2] ?? '', suffix: unescape(match[3])};
}

export function buildInvoiceNumberPattern(parts: InvoiceNumberParts): string {
    let number = '%d';
    if (parts.digits !== '') {
        number = `%0${parts.digits}d`;
    }
    return `${escape(parts.prefix)}${number}${escape(parts.suffix)}`;
}

/**
 * The invoice number for a sequence number, as the server formats it
 */
export function formatInvoiceNumber(parts: InvoiceNumberParts, sequence: number): string {
    let digits = 0;
    if (parts.digits !== '') {
        digits = Number(parts.digits);
    }
    return `${parts.prefix}${String(sequence).padStart(digits, '0')}${parts.suffix}`;
}

function escape(text: string): string {
    return text.replaceAll('%', '%%');
}

function unescape(text: string): string {
    return text.replaceAll('%%', '%');
}

/**
 * The invoice number setting: text before the number, minimum digits and text after the number, with a preview.
 * The field value is the pattern; '' = not set (the level above, or the default "1, 2, 3", applies).
 *
 * @param notSetText what applies when the setting isn't set at this level, if the level above sets a value
 */
export function invoiceNumberControl(field: BoundField<string>, setting: SettingDescriptor,
                                     notSetText: string | typeof nothing, helpText: string): TemplateResult {
    const value = field.state.value ?? '';
    const parts = parseInvoiceNumberPattern(value);
    if (parts == null) {
        // a pattern written by hand with codes the simple editor can't show: keep it editable as it is
        return html`
            <sl-input id=${setting.id} class="setting" label=${setting.label} .value=${value}
                      help-text=${helpText || 'This format uses advanced codes. Empty the field to use the simple editor.'}
                      @sl-input=${(e: Event) => field.handleChange((e.target as SlInput).value)}
                      @sl-blur=${() => field.handleBlur()}></sl-input>
        `;
    }
    const inheriting = value === '' && notSetText !== nothing;
    let mode = 'custom';
    if (inheriting) {
        mode = 'inherit';
    }
    const update = (change: Partial<InvoiceNumberParts>): void => {
        const pattern = buildInvoiceNumberPattern({...parts, ...change});
        if (pattern === DEFAULT_PATTERN && notSetText === nothing) {
            // nothing set here or above: the default applies, no need to save it
            field.handleChange('');
            return;
        }
        field.handleChange(pattern);
    };
    let digitOptions = DIGITS_OPTIONS;
    if (parts.digits !== '' && !digitOptions.includes(parts.digits)) {
        digitOptions = [...digitOptions, parts.digits].sort((a, b) => Number(a) - Number(b));
    }
    return html`
        <fieldset id=${setting.id} class="invoice-number setting">
            <legend>${setting.label}</legend>
            ${when(notSetText !== nothing, () => html`
                <sl-select class="invoice-number-mode" hoist .value=${mode}
                           @sl-change=${(e: Event) => {
                               if ((e.target as SlSelect).value === 'inherit') {
                                   field.handleChange('');
                               } else {
                                   field.handleChange(DEFAULT_PATTERN);
                               }
                           }}>
                    <sl-option value="inherit">${notSetText}</sl-option>
                    <sl-option value="custom">Custom numbering</sl-option>
                </sl-select>
            `)}
            ${when(!inheriting, () => html`
                <!-- one field made of three parts, so that it reads as a single number -->
                <div class="invoice-number-parts">
                    <sl-input class="invoice-number-prefix label-hidden" label="Text before the number" placeholder="None"
                              .value=${parts.prefix}
                              @sl-input=${(e: Event) => update({prefix: (e.target as SlInput).value})}
                              @sl-blur=${() => field.handleBlur()}></sl-input>
                    <sl-select class="invoice-number-digits label-hidden" label="Number" hoist .value=${parts.digits || 'any'}
                               @sl-change=${(e: Event) => {
                                   let digits = String((e.target as SlSelect).value);
                                   if (digits === 'any') {
                                       digits = '';
                                   }
                                   update({digits});
                               }}>
                        <sl-option value="any">1<span slot="suffix">no leading zeros</span></sl-option>
                        ${repeat(digitOptions, (digits) => digits, (digits) => html`
                            <sl-option value=${digits}>${'1'.padStart(Number(digits), '0')}<span slot="suffix">${digits} digits</span></sl-option>
                        `)}
                    </sl-select>
                    <sl-input class="invoice-number-suffix label-hidden" label="Text after the number" placeholder="None"
                              .value=${parts.suffix}
                              @sl-input=${(e: Event) => update({suffix: (e.target as SlInput).value})}
                              @sl-blur=${() => field.handleBlur()}></sl-input>
                    <span class="invoice-number-caption" aria-hidden="true">Text before</span>
                    <span class="invoice-number-caption" aria-hidden="true">Number</span>
                    <span class="invoice-number-caption" aria-hidden="true">Text after</span>
                </div>
                <p class="invoice-number-preview">
                    <span class="text-muted">Next invoices:</span>
                    ${repeat([1, 2], (sequence) => sequence, (sequence) => html`
                        <span class="invoice-number-sample">${parts.prefix}<mark>${formatInvoiceNumber({prefix: '', suffix: '', digits: parts.digits}, sequence)}</mark>${parts.suffix}<span class="text-muted">,</span></span>
                    `)}
                    <span class="text-muted">and so on</span>
                </p>
            `)}
            ${when(helpText !== '', () => html`<div class="invoice-number-help text-muted">${helpText}</div>`)}
        </fieldset>
    `;
}
