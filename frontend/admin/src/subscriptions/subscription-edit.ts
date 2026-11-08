import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
import type {SlCheckbox, SlSwitch} from '@shoelace-style/shoelace';
import {
    SubscriptionDescriptorModification,
    SubscriptionPayload,
    SubscriptionTimeUnit,
    SubscriptionUsageType,
    SubscriptionValidityType,
} from '../model/subscription-descriptor.ts';
import {ContentLanguage, LocalizedContent, PaymentProxy, VatStatus} from '../model/purchase-context.ts';
import {SubscriptionService} from '../service/subscription.ts';
import {ConfigurationService} from '../service/configuration.ts';
import {LocalizationService} from '../service/localization.ts';
import {UtilService, AvailableCurrency} from '../service/util.ts';
import {LocationService} from '../service/location.ts';
import {PAYMENT_PROXY_DESCRIPTIONS, PaymentMethodDTO, PaymentProxyService} from '../service/payment-proxy.ts';
import {errorMessage} from '../service/helpers.ts';
import {toDateTimeLocal, zonedIsoFromDateTimeLocal} from '../service/date-format.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {sectionCard} from '../components/section-card.ts';
import {taskContent} from '../components/task-content.ts';
import {statusBadge} from '../components/status-badge.ts';
import {ChoiceCardOption, choiceCards} from '../components/choice-cards.ts';
import {
    BoundField,
    fieldError,
    focusFirstError,
    FormMountController,
    httpsUrl,
    INVALID_FORM_MESSAGE,
    minLength,
    requiredHttpsUrl,
    requiredValue,
    submitValidated,
    validatedInput,
    ValidatedInputOptions,
    validatedSelect,
    validateWith,
} from '../components/form-validation.ts';
import {badges, base, choiceCards as choiceCardsStyles, form, modernLayout, row, textColors} from '../styles.ts';
import '../components/purchase-context-image.ts';
import type {ImageChangeDetail} from '../components/purchase-context-image.ts';
import '../components/multi-language-text.ts';
import type {MultiLanguageTextChange, MultiLanguageTextLocalesChange} from '../components/multi-language-text.ts';
import '../display-common-mark-preview/display-common-mark-preview.ts';

type Preset = 'multipleEntries' | 'period' | 'custom';

const VALIDITY_TYPE_BY_PRESET: Record<Preset, SubscriptionValidityType> = {
    multipleEntries: 'NOT_SET',
    period: 'STANDARD',
    custom: 'CUSTOM',
};

const PRESET_CHOICES: ChoiceCardOption<Preset>[] = [
    {
        value: 'multipleEntries',
        icon: 'ticket-perforated',
        title: 'Multi-Access Pass',
        description: 'Give full access to attendees for a defined number of events',
    },
    {
        value: 'period',
        icon: 'calendar-range',
        title: 'Monthly, yearly or daily subscription',
        description: 'Give full access to attendees for a period of time after they buy the subscription',
    },
    {
        value: 'custom',
        icon: 'calendar-check',
        title: 'Custom',
        description: 'Give full access to attendees only within a fixed date interval',
    },
];

const VAT_STATUSES: { value: VatStatus; label: string }[] = [
    {value: 'INCLUDED', label: 'Taxes included in the price'},
    {value: 'NOT_INCLUDED', label: 'Taxes must be added to the price'},
    {value: 'NONE', label: 'Do not apply taxes'},
];

const USAGE_TYPES: { value: SubscriptionUsageType; label: string }[] = [
    {value: 'ONCE_PER_EVENT', label: 'Once per Event'},
    {value: 'UNLIMITED', label: 'Multiple times per Event'},
];

const TIME_UNITS: { value: SubscriptionTimeUnit; label: string }[] = [
    {value: 'DAYS', label: 'Days'},
    {value: 'MONTHS', label: 'Months'},
    {value: 'YEARS', label: 'Years'},
];

interface SubscriptionFormValues {
    preset: Preset | '';
    title: LocalizedContent;
    description: LocalizedContent;
    termsAndConditionsUrl: string;
    privacyPolicyUrl: string;
    price: string;
    currency: string;
    vatStatus: string;
    vat: string;
    onSaleFrom: string;
    onSaleTo: string;
    timeZone: string;
    maxAvailable: string;
    paymentProxies: string[];
    maxEntries: string;
    validityUnits: string;
    validityTimeUnit: string;
    usageType: string;
    validityFrom: string;
    validityTo: string;
    supportsTicketsGeneration: boolean;
}

