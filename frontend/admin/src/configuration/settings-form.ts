import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
import type {SlCheckbox} from '@shoelace-style/shoelace';
import {
    ConfigurationModification,
    ConfigurationSetting,
    ExtensionSetting,
    ExtensionSettingModification,
    ExtensionSettingsById,
    InheritedSetting,
    PlatformModeStatus,
    SettingsLevel,
} from '../model/configuration.ts';
import {SettingsService} from '../service/settings.ts';
import {ConfirmationDialogService} from '../service/confirmation-dialog.ts';
import {PAYMENT_PROXY_DESCRIPTIONS} from '../service/payment-proxy.ts';
import {errorMessage} from '../service/helpers.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {sectionCard} from '../components/section-card.ts';
import {externalLink} from '../components/external-link.ts';
import {setupGuide} from '../components/setup-guide.ts';
import {taskContent} from '../components/task-content.ts';
import {choiceCards, ChoiceCardOption} from '../components/choice-cards.ts';
import {FormSection, SectionNav} from '../components/section-nav.ts';
import {
    BoundField,
    focusFirstError,
    FormMountController,
    INVALID_FORM_MESSAGE,
    submitValidated,
    validateWith,
} from '../components/form-validation.ts';
import {base, badges, choiceCards as choiceCardsStyles, creationLayout, form, modernLayout, row, textColors} from '../styles.ts';
import {notSetLabel, REMOVE_SECRET, SettingDescriptor, settingControl, validJson} from './setting-control.ts';
import {DEFAULT_LABEL_LAYOUT, labelLayoutPreview} from './label-layout-preview.ts';
import './regenerate-invoices-dialog.ts';
import type {RegenerateInvoicesDialog} from './regenerate-invoices-dialog.ts';
import './offline-payment-config-block.ts';
import type {OfflinePaymentConfigBlock} from './offline-payment-config-block.ts';
import '../components/section-nav.ts';

export const SETTINGS_SAVED_EVENT = 'alfio-settings-saved';
export const SETTINGS_CANCELLED_EVENT = 'alfio-settings-cancelled';

/**
 * The description of a yes/no setting without its default: "Display stats (default true)" -> "Display stats",
 * "Receive a mail (provider dependant, default: false)" -> "Receive a mail (provider dependant)"
 */
/**
 * The default that the description of a yes/no setting tells, e.g. "Live mode (default false)"
 */
function defaultValueOf(description: string): string | undefined {
    return /default:?\s*(true|false)\s*\)/i.exec(description)?.[1]?.toLowerCase();
}

function withoutDefaultValue(description: string): string {
    return description
        .replace(/,?\s*default:?\s*(true|false)\s*\)/i, ')')
        .replace(/\s*\(\s*\)/, '')
        .trim();
}

interface CardOptions {
    id: string;
    icon: string;
    title: string;
    // one sentence: what the card is for
    description?: TemplateResult;
    // the steps to set it up, collapsed below the description (see setupGuide())
    guide?: TemplateResult;
}

interface SettingsGroup extends CardOptions {
    keys: string[];
}

// the settings of the GENERAL category, by topic (each level shows only the keys it can change)
const GENERAL_GROUPS: SettingsGroup[] = [
    {
        id: 'website', icon: 'globe', title: 'Website',
        description: html`The address of alf.io and the texts shown on the list of the events.`,
        keys: ['BASE_URL', 'GLOBAL_PRIVACY_POLICY', 'GLOBAL_TERMS', 'ANNOUNCEMENT_BANNER_CONTENT', 'DESCRIPTION_MAXLENGTH'],
    },
    {
        id: 'reservations', icon: 'ticket-perforated', title: 'Reservations',
        description: html`How many tickets a buyer can take, and how long the tickets stay reserved.`,
        keys: ['MAX_AMOUNT_OF_TICKETS_BY_RESERVATION', 'RESERVATION_TIMEOUT', 'RESERVATION_MIN_TIMEOUT_AFTER_FAILED_PAYMENT',
            'SPECIAL_PRICE_CODE_LENGTH'],
    },
    {
        id: 'reminders', icon: 'bell', title: 'Reminders to attendees',
        description: html`E-mails sent before the event to the attendees whose ticket is not complete.`,
        keys: ['ASSIGNMENT_REMINDER_START', 'ASSIGNMENT_REMINDER_INTERVAL', 'OPTIONAL_DATA_REMINDER_ENABLED'],
    },
    {
        id: 'organizer', icon: 'person-workspace', title: 'Notifications and statistics for the organizer',
        keys: ['NOTIFY_ORGANIZER_ON_RESERVATION', 'NOTIFY_ALL_FAILED_PAYMENT_ATTEMPTS', 'DISPLAY_STATS_IN_EVENT_DETAIL',
            'CHECK_IN_STATS'],
    },
    {
        id: 'appearance', icon: 'palette', title: 'Appearance',
        description: html`CSS added to the public pages.`,
        keys: ['BASE_CUSTOM_CSS', 'EVENT_CUSTOM_CSS'],
    },
    {
        id: 'bot-protection', icon: 'shield-check', title: 'Protection against bots',
        description: html`A captcha on the login and on the reservation, with Google reCAPTCHA or Cloudflare Turnstile.`,
        keys: ['ENABLE_CAPTCHA_FOR_LOGIN', 'RECAPTCHA_API_KEY', 'RECAPTCHA_SECRET', 'CF_TURNSTILE_ENABLED',
            'CF_TURNSTILE_PRE_CLEARANCE', 'CF_TURNSTILE_SITE_KEY', 'CF_TURNSTILE_SECRET_KEY'],
    },
    {
        id: 'content-security-policy', icon: 'shield-lock', title: 'Content Security Policy',
        keys: ['SECURITY_CSP_REPORT_ENABLED', 'SECURITY_CSP_REPORT_URI'],
    },
];

// shown with the integrations
const GOOGLE_ANALYTICS: SettingsGroup = {
    id: 'google-analytics', icon: 'graph-up', title: 'Google Analytics',
    keys: ['GOOGLE_ANALYTICS_KEY', 'GOOGLE_ANALYTICS_ANONYMOUS_MODE'],
};

interface LoadedSettings {
    level: SettingsLevel;
    // the settings that can be edited, by key
    settings: Map<string, IndexedSetting>;
    // the setting categories (e.g. MAIL), with their settings in display order
    categories: Map<string, ConfigurationSetting[]>;
    extensions: ExtensionSetting[][];
    euCountries: { value: string; label: string }[];
    platform: PlatformModeStatus | null;
    // what applies to the settings that this level doesn't set
    inherited: Record<string, InheritedSetting>;
}

interface IndexedSetting {
    category: string;
    setting: ConfigurationSetting;
}

interface SettingsFormValues {
    // the value of each setting at this level, by key: '' = not set
    settings: Record<string, string>;
    // the value of each extension parameter at this level, by "x<parameter id>": '' = not set
    extensions: Record<string, string>;
}

// the name of a field of the form: a setting, or a parameter of an extension
type FieldName = `settings.${string}` | `extensions.${string}`;

