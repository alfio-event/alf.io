import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {cache} from 'lit/directives/cache.js';
import {keyed} from 'lit/directives/keyed.js';
import {classMap} from 'lit/directives/class-map.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
import type {SlCheckbox, SlInput, SlSelect, SlSwitch} from '@shoelace-style/shoelace';
import {Organization} from '../../model/organization.ts';
import {AlfioEvent} from '../../model/event.ts';
import {ContentLanguage, DateTimeModification, LocalizedContent, PaymentProxy} from '../../model/purchase-context.ts';
import {EventFormat, EventModificationPayload} from '../../model/event-modification.ts';
import {AdditionalField, AdditionalFieldCreateRequest} from '../../model/additional-field.ts';
import {ErrorDescriptor} from '../../model/validation.ts';
import {AdditionalItem} from '../../model/additional-item.ts';
import {ConfirmationDialogService} from '../../service/confirmation-dialog.ts';
import {OrganizationService} from '../../service/organization.ts';
import {EventService} from '../../service/event.ts';
import {ConfigurationService} from '../../service/configuration.ts';
import {LocalizationService} from '../../service/localization.ts';
import {LocationService} from '../../service/location.ts';
import {UtilService, AvailableCurrency} from '../../service/util.ts';
import {PAYMENT_PROXY_DESCRIPTIONS, PaymentMethodDTO, PaymentProxyService} from '../../service/payment-proxy.ts';
import {AdditionalFieldService} from '../../service/additional-field.ts';
import {errorMessage, fetchJson, postJson, readRouteParams} from '../../service/helpers.ts';
import {toDateTimeLocal} from '../../service/date-format.ts';
import {formatAmount} from '../../service/reservation-format.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {sectionCard} from '../../components/section-card.ts';
import {taskContent} from '../../components/task-content.ts';
import '../../components/section-nav.ts';
import type {SectionNav} from '../../components/section-nav.ts';
import {ChoiceCardOption, choiceCards} from '../../components/choice-cards.ts';
import {
    BoundField,
    fieldError,
    focusFirstError,
    FormMountController,
    INVALID_FORM_MESSAGE,
    minLength,
    requiredValue,
    setServerError,
    submitValidated,
    validatedInput,
    ValidatedInputOptions,
    validatedSelect,
    validateWith,
    ValidatedFieldMeta,
} from '../../components/form-validation.ts';
import {badges, base, choiceCards as choiceCardsStyles, creationLayout, form, modernLayout, row, textColors} from '../../styles.ts';
import '../../components/purchase-context-image.ts';
import type {ImageChangeDetail} from '../../components/purchase-context-image.ts';
import '../../components/multi-language-text.ts';
import type {MultiLanguageTextChange} from '../../components/multi-language-text.ts';
import '../../components/format-date.ts';
import './category-dialog.ts';
import type {CategoryDialogContext, CategoryDraft, EventCategoryDialog} from './category-dialog.ts';
import {categoryDraftToPayload, dateTimeLocalToModification, newCategoryDraft, wallClockToDateTimeLocal} from './category-dialog.ts';
import './copy-event-dialog.ts';
import type {CopyEventDialog, CopyEventSelection} from './copy-event-dialog.ts';
import '../custom-offline-payment-event-selector/custom-offline-payment-event-selector.ts';

type PriceModel = 'paid' | 'free';

/**
 * The values of the create-event form, managed by TanStackFormController. Dates are
 * datetime-local values ("yyyy-MM-ddTHH:mm", wall-clock in the event time zone)
 */
interface EventFormValues {
    displayName: string;
    organizationId: string;
    format: EventFormat;
    location: string;
    begin: string;
    end: string;
    timeZone: string;
    description: LocalizedContent;
    shortName: string;
    websiteUrl: string;
    termsAndConditionsUrl: string;
    privacyPolicyUrl: string;
    priceModel: PriceModel;
    availableSeats: string;
    regularPrice: string;
    currency: string;
    vatPercentage: string;
    vatIncluded: boolean;
    paymentProxies: string[];
    linkedSubscriptions: string[];
    categories: CategoryDraft[];
}

interface LoadedData {
    organizations: readonly Organization[];
    languages: ContentLanguage[];
    currencies: AvailableCurrency[];
    timeZones: string[];
    baseUrl: string;
    descriptionMaxLength: number;
}

interface PendingCopy {
    additionalFields: AdditionalField[];
    additionalServices: AdditionalItem[];
}

const PRICE_MODEL_CHOICES: ChoiceCardOption<PriceModel>[] = [
    {
        value: 'paid',
        icon: 'cash-coin',
        title: 'Entry fee requested',
        description: 'Attendees pay for their ticket when they reserve it.',
    },
    {
        value: 'free',
        icon: 'gift',
        title: 'Free of charge',
        description: 'Tickets are free: no payment method is needed.',
    },
];

const FORMAT_CHOICES: ChoiceCardOption<EventFormat>[] = [
    {value: 'IN_PERSON', icon: 'geo-alt', title: 'In person', description: 'Attendees join at a physical venue.'},
    {value: 'ONLINE', icon: 'camera-video', title: 'Online', description: 'Attendees join remotely.'},
    {value: 'HYBRID', icon: 'broadcast', title: 'Hybrid', description: 'Offer both venue and online access.'},
];

const FORM_SECTIONS = [
    {id: 'event-details', label: 'Event details'},
    {id: 'tickets', label: 'Tickets and payments'},
    {id: 'event-page', label: 'Event page'},
];

const SHORT_NAME_PATTERN = /^[A-Za-z0-9]{1,}([-_]*[A-Za-z0-9]+)+$/;

const STORAGE_KEY = 'new_event';

const SERVER_ERROR_FIELD_MAP: Record<string, string> = {
    shortName: 'shortName',
    organizationId: 'organizationId',
    location: 'location',
    websiteUrl: 'websiteUrl',
    termsAndConditionsUrl: 'termsAndConditionsUrl',
    privacyPolicyUrl: 'privacyPolicyUrl',
    description: 'description',
    begin: 'begin',
    end: 'end',
    availableSeats: 'availableSeats',
    regularPrice: 'regularPrice',
    currency: 'currency',
    vatPercentage: 'vatPercentage',
};

function defaultTimeZone(): string {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    } catch {
        return 'UTC';
    }
}

function emptyValues(): EventFormValues {
    return {
        displayName: '',
        organizationId: '',
        format: 'IN_PERSON',
        location: '',
        begin: '',
        end: '',
        timeZone: defaultTimeZone(),
        description: {},
        shortName: '',
        websiteUrl: '',
        termsAndConditionsUrl: '',
        privacyPolicyUrl: '',
        priceModel: 'paid',
        availableSeats: '',
        regularPrice: '',
        currency: '',
        vatPercentage: '0',
        vatIncluded: false,
        paymentProxies: [],
        linkedSubscriptions: [],
        categories: [],
    };
}

/**
 * A required URL: http:// or https:// followed by a valid URL (the input normalizes to https:// on blur)
 */
function requiredUrl(value: unknown): string | undefined {
    const missing = requiredValue(value);
    if (missing != null) {
        return missing;
    }
    return optionalUrl(value);
}

function optionalUrl(value: unknown): string | undefined {
    const text = String(value ?? '').trim();
    if (text === '') {
        return undefined;
    }
    if (!/^https?:\/\//i.test(text)) {
        return 'The URL must start with http:// or https://';
    }
    try {
        new URL(text);
        return undefined;
    } catch {
        return 'Please enter a valid URL';
    }
}

function requiredPositiveInteger(value: unknown): string | undefined {
    const missing = requiredValue(value);
    if (missing != null) {
        return missing;
    }
    const parsed = Number(value);
    if (!Number.isInteger(parsed) || parsed < 1) {
        return 'Please enter a number, at least 1';
    }
    return undefined;
}

function requiredPositiveNumber(value: unknown): string | undefined {
    const missing = requiredValue(value);
    if (missing != null) {
        return missing;
    }
    const parsed = Number(value);
    if (Number.isNaN(parsed) || parsed <= 0) {
        return 'Please enter a number greater than 0';
    }
    return undefined;
}

function requiredNonNegativeNumber(value: unknown): string | undefined {
    const missing = requiredValue(value);
    if (missing != null) {
        return missing;
    }
    const parsed = Number(value);
    if (Number.isNaN(parsed) || parsed < 0) {
        return 'Please enter a number, 0 or more';
    }
    return undefined;
}

/**
 * The page of #/events/new: creates a new event (replaces the AngularJS CreateEventController +
 * edit-event.html). The draft is persisted in the session storage while filling the form.
 */
@customElement('alfio-event-create')
export class EventCreate extends LitElement {

    @state() private paymentProxies: PaymentMethodDTO[] = [];
    @state() private subscriptionDescriptors: { id: string; title: string; description: string }[] = [];
    @state() private geolocation: { latitude: string | null; longitude: string | null; mapUrl: string | null } | null = null;
    @state() private mapLoading = false;
    @state() private mapError: string | null = null;
    @state() private fileBlobId: string | null = null;
    @state() private logoError: string | null = null;
    @state() private submitting = false;
    @state() private submitError: string | null = null;
    @state() private categoriesError: string | null = null;
    @state() private shortNameTouched = false;
    @state() private shortNameChecking = false;
    @state() private timeZoneTouched = false;

    @state() private paymentLoading = false;
    @state() private paymentError: string | null = null;
    private draftReady = false;
    private formGeneration = 0;
    private previousValues: EventFormValues | null = null;
    private syncingCategories = false;
    private paymentGeneration = 0;