interface LoadedData {
    languages: ContentLanguage[];
    currencies: AvailableCurrency[];
    paymentProxies: { id: PaymentProxy; description: string }[];
    timeZones: string[];
    ticketsGenerationJobActive: boolean;
    descriptionMaxLength: number;
}

const TITLE_MAX_LENGTH = 255;

/**
 * Creates or edits a subscription descriptor. Without data-subscription-id, it creates a new one.
 */
@customElement('alfio-subscription-edit')
export class SubscriptionEdit extends LitElement {

    @property({type: Number, attribute: 'data-organization-id'})
    organizationId = 0;

    @property({type: String, attribute: 'data-subscription-id'})
    subscriptionId = '';

    @state()
    private existing = false;

    @state()
    private isPublic = false;

    @state()
    private fileBlobId: string | null = null;

    // languages of the subscription being edited; undefined when creating a new one
    @state()
    private contextLanguages: ContentLanguage[] | undefined;

    @state()
    private imageError: string | null = null;

    @state()
    private saving = false;

    private previousFileBlobId: string | null = null;

    readonly #form = new TanStackFormController(this, {
        defaultValues: emptyFormValues(),
        onSubmit: async ({value}) => {
            await this.save(value);
        },
    });

    private readonly loadDataTask = new Task(this, {
        task: async ([organizationId, subscriptionId]): Promise<LoadedData | null> => {
            if (!(organizationId > 0)) {
                // the organization id is still being interpolated by AngularJS
                return null;
            }
            let descriptorPromise: Promise<SubscriptionDescriptorModification | null>;
            if (subscriptionId !== '') {
                descriptorPromise = SubscriptionService.loadDescriptor(organizationId, subscriptionId);
            } else {
                descriptorPromise = Promise.resolve(null);
            }
            const [languages, currencies, proxies, timeZones, ticketsGeneration, instanceSettings, descriptor] = await Promise.all([
                new LocalizationService().getEventsSupportedLanguages(),
                UtilService.availableCurrencies(),
                PaymentProxyService.selectableProxies(organizationId),
                LocationService.timezones(),
                ConfigurationService.loadSingleConfigForOrganization(organizationId, 'GENERATE_TICKETS_FOR_SUBSCRIPTIONS').catch(() => ''),
                ConfigurationService.loadInstanceSettings().catch(() => ({descriptionMaxLength: 4096, baseUrl: ''})),
                descriptorPromise,
            ]);
            const loaded: LoadedData = {
                languages,
                currencies,
                paymentProxies: dedupeByDescription(proxies),
                timeZones,
                ticketsGenerationJobActive: ticketsGeneration === 'true',
                descriptionMaxLength: instanceSettings.descriptionMaxLength,
            };
            this.initForm(descriptor);
            return loaded;
        },
        // the attribute is interpolated by AngularJS after the element is connected: wait for a real id
        args: () => [this.organizationId, this.subscriptionId] as const,
    });

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    static readonly styles = [
        base,
        form,
        modernLayout,
        choiceCardsStyles,
        row,
        badges,
        textColors,
        css`
            :host {
                display: block;
                --alfio-section-body-padding: var(--sl-spacing-medium);
            }

            .image-error {
                margin-top: var(--sl-spacing-small);
            }

            .visibility-alert {
                margin-bottom: var(--sl-spacing-medium);
            }

            /* the badge, the text and the action on one row, the action at the far right */
            .visibility-alert::part(message) {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-small);
            }

            .visibility-alert .toggle-visibility {
                margin-inline-start: auto;
            }

            sl-checkbox {
                display: block;
                margin-top: var(--sl-spacing-small);
            }

            .form-error-text {
                color: var(--sl-color-danger-600);
                font-size: var(--sl-font-size-small);
                margin-top: var(--sl-spacing-x-small);
            }

            .footer {
                margin-top: var(--sl-spacing-medium);
            }
        `,
    ];

    render(): TemplateResult {
        return html`
            <div class="page-title-row secondary">
                <h1>${when(this.existing, () => 'Edit', () => 'Create new')} subscription</h1>
            </div>
            <hr class="page-separator">
            ${taskContent(this.loadDataTask, this.loadDataTask.value ?? null,
                'Failed to load the subscription. Please try again.',
                (data) => this.renderForm(data))}
        `;
    }

    private renderForm(data: LoadedData): TemplateResult {
        const submitted = this.#form.api.state.submissionAttempts > 0;
        const values = this.#form.api.state.values;
        return html`
            <form novalidate @submit=${this.submit}>
                ${when(this.existing, () => this.renderVisibilitySection())}
                ${this.renderImageSection()}
                ${this.#form.field({name: 'title', validators: validateWith(localizedContent(TITLE_MAX_LENGTH))},
                    (field) => this.renderMultiLanguageText(data, field, {label: 'Title', icon: 'card-heading', maxLength: TITLE_MAX_LENGTH, markdown: false, multiline: false}))}
                ${this.#form.field({name: 'description', validators: validateWith(localizedContent(data.descriptionMaxLength))},
                    (field) => this.renderMultiLanguageText(data, field, {label: 'Description', icon: 'text-paragraph', maxLength: data.descriptionMaxLength, markdown: true, multiline: true}))}
                ${sectionCard({icon: 'link-45deg', title: 'URLs'}, html`
                    <div class="form-stack">
                        <div class="row" style="--alfio-row-cols: 2">
                            ${this.#form.field({name: 'termsAndConditionsUrl', validators: validateWith(requiredHttpsUrl)},
                                (field) => this.textInput(field, {id: 'termsAndConditionsUrl', label: 'Terms and Conditions URL', type: 'url', prefixIcon: 'link-45deg'}))}
                            ${this.#form.field({name: 'privacyPolicyUrl', validators: validateWith(httpsUrl)},
                                (field) => this.textInput(field, {
                                    id: 'privacyPolicyUrl',
                                    label: 'Privacy policy URL',
                                    type: 'url',
                                    prefixIcon: 'link-45deg',
                                    optional: true,
                                    helpText: 'Privacy policy link is optional. However, it is good practice to have one.',
                                }))}
                        </div>
                    </div>
                `)}
                ${sectionCard({icon: 'layers', title: 'Type'}, html`
                    ${this.#form.field({name: 'preset', validators: validateWith(requiredValue)}, (field) => {
                        const error = fieldError(field.state.meta, submitted);
                        const preset = (field.state.value ?? '') as Preset | '';
                        return html`
                            ${choiceCards<Preset | ''>({
                                name: 'subscription-preset',
                                className: 'subscription-type',
                                label: 'How can attendees use the subscription?',
                                value: preset,
                                options: PRESET_CHOICES,
                                panel: this.renderPresetPanel(preset),
                            }, (value) => this.selectPreset(field, value as Preset))}
                            ${when(error != null, () => html`<div class="form-error-text">${error}</div>`, () => nothing)}
                        `;
                    })}
                `)}
                ${when(values.preset !== '', () => html`
                    ${sectionCard({icon: 'cash-coin', title: 'Pricing and sales period'}, html`
                        <div class="form-stack">
                            <div class="row" style="--alfio-row-cols: 4">
                                ${this.#form.field({name: 'price', validators: validateWith(requiredNumber)},
                                    (field) => this.textInput(field, {id: 'price', label: 'Price', type: 'number', suffixIcon: 'cash'}))}
                                ${this.#form.field({name: 'currency', validators: validateWith(requiredValue)}, (field) =>
                                    validatedSelect(field, submitted, {id: 'currency', label: 'Currency', placeholder: 'Select a currency'}, html`
                                        ${repeat(data.currencies, (c) => c.code, (c) => html`
                                            <sl-option value=${c.code}>${c.code} – ${c.name}</sl-option>
                                        `)}
                                    `))}
                                ${this.#form.field({name: 'vatStatus', validators: validateWith(requiredValue)}, (field) =>
                                    validatedSelect(field, submitted, {id: 'vatStatus', label: 'Tax policy', placeholder: 'Select a tax policy'}, html`
                                        ${repeat(VAT_STATUSES, (s) => s.value, (s) => html`<sl-option value=${s.value}>${s.label}</sl-option>`)}
                                    `))}
                                ${when(values.vatStatus !== 'NONE' && values.vatStatus !== '', () => html`
                                    ${this.#form.field({
                                        name: 'vat',
                                        validators: {
                                            ...validateWith((value) => this.validateVat(value)),
                                            onChangeListenTo: ['vatStatus'],
                                        },
                                    }, (field) => this.textInput(field, {id: 'vat', label: 'Taxes %', type: 'number', suffix: '%'}))}
                                `)}
                            </div>
                            <div class="row" style="--alfio-row-cols: 4">
                                ${this.#form.field({name: 'onSaleFrom', validators: validateWith(requiredValue)},
                                    (field) => this.textInput(field, {id: 'onSaleFrom', label: 'On sale from', type: 'datetime-local'}))}
                                ${this.#form.field({name: 'onSaleTo'},
                                    (field) => this.textInput(field, {id: 'onSaleTo', label: 'On sale to (optional)', type: 'datetime-local', optional: true}))}
                                ${this.#form.field({name: 'timeZone', validators: validateWith(requiredValue)}, (field) =>
                                    validatedSelect(field, submitted, {id: 'timeZone', label: 'Time zone', placeholder: 'Select a time zone'}, html`
                                        ${repeat(data.timeZones, (tz) => tz, (tz) => html`<sl-option value=${tz}>${tz}</sl-option>`)}
                                    `))}
                                ${this.#form.field({name: 'maxAvailable', validators: validateWith(optionalNumber)},
                                    (field) => this.textInput(field, {
                                        id: 'maxAvailable',
                                        label: 'Max available (optional)',
                                        type: 'number',
                                        optional: true,
                                        helpText: 'How many subscriptions can be sold. Leave empty for unlimited.',
                                    }))}
                            </div>
                        </div>
                    `)}
                    ${this.renderPaymentMethodsSection(data, submitted)}
                    ${when(data.ticketsGenerationJobActive, () => sectionCard({icon: 'ticket', title: 'Automatic tickets generation'}, html`
                        ${this.#form.field({name: 'supportsTicketsGeneration'}, (field) => html`
                            <sl-switch ?checked=${field.state.value}
                                       @sl-change=${(e: Event) => field.handleChange((e.target as SlSwitch).checked)}
                                       help-text="If enabled, each subscription holder will receive one ticket for present and future compatible events automatically. Unless the subscriber has already used the subscription for that event, in which case no tickets will be generated.">
                                Generate tickets automatically for compatible events
                            </sl-switch>
                        `)}
                    `))}
                `)}
                <div class="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" href=${`#/subscriptions/${this.organizationId}/list`}>Cancel</sl-button>
                        <div></div>
                        <sl-button id="save-subscription" type="submit" variant="warning" size="large" ?loading=${this.saving}>Save</sl-button>
                    </div>
                </div>
            </form>
        `;
    }

    /**
     * A localized text (title, description) as a section card with one block per language,
     * rendered by the shared alfio-multi-language-text component
     */
    private renderMultiLanguageText(data: LoadedData, field: BoundField<LocalizedContent>, options: { label: string, icon: string, maxLength: number, markdown: boolean, multiline: boolean }): TemplateResult {
        const submitted = this.#form.api.state.submissionAttempts > 0;
        return html`
            <alfio-multi-language-text
                label=${options.label}
                icon=${options.icon}
                max-length=${options.maxLength}
                .markdown=${options.markdown}
                .multiline=${options.multiline}
                .languages=${data.languages}
                .contextLanguages=${this.contextLanguages}
                .value=${field.state.value ?? {}}
                error=${fieldError(field.state.meta, submitted) ?? ''}
                @change=${(e: CustomEvent<MultiLanguageTextChange>) => field.handleChange(e.detail.value)}
                @locales-change=${this.onLocalesChange}
            ></alfio-multi-language-text>
        `;
    }

    /**
     * A language was added or removed in one multi-language section: apply the same languages
     * to the other localized fields of the form, keeping their current texts
     */
    private onLocalesChange = (event: CustomEvent<MultiLanguageTextLocalesChange>): void => {
        const locales = event.detail.locales;
        const values = this.#form.api.state.values;
        this.#form.api.setFieldValue('title', localizedValues(values.title, locales));
        this.#form.api.setFieldValue('description', localizedValues(values.description, locales));
    };

    private renderPresetPanel(preset: Preset | ''): TemplateResult {
        const submitted = this.#form.api.state.submissionAttempts > 0;
        return html`
            <div class="form-stack">
                ${when(preset === 'multipleEntries', () => html`
                    ${this.#form.field({
                        name: 'maxEntries',
                        validators: validateWith((value) => this.validateForPreset('multipleEntries', requiredNumber, value)),
                    }, (field) => this.textInput(field, {
                        id: 'maxEntries',
                        label: 'Number of access included',
                        type: 'number',
                        helpText: 'How many times people can use this subscription',
                    }))}
                `)}
                ${when(preset === 'period', () => html`
                    <div class="row" style="--alfio-row-cols: 2">
                        ${this.#form.field({
                            name: 'validityUnits',
                            validators: validateWith((value) => this.validateForPreset('period', requiredNumber, value)),
                        }, (field) => this.textInput(field, {id: 'validityUnits', label: 'Duration of the subscription', type: 'number'}))}
                        ${this.#form.field({
                            name: 'validityTimeUnit',
                            validators: validateWith((value) => this.validateForPreset('period', requiredValue, value)),
                        }, (field) => validatedSelect(field, submitted, {id: 'validityTimeUnit', label: 'Time unit'}, html`
                            ${repeat(TIME_UNITS, (u) => u.value, (u) => html`<sl-option value=${u.value}>${u.label}</sl-option>`)}
                        `))}
                    </div>
                `)}
                ${when(preset === 'custom', () => html`
                    <div class="row" style="--alfio-row-cols: 2">
                        ${this.#form.field({
                            name: 'validityFrom',
                            validators: validateWith((value) => this.validateForPreset('custom', requiredValue, value)),
                        }, (field) => this.textInput(field, {id: 'validityFrom', label: 'Validity starts from', type: 'datetime-local'}))}
                        ${this.#form.field({name: 'validityTo'},
                            (field) => this.textInput(field, {id: 'validityTo', label: 'Validity ends (optional)', type: 'datetime-local', optional: true}))}
                    </div>
                `)}
                ${this.#form.field({name: 'usageType', validators: validateWith(requiredValue)}, (field) =>
                    validatedSelect(field, submitted, {id: 'usageType', label: 'Subscription can be used', placeholder: 'Select how often'}, html`
                        ${repeat(USAGE_TYPES, (u) => u.value, (u) => html`<sl-option value=${u.value}>${u.label}</sl-option>`)}
                    `))}
            </div>
        `;
    }

    private renderPaymentMethodsSection(data: LoadedData, submitted: boolean): TemplateResult {
        return sectionCard({icon: 'credit-card', title: 'Accepted payment methods'}, html`
            ${this.#form.field({
                name: 'paymentProxies',
                validators: validateWith((value) => {
                    if (Array.isArray(value) && value.length > 0) {
                        return undefined;
                    }
                    return 'Please select at least one payment method';
                }),
            }, (field) => {
                const error = fieldError(field.state.meta, submitted);
                const selected = (field.state.value as string[]) ?? [];
                return html`
                    ${repeat(data.paymentProxies, (p) => p.id, (p) => html`
                        <sl-checkbox class="payment-proxy" ?checked=${selected.includes(p.id)}
                                     @sl-change=${(e: Event) => this.togglePaymentProxy(field, p.id, (e.target as SlCheckbox).checked)}>
                            ${p.description}
                        </sl-checkbox>
                    `)}
                    ${when(error != null, () => html`<div class="form-error-text">${error}</div>`, () => nothing)}
                `;
            })}
        `);
    }

    private renderImageSection(): TemplateResult {
        return sectionCard({icon: 'image', title: 'Image'}, html`
            <p class="text-muted">Upload an image for this subscription in PNG, JPG, GIF or SVG format.</p>
            <alfio-purchase-context-image
                current-image=${this.previousFileBlobId != null ? `/file/${this.previousFileBlobId}` : ''}
                accept="image/*"
                max-size="1048576"
                label="Drop your image here or click to select"
                @image-change=${this.onImageChange}></alfio-purchase-context-image>
            ${when(this.imageError != null, () => html`
                <sl-alert variant="danger" open class="image-error">
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${this.imageError}
                </sl-alert>
            `, () => nothing)}
        `);
    }

    private renderVisibilitySection(): TemplateResult {
        let variant: 'success' | 'warning';
        let icon: string;
        let badge: TemplateResult;
        let actionIcon: string;
        let actionLabel: string;
        let statusText: string;
        if (this.isPublic) {
            variant = 'success';
            icon = 'eye';
            badge = statusBadge('public', {label: 'Visible', variant: 'success', icon: 'eye'}, 'subscription-visibility');
            actionIcon = 'eye-slash';
            actionLabel = 'Hide';
            statusText = 'visible';
        } else {
            variant = 'warning';
            icon = 'eye-slash';
            badge = statusBadge('hidden', {label: 'Hidden', variant: 'neutral', icon: 'eye-slash'}, 'subscription-visibility');
            actionIcon = 'eye';
            actionLabel = 'Publish';
            statusText = 'hidden';
        }
        return html`
            <sl-alert class="visibility-alert" variant=${variant} open>
                <sl-icon slot="icon" name=${icon}></sl-icon>
                ${badge}
                <span>
                    This subscription is <strong>${statusText}</strong> to the public.
                    ${when(!this.isPublic, () => html` Only people who have a direct link can see it.`, () => nothing)}
                </span>
                <sl-button variant="default" size="small" class="toggle-visibility" @click=${this.toggleVisibility}>
                    <sl-icon slot="prefix" name=${actionIcon}></sl-icon>
                    ${actionLabel}
                </sl-button>
            </sl-alert>
        `;
    }

    private textInput(field: BoundField<string>, options: ValidatedInputOptions): TemplateResult {
        return validatedInput(field, this.#form.api.state.submissionAttempts > 0, options);
    }

    private selectPreset(field: BoundField<Preset | ''>, value: Preset): void {
        field.handleChange(value);
        const values = this.#form.api.state.values;
        if (value === 'period') {
            if (values.validityTimeUnit === '') {
                this.#form.api.setFieldValue('validityTimeUnit', 'MONTHS');
            }
            if (values.validityUnits === '') {
                this.#form.api.setFieldValue('validityUnits', '1');
            }
        }
        if (values.usageType === '') {
            this.#form.api.setFieldValue('usageType', 'ONCE_PER_EVENT');
        }
    }

    private togglePaymentProxy(field: BoundField<string[]>, proxy: string, checked: boolean): void {
        const selected = (field.state.value as string[]) ?? [];
        let next: string[];
        if (checked) {
            next = [...selected, proxy];
        } else {
            next = selected.filter((p) => p !== proxy);
        }
        field.handleChange(next);
    }

    private onImageChange = (event: CustomEvent<ImageChangeDetail>): void => {
        const {fileBlobId} = event.detail;
        this.imageError = null;
        if (fileBlobId == null) {
            // removal: an existing subscription falls back to its saved image
            this.fileBlobId = this.existing ? this.previousFileBlobId : null;
        } else {
            this.fileBlobId = fileBlobId;
        }
    };

    private async toggleVisibility(): Promise<void> {
        const target = !this.isPublic;
        try {
            await SubscriptionService.setPublic(this.organizationId, this.subscriptionId, target);
            this.isPublic = target;
            let message = 'The subscription is now hidden.';
            if (target) {
                message = 'The subscription is now visible to the public.';
            }
            dispatchFeedback({type: 'success', message}, this);
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to change the visibility')}, this);
        }
    }

    private submit = async (event: Event): Promise<void> => {
        event.preventDefault();
        if (this.saving) {
            return;
        }
        if (!await submitValidated(this.#form.api)) {
            dispatchFeedback({type: 'danger', message: INVALID_FORM_MESSAGE}, this);
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    private async save(values: SubscriptionFormValues): Promise<void> {
        if (this.fileBlobId == null) {
            this.imageError = 'Please upload an image for the subscription.';
            await this.updateComplete;
            this.renderRoot.querySelector('.image-error')?.scrollIntoView({block: 'center'});
            return;
        }
        this.saving = true;
        try {
            const payload = this.toPayload(values);
            if (this.existing) {
                await SubscriptionService.update(this.organizationId, this.subscriptionId, payload);
                dispatchFeedback({type: 'success', message: 'Update successful'}, this);
                const descriptor = await SubscriptionService.loadDescriptor(this.organizationId, this.subscriptionId);
                this.initForm(descriptor);
            } else {
                const id = await SubscriptionService.create(this.organizationId, payload);
                dispatchFeedback({type: 'success', message: 'Subscription created'}, this);
                window.location.hash = `#/subscriptions/${this.organizationId}/${id}/edit`;
            }
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to save the subscription')}, this);
        } finally {
            this.saving = false;
        }
    }

    private toPayload(values: SubscriptionFormValues): SubscriptionPayload {
        const timeZone = values.timeZone;
        const vatStatus = values.vatStatus as VatStatus;
        let id: string | null = null;
        if (this.existing) {
            id = this.subscriptionId;
        }
        let maxAvailable: number | null = null;
        if (values.maxAvailable.trim() !== '') {
            maxAvailable = Number(values.maxAvailable);
        }
        let price: number | null = null;
        if (values.price.trim() !== '') {
            price = Number(values.price);
        }
        // the backend requires a value even when no taxes apply
        let vat = 0;
        if (vatStatus !== 'NONE' && values.vat.trim() !== '') {
            vat = Number(values.vat);
        }
        let isPublic = false;
        if (this.existing) {
            isPublic = this.isPublic;
        }
        let maxEntries: number | null = null;
        if (values.preset === 'multipleEntries' && values.maxEntries.trim() !== '') {
            maxEntries = Number(values.maxEntries);
        }
        let validityType: SubscriptionValidityType | null = null;
        if (values.preset !== '') {
            validityType = VALIDITY_TYPE_BY_PRESET[values.preset];
        }
        let validityTimeUnit: SubscriptionTimeUnit | null = null;
        if (values.preset === 'period') {
            validityTimeUnit = values.validityTimeUnit as SubscriptionTimeUnit;
        }
        let validityUnits: number | null = null;
        if (values.preset === 'period' && values.validityUnits.trim() !== '') {
            validityUnits = Number(values.validityUnits);
        }
        let validityFrom: string | null = null;
        let validityTo: string | null = null;
        if (values.preset === 'custom') {
            validityFrom = zonedIsoFromDateTimeLocal(values.validityFrom, timeZone);
            validityTo = zonedIsoFromDateTimeLocal(values.validityTo, timeZone);
        }
        let privacyPolicyUrl: string | null = null;
        if (values.privacyPolicyUrl.trim() !== '') {
            privacyPolicyUrl = values.privacyPolicyUrl.trim();
        }
        return {
            id,
            title: values.title,
            description: values.description,
            maxAvailable,
            onSaleFrom: zonedIsoFromDateTimeLocal(values.onSaleFrom, timeZone),
            onSaleTo: zonedIsoFromDateTimeLocal(values.onSaleTo, timeZone),
            price,
            vat,
            vatStatus,
            currency: values.currency,
            isPublic,
            organizationId: this.organizationId,
            maxEntries,
            validityType,
            validityTimeUnit,
            validityUnits,
            validityFrom,
            validityTo,
            usageType: (values.usageType as SubscriptionUsageType) || null,
            termsAndConditionsUrl: values.termsAndConditionsUrl.trim(),
            privacyPolicyUrl,
            fileBlobId: this.fileBlobId,
            paymentProxies: values.paymentProxies as PaymentProxy[],
            timeZone,
            supportsTicketsGeneration: values.supportsTicketsGeneration,
        };
    }

    private validateVat(value: unknown): string | undefined {
        if (this.#form.api.state.values.vatStatus !== 'NONE') {
            return requiredNumber(value);
        }
        return undefined;
    }

    /**
     * Runs the validator only while the given preset is selected: the field keeps its validators
     * after its control has been hidden by a different choice
     */
    private validateForPreset(preset: Preset, validator: (value: unknown) => string | undefined, value: unknown): string | undefined {
        if (this.#form.api.state.values.preset === preset) {
            return validator(value);
        }
        return undefined;
    }

    private initForm(descriptor: SubscriptionDescriptorModification | null): void {
        this.existing = descriptor != null;
        this.isPublic = descriptor?.isPublic ?? false;
        this.fileBlobId = descriptor?.fileBlobId ?? null;
        this.previousFileBlobId = descriptor?.fileBlobId ?? null;
        this.imageError = null;
        const timeZone = descriptor?.timeZone ?? defaultTimeZone();
        this.contextLanguages = descriptor?.contentLanguages;
        let title: LocalizedContent;
        let description: LocalizedContent;
        if (descriptor != null) {
            const locales = Object.keys(descriptor.title);
            title = localizedValues(descriptor.title, locales);
            description = localizedValues(descriptor.description, locales);
        } else {
            // empty selection: alfio-multi-language-text picks the default language
            // (purchase context, browser language, English)
            title = {};
            description = {};
        }
        let preset: Preset | '' = '';
        if (descriptor != null) {
            preset = presetFromValidityType(descriptor.validityType);
        }
        let onSaleFrom = '';
        let onSaleTo = '';
        let validityFrom = '';
        let validityTo = '';
        let vat = '0';
        if (descriptor != null) {
            onSaleFrom = toDateTimeLocal(descriptor.onSaleFromModel ?? descriptor.onSaleFrom, timeZone);
            onSaleTo = toDateTimeLocal(descriptor.onSaleToModel ?? descriptor.onSaleTo, timeZone);
            validityFrom = toDateTimeLocal(descriptor.validityFromModel ?? descriptor.validityFrom, timeZone);
            validityTo = toDateTimeLocal(descriptor.validityToModel ?? descriptor.validityTo, timeZone);
            if (descriptor.vat != null) {
                vat = String(descriptor.vat);
            }
        }
        const values: SubscriptionFormValues = {
            preset,
            title,
            description,
            termsAndConditionsUrl: descriptor?.termsAndConditionsUrl ?? '',
            privacyPolicyUrl: descriptor?.privacyPolicyUrl ?? '',
            price: optionalString(descriptor?.price),
            currency: descriptor?.currency ?? '',
            vatStatus: descriptor?.vatStatus ?? '',
            vat,
            onSaleFrom,
            onSaleTo,
            timeZone,
            maxAvailable: positiveOrEmpty(descriptor?.maxAvailable),
            paymentProxies: descriptor?.paymentProxies ?? [],
            maxEntries: positiveOrEmpty(descriptor?.maxEntries),
            validityUnits: optionalString(descriptor?.validityUnits),
            validityTimeUnit: descriptor?.validityTimeUnit ?? 'MONTHS',
            usageType: descriptor?.usageType ?? 'ONCE_PER_EVENT',
            validityFrom,
            validityTo,
            supportsTicketsGeneration: descriptor?.supportsTicketsGeneration === true,
        };
        this.#form.api.update({...this.#form.api.options, defaultValues: values});
        this.#form.api.reset(values);
    }
}

