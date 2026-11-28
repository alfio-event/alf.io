import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {TanStackFormController} from '@tanstack/lit-form';
import {ContentLanguage, LocalizedContent} from '../model/purchase-context.ts';
import {
    BoundField,
    fieldError,
    focusFirstError,
    FormMountController,
    INVALID_FORM_MESSAGE,
    submitValidated,
    validateWith,
} from '../components/form-validation.ts';
import type {MultiLanguageTextChange, MultiLanguageTextLocalesChange} from '../components/multi-language-text.ts';
import '../components/multi-language-text.ts';
import {base, dialog, form, modernLayout, row, textColors} from '../styles.ts';
import { t } from '../service/i18n';

export const OFFLINE_PAYMENT_SAVE_EVENT = 'alfio-offline-payment-save';

export interface OfflinePaymentSaveDetail {
    // null for a new payment method
    paymentMethodId: string | null;
    payment: CustomOfflinePayment;
}

interface PaymentMethodFormValues {
    name: LocalizedContent;
    description: LocalizedContent;
    instructions: LocalizedContent;
}

type TextField = keyof PaymentMethodFormValues;

const TEXT_FIELDS: TextField[] = ['name', 'description', 'instructions'];

/**
 * Creates or edits a custom offline payment method: its name, description and instructions, in every language
 * the organizer chooses. Fires "alfio-offline-payment-save": the host saves the payment method, then closes the
 * dialog with close(), or shows an error with showError()
 */
@customElement('offline-payment-dialog')
export class OfflinePaymentDialog extends LitElement {

    @state() private open = false;
    @state() private paymentMethodId: string | null = null;
    @state() private languages: ContentLanguage[] = [];
    @state() private saving = false;
    @state() private invalid = false;
    @state() private error: string | null = null;
    // names of the other payment methods of the organization, lower case: a name must be unique
    private otherNames = new Set<string>();
    @query('sl-dialog') private dialog!: SlDialog;