interface RenderedSection extends FormSection {
    content: TemplateResult;
}

// settings edited elsewhere (Translations tab), or only through their own control
const HIDDEN_SETTINGS = new Set(['SUPPORTED_LANGUAGES', 'TRANSLATION_OVERRIDE']);

const JSON_SETTINGS = new Set(['LABEL_LAYOUT', 'CHECK_IN_COLOR_CONFIGURATION', 'OPENID_CONFIGURATION_JSON', 'WALLET_SERVICE_ACCOUNT_KEY']);

// settings edited with a special editor, with the label that the editor shows instead of the description
const SPECIAL_EDITORS: Record<string, { editor: NonNullable<SettingDescriptor['editor']>, label: string, helpText?: string }> = {
    BASE_CUSTOM_CSS: {editor: 'css', label: 'CSS of all the public pages'},
    EVENT_CUSTOM_CSS: {editor: 'css', label: 'CSS of the event and subscription pages'},
    INVOICE_NUMBER_PATTERN: {editor: 'invoice-number', label: 'Invoice number'},
};

// clearer labels than the descriptions sent by the server, for the settings shown in a card that names the provider
const LABELS: Record<string, string> = {
    PAYPAL_ENABLED: 'Accept PayPal payments',
    PAYPAL_LIVE_MODE: 'Live mode',
    PAYPAL_CLIENT_ID: 'Client ID',
    PAYPAL_CLIENT_SECRET: 'Client secret',
    PAYPAL_DEMO_MODE_USERNAME: 'Demo mode username',
    PAYPAL_DEMO_MODE_PASSWORD: 'Demo mode password',
    STRIPE_CC_ENABLED: 'Accept Stripe payments',
    STRIPE_PUBLIC_KEY: 'Publishable key',
    STRIPE_SECRET_KEY: 'Secret key',
};

const PAYMENT_METHODS_DENY_LIST = 'PAYMENT_METHODS_BLACKLIST';
const MAILER_TYPE = 'MAILER_TYPE';
const MAPS_PROVIDER = 'MAPS_PROVIDER';
const LABEL_LAYOUT = 'LABEL_LAYOUT';
const CHECK_IN_COLORS = 'CHECK_IN_COLOR_CONFIGURATION';
const GENERATE_TICKETS_FOR_SUBSCRIPTIONS = 'GENERATE_TICKETS_FOR_SUBSCRIPTIONS';
const DEFERRED_BANK_TRANSFER = 'DEFERRED_BANK_TRANSFER_ENABLED';

// the settings of each mail provider start with its prefix
// the static resources of the server, e.g. the providers' images
const resourcesPath = `${window.ALFIO_CONTEXT_PATH ?? ''}/resources`;

const MAILER_PREFIXES: Record<string, string> = {
    smtp: 'SMTP_',
    mailgun: 'MAILGUN_',
    mailjet: 'MAILJET_',
    sendgrid: 'SENDGRID_',
};

const MAILER_CHOICES: ChoiceCardOption<string>[] = [
    {value: 'smtp', icon: 'hdd-network', title: 'SMTP server', description: 'Send through your own mail server.'},
    {value: 'mailgun', icon: 'envelope-at', title: 'Mailgun', description: 'Send through the Mailgun API.'},
    {value: 'mailjet', icon: 'envelope-paper', title: 'Mailjet', description: 'Send through the Mailjet API.'},
    {value: 'sendgrid', icon: 'send', title: 'SendGrid', description: 'Send through the SendGrid API.'},
    {value: 'disabled', icon: 'envelope-slash', title: 'Disabled', description: 'No e-mail is sent.'},
];

const MAPS_CHOICES: ChoiceCardOption<string>[] = [
    {value: 'NONE', icon: 'slash-circle', title: 'None', description: 'The event pages show the address only.'},
    {value: 'GOOGLE', icon: 'google', title: 'Google Maps', description: 'Needs a Google Maps API key.'},
    {value: 'HERE', icon: 'map', title: 'HERE', description: 'Needs a HERE API key.'},
];

// the API key of each maps provider
const MAPS_KEYS: Record<string, string> = {
    GOOGLE: 'MAPS_CLIENT_API_KEY',
    HERE: 'MAPS_HERE_API_KEY',
};

const CHECK_IN_COLORS_EXAMPLE = `{
  "defaultColorName": "success",
  "configurations": [
    { "colorName": "info", "categories": [1] }
  ]
}`;

/**
 * The settings of a level (system, organization or event), grouped by topic, with a section index.
 * A setting left empty ("not set") takes the value of the level above. Only the settings that have been
 * changed are saved; a setting that has been emptied is removed from the level.
 *
 * With "basic", only the settings needed to run alf.io are shown (first start): the host listens to
 * alfio-settings-saved and alfio-settings-cancelled.
 */
@customElement('alfio-settings-form')
export class SettingsForm extends LitElement {

    @property({attribute: false}) level!: SettingsLevel;
    @property({type: Boolean, attribute: 'data-basic'}) basic = false;
    // where Cancel goes (e.g. the event, when the form is one of its pages); without it, Cancel discards the changes
    @property({type: String, attribute: 'data-cancel-url'}) cancelUrl = '';

    @state() private lastData: LoadedSettings | null = null;
    @state() private saving = false;
    @state() private submitError: string | null = null;
    @state() private invalid = false;
    @state() private generatingTickets = false;
    @state() private refreshCount = 0;
    // the values right after the last load: what Cancel goes back to
    private initialValues: SettingsFormValues = {settings: {}, extensions: {}};