function optionalString(value: number | null | undefined): string {
    if (value != null) {
        return String(value);
    }
    return '';
}

/**
 * The backend stores -1 for "not set": an empty field means the same
 */
function positiveOrEmpty(value: number | null | undefined): string {
    if (value != null && value > -1) {
        return String(value);
    }
    return '';
}

function emptyFormValues(): SubscriptionFormValues {
    return {
        preset: '',
        title: {},
        description: {},
        termsAndConditionsUrl: '',
        privacyPolicyUrl: '',
        price: '',
        currency: '',
        vatStatus: '',
        vat: '0',
        onSaleFrom: '',
        onSaleTo: '',
        timeZone: defaultTimeZone(),
        maxAvailable: '',
        paymentProxies: [],
        maxEntries: '',
        validityUnits: '',
        validityTimeUnit: 'MONTHS',
        usageType: 'ONCE_PER_EVENT',
        validityFrom: '',
        validityTo: '',
        supportsTicketsGeneration: false,
    };
}

function defaultTimeZone(): string {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    } catch {
        return 'UTC';
    }
}

function presetFromValidityType(validityType: SubscriptionValidityType | null | undefined): Preset | '' {
    if (validityType === 'NOT_SET') {
        return 'multipleEntries';
    }
    if (validityType === 'CUSTOM') {
        return 'custom';
    }
    if (validityType === 'STANDARD') {
        return 'period';
    }
    return '';
}