    readonly #form = new TanStackFormController(this, {
        defaultValues: emptyValues(),
        onSubmit: async ({value}) => {
            this.saving = true;
            const localizations: CustomOfflinePayment['localizations'] = {};
            for (const locale of Object.keys(value.name)) {
                localizations[locale] = {
                    paymentName: value.name[locale].trim(),
                    paymentDescription: value.description[locale] ?? '',
                    paymentInstructions: value.instructions[locale] ?? '',
                };
            }
            this.dispatchEvent(new CustomEvent<OfflinePaymentSaveDetail>(OFFLINE_PAYMENT_SAVE_EVENT, {
                detail: {
                    paymentMethodId: this.paymentMethodId,
                    payment: {paymentMethodId: this.paymentMethodId, localizations},
                },
                bubbles: true,
                composed: true,
            }));
        },
    });

    static readonly styles = [
        base,
        textColors,
        form,
        row,
        modernLayout,
        dialog,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 60rem;
            }
            .payment-method-form {
                gap: var(--sl-spacing-x-large);
            }
        `,
    ];

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    /**
     * @param method the payment method to edit, null to create a new one
     * @param others the other payment methods of the organization
     */
    async show(method: CustomOfflinePayment | null, others: CustomOfflinePayment[], languages: ContentLanguage[]): Promise<void> {
        this.paymentMethodId = method?.paymentMethodId ?? null;
        this.languages = languages;
        this.otherNames = new Set(others
            .flatMap((other) => Object.values(other.localizations))
            .map((localization) => localization.paymentName.trim().toLowerCase()));
        this.saving = false;
        this.invalid = false;
        this.error = null;
        this.#form.api.reset(valuesOf(method));
        this.open = true;
        await this.updateComplete;
        await this.dialog.show();
    }

    close(): void {
        this.saving = false;
        void this.dialog.hide();
    }

    showError(message: string): void {
        this.saving = false;
        this.error = message;
    }

    render(): TemplateResult {
        let label = t('admin.settings.add-payment-method');
        if (this.paymentMethodId != null) {
            label = t('admin.offline-payment.edit');
        }
        return html`
            <sl-dialog class="responsive-dialog" label=${label}
                       @sl-request-close=${this.onRequestClose}
                       @sl-after-hide=${this.onAfterHide}>
                ${when(this.open, () => this.renderForm())}
            </sl-dialog>
        `;
    }

    private renderForm(): TemplateResult {
        return html`
            <form id="offline-payment-form" class="form-stack payment-method-form" novalidate @submit=${this.submit}>
                ${this.textField('name', t('admin.common.name'), {
                    validate: (value) => this.validateName(value),
                    multiline: false,
                })}
                ${this.textField('description', t('admin.common.description'), {
                    validate: (value) => missingTranslation(value, t('admin.offline-payment.error.description')),
                    multiline: true,
                })}
                ${this.textField('instructions', t('admin.offline-payment.instructions'), {
                    validate: (value) => missingTranslation(value, t('admin.offline-payment.error.instructions')),
                    multiline: true,
                })}
                ${when(this.error, () => html`
                    <sl-alert variant="danger" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.error}
                    </sl-alert>
                `)}
            </form>
            <div slot="footer">
                <sl-divider></sl-divider>
                ${when(this.invalid, () => html`<p class="text-danger">${INVALID_FORM_MESSAGE}</p>`)}
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" @click=${() => this.close()}>${t('admin.common.cancel')}</sl-button>
                    <div></div>
                    <sl-button id="save-payment-method" variant="warning" size="large" ?loading=${this.saving}
                               ?disabled=${this.saving} @click=${this.submit}>${t('admin.common.save')}</sl-button>
                </div>
            </div>
        `;
    }

    /**
     * A text in every language of the payment method. Adding or removing a language applies to all the texts.
     * Description and instructions are markdown: the instructions are shown to the buyer after the reservation.
     */
    private textField(name: TextField, label: string,
                      options: { validate: (value: LocalizedContent) => string | undefined, multiline: boolean }): TemplateResult {
        const submitted = this.#form.api.state.submissionAttempts > 0;
        const validators = validateWith((value) => options.validate((value ?? {}) as LocalizedContent));
        return html`${this.#form.field({name, validators}, (field: BoundField<LocalizedContent>) => html`
            <alfio-multi-language-text id=${`payment-method-${name}`} label=${label} .card=${false}
                                       .multiline=${options.multiline} .markdown=${options.multiline}
                                       .languages=${this.languages}
                                       .value=${field.state.value}
                                       error=${fieldError(field.state.meta, submitted) ?? ''}
                                       @change=${(e: CustomEvent<MultiLanguageTextChange>) => {
                                           if (e.detail?.value != null) {
                                               field.handleChange(e.detail.value);
                                           }
                                       }}
                                       @locales-change=${(e: CustomEvent<MultiLanguageTextLocalesChange>) => this.syncLocales(name, e.detail.locales)}
                                       @focusout=${() => field.handleBlur()}></alfio-multi-language-text>
        `)}`;
    }

    // the same languages for all the texts, keeping what has been typed
    private syncLocales(source: TextField, locales: string[]): void {
        for (const name of TEXT_FIELDS) {
            if (name === source) {
                continue;
            }
            const current = this.#form.api.getFieldValue(name) ?? {};
            const next: LocalizedContent = {};
            for (const locale of locales) {
                next[locale] = current[locale] ?? '';
            }
            this.#form.api.setFieldValue(name, next, {dontUpdateMeta: true});
        }
    }

    private validateName(value: LocalizedContent): string | undefined {
        const missing = missingTranslation(value, t('admin.offline-payment.error.name'));
        if (missing != null) {
            return missing;
        }
        if (Object.values(value).some((name) => this.otherNames.has(name.trim().toLowerCase()))) {
            return t('admin.offline-payment.error.duplicate');
        }
        return undefined;
    }

    private submit = async (event: Event): Promise<void> => {
        event.preventDefault();
        if (this.saving) {
            return;
        }
        this.error = null;
        const valid = await submitValidated(this.#form.api);
        this.invalid = !valid;
        if (!valid) {
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    // a click outside the dialog would discard what the user typed: only the close button and Cancel close it
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.target === event.currentTarget && event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private onAfterHide = (event: Event): void => {
        // nested Shoelace components (e.g. selects, tooltips) emit their own sl-after-hide
        if (event.target !== event.currentTarget) {
            return;
        }
        this.open = false;
    };
}

function emptyValues(): PaymentMethodFormValues {
    return {name: {}, description: {}, instructions: {}};
}

function valuesOf(method: CustomOfflinePayment | null): PaymentMethodFormValues {
    const values = emptyValues();
    for (const [locale, localization] of Object.entries(method?.localizations ?? {})) {
        values.name[locale] = localization.paymentName;
        values.description[locale] = localization.paymentDescription;
        values.instructions[locale] = localization.paymentInstructions;
    }
    return values;
}

function missingTranslation(value: LocalizedContent, message: string): string | undefined {
    const texts = Object.values(value);
    if (texts.length === 0 || texts.some((text) => (text ?? '').trim() === '')) {
        return message;
    }
    return undefined;
}

declare global {
    interface HTMLElementTagNameMap {
        'offline-payment-dialog': OfflinePaymentDialog;
    }
    interface GlobalEventHandlersEventMap {
        'alfio-offline-payment-save': CustomEvent<OfflinePaymentSaveDetail>;
    }
}