    private copiedFrom: string | null = null;
    private pendingCopy: PendingCopy | null = null;
    private customOfflineMethods: CustomOfflinePayment[] = [];
    private nextCategoryKey = 1;
    private shortNameGeneration = 0;
    private lastCheckedShortName: string | null = null;
    private lastCheckedShortNameValid = true;
    private copyDialogAutoOpened = false;

    readonly #form = new TanStackFormController(this, {
        defaultValues: emptyValues(),
        onSubmit: async ({value}) => {
            await this.save(value);
        },
    });

    private readonly loadDataTask = new Task(this, {
        task: async (): Promise<LoadedData> => {
            const [organizations, languages, currencies, timeZones, instanceSettings] = await Promise.all([
                OrganizationService.loadAll(),
                new LocalizationService().getEventsSupportedLanguages(),
                UtilService.availableCurrencies(),
                LocationService.timezones(),
                ConfigurationService.loadInstanceSettings(),
            ]);
            const data: LoadedData = {
                organizations,
                languages,
                currencies,
                timeZones,
                baseUrl: instanceSettings.baseUrl,
                descriptionMaxLength: instanceSettings.descriptionMaxLength,
            };
            this.initForm(data);
            return data;
        },
        args: () => [] as const,
    });

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
        // persist the draft while the user fills the form, as the AngularJS page did
        this.#form.api.store.subscribe(() => {
            this.syncCategoryDefaults();
            this.persistDraft();
        });
    }

    static readonly styles = [
        base,
        textColors,
        form,
        badges,
        row,
        modernLayout,
        choiceCardsStyles,
        creationLayout,
        css`
            :host {
                display: block;
                --alfio-section-body-padding: var(--sl-spacing-medium);
            }
            .page-actions {
                margin-inline-start: auto;
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-small);
            }
            .organizer-field { max-width: 32rem; }
            .category-conflict { flex-basis: 100%; }
            .event-url-prefix { overflow-wrap: anywhere; }
            @media (max-width: 900px) {
                .page-actions { margin-inline-start: 0; }
            }
            .price-model-free .choice-panel { display: none; }
            .container { padding-top: var(--sl-spacing-large); }
            .map-preview {
                display: grid;
                place-items: center;
                min-height: 12rem;
                border: 1px solid var(--sl-color-gray-200);
                border-radius: var(--sl-input-border-radius);
                background: var(--sl-color-neutral-5);
                padding: var(--sl-spacing-small);
            }
            .map-preview img {
                max-width: 100%;
                max-height: 20rem;
                object-fit: contain;
            }
            .map-loading {
                color: var(--sl-color-gray-400);
                font-size: var(--sl-font-size-3x-large);
            }
            sl-checkbox.payment-proxy, sl-checkbox.subscription {
                display: block;
                margin-top: var(--sl-spacing-small);
            }
            .form-error-text {
                color: var(--sl-color-danger-600);
                font-size: var(--sl-font-size-small);
                margin-top: var(--sl-spacing-x-small);
            }
            .category-card .section-body {
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-small) var(--sl-spacing-large);
                align-items: center;
            }
            .category-card .category-meta {
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
            }
            .seats-progress {
                margin-bottom: var(--sl-spacing-medium);
            }
            .seats-progress .sales-meta {
                display: flex;
                justify-content: space-between;
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-x-small);
                margin-bottom: var(--sl-spacing-2x-small);
            }
            sl-progress-bar {
                --height: var(--sl-spacing-2x-small);
                --indicator-color: var(--sl-color-primary-600);
                --track-color: var(--sl-color-gray-100);
            }
            .footer sl-divider {
                margin-top: 0;
            }
            .short-name-loading {
                margin-inline-start: var(--sl-spacing-x-small);
            }
        `,
    ];

    private get values(): EventFormValues {
        return this.#form.api.state.values;
    }

    render(): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>Create new event</h1>
                    <div class="page-actions"><sl-button variant="default" size="medium" @click=${this.openCopyDialog}>
                        <sl-icon slot="prefix" name="clipboard"></sl-icon>
                        Copy from previous event
                    </sl-button>
                    <sl-dropdown hoist>
                        <sl-button slot="trigger" caret>More</sl-button>
                        <sl-menu @sl-select=${() => void this.confirmReset()}>
                            <sl-menu-item id="reset-button">Reset</sl-menu-item>
                        </sl-menu>
                    </sl-dropdown></div>
                </div>
                <hr class="page-separator">
                <p class="page-description text-muted">Set up your event, configure tickets and prepare the event page.</p>
                ${taskContent(this.loadDataTask, this.loadDataTask.value ?? null,
                    'Failed to load the data needed to create the event. Please try again.',
                    (data) => this.renderForm(data))}
            </div>
            <alfio-event-category-dialog></alfio-event-category-dialog>
            <alfio-copy-event-dialog></alfio-copy-event-dialog>
        `;
    }

    private renderForm(data: LoadedData): TemplateResult {
        const submitted = this.#form.api.state.submissionAttempts > 0;
        const values = this.values;
        return html`
            <form novalidate @submit=${this.submit}>
                ${this.renderOrganizer(data)}
                ${when(values.organizationId !== '', () => html`
                    <div class="creation-layout">
                        <alfio-section-nav label="Event form sections" .sections=${FORM_SECTIONS}></alfio-section-nav>
                        <div class="creation-content">
                            <section id="event-details" class="form-section" tabindex="-1" aria-labelledby="event-details-title">
                                <h2 id="event-details-title">Event details</h2>
                                ${this.renderBasicInfo(data)}
                            </section>
                            <section id="tickets" class="form-section" tabindex="-1" aria-labelledby="tickets-title">
                                <h2 id="tickets-title">Tickets and payments</h2>
                                ${this.renderSeatsAndPayments(data, submitted)}
                                ${this.renderCategories()}
                            </section>
                            <section id="event-page" class="form-section" tabindex="-1" aria-labelledby="event-page-title">
                                <h2 id="event-page-title">Event page</h2>
                                ${this.renderDescription(data)}
                                ${this.renderUrls(data)}
                                ${this.renderLogo()}
                            </section>
                            ${when(this.submitError, () => html`
                                <sl-alert id="submit-error" variant="danger" open closable @sl-after-hide=${() => { this.submitError = null; }}>
                                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                                    ${this.submitError}
                                </sl-alert>
                            `)}
                            <div class="footer">
                                <sl-divider></sl-divider>
                                <div class="row" style="--alfio-row-cols: 3">
                                    <sl-button id="cancel-button" variant="default" size="large" href="#/">Cancel</sl-button>
                                    <div></div>
                                    <sl-button id="save-button" type="submit" variant="warning" size="large" ?loading=${this.submitting}>Save</sl-button>
                                </div>
                            </div>
                        </div>
                    </div>
                `, () => html`<sl-button variant="default" size="large" href="#/">Cancel</sl-button>`)}
            </form>
        `;
    }

    private renderOrganizer(data: LoadedData): TemplateResult {
        return sectionCard({icon: 'building', title: 'Event setup'}, html`
            <div class="organizer-field">
                ${this.#form.field({ name: 'organizationId', validators: validateWith(requiredValue) }, (field) => {
                    const error = this.error(field.state.meta);
                    return html`
                        <sl-select id="organizationId" label="Event organizer" placeholder="Select an organizer" required hoist ?disabled=${data.organizations.length === 1}
                                   class=${classMap({ error: error != null })}
                                   help-text=${error ?? 'The organizer determines available payment methods and subscriptions.'}
                                   .value=${field.state.value ?? ''}
                                   @sl-change=${(e: Event) => this.onOrganizationChange(field, String((e.target as SlSelect).value))}
                                   @sl-blur=${() => field.handleBlur()}>
                            ${repeat(data.organizations, (o) => String(o.id), (o) => html`
                                <sl-option value=${String(o.id)}>${o.name}</sl-option>
                            `)}
                        </sl-select>
                    `;
                })}

            </div>
        `);
    }

    private renderBasicInfo(data: LoadedData): TemplateResult {
        return sectionCard({ icon: 'info-circle', title: 'Name and schedule' }, html`
            <div class="form-stack">
                <div>
                    ${this.#form.field({ name: 'displayName', validators: validateWith(requiredValue) }, (field) => {
                        const error = this.error(field.state.meta);
                        return html`
                            <sl-input id="displayName" name="displayName" label="Event name" required
                                      class=${classMap({ error: error != null })}
                                      help-text=${error ?? ''}
                                      .value=${field.state.value ?? ''}
                                      @sl-input=${(e: Event) => {
                                          field.handleChange((e.target as SlInput).value);
                                          this.onDisplayNameChange();
                                      }}
                                      @sl-blur=${() => field.handleBlur()}></sl-input>
                        `;
                    })}
                </div>
                ${this.#form.field({name: 'format', validators: validateWith(requiredValue)}, field => choiceCards({
                    name: 'event-format',
                    className: 'event-format',
                    label: 'How will attendees join?',
                    value: field.state.value,
                    options: FORMAT_CHOICES,
                    panel: this.renderFormatPanel(),
                }, value => this.onFormatChange(field, value)))}
                ${this.#form.field({ name: 'timeZone', validators: validateWith(requiredValue) }, (field) => {
                    const error = this.error(field.state.meta);
                    return html`
                        <sl-select id="timeZone" label="Event time zone" placeholder="Select a time zone" required hoist
                                   class=${classMap({ error: error != null })}
                                   help-text=${error ?? ''}
                                   .value=${field.state.value ?? ''}
                                   @sl-change=${(e: Event) => {
                                       this.timeZoneTouched = true;
                                       field.handleChange(String((e.target as SlSelect).value));
                                   }}
                                   @sl-blur=${() => field.handleBlur()}>
                            ${repeat(data.timeZones, (tz) => tz, (tz) => html`<sl-option value=${tz}>${tz}</sl-option>`)}
                        </sl-select>
                    `;
                })}
                <div class="row" style="--alfio-row-cols: 2">
                    ${this.#form.field({ name: 'begin', validators: validateWith(requiredValue) }, (field) =>
                        this.textInput(field, { id: 'begin', label: 'Begin', type: 'datetime-local' }))}
                    ${this.#form.field({
                        name: 'end',
                        validators: {
                            ...validateWith((value) => this.validateEnd(value)),
                            onChangeListenTo: ['begin'],
                        },
                    }, (field) => this.textInput(field, { id: 'end', label: 'End', type: 'datetime-local' }))}
                </div>
            </div>
        `);
    }

    private renderFormatPanel(): TemplateResult {
        if (this.values.format === 'ONLINE') {
            return html`<p class="text-muted">No physical venue is needed for an online event.</p>`;
        }
        return this.renderLocation();
    }

    private renderLocation(): TemplateResult {
        const mapUrl = this.geolocation?.mapUrl ?? null;
        return html`
            <div class="row" style="--alfio-row-cols: 2">
                <div>
                    ${this.#form.field({
                        name: 'location',
                        validators: {
                            ...validateWith((value) => this.validateLocation(value)),
                            onChangeListenTo: ['format'],
                        },
                    }, (field) => {
                        const error = this.error(field.state.meta);
                        return html`
                            <sl-textarea id="location" name="location" label="Event location" rows="3" resize="auto" required
                                         placeholder="Please enter the address of the location. This will be used for displaying the map on the event page."
                                         class=${classMap({ error: error != null })}
                                         help-text=${error ?? ''}
                                         .value=${field.state.value ?? ''}
                                         @sl-input=${(e: Event) => field.handleChange((e.target as SlInput).value)}
                                         @sl-blur=${() => {
                                             field.handleBlur();
                                             void this.geolocateCurrentLocation();
                                         }}></sl-textarea>
                        `;
                    })}
                </div>
                <div class="map-preview">
                    ${when(this.mapLoading, () => html`<sl-spinner class="map-loading"></sl-spinner>`, () => nothing)}
                    ${when(!this.mapLoading && this.mapError != null, () => html`
                        <span class="text-danger">${this.mapError}</span>
                    `, () => nothing)}
                    ${when(!this.mapLoading && this.mapError == null && mapUrl != null, () => html`
                        <img class="event-logo" src=${mapUrl} alt="Map of the event location">
                    `, () => nothing)}
                    ${when(!this.mapLoading && this.mapError == null && mapUrl == null, () => html`
                        <sl-icon name="map" class="map-loading" aria-label="No map preview"></sl-icon>
                    `, () => nothing)}
                </div>
            </div>
        `;
    }

    private renderDescription(data: LoadedData): TemplateResult {
        return html`
            ${this.#form.field({
                name: 'description',
                validators: validateWith((value) => this.validateDescription(value, data.descriptionMaxLength)),
            }, (field) => html`
                <alfio-multi-language-text
                    label="Event description"
                    icon="text-paragraph"
                    max-length=${data.descriptionMaxLength}
                    .markdown=${true}
                    .multiline=${true}
                    .languages=${data.languages}
                    .value=${field.state.value ?? {}}
                    error=${this.error(field.state.meta) ?? ''}
                    @change=${(e: CustomEvent<MultiLanguageTextChange>) => field.handleChange(e.detail.value)}
                ></alfio-multi-language-text>
            `)}
        `;
    }

    private renderUrls(data: LoadedData): TemplateResult {
        return sectionCard({ icon: 'link-45deg', title: 'Public links' }, html`
            <p class="text-muted">Define how users can access your event and where the relevant links should point.</p>
            <div class="form-stack">
                <div class="row" style="--alfio-row-cols: 2">
                    ${this.#form.field({
                        name: 'shortName',
                        validators: {
                            onChangeAsyncDebounceMs: 400,
                            onChangeAsync: async ({value}) => await this.validateShortName(value),
                            onSubmitAsync: async ({value}) => await this.validateShortName(value),
                        },
                    }, (field) => {
                        const error = this.error(field.state.meta);
                        return html`
                            <sl-input id="shortName" name="shortName" label="Event URL" required
                                      class=${classMap({ error: error != null })}
                                      help-text=${error ?? ''}
                                      .value=${field.state.value ?? ''}
                                      @sl-input=${(e: Event) => this.onShortNameInput(field, (e.target as SlInput).value)}
                                      @sl-blur=${() => field.handleBlur()}>
                                <div slot="help-text" class="event-url-prefix">${error ?? `${data.baseUrl}/event/${field.state.value}/`}</div>
                                ${when(this.shortNameChecking, () => html`<sl-spinner slot="suffix" class="short-name-loading" size="small"></sl-spinner>`, () => nothing)}
                            </sl-input>
                        `;
                    })}
                    ${this.#form.field({ name: 'websiteUrl', validators: validateWith(requiredUrl) }, (field) =>
                        this.textInput(field, { id: 'websiteUrl', label: 'Website link', type: 'url', prefixIcon: 'link-45deg' }))}
                </div>
                <div class="row" style="--alfio-row-cols: 2">
                    ${this.#form.field({ name: 'termsAndConditionsUrl', validators: validateWith(requiredUrl) }, (field) =>
                        this.textInput(field, { id: 'termsAndConditionsUrl', label: 'Terms and Conditions URL', type: 'url', prefixIcon: 'link-45deg' }))}
                    ${this.#form.field({ name: 'privacyPolicyUrl', validators: validateWith(optionalUrl) }, (field) =>
                        this.textInput(field, {
                            id: 'privacyPolicyUrl',
                            label: 'Privacy policy URL (recommended)',
                            type: 'url',
                            prefixIcon: 'link-45deg',
                            optional: true,
                            helpText: 'Strongly recommended so attendees can understand how their data is used.',
                        }))}
                </div>
            </div>
        `);
    }

    private renderLogo(): TemplateResult {
        return sectionCard({ icon: 'image', title: 'Event logo' }, html`
            <p class="text-muted">Upload the event logo in PNG, JPG, GIF or SVG format. Maximum size: 1 MB.</p>
            <alfio-purchase-context-image
                id="event-logo" tabindex="-1" aria-label="Event logo (required)"
                aria-invalid=${this.logoError != null} aria-describedby="logo-error"
                current-image=${this.currentImageAttr()}
                accept="image/*"
                max-size="1048576"
                label="Drop image here or click to upload"
                @image-change=${this.onImageChange}></alfio-purchase-context-image>
            ${when(this.logoError != null, () => html`
                <sl-alert id="logo-error" variant="danger" open class="logo-error">
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${this.logoError}
                </sl-alert>
            `, () => nothing)}
        `);
    }

    private renderSeatsAndPayments(data: LoadedData, submitted: boolean): TemplateResult {
        const values = this.values;
        const paid = values.priceModel === 'paid';
        const panel = html`${keyed(this.formGeneration, html`${cache(when(paid, () => this.renderPricePanel(data, submitted)))}`)}`;
        let priceClass = 'price-model';
        if (!paid) {
            priceClass += ' price-model-free';
        }
        return sectionCard({ icon: 'ticket-perforated', title: 'Capacity and admission' }, html`
            <div class="form-stack">
                ${this.#form.field({ name: 'availableSeats', validators: validateWith(requiredPositiveInteger) }, (field) =>
                    this.textInput(field, { id: 'availableSeats', label: 'Max tickets', type: 'number', helpText: 'The total number of seats of the event.' }))}
                ${this.#form.field({ name: 'priceModel', validators: validateWith(requiredValue) }, (field) => choiceCards({
                    name: 'price-model',
                    className: priceClass,
                    label: 'Ticket price model',
                    value: field.state.value,
                    options: PRICE_MODEL_CHOICES,
                    panel,
                }, (value) => field.handleChange(value as PriceModel)))}
            </div>
        `);
    }

    /**
     * The fields of the "Entry fee requested" choice: price, currency, taxes,
     * payment methods and subscriptions
     */
    private renderPricePanel(data: LoadedData, submitted: boolean): TemplateResult {
        const values = this.values;
        let vatHelp = `Including ${values.vatPercentage || '0'}% VAT`;
        if (values.vatIncluded) {
            vatHelp = 'VAT included';
        }
        return html`
            <div class="form-stack">
                <div class="row" style="--alfio-row-cols: 2">
                    ${this.#form.field({
                        name: 'currency',
                        validators: {
                            ...validateWith((value) => this.ifPaid(requiredValue, value)),
                            onChangeListenTo: ['priceModel'],
                        },
                    }, (f) => validatedSelect(f, submitted, { id: 'currency', label: 'Currency', placeholder: 'Select a currency' }, html`
                        ${repeat(data.currencies, (c) => c.code, (c) => html`
                            <sl-option value=${c.code}>${c.code} – ${c.name}</sl-option>
                        `)}
                    `))}
                    ${this.#form.field({
                        name: 'regularPrice',
                        validators: {
                            ...validateWith((value) => this.ifPaid(requiredPositiveNumber, value)),
                            onChangeListenTo: ['priceModel'],
                        },
                    }, (f) => this.textInput(f, { id: 'regularPrice', label: 'Regular price', type: 'number', suffix: values.currency || undefined }))}
                    ${this.#form.field({
                        name: 'vatPercentage',
                        validators: {
                            ...validateWith((value) => this.ifPaid(requiredNonNegativeNumber, value)),
                            onChangeListenTo: ['priceModel'],
                        },
                    }, (f) => this.textInput(f, { id: 'vatPercentage', label: 'Taxes', type: 'number', suffix: '%' }))}
                    <sl-input class="final-price" label="Price after taxes" readonly
                              help-text=${vatHelp}
                              .value=${this.finalPriceText()}></sl-input>
                </div>
                ${this.#form.field({ name: 'vatIncluded' }, (f) => html`
                    <sl-switch id="vatIncluded" ?checked=${f.state.value}
                               help-text="Whether the regular price already includes the taxes."
                               @sl-change=${(e: Event) => f.handleChange((e.target as SlSwitch).checked)}>
                        Price includes taxes
                    </sl-switch>
                `)}
                ${this.renderPaymentMethods(values)}
                ${this.renderSubscriptions()}
            </div>
        `;
    }

    private renderPaymentMethods(values: EventFormValues): TemplateResult {
        const currency = values.currency;
        const isCompatible = (p: PaymentMethodDTO): boolean => p.status === 'ACTIVE'
            && (p.onlyForCurrency == null || p.onlyForCurrency.length === 0 || p.onlyForCurrency.includes(currency));
        const compatible = this.paymentProxies.filter(isCompatible);
        const unavailable = this.paymentProxies.filter((p) => !isCompatible(p));
        return html`
            ${this.#form.field({
                name: 'paymentProxies',
                validators: {
                    ...validateWith((value) => this.ifPaid(() => {
                        if (this.paymentLoading || this.paymentError != null) {
                            return 'Wait for the organizer payment methods to load, or retry.';
                        }
                        if (Array.isArray(value) && value.length > 0) {
                            if (value.some(proxy => !this.paymentProxies.some(method => method.paymentProxy === proxy && isCompatible(method)))) {
                                return 'Remove unavailable payment methods or change the organizer or currency.';
                            }
                            if (value.includes('CUSTOM_OFFLINE') && this.customOfflineMethods.length === 0) {
                                return 'Select at least one custom offline payment method.';
                            }
                            return undefined;
                        }
                        return 'Please select at least one payment method';
                    }, value)),
                    onChangeListenTo: ['priceModel', 'currency', 'organizationId'],
                },
            }, (field) => {
                const error = this.error(field.state.meta);
                const selected = (field.state.value as string[]) ?? [];
                return html`
                    <div>
                        <strong>Accepted payment methods</strong>
                        ${when(this.paymentLoading, () => html`<p role="status"><sl-spinner></sl-spinner> Loading payment methods…</p>`)}
                        ${when(this.paymentError != null, () => html`
                            <sl-alert variant="danger" open><sl-icon slot="icon" name="exclamation-triangle"></sl-icon>${this.paymentError}
                                <sl-button size="small" @click=${() => void this.loadPaymentProxies(Number(values.organizationId))}>Retry</sl-button>
                            </sl-alert>
                        `)}
                        ${repeat(selected.filter(proxy => !this.paymentLoading && this.paymentError == null && !compatible.some(method => method.paymentProxy === proxy)), proxy => proxy, proxy => html`
                            <sl-checkbox class="payment-proxy" checked
                                         @sl-change=${() => this.togglePaymentProxy(field, proxy, false)}>
                                ${PAYMENT_PROXY_DESCRIPTIONS[proxy] ?? proxy} — unavailable for this organizer or currency; uncheck to remove
                            </sl-checkbox>
                        `)}
                        ${repeat(compatible, (p) => p.paymentProxy, (p) => html`
                            <sl-checkbox class="payment-proxy" ?checked=${selected.includes(p.paymentProxy)}
                                         @sl-change=${(e: Event) => this.togglePaymentProxy(field, p.paymentProxy, (e.target as SlCheckbox).checked)}>
                                ${PAYMENT_PROXY_DESCRIPTIONS[p.paymentProxy] ?? `${p.paymentProxy} (please check the configuration)`}
                            </sl-checkbox>
                        `)}
                        ${when(unavailable.length > 0, () => html`
                            <p class="text-muted">
                                <sl-icon name="info-circle"></sl-icon>
                                The following methods are not available: ${unavailable.map((p) => p.paymentProxy).join(', ')}.
                                Please check that the provider supports the selected currency, or the organization configuration.
                            </p>
                        `)}
                        ${when(error != null, () => html`<div class="form-error-text">${error}</div>`, () => nothing)}
                    </div>
                    ${cache(when(selected.includes('CUSTOM_OFFLINE') && compatible.some(method => method.paymentProxy === 'CUSTOM_OFFLINE'), () => html`
                        <div>
                            <strong>Custom payment methods</strong>
                            ${keyed(values.organizationId, html`<custom-offline-payment-event-selector
                                id="custom-offline-payment-event-selector"
                                organization=${Number(values.organizationId) || -1}
                                @selection-changed=${(e: CustomEvent<CustomOfflinePayment[]>) => { this.customOfflineMethods = e.detail; }}>
                            </custom-offline-payment-event-selector>`)}
                        </div>
                    `))}
                `;
            })}
        `;
    }

    private renderSubscriptions(): TemplateResult | typeof nothing {
        if (this.subscriptionDescriptors.length === 0 && this.values.linkedSubscriptions.length === 0) {
            return nothing;
        }
        return html`
            ${this.#form.field({ name: 'linkedSubscriptions', validators: validateWith(value => {
                if (this.values.priceModel === 'free') {
                    return undefined;
                }
                if (Array.isArray(value) && value.some(id => !this.subscriptionDescriptors.some(subscription => subscription.id === id))) {
                    return 'Remove subscriptions that are unavailable for this organizer.';
                }
                return undefined;
            }) }, (field) => {
                const selected = (field.state.value as string[]) ?? [];
                return html`
                    <div>
                        <strong>Subscriptions</strong>
                        ${repeat(selected.filter(id => !this.paymentLoading && this.paymentError == null && !this.subscriptionDescriptors.some(subscription => subscription.id === id)), id => id, id => html`
                            <sl-checkbox class="subscription" checked @sl-change=${() => this.toggleSubscription(field, id, false)}>
                                Unavailable subscription — uncheck to remove
                            </sl-checkbox>
                        `)}
                        ${when(this.error(field.state.meta), () => html`<p class="form-error-text">${this.error(field.state.meta)}</p>`)}
                        <p class="text-muted">Select the subscriptions that can be used for this event.</p>
                        ${repeat(this.subscriptionDescriptors, (s) => s.id, (s) => html`
                            <sl-checkbox class="subscription" ?checked=${selected.includes(s.id)}
                                         @sl-change=${(e: Event) => this.toggleSubscription(field, s.id, (e.target as SlCheckbox).checked)}>
                                <strong>${s.title}</strong> — <span class="text-muted">${s.description}</span>
                            </sl-checkbox>
                        `)}
                    </div>
                `;
            })}
        `;
    }

    private renderCategories(): TemplateResult {
        const values = this.values;
        const categories = values.categories;
        const seats = Number.parseInt(values.availableSeats, 10);
        const allocated = categories.filter((c) => c.bounded).reduce((sum, c) => sum + (Number.parseInt(c.maxTickets, 10) || 0), 0);
        let percentage = 0;
        if (!Number.isNaN(seats) && seats > 0) {
            percentage = Math.min(100, Math.round(allocated * 100 / seats));
        }
        return html`
            ${sectionCard({ icon: 'grid-3x3-gap', title: 'Categories' }, html`
                <p class="text-muted">Define all the ticket categories for the event. At least one category is required in order to create the event.</p>
                ${when(categories.length > 0 && !Number.isNaN(seats) && seats > 0, () => html`
                    <div class="seats-progress">
                        <div class="sales-meta">
                            <span>Allocated seats: ${allocated} of ${seats}</span>
                            <span>${percentage}%</span>
                        </div>
                        <sl-progress-bar value=${percentage} aria-label="${percentage}% of the seats allocated"></sl-progress-bar>
                    </div>
                `)}
                ${this.#form.field({
                    name: 'categories',
                    validators: {
                        ...validateWith((value) => this.validateCategories(value)),
                        onChangeListenTo: ['availableSeats', 'begin', 'end', 'format', 'priceModel', 'currency', 'description'],
                    },
                }, (field) => {
                    const error = this.categoriesError ?? this.error(field.state.meta);
                    return when(error != null, () => html`
                        <sl-alert variant="danger" open class="categories-error">
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            ${error}
                        </sl-alert>
                    `, () => nothing);
                })}
            `)}
            ${repeat(categories, (c) => c.key, (category, index) => this.renderCategoryCard(category, index, categories.length))}
            <sl-button id="add-category" class="section-card-add" variant="default" size="large" @click=${this.addCategory}>
                <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                Add new category
            </sl-button>
        `;
    }

    private renderCategoryCard(category: CategoryDraft, index: number, count: number): TemplateResult {
        const values = this.values;
        let meta = 'dynamic allocation';
        if (category.bounded) {
            meta = `${category.maxTickets} tickets`;
        }
        if (category.tokenGenerationRequested) {
            meta += ', hidden';
        }
        const actions = html`
            <sl-button variant="default" size="small" class="edit-category" @click=${() => void this.editCategory(category)}>
                <sl-icon slot="prefix" name="pencil"></sl-icon>
                Edit
            </sl-button>
            ${when(count > 1, () => html`
                <sl-icon-button name="arrow-up" label="Move up" ?disabled=${index === 0}
                                @click=${() => this.moveCategory(index, -1)}></sl-icon-button>
                <sl-icon-button name="arrow-down" label="Move down" ?disabled=${index === count - 1}
                                @click=${() => this.moveCategory(index, 1)}></sl-icon-button>
            `)}
            <sl-button variant="danger" size="small" class="remove-category" @click=${() => this.removeCategory(index)}>
                <sl-icon slot="prefix" name="trash"></sl-icon>
                Remove
            </sl-button>
        `;
        return sectionCard({
            icon: 'tag',
            title: this.categoryTitle(category, index),
            actions,
            className: 'category-card',
        }, html`
            ${when(this.categoryConflict(category) != null, () => html`
                <sl-alert variant="warning" open class="category-conflict">
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>${this.categoryConflict(category)}
                </sl-alert>
            `)}
            <span class="category-meta">
                <sl-icon name="ticket"></sl-icon>
                ${meta}
                ${when(values.priceModel === 'paid', () => html`
                    <span class="category-meta"><sl-icon name="cash"></sl-icon>${formatAmount(Number(category.price) || 0, values.currency)}</span>
                `)}
            </span>
            <span class="category-meta">
                <sl-icon name="calendar-event"></sl-icon>
                <alfio-format-date .date=${this.modificationOf(category.inception)}></alfio-format-date>
                &ndash;
                <alfio-format-date .date=${this.modificationOf(category.expiration)}></alfio-format-date>
            </span>
        `);
    }

    private modificationOf(dateTimeLocal: string): DateTimeModification {
        return dateTimeLocalToModification(dateTimeLocal) ?? { date: '', time: '' };
    }

    private currentImageAttr(): string {
        if (this.fileBlobId != null) {
            return `/file/${this.fileBlobId}`;
        }
        return '';
    }

    private categoryTitle(category: CategoryDraft, index: number): string {
        if (category.name.trim() !== '') {
            return category.name;
        }
        return `Category #${index + 1}`;
    }

    // ---------- field helpers and validators ----------

    private textInput(field: BoundField<string>, options: ValidatedInputOptions): TemplateResult {
        return validatedInput(field, this.#form.api.state.submissionAttempts > 0, options);
    }

    private error(meta: ValidatedFieldMeta): string | undefined {
        return fieldError(meta, this.#form.api.state.submissionAttempts > 0);
    }

    private ifPaid(validate: (value: unknown) => string | undefined, value: unknown): string | undefined {
        if (this.values.priceModel === 'free') {
            return undefined;
        }
        return validate(value);
    }

    private validateLocation(value: unknown): string | undefined {
        if (this.values.format === 'ONLINE') {
            return undefined;
        }
        return minLength(value, 10);
    }

    private validateEnd(value: unknown): string | undefined {
        const missing = requiredValue(value);
        if (missing != null) {
            return missing;
        }
        if (this.values.begin !== '' && String(value) <= this.values.begin) {
            return 'The end must be after the begin';
        }
        return undefined;
    }

    private validateCategories(value: unknown): string | undefined {
        const categories = (value ?? []) as CategoryDraft[];
        if (categories.length === 0) {
            return 'Please define at least one category';
        }
        for (const category of categories) {
            const conflict = this.categoryConflict(category);
            if (conflict != null) {
                return `${category.name}: ${conflict}`;
            }
        }
        const seats = Number.parseInt(this.values.availableSeats, 10);
        if (!Number.isNaN(seats) && seats > 0) {
            const allocated = categories.filter((c) => c.bounded)
                .reduce((sum, c) => sum + (Number.parseInt(c.maxTickets, 10) || 0), 0);
            if (allocated > seats) {
                return `The categories cannot allocate more than the ${seats} seats of the event`;
            }
        }
        return undefined;
    }

    private validateDescription(value: unknown, maxLength: number): string | undefined {
        const map = (value ?? {}) as LocalizedContent;
        const locales = Object.keys(map);
        if (locales.length === 0) {
            return requiredValue('');
        }
        for (const locale of locales) {
            const text = map[locale] ?? '';
            const missing = minLength(text, 10);
            if (missing != null) {
                return missing;
            }
            if (text.length > maxLength) {
                return `Please enter at most ${maxLength} characters`;
            }
        }
        return undefined;
    }

    private async validateShortName(value: unknown): Promise<string | undefined> {
        const missing = requiredValue(value);
        if (missing != null) {
            return missing;
        }
        const shortName = String(value).trim();
        if (!SHORT_NAME_PATTERN.test(shortName)) {
            return 'Only letters, numbers and - _ (no spaces; the last character must be a letter or a number)';
        }
        if (shortName === this.lastCheckedShortName) {
            if (this.lastCheckedShortNameValid) {
                return undefined;
            }
            return 'This URL is already taken, please choose another one';
        }
        this.shortNameChecking = true;
        try {
            const unique = await UtilService.validateShortName(shortName);
            if (this.values.shortName.trim() === shortName) {
                this.lastCheckedShortName = shortName;
                this.lastCheckedShortNameValid = unique;
            }
            if (!unique) {
                return 'This URL is already taken, please choose another one';
            }
            return undefined;
        } catch {
            return 'Could not verify the event URL. Please try again.';
        } finally {
            if (this.values.shortName.trim() === shortName) {
                this.shortNameChecking = false;
            }
        }
    }

    // ---------- field events ----------

    private onDisplayNameChange(): void {
        // generate the event URL from the name, until the user edits it by hand
        const displayName = this.values.displayName.trim();
        const shortName = this.values.shortName.trim();
        if (displayName === '' || this.shortNameTouched || shortName !== '') {
            return;
        }
        const generation = ++this.shortNameGeneration;
        setTimeout(() => {
            if (generation !== this.shortNameGeneration) {
                return;
            }
            void UtilService.generateShortName(displayName).then((generated) => {
                if (generation === this.shortNameGeneration && !this.shortNameTouched && this.values.shortName.trim() === '') {
                    this.#form.api.setFieldValue('shortName', generated);
                }
            }).catch(() => undefined);
        }, 500);
    }

    private onShortNameInput(field: BoundField<string>, value: string): void {
        this.shortNameTouched = true;
        this.shortNameChecking = false;
        this.lastCheckedShortName = null;
        field.handleChange(value);
    }

    private onFormatChange(field: BoundField<EventFormat>, value: string): void {
        field.handleChange(value as EventFormat);
        if (value === 'ONLINE') {
            this.mapError = null;
        }
    }

    private async onOrganizationChange(field: BoundField<string>, value: string): Promise<void> {
        if (value === field.state.value) {
            return;
        }
        if (this.customOfflineMethods.length > 0) {
            const confirmed = await ConfirmationDialogService.requestConfirm('Change organizer?',
                'The selected custom offline payment methods belong to the current organizer and will be cleared. Other event information will be kept.', 'warning');
            if (!confirmed) {
                const select = this.renderRoot.querySelector<SlSelect>('#organizationId');
                if (select != null) {
                    select.value = field.state.value;
                }
                return;
            }
            this.customOfflineMethods = [];
        }
        field.handleChange(value);
        void this.loadPaymentProxies(Number(value));
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

    private toggleSubscription(field: BoundField<string[]>, id: string, checked: boolean): void {
        const selected = (field.state.value as string[]) ?? [];
        let next: string[];
        if (checked) {
            next = [...selected, id];
        } else {
            next = selected.filter((s) => s !== id);
        }
        field.handleChange(next);
    }

    private onImageChange = (event: CustomEvent<ImageChangeDetail>): void => {
        this.logoError = null;
        this.fileBlobId = event.detail.fileBlobId;
    };

    // ---------- geolocation ----------

    private async geolocateCurrentLocation(): Promise<void> {
        const values = this.values;
        if (values.format === 'ONLINE') {
            return;
        }
        const location = values.location.trim();
        if (location === '') {
            this.geolocation = null;
            this.mapError = null;
            return;
        }
        this.mapLoading = true;
        this.mapError = null;
        try {
            const result = await LocationService.geolocate(location);
            if (this.values.location.trim() !== location || this.values.format === 'ONLINE') {
                return;
            }
            this.geolocation = { latitude: result.latitude, longitude: result.longitude, mapUrl: result.mapUrl };
            if (result.timeZone != null && !this.timeZoneTouched) {
                this.#form.api.setFieldValue('timeZone', result.timeZone);
            }
        } catch (error) {
            this.mapError = errorMessage(error, 'The location could not be geocoded');
            this.geolocation = null;
        } finally {
            this.mapLoading = false;
        }
    }

    // ---------- categories ----------

    private syncCategoryDefaults(): void {
        const values = this.values;
        const previous = this.previousValues;
        this.previousValues = values;
        if (this.syncingCategories || previous == null || previous.categories !== values.categories) {
            return;
        }
        if (previous.begin === values.begin && previous.regularPrice === values.regularPrice
            && previous.currency === values.currency && previous.format === values.format
            && previous.description === values.description) {
            return;
        }
        const categories = values.categories.map(category => {
            const next = {...category};
            next.currency ??= previous.currency;
            if (category.inheritedFields?.includes('expiration')) {
                next.expiration = values.begin;
            }
            if (category.inheritedFields?.includes('price')) {
                next.price = values.regularPrice;
                next.currency = values.currency;
            }
            if (category.inheritedFields?.includes('ticketAccessType')) {
                next.ticketAccessType = '';
                if (values.format === 'HYBRID') {
                    next.ticketAccessType = 'IN_PERSON';
                }
            }
            // Empty translations can follow the event; never discard written translations.
            if (Object.keys(category.description).length > 0) {
                next.description = {...category.description};
                for (const locale of Object.keys(values.description)) {
                    next.description[locale] ??= '';
                }
                for (const [locale, text] of Object.entries(next.description)) {
                    if (!(locale in values.description) && text.trim() === '') {
                        delete next.description[locale];
                    }
                }
            }
            return next;
        });
        this.syncingCategories = true;
        try {
            this.#form.api.setFieldValue('categories', categories);
        } finally {
            this.syncingCategories = false;
        }
    }

    private categoryConflict(category: CategoryDraft): string | undefined {
        const values = this.values;
        if (category.expiration <= category.inception || (values.end !== '' && category.expiration > values.end)) {
            return 'Review the sales dates after changing the event schedule.';
        }
        if (values.format === 'HYBRID' && category.ticketAccessType === '') {
            return 'Choose whether these tickets provide venue or online access.';
        }
        if (values.format !== 'HYBRID' && category.ticketAccessType !== '' && category.ticketAccessType !== values.format) {
            return 'Review ticket access after changing the event format.';
        }
        if (values.priceModel === 'paid') {
            if (category.currency != null && category.currency !== values.currency) {
                return 'Review this custom price in the new currency.';
            }
            if (category.price.trim() === '' || !Number.isFinite(Number(category.price)) || Number(category.price) < 0) {
                return 'Set a valid ticket price for this paid event.';
            }
        }
        const extraTranslation = Object.entries(category.description).some(([locale, text]) =>
            !(locale in values.description) && text.trim() !== '');
        if (extraTranslation) {
            return 'This category has a translation outside the event languages. Add the event language or edit the category.';
        }
        if (category.bounded && Number(category.maxTickets) > Number(values.availableSeats)) {
            return 'Review this allocation after changing the event capacity.';
        }
        return undefined;
    }

    private categoryDialogContext(): CategoryDialogContext {
        const values = this.values;
        return {
            format: values.format,
            freeOfCharge: values.priceModel === 'free',
            currency: values.currency,
            eventBegin: values.begin,
            eventEnd: values.end,
            availableSeats: values.availableSeats,
            languages: this.loadDataTask.value?.languages ?? [],
            selectedLocales: Object.keys(values.description),
            descriptionMaxLength: this.loadDataTask.value?.descriptionMaxLength ?? 4096,
            baseUrl: this.loadDataTask.value?.baseUrl ?? '',
            shortName: values.shortName,
            categories: values.categories,
        };
    }

    private addCategory = async (): Promise<void> => {
        const values = this.values;
        const dialog = this.renderRoot.querySelector<EventCategoryDialog>('alfio-event-category-dialog');
        if (dialog == null) {
            return;
        }
        const draft = newCategoryDraft(values.begin, values.regularPrice, values.format, values.categories.length + 1, this.nextCategoryKey++);
        draft.currency = values.currency;
        const result = await dialog.open(this.categoryDialogContext(), draft);
        if (result == null) {
            return;
        }
        this.#form.api.pushFieldValue('categories', result);
        this.categoriesError = null;
    };

    private editCategory = async (category: CategoryDraft): Promise<void> => {
        const dialog = this.renderRoot.querySelector<EventCategoryDialog>('alfio-event-category-dialog');
        if (dialog == null) {
            return;
        }
        const result = await dialog.open(this.categoryDialogContext(), category);
        if (result == null) {
            return;
        }
        const index = this.values.categories.findIndex((c) => c.key === category.key);
        if (index < 0) {
            return;
        }
        const categories = this.values.categories.map((c, i) => {
            if (i === index) {
                return result;
            }
            return c;
        });
        this.#form.api.setFieldValue('categories', categories);
    };

    private removeCategory(index: number): void {
        void this.#form.api.removeFieldValue('categories', index);
        // re-assign the ordinals
        const categories = this.values.categories.map((c, i) => ({ ...c, ordinal: i + 1 }));
        this.#form.api.setFieldValue('categories', categories);
    }

    private moveCategory(index: number, delta: number): void {
        const target = index + delta;
        const categories = this.values.categories.slice();
        const [moved] = categories.splice(index, 1);
        categories.splice(target, 0, moved);
        this.#form.api.setFieldValue('categories', categories.map((c, i) => ({ ...c, ordinal: i + 1 })));
    }

    // ---------- copy from previous event ----------

    private openCopyDialog = async (): Promise<void> => {
        const dialog = this.renderRoot.querySelector<CopyEventDialog>('alfio-copy-event-dialog');
        if (dialog == null) {
            return;
        }
        const preselect = readRouteParams().get('eventName') ?? undefined;
        const selection = await dialog.open({
            preselectShortName: preselect,
            currentBegin: this.values.begin,
            currentEnd: this.values.end,
        });
        if (selection == null) {
            return;
        }
        await this.applyCopy(selection);
    };

    private async applyCopy(selection: CopyEventSelection): Promise<void> {
        try {
            const { event } = await EventService.load(selection.source.shortName);
            const values = this.values;
            // shift between the source event and the new one, in wall-clock milliseconds
            const sourceBegin = toDateTimeLocal(event.begin, event.timeZone);
            const delta = Date.parse(`${selection.begin}:00Z`) - Date.parse(`${sourceBegin}:00Z`);
            const adjust = (wallClock: string | null | undefined): string => {
                const local = wallClockToDateTimeLocal(wallClock);
                if (local === '') {
                    return '';
                }
                return shiftDateTimeLocal(local, delta);
            };
            const isAllOrdinalZero = event.ticketCategories.every((tc) => tc.ordinal === 0 || tc.ordinal == null);
            const categories: CategoryDraft[] = event.ticketCategories.map((tc, idx) => {
                let ordinal = tc.ordinal ?? idx;
                if (isAllOrdinalZero) {
                    ordinal = idx;
                }
                let ticketAccessType = '';
                if (event.format === 'HYBRID') {
                    ticketAccessType = tc.ticketAccessType ?? 'IN_PERSON';
                }
                let description: LocalizedContent = {};
                if (tc.description != null) {
                    description = { ...tc.description };
                }
                let maxTickets = '';
                if (tc.maxTickets != null) {
                    maxTickets = String(tc.maxTickets);
                }
                let price = values.regularPrice;
                if (tc.price != null) {
                    price = String(tc.price);
                }
                return {
                    key: this.nextCategoryKey++,
                    name: tc.name,
                    ticketAccessType,
                    description,
                    tokenGenerationRequested: tc.accessRestricted ?? false,
                    bounded: tc.bounded ?? false,
                    maxTickets,
                    inception: adjust(tc.formattedInception),
                    expiration: adjust(tc.formattedExpiration),
                    price,
                    code: tc.code ?? '',
                    validCheckInFrom: adjust(tc.formattedValidCheckInFrom),
                    validCheckInTo: adjust(tc.formattedValidCheckInTo),
                    ticketValidityStart: adjust(tc.formattedTicketValidityStart),
                    ticketValidityEnd: adjust(tc.formattedTicketValidityEnd),
                    ordinal,
                    metadataCopiedFrom: tc.id,
                    currency: event.currency ?? values.currency,
                };
            });
            this.copiedFrom = event.shortName;
            this.pendingCopy = {
                additionalFields: selection.additionalFields,
                additionalServices: selection.additionalServices.map((as) => ({
                    ...as,
                    inception: this.adjustModification(as.inception, delta) ?? as.inception,
                    expiration: this.adjustModification(as.expiration, delta) ?? as.expiration,
                })),
            };
            this.fileBlobId = selection.source.fileBlobId;
            this.geolocation = {
                latitude: event.latitude ?? null,
                longitude: event.longitude ?? null,
                mapUrl: (event as AlfioEvent & { mapUrl?: string }).mapUrl ?? null,
            };
            this.#form.api.setFieldValue('begin', selection.begin);
            this.#form.api.setFieldValue('end', selection.end);
            this.#form.api.setFieldValue('timeZone', event.timeZone || values.timeZone);
            this.#form.api.setFieldValue('location', event.location ?? '');
            let description: LocalizedContent = {};
            if (event.description != null) {
                description = { ...event.description };
            }
            let priceModel: PriceModel = 'paid';
            if (event.freeOfCharge) {
                priceModel = 'free';
            }
            let availableSeats = values.availableSeats;
            if (selection.source.availableSeats != null) {
                availableSeats = String(selection.source.availableSeats);
            }
            let regularPrice = values.regularPrice;
            if (event.regularPrice != null) {
                regularPrice = String(event.regularPrice);
            }
            let vatPercentage = values.vatPercentage;
            if (event.vatPercentage != null) {
                vatPercentage = String(event.vatPercentage);
            }
            this.#form.api.setFieldValue('description', description);
            this.#form.api.setFieldValue('websiteUrl', event.websiteUrl ?? '');
            this.#form.api.setFieldValue('termsAndConditionsUrl', event.termsAndConditionsUrl ?? '');
            this.#form.api.setFieldValue('privacyPolicyUrl', event.privacyPolicyUrl ?? '');
            this.#form.api.setFieldValue('priceModel', priceModel);
            this.#form.api.setFieldValue('availableSeats', availableSeats);
            this.#form.api.setFieldValue('regularPrice', regularPrice);
            this.#form.api.setFieldValue('currency', event.currency ?? values.currency);
            this.#form.api.setFieldValue('vatPercentage', vatPercentage);
            this.#form.api.setFieldValue('vatIncluded', event.vatIncluded ?? false);
            this.#form.api.setFieldValue('paymentProxies', (event.allowedPaymentProxies ?? []).map(String));
            this.#form.api.setFieldValue('format', (event.format as EventFormat) ?? 'IN_PERSON');
            this.#form.api.setFieldValue('categories', categories);
            dispatchFeedback({ type: 'success', message: `Copied the settings from ${event.displayName}` }, this);
        } catch (error) {
            dispatchFeedback({ type: 'danger', message: errorMessage(error, 'Failed to copy the event') }, this);
        }
    }

    private adjustModification(value: DateTimeModification | null | undefined, delta: number): DateTimeModification | null {
        if (value == null || value.date == null || value.date === '') {
            return null;
        }
        const local = `${value.date}T${(value.time ?? '00:00').substring(0, 5)}`;
        const shifted = shiftDateTimeLocal(local, delta);
        return {
            date: shifted.substring(0, 10),
            time: shifted.substring(11, 16),
        };
    }

    // ---------- form lifecycle ----------

    private initForm(data: LoadedData): void {
        const restored = this.restoreDraft();
        const values: EventFormValues = restored ?? emptyValues();
        if (!data.organizations.some(organization => String(organization.id) === values.organizationId)) {
            values.organizationId = '';
        }
        if (data.organizations.length === 1 && values.organizationId === '') {
            values.organizationId = String(data.organizations[0].id);
        }
        this.#form.api.update({ ...this.#form.api.options, defaultValues: values });
        this.formGeneration++;
        this.#form.api.reset(values);
        this.draftReady = true;
        this.submitError = null;
        if (values.organizationId !== '') {
            void this.loadPaymentProxies(Number(values.organizationId));
        }
        // #/events/new?eventName=... opens the copy dialog preselected, as the AngularJS page did
        if (!this.copyDialogAutoOpened) {
            const eventName = readRouteParams().get('eventName');
            if (eventName != null && eventName !== '') {
                this.copyDialogAutoOpened = true;
                void this.openCopyDialog();
            }
        }
    }

    private async confirmReset(): Promise<void> {
        const empty = emptyValues();
        const values = {...this.values, organizationId: ''};
        const hasData = JSON.stringify(values) !== JSON.stringify(empty) || this.fileBlobId != null;
        if (hasData && !await ConfirmationDialogService.requestConfirm('Reset event?', 'This will discard the event information you have entered.', 'warning')) {
            return;
        }
        this.reset();
    }

    private reset = (): void => {
        this.copiedFrom = null;
        this.pendingCopy = null;
        this.fileBlobId = null;
        this.geolocation = null;
        this.logoError = null;
        this.shortNameTouched = false;
        this.timeZoneTouched = false;
        const values = emptyValues();
        const organizations = this.loadDataTask.value?.organizations ?? [];
        if (organizations.length === 1) {
            values.organizationId = String(organizations[0].id);
        }
        this.paymentGeneration++;
        this.paymentProxies = [];
        this.subscriptionDescriptors = [];
        this.customOfflineMethods = [];
        this.paymentError = null;
        this.paymentLoading = false;
        this.submitError = null;
        this.categoriesError = null;
        this.mapError = null;
        this.formGeneration++;
        this.#form.api.reset(values);
        if (values.organizationId !== '') {
            void this.loadPaymentProxies(Number(values.organizationId));
        }
        this.renderRoot.querySelector<SectionNav>('alfio-section-nav')?.reset();
        try {
            window.sessionStorage.removeItem(STORAGE_KEY);
        } catch {
            // session storage may be unavailable
        }
    };

    private persistDraft(): void {
        if (!this.draftReady) {
            return;
        }
        try {
            window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
                values: this.values,
                fileBlobId: this.fileBlobId,
                copiedFrom: this.copiedFrom,
            }));
        } catch {
            // session storage may be unavailable or full
        }
    }

    private restoreDraft(): EventFormValues | null {
        try {
            const raw = window.sessionStorage.getItem(STORAGE_KEY);
            if (raw == null) {
                return null;
            }
            const parsed = JSON.parse(raw);
            const stored = parsed?.values ?? parsed;
            if (stored == null || typeof stored !== 'object' || stored.displayName == null) {
                return null;
            }
            const values = emptyValues();
            const src = stored as Record<string, unknown>;
            const copyString = (key: keyof EventFormValues): void => {
                if (typeof src[key] === 'string') {
                    (values as unknown as Record<string, unknown>)[key] = src[key];
                }
            };
            copyString('displayName');
            copyString('organizationId');
            copyString('location');
            copyString('timeZone');
            copyString('shortName');
            copyString('websiteUrl');
            copyString('termsAndConditionsUrl');
            copyString('privacyPolicyUrl');
            copyString('availableSeats');
            copyString('regularPrice');
            copyString('currency');
            copyString('vatPercentage');
            // the AngularJS page stored begin/end and the category dates as {date, time} pairs
            const asDateTimeLocal = (v: unknown): string => {
                if (typeof v === 'string') {
                    return wallClockToDateTimeLocal(v) || v;
                }
                if (v != null && typeof v === 'object') {
                    const dt = v as { date?: string; time?: string };
                    if (dt.date != null) {
                        return `${dt.date}T${(dt.time ?? '00:00').substring(0, 5)}`;
                    }
                }
                return '';
            };
            values.begin = asDateTimeLocal(src['begin']) || values.begin;
            values.end = asDateTimeLocal(src['end']) || values.end;
            if (src['format'] === 'ONLINE' || src['format'] === 'HYBRID' || src['format'] === 'IN_PERSON') {
                values.format = src['format'] as EventFormat;
            }
            if (src['description'] != null && typeof src['description'] === 'object') {
                values.description = src['description'] as LocalizedContent;
            }
            if (src['priceModel'] === 'free' || src['priceModel'] === 'paid') {
                values.priceModel = src['priceModel'];
            } else if (src['freeOfCharge'] === true) {
                values.priceModel = 'free';
            }
            if (typeof src['vatIncluded'] === 'boolean') {
                values.vatIncluded = src['vatIncluded'];
            }
            if (Array.isArray(src['paymentProxies'])) {
                values.paymentProxies = src['paymentProxies'].map(String);
            }
            if (Array.isArray(src['allowedPaymentProxies'])) {
                values.paymentProxies = src['allowedPaymentProxies'].map(String);
            }
            if (Array.isArray(src['linkedSubscriptions'])) {
                values.linkedSubscriptions = src['linkedSubscriptions'].map(String);
            }
            let rawCategories = src['ticketCategories'];
            if (Array.isArray(src['categories'])) {
                rawCategories = src['categories'];
            }
            if (Array.isArray(rawCategories)) {
                values.categories = (rawCategories as Record<string, unknown>[]).map((c, idx) => {
                    let maxTickets = '';
                    if (c['maxTickets'] != null) {
                        maxTickets = String(c['maxTickets']);
                    }
                    let price = '';
                    if (c['price'] != null) {
                        price = String(c['price']);
                    }
                    let ordinal = idx + 1;
                    if (typeof c['ordinal'] === 'number') {
                        ordinal = c['ordinal'];
                    }
                    let metadataCopiedFrom: number | undefined;
                    if (typeof c['metadata'] === 'object' && c['metadata'] != null) {
                        metadataCopiedFrom = Number((c['metadata'] as { copiedFrom?: number }).copiedFrom);
                    }
                    return {
                        key: this.nextCategoryKey++,
                        name: String(c['name'] ?? ''),
                        ticketAccessType: String(c['ticketAccessType'] ?? ''),
                        description: (c['description'] as LocalizedContent) ?? {},
                        tokenGenerationRequested: c['tokenGenerationRequested'] === true,
                        bounded: c['bounded'] === true,
                        maxTickets,
                        inception: asDateTimeLocal(c['inception']),
                        expiration: asDateTimeLocal(c['expiration']),
                        price,
                        code: String(c['code'] ?? ''),
                        validCheckInFrom: asDateTimeLocal(c['validCheckInFrom']),
                        validCheckInTo: asDateTimeLocal(c['validCheckInTo']),
                        ticketValidityStart: asDateTimeLocal(c['ticketValidityStart']),
                        ticketValidityEnd: asDateTimeLocal(c['ticketValidityEnd']),
                        ordinal,
                        metadataCopiedFrom,
                        inheritedFields: c['inheritedFields'] as CategoryDraft['inheritedFields'],
                        currency: c['currency'] as string | undefined,
                    };
                });
            }
            if (typeof parsed?.fileBlobId === 'string') {
                this.fileBlobId = parsed.fileBlobId;
            }
            if (typeof parsed?.copiedFrom === 'string') {
                this.copiedFrom = parsed.copiedFrom;
            }
            return values;
        } catch {
            return null;
        }
    }

    private async loadPaymentProxies(organizationId: number): Promise<void> {
        if (!(organizationId > 0)) {
            return;
        }
        const generation = ++this.paymentGeneration;
        this.paymentLoading = true;
        this.paymentError = null;
        this.paymentProxies = [];
        this.subscriptionDescriptors = [];
        try {
            const [proxies, subscriptions] = await Promise.all([
                PaymentProxyService.allProxies(organizationId),
                fetchJson<Array<{ id: string; title: Record<string, string>; description: Record<string, string> }>>(`/admin/api/organization/${organizationId}/subscription/active`),
            ]);
            if (generation !== this.paymentGeneration || String(organizationId) !== this.values.organizationId) {
                return;
            }
            this.paymentProxies = proxies;
            this.subscriptionDescriptors = subscriptions.map((s) => ({
                id: s.id,
                title: Object.values(s.title ?? {})[0] ?? s.id,
                description: Object.values(s.description ?? {})[0] ?? '',
            }));
        } catch (error) {
            if (generation === this.paymentGeneration) {
                this.paymentError = errorMessage(error, 'Could not load payment methods. Please retry.');
            }
        } finally {
            if (generation === this.paymentGeneration) {
                this.paymentLoading = false;
            }
        }
    }

    // ---------- submit ----------

    private submit = async (e: Event): Promise<void> => {
        e.preventDefault();
        e.stopImmediatePropagation();
        if (this.submitting) {
            return;
        }
        this.submitError = null;
        this.categoriesError = null;
        this.logoError = null;
        if (!this.fileBlobId) {
            this.logoError = 'Please upload an event logo.';
        }
        if (!await submitValidated(this.#form.api)) {
            this.submitError = INVALID_FORM_MESSAGE;
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    private finalPriceText(): string {
        const values = this.values;
        const price = Number(values.regularPrice);
        if (Number.isNaN(price)) {
            return '';
        }
        const vat = Number(values.vatPercentage);
        let finalPrice = price;
        if (!values.vatIncluded && !Number.isNaN(vat)) {
            finalPrice = price + price * vat / 100;
        }
        return formatAmount(finalPrice, values.currency);
    }

    private payloadMapUrl(online: boolean): string | null {
        if (online) {
            return null;
        }
        return this.geolocation?.mapUrl ?? null;
    }

    private payloadSubscriptions(freeOfCharge: boolean, values: EventFormValues): string[] {
        if (freeOfCharge) {
            return [];
        }
        return values.linkedSubscriptions;
    }

    private buildPayload(values: EventFormValues): EventModificationPayload {
        const freeOfCharge = values.priceModel === 'free';
        const online = values.format === 'ONLINE';
        const description = values.description ?? {};
        const languages = this.loadDataTask.value?.languages ?? [];
        let locales = 0;
        for (const language of languages) {
            if (description[language.locale] != null) {
                locales = locales | language.value;
            }
        }
        let latitude: string | null = null;
        let longitude: string | null = null;
        if (!online) {
            latitude = this.geolocation?.latitude ?? null;
            longitude = this.geolocation?.longitude ?? null;
        }
        let proxies: string[] = [];
        if (!freeOfCharge) {
            proxies = values.paymentProxies.filter((p) => {
                if (p !== 'CUSTOM_OFFLINE') {
                    return true;
                }
                // a custom offline payment needs at least one selected method
                return this.customOfflineMethods.length > 0;
            });
        }
        let privacyPolicyUrl: string | null = null;
        if (values.privacyPolicyUrl.trim() !== '') {
            privacyPolicyUrl = values.privacyPolicyUrl.trim();
        }
        let location: string | null = null;
        if (!online) {
            location = values.location.trim();
        }
        let regularPrice: number | null = null;
        let currency: string | null = null;
        let vatPercentage: number | null = null;
        if (!freeOfCharge) {
            regularPrice = Number(values.regularPrice);
            currency = values.currency;
            vatPercentage = Number(values.vatPercentage);
        }
        let metadata: { copiedFrom: string } | null = null;
        if (this.copiedFrom != null) {
            metadata = { copiedFrom: this.copiedFrom };
        }
        return {
            format: values.format,
            websiteUrl: values.websiteUrl.trim(),
            termsAndConditionsUrl: values.termsAndConditionsUrl.trim(),
            privacyPolicyUrl,
            fileBlobId: this.fileBlobId,
            shortName: values.shortName.trim(),
            displayName: values.displayName.trim(),
            organizationId: Number(values.organizationId),
            location,
            latitude,
            longitude,
            zoneId: values.timeZone,
            description,
            begin: dateTimeLocalToModification(values.begin) ?? { date: '', time: '' },
            end: dateTimeLocalToModification(values.end) ?? { date: '', time: '' },
            regularPrice,
            currency,
            availableSeats: Number.parseInt(values.availableSeats, 10),
            vatPercentage,
            vatIncluded: values.vatIncluded,
            allowedPaymentProxies: proxies as PaymentProxy[],
            ticketCategories: values.categories.map(category => {
                const payload = categoryDraftToPayload(category);
                if (freeOfCharge) {
                    payload.price = 0;
                }
                if (values.format !== 'HYBRID') {
                    payload.ticketAccessType = null;
                }
                return payload;
            }),
            freeOfCharge,
            geolocation: {
                timeZone: values.timeZone,
                latitude,
                longitude,
                mapUrl: this.payloadMapUrl(online),
            },
            locales,
            metadata,
            linkedSubscriptions: this.payloadSubscriptions(freeOfCharge, values),
            additionalServices: [],
        };
    }

    /**
     * Called by the form once all the fields are valid: checks the payload on the server,
     * then creates the event
     */
    private async save(values: EventFormValues): Promise<void> {
        if (!this.fileBlobId) {
            this.logoError = 'Please upload an event logo.';
            this.submitError = INVALID_FORM_MESSAGE;
            await this.updateComplete;
            const logo = this.renderRoot.querySelector<HTMLElement>('#event-logo');
            logo?.focus({preventScroll: true});
            logo?.scrollIntoView({block: 'center'});
            return;
        }
        this.submitting = true;
        this.submitError = null;
        this.logoError = null;
        try {
            const payload = this.buildPayload(values);
            const check = await EventService.check(payload);
            if (!check.success) {
                this.applyServerErrors(check.validationErrors ?? []);
                this.submitError = 'Please correct the errors highlighted in the form.';
                await this.updateComplete;
                focusFirstError(this.renderRoot);
                return;
            }
            await EventService.create(payload);
            const created = await EventService.load(payload.shortName);
            // lets the custom-offline-payment selector attach the selected methods to the new event
            window.dispatchEvent(new CustomEvent('new-event-created', { detail: { data: created } }));
            await this.createCopiedAdditionalData(created.event);
            try {
                window.sessionStorage.removeItem(STORAGE_KEY);
            } catch {
                // session storage may be unavailable
            }
            window.location.hash = `#/events/${payload.shortName}/detail`;
        } catch (error) {
            this.submitError = errorMessage(error, 'Unexpected error. Please retry and/or check the logs.');
        } finally {
            this.submitting = false;
        }
    }

    private applyServerErrors(errors: ErrorDescriptor[]): void {
        const categoryMessages: string[] = [];
        for (const error of errors) {
            const field = error.fieldName;
            if (field === 'ticketCategories' || field.startsWith('ticketCategories[')) {
                categoryMessages.push(error.message ?? error.code ?? 'Invalid category');
                continue;
            }
            if (field === 'imageUrl' || field === 'fileBlobId') {
                this.logoError = error.message || 'Event logo is missing';
                continue;
            }
            if (field === 'locationDescriptor') {
                setServerError(this.#form.api, 'location', error.message || 'The location could not be geocoded');
                continue;
            }
            if (field === 'allowedPaymentProxies') {
                setServerError(this.#form.api, 'paymentProxies', error.message ?? 'Please select valid payment methods');
                continue;
            }
            const formField = SERVER_ERROR_FIELD_MAP[field];
            if (formField != null) {
                setServerError(this.#form.api, formField, error.message ?? 'Invalid value');
            } else {
                categoryMessages.push(`${field}: ${error.message ?? error.code ?? 'invalid'}`);
            }
        }
        if (categoryMessages.length > 0) {
            this.categoriesError = categoryMessages.join(' ');
        }
    }

    /**
     * After a copy, creates the additional fields and options selected in the copy dialog
     */
    private async createCopiedAdditionalData(createdEvent: AlfioEvent): Promise<void> {
        const pending = this.pendingCopy;
        if (pending == null) {
            return;
        }
        const categoryIdByName = new Map<string, number>();
        for (const tc of createdEvent.ticketCategories) {
            categoryIdByName.set(tc.name, tc.id);
        }
        for (const service of pending.additionalServices) {
            const { id, ...body } = service;
            const response = await postJson(`/admin/api/event/${createdEvent.id}/additional-services`, body);
            if (!response.ok) {
                console.error('failed to copy an additional service', await response.text());
            }
        }
        for (const field of pending.additionalFields) {
            const description: AdditionalFieldCreateRequest['description'] = {};
            for (const [locale, value] of Object.entries(field.description ?? {})) {
                description[locale] = {
                    label: value.description?.label ?? '',
                    placeholder: value.description?.placeholder ?? '',
                    restrictedValues: value.description?.restrictedValues,
                };
            }
            const request: AdditionalFieldCreateRequest = {
                order: field.order,
                userDefinedOrder: true,
                name: field.name,
                type: field.type,
                required: field.required,
                readOnly: !field.editable,
                minLength: field.minLength,
                maxLength: field.maxLength,
                restrictedValues: (field.restrictedValues ?? []).map((rv) => ({ value: rv, enabled: true })),
                description,
                categoryIds: (field.categoryIds ?? [])
                    .map((id) => categoryIdByName.get(String(id)) ?? Number(id))
                    .filter((id) => !Number.isNaN(id)),
                displayAtCheckIn: field.displayAtCheckIn === true,
            };
            await AdditionalFieldService.createNewField(
                { type: 'event', publicIdentifier: createdEvent.shortName } as Parameters<typeof AdditionalFieldService.createNewField>[0],
                request,
            );
        }
    }
}

function shiftDateTimeLocal(local: string, deltaMs: number): string {
    const shifted = new Date(Date.parse(`${local}:00Z`) + deltaMs);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${shifted.getUTCFullYear()}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())}T${pad(shifted.getUTCHours())}:${pad(shifted.getUTCMinutes())}`;
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-event-create': EventCreate;
    }
}