function localizedValues(content: LocalizedContent | null | undefined, locales: string[]): LocalizedContent {
    const result: LocalizedContent = {};
    for (const locale of locales) {
        result[locale] = content?.[locale] ?? '';
    }
    return result;
}

function dedupeByDescription(proxies: PaymentMethodDTO[]): { id: PaymentProxy; description: string }[] {
    const seen = new Set<string>();
    const result: { id: PaymentProxy; description: string }[] = [];
    for (const proxy of proxies) {
        const description = PAYMENT_PROXY_DESCRIPTIONS[proxy.paymentProxy]
            ?? `Unknown provider (${proxy.paymentProxy}). Please check the configuration`;
        if (!seen.has(description)) {
            seen.add(description);
            result.push({id: proxy.paymentProxy, description});
        }
    }
    return result;
}

function localizedText(maxLength: number): (value: unknown) => string | undefined {
    return (value) => {
        const missing = minLength(value, 10);
        if (missing != null) {
            return missing;
        }
        if (String(value).length > maxLength) {
            return `Please enter at most ${maxLength} characters`;
        }
        return undefined;
    };
}

/**
 * Same checks as localizedText, applied to every translation of a localized map
 */
function localizedContent(maxLength: number): (value: unknown) => string | undefined {
    return (value) => {
        const map = (value ?? {}) as LocalizedContent;
        const locales = Object.keys(map);
        if (locales.length === 0) {
            return requiredValue('');
        }
        for (const locale of locales) {
            const error = localizedText(maxLength)(map[locale]);
            if (error != null) {
                return error;
            }
        }
        return undefined;
    };
}

function requiredNumber(value: unknown): string | undefined {
    const missing = requiredValue(value);
    if (missing != null) {
        return missing;
    }
    const parsed = Number(value);
    if (Number.isNaN(parsed) || parsed < 0) {
        return 'Please enter a positive number';
    }
    return undefined;
}

function optionalNumber(value: unknown): string | undefined {
    if (value == null || String(value).trim() === '') {
        return undefined;
    }
    return requiredNumber(value);
}



declare global {
    interface HTMLElementTagNameMap {
        'alfio-subscription-edit': SubscriptionEdit;
    }
}