    readonly #form = new TanStackFormController(this, {
        defaultValues: {settings: {}, extensions: {}} as SettingsFormValues,
        onSubmit: async ({value}) => {
            await this.save(value);
        },
    });

    private readonly loadTask = new Task(this, {
        task: async ([level]): Promise<LoadedSettings> => {
            let extensionsRequest: Promise<ExtensionSettingsById> = Promise.resolve({});
            let euCountriesRequest: Promise<{ code: string; name: string }[]> = Promise.resolve([]);
            if (!this.basic) {
                extensionsRequest = SettingsService.loadExtensionSettings(level);
                euCountriesRequest = SettingsService.loadEuCountries();
            }
            let platformRequest: Promise<PlatformModeStatus | null> = Promise.resolve(null);
            if (level.type === 'ORGANIZATION') {
                platformRequest = SettingsService.loadPlatformModeStatus(level.organizationId);
            }
            const [byCategory, inherited, extensions, euCountries, platform] = await Promise.all([
                SettingsService.loadSettings(level),
                SettingsService.loadInheritedValues(level),
                extensionsRequest,
                euCountriesRequest,
                platformRequest,
            ]);
            const settings = new Map<string, IndexedSetting>();
            const categories = new Map<string, ConfigurationSetting[]>();
            for (const [category, list] of Object.entries(byCategory)) {
                const visible = list.filter((setting) => setting.internal !== true && !HIDDEN_SETTINGS.has(setting.key));
                // yes/no settings first, then the values to type
                visible.sort((a, b) => sortWeight(a) - sortWeight(b));
                categories.set(category, visible);
                for (const setting of visible) {
                    settings.set(setting.key, {category, setting});
                }
            }
            return {
                level,
                settings,
                categories,
                extensions: Object.values(extensions).filter((list) => list.length > 0),
                euCountries: euCountries.map((country) => ({value: country.code, label: country.name})),
                platform,
                inherited,
            };
        },
        args: () => [this.level, this.refreshCount] as const,
        onComplete: (data) => {
            this.initialValues = initialValues(data);
            this.#form.api.reset(structuredClone(this.initialValues));
            this.invalid = false;
            this.lastData = data;
        },
    });

    static readonly styles = [
        base,
        textColors,
        badges,
        form,
        row,
        modernLayout,
        creationLayout,
        choiceCardsStyles,
        css`
            :host {
                display: block;
            }
            .section-card + .section-card {
                margin-top: var(--sl-spacing-medium);
            }
            .card-description {
                margin: 0 0 var(--sl-spacing-medium);
            }
            .switches {
                row-gap: var(--sl-spacing-medium);
            }
            /* a longer label wraps: the label takes the extra height so that the selects of a row line up */
            .switches > sl-select::part(form-control) {
                display: flex;
                flex-direction: column;
                height: 100%;
            }
            .switches > sl-select::part(form-control-label) {
                flex: 1 0 auto;
            }
            .secret-help sl-button::part(base) {
                height: auto;
                min-height: 0;
                line-height: inherit;
                font-size: inherit;
                padding: 0;
            }
            .secret-help sl-button::part(label) {
                padding-inline-start: var(--sl-spacing-2x-small);
            }
            .secret-help .remove-secret::part(base) {
                color: var(--sl-color-danger-600);
            }
            .card-alert {
                margin-bottom: var(--sl-spacing-medium);
            }
            .payment-methods {
                display: grid;
                gap: var(--sl-spacing-x-small);
            }
            .payment-methods legend {
                font-weight: bold;
                margin-bottom: var(--sl-spacing-x-small);
                padding: 0;
            }
            fieldset.payment-methods {
                border: 0;
                margin: 0;
                padding: 0;
                min-width: 0;
            }
            .connect {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                gap: var(--sl-spacing-small) var(--sl-spacing-medium);
                margin-bottom: var(--sl-spacing-medium);
            }
            .connect-button {
                display: inline-flex;
                border-radius: var(--sl-border-radius-medium);
            }
            /* Stripe's "Connect with Stripe" button, as in https://codepen.io/stripe-connect/pen/BaQNbjK */
            .stripe-connect {
                position: relative;
                width: 180px;
                height: 38px;
                background: #635bff;
                border-radius: 4px;
                text-decoration: none;
                user-select: none;
                -webkit-font-smoothing: antialiased;
            }
            .stripe-connect:hover {
                background: #7a73ff;
            }
            .stripe-connect span {
                padding: 11px 0 0 24px;
                color: #ffffff;
                font-family: sohne-var, "Helvetica Neue", Arial, sans-serif;
                font-size: 15px;
                font-weight: 400;
                line-height: 14px;
            }
            .connect-button.stripe-connect img {
                position: absolute;
                left: 62%;
                top: 7.24px;
                width: 49.58px;
                height: auto;
            }
            .connect-button img {
                display: block;
            }
            .connect-button:focus-visible {
                outline: var(--sl-focus-ring);
                outline-offset: var(--sl-focus-ring-offset);
            }
            .label-layout {
                align-items: start;
            }
            fieldset.invoice-number {
                display: grid;
                gap: var(--sl-spacing-x-small);
                border: 0;
                margin: 0;
                padding: 0;
                min-width: 0;
            }
            fieldset.invoice-number legend {
                padding: 0;
                margin: 0 0 var(--sl-spacing-3x-small);
                font-size: var(--sl-input-label-font-size-medium);
                font-weight: bold;
                color: var(--sl-input-label-color);
            }
            fieldset.invoice-number sl-select,
            fieldset.invoice-number sl-input {
                margin-top: 0;
            }
            .invoice-number-parts {
                display: grid;
                grid-template-columns: minmax(0, 1fr) 8.5rem minmax(0, 1fr);
                row-gap: var(--sl-spacing-3x-small);
            }
            /* the parts share their borders, like a single input */
            .invoice-number-parts > sl-input:focus-within,
            .invoice-number-parts > sl-select:focus-within {
                position: relative;
                z-index: 1;
            }
            .invoice-number-prefix::part(base) {
                border-start-end-radius: 0;
                border-end-end-radius: 0;
            }
            .invoice-number-digits {
                margin-inline: -1px;
            }
            .invoice-number-digits::part(combobox) {
                border-radius: 0;
                background-color: var(--sl-color-primary-50);
                font-family: var(--sl-font-mono);
            }
            .invoice-number-suffix::part(base) {
                border-start-start-radius: 0;
                border-end-start-radius: 0;
            }
            /* the samples and their hints need more room than the field */
            .invoice-number-digits::part(listbox) {
                min-width: 13rem;
            }
            .invoice-number-digits sl-option::part(suffix) {
                font-size: var(--sl-font-size-x-small);
                opacity: 0.75;
            }
            .invoice-number-caption {
                font-size: var(--sl-font-size-x-small);
                color: var(--sl-color-gray-600);
            }
            .invoice-number-preview {
                display: flex;
                flex-wrap: wrap;
                align-items: baseline;
                gap: var(--sl-spacing-x-small);
                margin: 0;
                padding: var(--sl-spacing-x-small) var(--sl-spacing-small);
                background: var(--sl-color-gray-50);
                border-radius: var(--sl-border-radius-medium);
                font-size: var(--sl-font-size-small);
            }
            .invoice-number-sample {
                font-family: var(--sl-font-mono);
                font-weight: var(--sl-font-weight-semibold);
                overflow-wrap: anywhere;
            }
            .invoice-number-sample mark {
                padding: 0 var(--sl-spacing-3x-small);
                border-radius: var(--sl-border-radius-small);
                background-color: var(--sl-color-primary-100);
                color: var(--sl-color-primary-800);
            }
            .invoice-number-help {
                font-size: var(--sl-font-size-x-small);
            }
            .example {
                margin: var(--sl-spacing-2x-small) 0 0;
                padding: var(--sl-spacing-small);
                background: var(--sl-color-gray-50);
                border: 1px solid var(--sl-color-gray-200);
                border-radius: var(--sl-border-radius-medium);
                font-size: var(--sl-font-size-x-small);
                overflow-x: auto;
            }
            .example-label {
                margin: 0;
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }
            .footer sl-divider {
                margin-top: var(--sl-spacing-large);
            }
            .footer-message {
                margin: 0 0 var(--sl-spacing-small);
                text-align: right;
            }
            #submit-error {
                margin-top: var(--sl-spacing-medium);
            }
            /* preview of the badge layout, see label-layout-preview.ts */
            .label-preview {
                margin: 0;
            }
            .label-sample {
                display: flex;
                justify-content: space-between;
                gap: var(--sl-spacing-medium);
                margin-top: var(--sl-spacing-2x-small);
                padding: var(--sl-spacing-medium);
                border: 1px dashed var(--sl-color-gray-400);
                border-radius: var(--sl-border-radius-medium);
                background: var(--sl-color-neutral-0);
                max-width: 22rem;
            }
            .label-text {
                display: grid;
                align-content: start;
                gap: var(--sl-spacing-3x-small);
                min-width: 0;
                overflow-wrap: anywhere;
            }
            .label-first-row {
                font-size: var(--sl-font-size-x-large);
            }
            .label-second-row {
                font-size: var(--sl-font-size-large);
            }
            .label-other-row {
                font-size: var(--sl-font-size-small);
                color: var(--sl-color-gray-600);
            }
            .label-code {
                display: grid;
                justify-items: center;
                gap: var(--sl-spacing-2x-small);
                font-size: var(--sl-font-size-x-small);
            }
            .basic-form .section-card + .section-card {
                margin-top: var(--sl-spacing-medium);
            }
        `,
    ];

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    render(): TemplateResult {
        return taskContent(this.loadTask, this.lastData, 'Failed to load the settings. Please try again.',
            (data) => this.renderContent(data));
    }

    private renderContent(data: LoadedSettings): TemplateResult {
        if (data.settings.size === 0) {
            return html`
                <sl-alert open variant="primary">
                    <sl-icon slot="icon" name="info-circle"></sl-icon>
                    You can't change the settings of ${levelDescription(data.level)}.
                </sl-alert>
            `;
        }
        if (this.basic) {
            return html`
                <form class="basic-form" novalidate @submit=${this.submit}>
                    ${this.renderBasicCards(data)}
                    ${this.renderFooter()}
                </form>
            `;
        }
        const sections = this.sections(data);
        return html`
            <form novalidate @submit=${this.submit}>
                <div class="creation-layout">
                    <alfio-section-nav label="Settings sections" .sections=${sections.map(({id, label}) => ({id, label}))}></alfio-section-nav>
                    <div class="creation-content">
                        ${repeat(sections, (section) => section.id, (section) => html`
                            <section id=${section.id} class="form-section" tabindex="-1" aria-labelledby="${section.id}-title">
                                <h2 id="${section.id}-title">${section.label}</h2>
                                ${section.content}
                            </section>
                        `)}
                        ${this.renderFooter()}
                    </div>
                </div>
            </form>
            ${when(data.level.type === 'EVENT', () => html`<alfio-regenerate-invoices-dialog></alfio-regenerate-invoices-dialog>`)}
        `;
    }

    private renderFooter(): TemplateResult {
        return html`
            ${when(this.submitError, () => html`
                <sl-alert id="submit-error" variant="danger" open closable @sl-after-hide=${() => { this.submitError = null; }}>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${this.submitError}
                </sl-alert>
            `)}
            <!-- the footer stays inside the form, because Save submits it -->
            <div class="footer">
                <sl-divider></sl-divider>
                ${when(this.invalid, () => html`<p class="footer-message text-danger">${INVALID_FORM_MESSAGE}</p>`)}
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button id="cancel-settings" variant="default" size="large" ?disabled=${this.saving}
                               @click=${() => void this.cancel()}>Cancel</sl-button>
                    <div></div>
                    <sl-button id="save-settings" type="submit" variant="warning" size="large"
                               ?loading=${this.saving} ?disabled=${this.saving}>Save</sl-button>
                </div>
            </div>
        `;
    }

    // ---------- sections ----------

    private sections(data: LoadedSettings): RenderedSection[] {
        const candidates: { id: string; label: string; cards: (TemplateResult | null)[] }[] = [
            {id: 'general', label: 'General', cards: this.generalCards(data)},
            {id: 'email', label: 'E-mail', cards: this.emailCards(data, false)},
            {id: 'payments', label: 'Payments', cards: this.paymentCards(data)},
            {id: 'invoices', label: 'Invoices', cards: this.invoiceCards(data)},
            {id: 'integrations', label: 'Integrations', cards: this.integrationCards(data)},
            {id: 'extensions', label: 'Extensions', cards: this.extensionCards(data)},
        ];
        return candidates
            .map(({id, label, cards}) => ({id, label, cards: cards.filter((card): card is TemplateResult => card != null)}))
            .filter(({cards}) => cards.length > 0)
            .map(({id, label, cards}) => ({id, label, content: html`${cards}`}));
    }

    private generalCards(data: LoadedSettings): (TemplateResult | null)[] {
        const system = data.level.type === 'SYSTEM';
        return [
            ...this.generalOptionCards(data),
            this.simpleCard(data, 'RESERVATION_UI', {id: 'reservation-process', icon: 'cart3', title: 'Reservation process'}),
            this.simpleCard(data, 'WAITING_LIST', {id: 'waiting-list', icon: 'hourglass-split', title: 'Waiting list'}),
            this.subscriptionsCard(data),
            when(system, () => this.simpleCard(data, 'IMPORT_ATTENDEE', {id: 'attendee-import', icon: 'person-plus', title: 'Attendee import'}), () => null),
            when(system, () => this.simpleCard(data, 'RESERVATION_EMBED', {
                id: 'embedding', icon: 'code-slash', title: 'Embedding',
                description: html`Allow other websites to show the reservation process in a frame.`,
            }), () => null),
            when(system, () => this.simpleCard(data, 'OPENID', {
                id: 'attendee-login', icon: 'person-badge', title: 'Attendee login',
                description: html`Let attendees log in to save their profile.`,
            }), () => null),
        ];
    }

    /**
     * The GENERAL category mixes unrelated settings: one card per topic, then the remaining ones
     */
    private generalOptionCards(data: LoadedSettings): (TemplateResult | null)[] {
        // APPLY_TAX_TO_CATEGORY applies to the ticket categories only
        const grouped = new Set([...GENERAL_GROUPS, GOOGLE_ANALYTICS].flatMap((group) => group.keys).concat('APPLY_TAX_TO_CATEGORY'));
        const others = this.categorySettings(data, 'GENERAL').filter((setting) => !grouped.has(setting.key));
        return [
            ...GENERAL_GROUPS.map((group) => this.settingsCard(data, this.generalSettings(data, group.keys), group)),
            this.settingsCard(data, others, {id: 'general-options', icon: 'sliders', title: 'Other options'}),
        ];
    }

    // the GENERAL settings with the given keys, in the order of the keys
    private generalSettings(data: LoadedSettings, keys: string[]): ConfigurationSetting[] {
        const settings = this.categorySettings(data, 'GENERAL');
        return keys
            .map((key) => settings.find((setting) => setting.key === key))
            .filter((setting): setting is ConfigurationSetting => setting != null);
    }

    private subscriptionsCard(data: LoadedSettings): TemplateResult | null {
        const settings = this.categorySettings(data, 'SUBSCRIPTIONS');
        if (settings.length === 0) {
            return null;
        }
        // the generation can be forced only once it has been enabled, and saved, at this level
        const generation = data.settings.get(GENERATE_TICKETS_FOR_SUBSCRIPTIONS)?.setting;
        const canGenerate = generation != null && generation.id > -1 && generation.value === 'true';
        return sectionCard({
            icon: 'collection', title: 'Subscriptions',
            actions: when(canGenerate, () => html`
                <sl-button id="generate-tickets" variant="default" size="small" ?loading=${this.generatingTickets}
                           @click=${() => void this.generateTickets(data.level)}>
                    <sl-icon slot="prefix" name="arrow-clockwise"></sl-icon>
                    Generate tickets now
                </sl-button>
            `, () => undefined),
        }, html`<div data-card="subscriptions">${this.renderSettings(data, settings)}</div>`);
    }

    private emailCards(data: LoadedSettings, basic: boolean): (TemplateResult | null)[] {
        const settings = this.categorySettings(data, 'MAIL');
        if (settings.length === 0) {
            return [];
        }
        const hasMailerType = settings.some((setting) => setting.key === MAILER_TYPE);
        const isProviderSetting = (setting: ConfigurationSetting) => hasMailerType && mailerOf(setting.key) != null;
        let options = settings.filter((setting) => setting.key !== MAILER_TYPE && !isProviderSetting(setting));
        if (basic) {
            options = options.filter((setting) => setting.key === 'MAX_EMAIL_PER_CYCLE' || setting.key === 'MAIL_REPLY_TO');
        }
        return [
            when(hasMailerType, () => sectionCard({icon: 'envelope', title: 'E-mail delivery'},
                this.field({name: `settings.${MAILER_TYPE}`}, (field: BoundField<string>) => {
                    const providerSettings = settings.filter((setting) => mailerOf(setting.key) === field.state.value);
                    let choices = MAILER_CHOICES;
                    if (data.level.type !== 'SYSTEM') {
                        choices = [{value: '', icon: 'arrow-up-circle', title: notSetLabel(data.level), description: 'Use the delivery settings of the level above.'}, ...MAILER_CHOICES];
                    }
                    return choiceCards({
                        name: 'mailer-type',
                        className: 'mailer-type',
                        label: 'How should alf.io send e-mails?',
                        value: field.state.value,
                        options: choices,
                        panel: when(providerSettings.length > 0, () => this.renderSettings(data, providerSettings), () => undefined),
                    }, (value) => field.handleChange(value));
                })), () => null),
            when(options.length > 0, () => sectionCard({icon: 'envelope-paper', title: 'E-mail options'},
                html`<div data-card="email-options">${this.renderSettings(data, options)}</div>`), () => null),
        ];
    }

    private paymentCards(data: LoadedSettings): (TemplateResult | null)[] {
        const level = data.level;
        return [
            this.paymentOptionsCard(data, false),
            this.stripeCard(data),
            this.simpleCard(data, 'PAYMENT_PAYPAL', {
                id: 'paypal', icon: 'paypal', title: 'PayPal',
                description: html`Accept payments with your PayPal account.`,
                guide: setupGuide('How to get the Client ID and Secret', [
                    html`In the ${externalLink('https://developer.paypal.com/dashboard/applications/live', 'PayPal Developer Dashboard')},
                        open Apps & Credentials in Live mode and create a REST API app, as explained in the
                        ${externalLink('https://developer.paypal.com/api/rest/', 'PayPal documentation')}.`,
                    html`Enter the Client ID and Secret of the live app below, and set "Live mode" to Yes.`,
                    html`To accept a currency that your account doesn't hold, allow the automatic conversion in the
                        ${externalLink('https://www.paypal.com/cgi-bin/customerprofileweb?cmd=_profile-pref', 'PayPal dashboard')}:
                        Profile, Settings, Account Settings, Payment Preferences, Block Payments.`,
                ]),
            }),
            when(level.type !== 'EVENT', () => this.mollieCard(data), () => null),
            this.bankTransferCard(data),
            this.revolutCard(data),
            when(level.type !== 'EVENT', () => this.simpleCard(data, 'PAYMENT_SAFERPAY', {id: 'saferpay', icon: 'credit-card', title: 'Saferpay'}), () => null),
            this.customOfflinePaymentsCard(level),
        ];
    }

    private customOfflinePaymentsCard(level: SettingsLevel): TemplateResult | null {
        if (level.type !== 'ORGANIZATION') {
            return null;
        }
        return sectionCard({
            icon: 'cash-coin', title: 'Custom offline payment methods',
            actions: html`
                <sl-button id="add-payment-method" variant="success" size="small"
                           @click=${() => this.renderRoot.querySelector<OfflinePaymentConfigBlock>('offline-payment-config-block')?.add()}>
                    <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                    Add payment method
                </sl-button>
            `,
        }, html`
            <p class="card-description text-muted">
                Payments that the buyers make outside alf.io, following your instructions. Each event chooses which ones to offer.
            </p>
            <offline-payment-config-block organization=${level.organizationId}></offline-payment-config-block>
        `);
    }

    private paymentOptionsCard(data: LoadedSettings, basic: boolean): TemplateResult | null {
        let settings = this.categorySettings(data, 'PAYMENT');
        if (basic) {
            settings = settings.filter((setting) => setting.basic === true);
        }
        if (settings.length === 0) {
            return null;
        }
        const others = settings.filter((setting) => setting.key !== PAYMENT_METHODS_DENY_LIST);
        const hasDenyList = settings.length > others.length;
        return sectionCard({icon: 'wallet2', title: 'Payment options'}, html`
            <div class="form-stack" data-card="payment-options">
                ${this.renderSettings(data, others)}
                ${when(hasDenyList, () => this.renderDenyList())}
            </div>
        `);
    }

    private renderDenyList(): TemplateResult {
        return this.field({name: `settings.${PAYMENT_METHODS_DENY_LIST}`}, (field: BoundField<string>) => {
            const denied = splitList(field.state.value);
            const toggle = (method: string, checked: boolean) => {
                const methods = denied.filter((m) => m !== method);
                if (checked) {
                    methods.push(method);
                }
                field.handleChange(methods.join(','));
            };
            return html`
                <fieldset class="payment-methods" id=${PAYMENT_METHODS_DENY_LIST}>
                    <legend>Payment methods that can't be used</legend>
                    ${repeat(Object.keys(PAYMENT_PROXY_DESCRIPTIONS), (method) => method, (method) => html`
                        <sl-checkbox class="denied-payment-method" value=${method} ?checked=${denied.includes(method)}
                                     @sl-change=${(e: Event) => toggle(method, (e.target as SlCheckbox).checked)}>
                            ${when(denied.includes(method),
                                () => html`<del>${PAYMENT_PROXY_DESCRIPTIONS[method]}</del>`,
                                () => html`${PAYMENT_PROXY_DESCRIPTIONS[method]}`)}
                        </sl-checkbox>
                    `)}
                </fieldset>
            `;
        });
    }

    private stripeSteps(level: SettingsLevel): TemplateResult[] {
        const steps = [
            html`Copy the publishable key and the secret key from the Stripe dashboard into the fields below, as explained in the
                ${externalLink('https://docs.stripe.com/keys', 'Stripe documentation')}.`,
        ];
        // Strong Customer Authentication is enabled for the whole system
        if (level.type === 'SYSTEM') {
            steps.push(html`To use Strong Customer Authentication (3D Secure 2), follow the
                ${externalLink('https://alf.io/docs/configuration/payment/stripe-sca/', 'alf.io guide')}.`);
        }
        return steps;
    }

    private stripeCard(data: LoadedSettings): TemplateResult | null {
        const settings = this.categorySettings(data, 'PAYMENT_STRIPE');
        if (settings.length === 0) {
            return null;
        }
        const level = data.level;
        if (level.type === 'ORGANIZATION' && data.platform?.enabled === true) {
            return sectionCard({icon: 'stripe', title: 'Stripe'}, html`
                ${this.connectStatus(html`
                    <!-- Stripe's "Connect with Stripe" button, see https://codepen.io/stripe-connect/pen/BaQNbjK -->
                    <a class="connect-button stripe-connect"
                       href="/admin/configuration/payment/stripe/connect/${level.organizationId}">
                        <span>Connect with</span>
                        <img src="${resourcesPath}/images/payment/stripe-connect-wordmark-white.svg" alt="Stripe">
                    </a>
                `, data.platform.stripeConnected === true)}
            `);
        }
        return sectionCard({icon: 'stripe', title: 'Stripe'}, html`
            <p class="card-description text-muted">Accept credit cards and other payment methods with your Stripe account.</p>
            ${setupGuide('How to get the API keys', this.stripeSteps(level))}
            <div data-card="stripe">${this.renderSettings(data, settings)}</div>
        `);
    }

    private mollieCard(data: LoadedSettings): TemplateResult | null {
        const settings = this.categorySettings(data, 'PAYMENT_MOLLIE');
        if (settings.length === 0) {
            return null;
        }
        const level = data.level;
        if (level.type === 'ORGANIZATION') {
            // with the platform mode, the organization connects its own account; otherwise it enters its API key
            const platform = data.platform?.enabled === true;
            const visible = settings.filter((setting) => setting.key.includes('_CONNECT') === platform);
            return sectionCard({icon: 'credit-card-2-front', title: 'Mollie'}, html`
                ${when(platform, () => this.connectStatus(html`
                    <!-- Mollie's own button, as its brand guidelines require -->
                    <a class="connect-button" href="/admin/configuration/payment/mollie/connect/${level.organizationId}">
                        <img src="${resourcesPath}/images/payment/mollie-connect-button.png"
                             srcset="${resourcesPath}/images/payment/mollie-connect-button@2x.png 2x"
                             width="200" height="45" alt="Connect via Mollie">
                    </a>
                `, data.platform?.mollieConnected === true))}
                <div data-card="mollie">${this.renderSettings(data, visible)}</div>
            `);
        }
        return sectionCard({icon: 'credit-card-2-front', title: 'Mollie'},
            html`<div data-card="mollie">${this.renderSettings(data, settings)}</div>`);
    }

    /**
     * @param button the provider's own "connect" button
     */
    private connectStatus(button: TemplateResult, connected: boolean): TemplateResult {
        return html`
            <div class="connect">
                ${button}
                ${when(connected,
                    () => html`<sl-badge variant="success" pill>Connected</sl-badge>`,
                    () => html`<sl-badge variant="neutral" pill>Not connected</sl-badge>`)}
            </div>
        `;
    }

    private bankTransferCard(data: LoadedSettings): TemplateResult | null {
        const settings = this.categorySettings(data, 'PAYMENT_OFFLINE');
        if (settings.length === 0) {
            return null;
        }
        return sectionCard({icon: 'bank', title: 'Bank transfer'}, html`
            <p class="card-description text-muted">
                The buyers pay by bank transfer, and the reservation is confirmed once the payment has arrived.
                ${externalLink('https://alf.io/docs/configuration/payment/bank-transfer/', 'Documentation')}
            </p>
            ${this.field({name: `settings.${DEFERRED_BANK_TRANSFER}`}, (deferredField: BoundField<string>) => {
                // the payment instructions sent manually replace the ones computed by alf.io
                const deferred = deferredField.state.value === 'true';
                const visible = settings.filter((setting) => {
                    if (setting.key === 'BANK_TRANSFER_ENABLED' || setting.key === DEFERRED_BANK_TRANSFER) {
                        return true;
                    }
                    if (setting.key.startsWith('REVOLUT')) {
                        return false;
                    }
                    if (setting.key === 'DEFERRED_BANK_TRANSFER_SEND_CONFIRMATION_EMAIL') {
                        return deferred;
                    }
                    return !deferred;
                });
                return html`<div data-card="bank-transfer">${this.renderSettings(data, visible)}</div>`;
            })}
        `);
    }

    private revolutCard(data: LoadedSettings): TemplateResult | null {
        const settings = this.categorySettings(data, 'PAYMENT_OFFLINE').filter((setting) => setting.key.startsWith('REVOLUT'));
        if (settings.length === 0) {
            return null;
        }
        return this.field({name: `settings.${DEFERRED_BANK_TRANSFER}`}, (deferredField: BoundField<string>) => {
            if (deferredField.state.value === 'true') {
                return html`${nothing}`;
            }
            return sectionCard({icon: 'arrow-left-right', title: 'Revolut'}, html`
                <p class="card-description text-muted">
                    If the bank account of the bank transfers is a Revolut account, alf.io can check the incoming payments
                    and confirm the reservations once they have been paid.
                </p>
                <div data-card="revolut">${this.renderSettings(data, settings)}</div>
            `);
        });
    }

    private invoiceCards(data: LoadedSettings): (TemplateResult | null)[] {
        const level = data.level;
        return [
            this.simpleCard(data, 'INVOICE', {id: 'invoice-options', icon: 'receipt', title: 'Invoice options'}),
            this.simpleCard(data, 'INVOICE_EU', {id: 'vat', icon: 'percent', title: 'VAT for business customers'}),
            when(level.type === 'EVENT', () => sectionCard({
                icon: 'arrow-repeat', title: 'Regenerate invoices',
                actions: html`
                    <sl-button id="regenerate-invoices" variant="default" size="small"
                               @click=${() => this.openRegenerateInvoices(level)}>
                        <sl-icon slot="prefix" name="arrow-repeat"></sl-icon>
                        Regenerate
                    </sl-button>
                `,
            }, html`
                <p class="card-description text-muted">
                    An invoice keeps the details it was created with. Regenerate the invoices of a period to apply the
                    current settings of the event to them. To change the details of a single invoice, regenerate it from
                    its reservation.
                </p>
            `), () => null),
        ];
    }

    private integrationCards(data: LoadedSettings): (TemplateResult | null)[] {
        const system = data.level.type === 'SYSTEM';
        return [
            when(system, () => this.mapsCard(data), () => null),
            when(system, () => this.simpleCard(data, 'WALLET_INTEGRATION', {id: 'google-wallet', icon: 'google', title: 'Google Wallet'}), () => null),
            this.simpleCard(data, 'PASS_INTEGRATION', {id: 'apple-wallet', icon: 'apple', title: 'Apple Wallet'}),
            this.checkInCard(data),
            this.settingsCard(data, this.generalSettings(data, GOOGLE_ANALYTICS.keys), GOOGLE_ANALYTICS),
        ];
    }

    private mapsCard(data: LoadedSettings): TemplateResult | null {
        if (!data.settings.has(MAPS_PROVIDER)) {
            return null;
        }
        return sectionCard({icon: 'geo-alt', title: 'Maps'},
            this.field({name: `settings.${MAPS_PROVIDER}`}, (field: BoundField<string>) => {
                const key = MAPS_KEYS[field.state.value];
                const apiKey = data.settings.get(key ?? '')?.setting;
                return choiceCards({
                    name: 'maps-provider',
                    className: 'maps-provider',
                    label: 'Which map should the event pages show?',
                    value: field.state.value,
                    options: MAPS_CHOICES,
                    panel: when(apiKey != null, () => this.renderSettings(data, [apiKey!]), () => undefined),
                }, (value) => field.handleChange(value));
            }));
    }

    private checkInCard(data: LoadedSettings): TemplateResult | null {
        const settings = this.categorySettings(data, 'ALFIO_PI');
        if (settings.length === 0) {
            return null;
        }
        const options = settings.filter((setting) => setting.key !== LABEL_LAYOUT && setting.key !== CHECK_IN_COLORS);
        // the badges are printed for the attendees of an event: their layout and colors are set for each event
        const event = data.level.type === 'EVENT';
        const hasLabelLayout = event && data.settings.has(LABEL_LAYOUT);
        const hasColors = event && data.settings.has(CHECK_IN_COLORS);
        return sectionCard({icon: 'qr-code-scan', title: 'Offline check-in and badge printing'}, html`
            <p class="card-description text-muted">
                Settings of ${externalLink('https://github.com/alfio-event/alf.io-PI', 'Alf.io-PI')},
                the app for offline check-in and badge printing.
            </p>
            <div class="form-stack" data-card="check-in">
                ${this.renderSettings(data, options)}
                ${when(hasLabelLayout, () => this.field({name: `settings.${LABEL_LAYOUT}`, validators: validateWith(validJson)},
                    (field: BoundField<string>) => html`
                        <div class="row label-layout">
                            ${settingControl(field, this.submitted(), {
                                id: LABEL_LAYOUT, label: 'Badge layout', componentType: 'TEXTAREA',
                                helpText: 'The layout of the badges, in JSON.',
                            }, data.level)}
                            ${labelLayoutPreview(field.state.value)}
                        </div>
                    `))}
                ${when(hasColors, () => this.field({name: `settings.${CHECK_IN_COLORS}`, validators: validateWith(validJson)},
                    (field: BoundField<string>) => html`
                        <div class="row">
                            ${settingControl(field, this.submitted(), {
                                id: CHECK_IN_COLORS, label: 'Badge colors', componentType: 'TEXTAREA',
                                helpText: 'The color shown by the check-in app for each ticket category, in JSON.',
                            }, data.level)}
                            <div>
                                <p class="example-label">Example</p>
                                <pre class="example">${CHECK_IN_COLORS_EXAMPLE}</pre>
                            </div>
                        </div>
                    `))}
            </div>
        `);
    }

    private extensionCards(data: LoadedSettings): TemplateResult[] {
        return data.extensions.map((parameters) => sectionCard({icon: 'plug', title: parameters[0].extensionDisplayName}, html`
            <div class="form-stack" data-card="extension-${parameters[0].extensionId}">
                ${repeat(parameters, (parameter) => parameter.id, (parameter) => this.renderExtensionSetting(data, parameter))}
            </div>
        `));
    }

    private renderBasicCards(data: LoadedSettings): TemplateResult {
        const general = this.categorySettings(data, 'GENERAL').filter((setting) => setting.basic === true);
        return html`
            ${when(general.length > 0, () => sectionCard({icon: 'sliders', title: 'Common options'},
                html`<div data-card="general-options">${this.renderSettings(data, general)}</div>`))}
            ${this.emailCards(data, true)}
            ${this.mapsCard(data)}
            ${this.paymentOptionsCard(data, true)}
        `;
    }

    // ---------- settings ----------

    private simpleCard(data: LoadedSettings, category: string, options: CardOptions): TemplateResult | null {
        return this.settingsCard(data, this.categorySettings(data, category), options);
    }

    private settingsCard(data: LoadedSettings, settings: ConfigurationSetting[], options: CardOptions): TemplateResult | null {
        if (settings.length === 0) {
            return null;
        }
        return sectionCard({icon: options.icon, title: options.title}, html`
            ${when(options.description, () => html`<p class="card-description text-muted">${options.description}</p>`)}
            ${options.guide ?? nothing}
            <div data-card=${options.id}>${this.renderSettings(data, settings)}</div>
        `);
    }

    private categorySettings(data: LoadedSettings, category: string): ConfigurationSetting[] {
        return data.categories.get(category) ?? [];
    }

    /**
     * The yes/no settings side by side, then the values to type
     */
    private renderSettings(data: LoadedSettings, settings: ConfigurationSetting[]): TemplateResult {
        const switches = settings.filter((setting) => setting.componentType === 'BOOLEAN');
        const values = settings.filter((setting) => setting.componentType !== 'BOOLEAN');
        return html`
            <div class="form-stack">
                ${when(switches.length > 0, () => html`
                    <div class="row switches" style="--alfio-row-cols: 2">
                        ${repeat(switches, (setting) => setting.key, (setting) => this.renderSetting(data, setting))}
                    </div>
                `)}
                ${repeat(values, (setting) => setting.key, (setting) => this.renderSetting(data, setting))}
            </div>
        `;
    }

    private renderSetting(data: LoadedSettings, setting: ConfigurationSetting): TemplateResult {
        let validators = undefined;
        if (JSON_SETTINGS.has(setting.key)) {
            validators = validateWith(validJson);
        }
        const inherited = data.inherited[setting.key];
        const inheritedValue = inherited?.value ?? undefined;
        let label = setting.description;
        let defaultValue: string | undefined = undefined;
        if (setting.componentType === 'BOOLEAN') {
            // the "not set" option shows the value that applies, or the default when that value is hidden
            defaultValue = defaultValueOf(label);
            label = withoutDefaultValue(label);
        }
        label = LABELS[setting.key] ?? label;
        const descriptor: SettingDescriptor = {
            id: setting.key,
            label,
            componentType: setting.componentType,
            inheritedValue,
            defaultValue,
            inheritedSet: inherited?.set === true,
            secret: setting.secret === true,
            secretStored: setting.secretValueSet === true,
        };
        if (setting.key === 'COUNTRY_OF_BUSINESS') {
            descriptor.options = data.euCountries;
        }
        const special = SPECIAL_EDITORS[setting.key];
        if (special != null) {
            descriptor.editor = special.editor;
            descriptor.label = special.label;
            descriptor.helpText = special.helpText;
        }
        return this.field({name: `settings.${setting.key}`, validators},
            (field: BoundField<string>) => settingControl(field, this.submitted(), descriptor, data.level));
    }

    private renderExtensionSetting(data: LoadedSettings, parameter: ExtensionSetting): TemplateResult {
        // a mandatory parameter is needed by the extension when it runs, not to save the other settings:
        // it is often left empty on the events that don't use the extension
        let helpText: string | undefined = undefined;
        if (parameter.mandatory) {
            helpText = 'The extension needs this value to work.';
        }
        const descriptor: SettingDescriptor = {
            id: `extension-${parameter.id}`,
            label: parameter.description || parameter.name,
            componentType: parameter.componentType,
            helpText,
        };
        return this.field({name: `extensions.${extensionKey(parameter)}`},
            (field: BoundField<string>) => settingControl(field, this.submitted(), descriptor, data.level));
    }

    /**
     * A control bound to a field of the form
     */
    private field(options: { name: FieldName; validators?: ReturnType<typeof validateWith> },
                  render: (field: BoundField<string>) => TemplateResult): TemplateResult {
        return html`${this.#form.field({name: options.name, validators: options.validators}, render)}`;
    }

    private submitted(): boolean {
        return this.#form.api.state.submissionAttempts > 0;
    }

    // ---------- actions ----------

    private submit = async (event: Event): Promise<void> => {
        event.preventDefault();
        if (this.saving) {
            return;
        }
        this.submitError = null;
        const valid = await submitValidated(this.#form.api);
        this.invalid = !valid;
        if (!valid) {
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    private async save(values: SettingsFormValues): Promise<void> {
        const data = this.lastData;
        if (data == null) {
            return;
        }
        this.saving = true;
        try {
            const level = data.level;
            const changed: Record<string, ConfigurationModification[]> = {};
            const removed: string[] = [];
            for (const [key, {category, setting}] of data.settings) {
                const value = values.settings[key] ?? '';
                if (value === REMOVE_SECRET) {
                    // the saved value of a secret isn't known: the user asked to remove it
                    removed.push(key);
                    continue;
                }
                if (value === (setting.value ?? '')) {
                    continue;
                }
                if (value.trim() === '') {
                    if (setting.value != null) {
                        removed.push(key);
                    }
                    continue;
                }
                changed[category] = [...(changed[category] ?? []), {id: setting.id, key, value}];
            }
            const changedExtensions: ExtensionSettingModification[] = [];
            const removedExtensions: number[] = [];
            for (const parameter of data.extensions.flat()) {
                const value = values.extensions[extensionKey(parameter)] ?? '';
                if (value === (parameter.value ?? '')) {
                    continue;
                }
                if (value.trim() === '') {
                    if (parameter.value != null) {
                        removedExtensions.push(parameter.id);
                    }
                    continue;
                }
                changedExtensions.push({id: parameter.id, value});
            }
            if (Object.keys(changed).length > 0) {
                await SettingsService.saveSettings(level, changed);
            }
            for (const key of removed) {
                await SettingsService.deleteSetting(level, key);
            }
            if (changedExtensions.length > 0) {
                await SettingsService.saveExtensionSettings(level, changedExtensions);
            }
            for (const id of removedExtensions) {
                await SettingsService.deleteExtensionSetting(level, id);
            }
            dispatchFeedback({type: 'success', message: 'The settings have been saved'}, this);
            this.dispatchEvent(new CustomEvent(SETTINGS_SAVED_EVENT, {bubbles: true, composed: true}));
            this.refreshCount++;
        } catch (e) {
            this.submitError = errorMessage(e, 'Failed to save the settings. Please try again.');
        } finally {
            this.saving = false;
        }
    }

    private async cancel(): Promise<void> {
        if (this.basic) {
            this.dispatchEvent(new CustomEvent(SETTINGS_CANCELLED_EVENT, {bubbles: true, composed: true}));
            return;
        }
        if (this.hasChanges()) {
            const confirmed = await ConfirmationDialogService.requestConfirm('Discard the changes',
                'The changes you made to the settings will be lost.', 'warning');
            if (!confirmed) {
                return;
            }
        }
        if (this.cancelUrl !== '') {
            window.location.hash = this.cancelUrl;
            return;
        }
        this.#form.api.reset(structuredClone(this.initialValues));
        this.invalid = false;
        this.submitError = null;
        this.renderRoot.querySelector<SectionNav>('alfio-section-nav')?.reset();
        window.scrollTo({top: 0});
    }

    private hasChanges(): boolean {
        return JSON.stringify(this.#form.api.state.values) !== JSON.stringify(this.initialValues);
    }

    private async generateTickets(level: SettingsLevel): Promise<void> {
        this.generatingTickets = true;
        try {
            await SettingsService.generateTicketsForSubscribers(level);
            dispatchFeedback({type: 'success', message: 'The tickets will be generated within a minute'}, this);
        } catch (e) {
            dispatchFeedback({type: 'danger', message: errorMessage(e, 'Failed to schedule the generation of the tickets')}, this);
        } finally {
            this.generatingTickets = false;
        }
    }

    private openRegenerateInvoices(level: SettingsLevel): void {
        if (level.type !== 'EVENT') {
            return;
        }
        void this.renderRoot.querySelector<RegenerateInvoicesDialog>('alfio-regenerate-invoices-dialog')?.show(level.eventId, level.eventDisplayName);
    }
}

function initialValues(data: LoadedSettings): SettingsFormValues {
    const settings: Record<string, string> = {};
    for (const [key, {setting}] of data.settings) {
        settings[key] = setting.value ?? '';
    }
    // as the previous editor did, the badge layout of an event starts from the default one, which is saved with the settings
    if (data.level.type === 'EVENT' && settings[LABEL_LAYOUT] === '') {
        settings[LABEL_LAYOUT] = DEFAULT_LABEL_LAYOUT;
    }
    const extensions: Record<string, string> = {};
    for (const parameter of data.extensions.flat()) {
        extensions[extensionKey(parameter)] = parameter.value ?? '';
    }
    return {settings, extensions};
}

// the field name of an extension parameter: a key starting with a digit would be read as an array index
function extensionKey(parameter: ExtensionSetting): string {
    return `x${parameter.id}`;
}

function sortWeight(setting: ConfigurationSetting): number {
    if (setting.componentType === 'BOOLEAN') {
        return 0;
    }
    return 10;
}

/**
 * The mail provider a setting belongs to, if any
 */
function mailerOf(key: string): string | undefined {
    return Object.keys(MAILER_PREFIXES).find((mailer) => key.startsWith(MAILER_PREFIXES[mailer]));
}

function splitList(value: string): string[] {
    return (value ?? '').split(',').map((item) => item.trim()).filter((item) => item !== '');
}

export function levelDescription(level: SettingsLevel): string {
    if (level.type === 'ORGANIZATION') {
        return level.organizationName;
    }
    if (level.type === 'EVENT') {
        return level.eventDisplayName;
    }
    return 'the system';
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-settings-form': SettingsForm;
    }
}
