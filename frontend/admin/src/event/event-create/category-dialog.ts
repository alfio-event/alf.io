import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import type {SlDialog, SlInput, SlSelect, SlRequestCloseEvent, SlSwitch, SlTabGroup} from '@shoelace-style/shoelace';
import {ContentLanguage, DateTimeModification, LocalizedContent} from '../../model/purchase-context.ts';
import {EventFormat, TicketCategoryModificationPayload} from '../../model/event-modification.ts';
import {choiceCards as choiceCardsStyles, dialog as dialogStyles, form, modernLayout, row, textColors} from '../../styles.ts';
import {sectionCard} from '../../components/section-card.ts';
import {ChoiceCardOption, choiceCards} from '../../components/choice-cards.ts';
import {ConfigurationService} from '../../service/configuration.ts';
import {BADGE_COLORS, badgeSwatch, findBadgeColor} from '../../components/badge-color.ts';
import '../../components/multi-language-text.ts';
import type {MultiLanguageTextChange} from '../../components/multi-language-text.ts';
import '../event-detail/category-options.ts';
import type {CategoryOptions, CategoryOptionsTarget} from '../event-detail/category-options.ts';
import {errorMessage} from '../../service/helpers.ts';

/**
 * A ticket category of the event being created, as edited in the category dialog.
 * Dates are kept as datetime-local values ("yyyy-MM-ddTHH:mm", wall-clock in the event time zone)
 */
export interface CategoryDraft {
    // stable key for repeat()
    key: number;
    name: string;
    // '' = inherit from the event
    ticketAccessType: string;
    description: LocalizedContent;
    tokenGenerationRequested: boolean;
    bounded: boolean;
    maxTickets: string;
    inception: string;
    expiration: string;
    price: string;
    code: string;
    validCheckInFrom: string;
    validCheckInTo: string;
    ticketValidityStart: string;
    ticketValidityEnd: string;
    // ONCE_PER_EVENT | ONCE_PER_DAY
    ticketCheckInStrategy: string;
    // color shown by the check-in app on a successful scan, '' = none
    badgeColor: string;
    ordinal: number;
    // set when the category was copied from another event
    metadataCopiedFrom?: number;
    // Only untouched defaults follow subsequent event-level changes.
    inheritedFields?: Array<'expiration' | 'price' | 'ticketAccessType'>;
    currency?: string;
}

export interface CategoryDialogContext {
    format: EventFormat;
    freeOfCharge: boolean;
    currency: string;
    // wall-clock (datetime-local) begin/end of the event, for the default and the validation of the on-sale period
    eventBegin: string;
    eventEnd: string;
    availableSeats: string;
    languages: ContentLanguage[];
    // locales currently selected for the event description
    selectedLocales: string[];
    descriptionMaxLength: number;
    baseUrl: string;
    shortName: string;
    // the other categories, to check the total of allocated seats
    categories: CategoryDraft[];
}

const CODE_PATTERN = /^[A-Za-z0-9_\-:@!$*,;]*$/;

/**
 * Converts a datetime-local value ("yyyy-MM-ddTHH:mm") to the {date, time} pair the backend expects.
 * Returns null for an empty value
 */
export function dateTimeLocalToModification(value: string): DateTimeModification | null {
    const trimmed = (value ?? '').trim();
    if (trimmed === '') {
        return null;
    }
    return {
        date: trimmed.substring(0, 10),
        time: trimmed.substring(11, 16),
    };
}

/**
 * Converts a wall-clock "yyyy-MM-dd HH:mm" value (as formattedInception & co.) to a datetime-local value
 */
export function wallClockToDateTimeLocal(value: string | null | undefined): string {
    if (value == null || value === '') {
        return '';
    }
    if (/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}/.test(value)) {
        return value.substring(0, 10) + 'T' + value.substring(11, 16);
    }
    return '';
}

/**
 * A new category: on sale from now until the event starts, without a limit, price = regular price
 */
export function newCategoryDraft(eventBegin: string, regularPrice: string, format: EventFormat, ordinal: number, key: number): CategoryDraft {
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    const nowLocal = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
    let ticketAccessType = '';
    if (format === 'HYBRID') {
        ticketAccessType = 'IN_PERSON';
    }
    return {
        key,
        name: '',
        ticketAccessType,
        description: {},
        tokenGenerationRequested: false,
        bounded: false,
        maxTickets: '',
        inception: nowLocal,
        expiration: eventBegin,
        price: regularPrice,
        code: '',
        validCheckInFrom: '',
        validCheckInTo: '',
        ticketValidityStart: '',
        ticketValidityEnd: '',
        ticketCheckInStrategy: 'ONCE_PER_EVENT',
        badgeColor: '',
        ordinal,
        inheritedFields: ['expiration', 'price', 'ticketAccessType'],
    };
}

export function categoryDraftToPayload(draft: CategoryDraft): TicketCategoryModificationPayload {
    const inception = dateTimeLocalToModification(draft.inception);
    const expiration = dateTimeLocalToModification(draft.expiration);
    let description: LocalizedContent | null = null;
    if (Object.keys(draft.description).length > 0) {
        description = draft.description;
    }
    let ticketAccessType: string | null = null;
    if (draft.ticketAccessType !== '') {
        ticketAccessType = draft.ticketAccessType;
    }
    let maxTickets = 0;
    if (draft.bounded) {
        maxTickets = Number.parseInt(draft.maxTickets, 10) || 0;
    }
    let price: number | null = null;
    if (draft.price.trim() !== '') {
        price = Number(draft.price);
    }
    let code: string | null = null;
    if (draft.code.trim() !== '') {
        code = draft.code.trim();
    }
    let metadata: { copiedFrom: number } | null = null;
    if (draft.metadataCopiedFrom != null) {
        metadata = { copiedFrom: draft.metadataCopiedFrom };
    }
    return {
        name: draft.name.trim(),
        ticketAccessType,
        maxTickets,
        inception: inception ?? { date: '', time: '' },
        expiration: expiration ?? { date: '', time: '' },
        description,
        price,
        tokenGenerationRequested: draft.tokenGenerationRequested,
        bounded: draft.bounded,
        code,
        ordinal: draft.ordinal,
        validCheckInFrom: dateTimeLocalToModification(draft.validCheckInFrom),
        validCheckInTo: dateTimeLocalToModification(draft.validCheckInTo),
        ticketValidityStart: dateTimeLocalToModification(draft.ticketValidityStart),
        ticketValidityEnd: dateTimeLocalToModification(draft.ticketValidityEnd),
        ticketCheckInStrategy: draft.ticketCheckInStrategy || 'ONCE_PER_EVENT',
        badgeColor: draft.badgeColor || null,
        metadata,
    };
}

export type CategoryDialogTab = 'general' | 'validity' | 'options';

type ValidityMode = 'event' | 'custom';
type CheckInMode = 'anytime' | 'custom';

const VALIDITY_CHOICES: ChoiceCardOption<ValidityMode>[] = [
    {
        value: 'event',
        icon: 'calendar-range',
        title: 'For the whole event',
        description: 'The ticket shows the dates of the event.',
    },
    {
        value: 'custom',
        icon: 'calendar-day',
        title: 'For specific dates',
        description: 'For example, a one-day pass of a multi-day event.',
    },
];

const CHECK_IN_CHOICES: ChoiceCardOption<CheckInMode>[] = [
    {
        value: 'anytime',
        icon: 'clock',
        title: 'At any time',
        description: 'Tickets can be scanned whenever attendees arrive.',
    },
    {
        value: 'custom',
        icon: 'clock-history',
        title: 'Only at specific times',
        description: 'Tickets scanned outside these times are refused.',
    },
];

const CHECK_IN_STRATEGY_CHOICES: ChoiceCardOption<string>[] = [
    {
        value: 'ONCE_PER_EVENT',
        icon: 'ticket-perforated',
        title: 'Once',
        description: 'The first scan of the ticket checks attendees in for the whole event.',
    },
    {
        value: 'ONCE_PER_DAY',
        icon: 'person-badge',
        title: 'Every day',
        description: 'With the ticket on the first day, then with the badge printed at check-in.',
    },
];

function hasValue(value: string): boolean {
    return value.trim() !== '';
}

/**
 * The options of an existing category, edited in a second tab of the dialog
 */
export interface CategoryDialogOptions {
    configuration?: CategoryOptionsTarget;
    // the tab displayed when the dialog opens
    tab?: CategoryDialogTab;
}

const TICKET_ACCESS_TYPES = [
    { value: 'IN_PERSON', label: 'In person' },
    { value: 'ONLINE', label: 'Online' },
];

/**
 * Dialog to add or edit a ticket category. open() resolves with the edited draft, or with null when the
 * user cancels. For an existing category, the dialog can also edit its options (second tab): they are
 * saved by the dialog itself, before it resolves
 */
@customElement('alfio-event-category-dialog')
export class EventCategoryDialog extends LitElement {

    @query('sl-dialog') private dialog?: SlDialog;
    @query('sl-tab-group') private tabGroup?: SlTabGroup;
    @query('alfio-category-options') private options?: CategoryOptions;

    @state() private isOpen = false;
    @state() private draft: CategoryDraft | null = null;
    @state() private context: CategoryDialogContext | null = null;
    // validation messages, by field
    @state() private errors: Record<string, string> = {};
    // decided when the dialog opens, so that typing the name of a new category doesn't change it
    @state() private label = 'New category';
    @state() private optionsTarget: CategoryOptionsTarget | null = null;
    @state() private saving = false;
    @state() private saveError: string | null = null;
    // the tab displayed when the dialog opens
    @state() private initialTab: CategoryDialogTab = 'general';
    // whether the ticket validity and the check-in window differ from the dates of the event
    @state() private validityMode: ValidityMode = 'event';
    @state() private checkInMode: CheckInMode = 'anytime';
    @state() private badgeColorLoading = false;
    @state() private badgeColorError: string | null = null;
    // discards the badge color loaded for a previous opening
    private openCount = 0;

    private resolve: ((result: CategoryDraft | null) => void) | null = null;

    static readonly styles = [
        dialogStyles,
        modernLayout,
        form,
        row,
        textColors,
        choiceCardsStyles,
        css`
            :host {
                display: block;
            }
            .dialog-form {
                display: grid;
                gap: var(--sl-spacing-medium);
            }
            .dialog-form .section-card {
                margin-bottom: 0;
            }
            .form-error-text {
                color: var(--sl-color-danger-600);
                font-size: var(--sl-font-size-small);
                margin-top: var(--sl-spacing-x-small);
            }
            .access-url code {
                overflow-wrap: anywhere;
            }
            sl-input.error, sl-select.error, sl-textarea.error {
                --sl-input-border-color: var(--sl-color-danger-600);
            }
            sl-tab-group::part(body) {
                padding-top: var(--sl-spacing-medium);
            }
            .save-error {
                margin-top: var(--sl-spacing-medium);
            }
            .validity-note {
                margin: 0;
            }
            .badge-swatch {
                display: inline-block;
                width: 1rem;
                height: 1rem;
                border-radius: var(--sl-border-radius-circle);
                border: 1px solid var(--sl-color-neutral-300);
            }
        `,
    ];

    /**
     * Opens the dialog to edit the given draft (a fresh one when adding a category)
     */
    public open(context: CategoryDialogContext, existing: CategoryDraft, options: CategoryDialogOptions = {}): Promise<CategoryDraft | null> {
        this.context = context;
        this.optionsTarget = options.configuration ?? null;
        this.initialTab = options.tab ?? 'general';
        this.saveError = null;
        this.saving = false;
        // a new category gets one empty description per language selected for the event
        const description: LocalizedContent = { ...existing.description };
        for (const locale of context.selectedLocales) {
            if (description[locale] == null) {
                description[locale] = '';
            }
        }
        this.draft = { ...existing, description };
        this.validityMode = 'event';
        if (hasValue(existing.ticketValidityStart) || hasValue(existing.ticketValidityEnd)) {
            this.validityMode = 'custom';
        }
        this.checkInMode = 'anytime';
        if (hasValue(existing.validCheckInFrom) || hasValue(existing.validCheckInTo)) {
            this.checkInMode = 'custom';
        }
        this.loadBadgeColor(options.configuration);
        this.errors = {};
        this.label = 'New category';
        if (existing.name.trim() !== '') {
            this.label = `Edit category ${existing.name}`;
        }
        this.isOpen = true;
        const promise = new Promise<CategoryDraft | null>((resolve) => {
            this.resolve = resolve;
        });
        // render the content only while the dialog is open
        void this.updateComplete.then(() => this.dialog?.show());
        return promise;
    }

    protected render(): TemplateResult {
        const draft = this.draft;
        const context = this.context;
        if (!this.isOpen || draft == null || context == null) {
            return html``;
        }
        const hybrid = context.format === 'HYBRID' || draft.ticketAccessType !== '';
        const error = (field: string): string => this.errors[field] ?? '';
        let codeHelp = 'Allowed characters: letters, numbers, and -:_@!$*,; — no spaces';
        if (error('code') !== '') {
            codeHelp = error('code');
        }
        return html`
            <sl-dialog class="responsive-dialog" style="--alfio-dialog-max-width: 60rem" label=${this.label}
                       @sl-request-close=${this.onRequestClose} @sl-after-hide=${this.onAfterHide}>
                ${this.renderBody(this.renderValidity(draft, context), html`<form class="dialog-form" novalidate @submit=${this.onSubmit}>
                    ${sectionCard({ icon: 'tag', title: 'Base information' }, html`
                        <div class="form-stack">
                            <sl-input id="name" name="name" label="Name" required
                                      class=${classMap({ error: error('name') !== '' })}
                                      help-text=${error('name')}
                                      .value=${draft.name}
                                      @sl-input=${(e: Event) => this.updateDraft('name', (e.target as SlInput).value)}></sl-input>
                            ${when(hybrid, () => html`
                                <sl-select id="accessType" label="Ticket holders can access the event" required hoist
                                           class=${classMap({ error: error('ticketAccessType') !== '' })}
                                           help-text=${error('ticketAccessType')}
                                           .value=${draft.ticketAccessType}
                                           @sl-change=${(e: Event) => this.updateDraft('ticketAccessType', String((e.target as SlSelect).value))}>
                                    ${repeat(TICKET_ACCESS_TYPES, (t) => t.value, (t) => html`<sl-option value=${t.value}>${t.label}</sl-option>`)}
                                </sl-select>
                            `)}
                            <alfio-multi-language-text
                                label="Description"
                                icon="text-paragraph"
                                .card=${false}
                                max-length=${context.descriptionMaxLength}
                                .markdown=${true}
                                .multiline=${true}
                                .languages=${context.languages}
                                .contextLanguages=${this.contextLanguages()}
                                .value=${draft.description}
                                @change=${(e: CustomEvent<MultiLanguageTextChange>) => this.updateDescription(e.detail.value)}
                            ></alfio-multi-language-text>
                        </div>
                    `)}
                    ${sectionCard({ icon: 'ticket-perforated', title: 'Tickets and sales' }, html`
                        <div class="form-stack">
                            <sl-select id="tokenGenerationRequested" label="Visibility" required hoist
                                       help-text=${this.visibilityHelp(draft)}
                                       .value=${this.visibilityValue(draft)}
                                       @sl-change=${this.onVisibilityChange}>
                                <sl-option value="public">Listed on the event page</sl-option>
                                <sl-option value="hidden">Unlisted</sl-option>
                            </sl-select>
                            <sl-switch id="bounded" ?checked=${draft.bounded} ?disabled=${draft.tokenGenerationRequested}
                                       help-text=${this.limitHelp(draft)}
                                       @sl-change=${this.onBoundedChange}>Limit the number of tickets</sl-switch>
                            ${when(draft.bounded, () => html`
                                <sl-input id="maxTickets" name="maxTickets" label="Number of tickets" type="number" min="1" step="1" required
                                          class=${classMap({ error: error('maxTickets') !== '' })}
                                          help-text=${error('maxTickets')}
                                          .value=${draft.maxTickets}
                                          @sl-input=${(e: Event) => this.updateDraft('maxTickets', (e.target as SlInput).value)}></sl-input>
                            `)}
                            <div class="row" style="--alfio-row-cols: 2">
                                <sl-input id="inception" label="On sale from" type="datetime-local" required
                                          class=${classMap({ error: error('inception') !== '' })}
                                          help-text=${error('inception')}
                                          .value=${draft.inception}
                                          @sl-input=${(e: Event) => this.updateDraft('inception', (e.target as SlInput).value)}></sl-input>
                                <sl-input id="expiration" label="On sale until" type="datetime-local" required
                                          class=${classMap({ error: error('expiration') !== '' })}
                                          help-text=${error('expiration')}
                                          .value=${draft.expiration}
                                          @sl-input=${(e: Event) => this.updateDraft('expiration', (e.target as SlInput).value)}></sl-input>
                            </div>
                            ${when(!context.freeOfCharge, () => html`
                                <sl-input id="price" name="price" label="Price" type="number" min="0" step="0.01" required
                                          class=${classMap({ error: error('price') !== '' })}
                                          help-text=${error('price')}
                                          .value=${draft.price}
                                          @sl-input=${(e: Event) => this.updateDraft('price', (e.target as SlInput).value)}>
                                    <span slot="suffix">${context.currency}</span>
                                </sl-input>
                            `)}
                        </div>
                    `)}
                    ${sectionCard({ icon: 'link-45deg', title: 'Direct reservation link' }, html`
                        <div class="form-stack">
                            <sl-input id="code" name="code" label="Code (optional)" placeholder="Leave empty for none"
                                      class=${classMap({ error: error('code') !== '' })}
                                      help-text=${codeHelp}
                                      .value=${draft.code}
                                      @sl-input=${(e: Event) => this.updateDraft('code', (e.target as SlInput).value)}></sl-input>
                            ${when(draft.code.trim() !== '', () => html`
                                <p class="text-muted access-url">Access URL: <code>${context.baseUrl}/e/${context.shortName}/c/${draft.code.trim()}</code></p>
                            `)}
                        </div>
                    `)}
                </form>`)}
                ${when(this.saveError != null, () => html`
                    <sl-alert class="save-error" variant="danger" open closable @sl-after-hide=${() => { this.saveError = null; }}>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.saveError}
                    </sl-alert>
                `)}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${this.cancel}>Cancel</sl-button>
                        <div></div>
                        <sl-button id="category-save" variant="warning" size="large" ?loading=${this.saving} ?disabled=${this.saving} @click=${this.onSubmit}>Save</sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    /**
     * The tabs of the dialog: the form of the category, its ticket validity and, for an existing category, its options
     */
    private renderBody(validity: TemplateResult, general: TemplateResult): TemplateResult {
        const target = this.optionsTarget;
        return html`
            <sl-tab-group>
                <sl-tab slot="nav" panel="general" class="general-tab" ?active=${this.initialTab === 'general'}>General</sl-tab>
                <sl-tab slot="nav" panel="validity" class="validity-tab" ?active=${this.initialTab === 'validity'}>Ticket validity</sl-tab>
                ${when(target != null, () => html`
                    <sl-tab slot="nav" panel="options" class="options-tab" ?active=${this.initialTab === 'options'}>Options</sl-tab>
                `)}
                <sl-tab-panel name="general">${general}</sl-tab-panel>
                <sl-tab-panel name="validity">${validity}</sl-tab-panel>
                ${when(target != null, () => html`
                    <sl-tab-panel name="options">
                        <alfio-category-options .target=${target}></alfio-category-options>
                    </sl-tab-panel>
                `)}
            </sl-tab-group>
        `;
    }

    /**
     * When the ticket is valid (shown on the ticket) and how attendees check in
     */
    private renderValidity(draft: CategoryDraft, context: CategoryDialogContext): TemplateResult {
        const error = (field: string): string => this.errors[field] ?? '';
        let validityPanel: TemplateResult | undefined;
        if (this.validityMode === 'custom') {
            validityPanel = html`
                <div class="row" style="--alfio-row-cols: 2">
                    ${this.dateInput('ticketValidityStart', 'Valid from', draft.ticketValidityStart, error('ticketValidityStart'))}
                    ${this.dateInput('ticketValidityEnd', 'Valid until', draft.ticketValidityEnd, error('ticketValidityEnd'))}
                </div>
            `;
        }
        let checkInPanel: TemplateResult | undefined;
        if (this.checkInMode === 'custom') {
            checkInPanel = html`
                <div class="row" style="--alfio-row-cols: 2">
                    ${this.dateInput('validCheckInFrom', 'Check-in from', draft.validCheckInFrom, error('validCheckInFrom'))}
                    ${this.dateInput('validCheckInTo', 'Check-in until', draft.validCheckInTo, error('validCheckInTo'))}
                </div>
            `;
        }
        return html`
            <div class="dialog-form">
                ${sectionCard({ icon: 'calendar-check', title: 'Validity' }, html`
                    <div class="form-stack">
                        ${choiceCards({
                            name: 'ticket-validity',
                            label: 'When is the ticket valid?',
                            value: this.validityMode,
                            options: VALIDITY_CHOICES,
                            className: 'validity-choice',
                            panel: validityPanel,
                        }, mode => this.onValidityModeChange(mode))}
                        <p class="text-muted validity-note">Shown on the ticket PDF, the Apple Wallet pass and the calendar invitation.</p>
                    </div>
                `)}
                ${sectionCard({ icon: 'qr-code-scan', title: 'Check-in' }, html`
                    <div class="form-stack">
                        ${choiceCards({
                            name: 'check-in-window',
                            label: 'When can attendees check in?',
                            value: this.checkInMode,
                            options: CHECK_IN_CHOICES,
                            className: 'check-in-choice',
                            panel: checkInPanel,
                        }, mode => this.onCheckInModeChange(mode))}
                        ${when(this.isMultiDay(context), () => choiceCards({
                            name: 'check-in-strategy',
                            label: 'How often do attendees check in?',
                            value: draft.ticketCheckInStrategy,
                            options: CHECK_IN_STRATEGY_CHOICES,
                            className: 'check-in-strategy-choice',
                        }, strategy => this.updateDraft('ticketCheckInStrategy', strategy)))}
                        ${this.renderBadgeColor(draft)}
                    </div>
                `)}
            </div>
        `;
    }

    private dateInput(field: 'ticketValidityStart' | 'ticketValidityEnd' | 'validCheckInFrom' | 'validCheckInTo',
                      label: string, value: string, error: string): TemplateResult {
        return html`
            <sl-input id=${field} label=${label} type="datetime-local" required
                      class=${classMap({ error: error !== '' })}
                      help-text=${error}
                      .value=${value}
                      @sl-input=${(e: Event) => this.updateDraft(field, (e.target as SlInput).value)}></sl-input>
        `;
    }

    private renderBadgeColor(draft: CategoryDraft): TemplateResult {
        let help = 'The check-in app shows this color when it scans a ticket of this category, so that your staff can spot it.';
        if (this.badgeColorLoading) {
            help = 'Loading the current color…';
        }
        if (this.badgeColorError != null) {
            help = this.badgeColorError;
        }
        const selected = findBadgeColor(draft.badgeColor);
        return html`
            <sl-select id="badgeColor" label="Badge color" placeholder="None" hoist
                       help-text=${help}
                       ?disabled=${this.badgeColorLoading || this.badgeColorError != null}
                       .value=${draft.badgeColor}
                       @sl-change=${(e: Event) => this.updateDraft('badgeColor', String((e.target as SlSelect).value))}>
                ${when(selected != null, () => badgeSwatch(selected!, 'prefix'))}
                ${repeat(BADGE_COLORS, c => c.value, c => html`
                    <sl-option value=${c.value}>
                        ${badgeSwatch(c, 'prefix')}
                        ${c.label}
                    </sl-option>
                `)}
            </sl-select>
        `;
    }

    /**
     * Multi-day events can ask attendees to check in every day
     */
    private isMultiDay(context: CategoryDialogContext): boolean {
        if (context.eventBegin === '' || context.eventEnd === '') {
            return false;
        }
        return context.eventBegin.substring(0, 10) !== context.eventEnd.substring(0, 10);
    }

    /**
     * The badge color is part of the event's check-in settings: load it for an existing category
     */
    private loadBadgeColor(configuration: CategoryOptionsTarget | undefined): void {
        this.openCount++;
        this.badgeColorError = null;
        this.badgeColorLoading = false;
        if (configuration == null) {
            return;
        }
        const openCount = this.openCount;
        this.badgeColorLoading = true;
        ConfigurationService.loadBadgeColor(configuration.eventName, configuration.categoryId)
            .then(color => {
                if (openCount !== this.openCount || this.draft == null) {
                    return;
                }
                this.draft = { ...this.draft, badgeColor: color };
            })
            .catch(error => {
                if (openCount === this.openCount) {
                    this.badgeColorError = errorMessage(error, 'Failed to load the badge color');
                }
            })
            .finally(() => {
                if (openCount === this.openCount) {
                    this.badgeColorLoading = false;
                }
            });
    }

    private onValidityModeChange(mode: ValidityMode): void {
        this.validityMode = mode;
        const draft = this.draft;
        const context = this.context;
        if (mode !== 'custom' || draft == null || context == null) {
            return;
        }
        // start from the dates of the event, so that only the different one needs to be changed
        let ticketValidityStart = draft.ticketValidityStart;
        if (!hasValue(ticketValidityStart)) {
            ticketValidityStart = context.eventBegin;
        }
        let ticketValidityEnd = draft.ticketValidityEnd;
        if (!hasValue(ticketValidityEnd)) {
            ticketValidityEnd = context.eventEnd;
        }
        this.draft = { ...draft, ticketValidityStart, ticketValidityEnd };
    }

    private onCheckInModeChange(mode: CheckInMode): void {
        this.checkInMode = mode;
        const draft = this.draft;
        const context = this.context;
        if (mode !== 'custom' || draft == null || context == null) {
            return;
        }
        // start from the validity of the ticket
        let defaultFrom = context.eventBegin;
        let defaultTo = context.eventEnd;
        if (this.validityMode === 'custom') {
            defaultFrom = draft.ticketValidityStart;
            defaultTo = draft.ticketValidityEnd;
        }
        let validCheckInFrom = draft.validCheckInFrom;
        if (!hasValue(validCheckInFrom)) {
            validCheckInFrom = defaultFrom;
        }
        let validCheckInTo = draft.validCheckInTo;
        if (!hasValue(validCheckInTo)) {
            validCheckInTo = defaultTo;
        }
        this.draft = { ...draft, validCheckInFrom, validCheckInTo };
    }

    /**
     * Errors of the ticket validity tab
     */
    private validateValidity(draft: CategoryDraft, context: CategoryDialogContext): Record<string, string> {
        const errors: Record<string, string> = {};
        const begin = context.eventBegin;
        const end = context.eventEnd;
        if (this.validityMode === 'custom') {
            const from = draft.ticketValidityStart;
            const to = draft.ticketValidityEnd;
            if (!hasValue(from)) {
                errors.ticketValidityStart = 'Please fill in this field';
            } else if (begin !== '' && from < begin) {
                errors.ticketValidityStart = 'The ticket cannot be valid before the event starts';
            }
            if (!hasValue(to)) {
                errors.ticketValidityEnd = 'Please fill in this field';
            } else if (end !== '' && to > end) {
                errors.ticketValidityEnd = 'The ticket cannot be valid after the event ends';
            } else if (hasValue(from) && to <= from) {
                errors.ticketValidityEnd = 'The end of the validity must be after its start';
            }
        }
        if (this.checkInMode === 'custom') {
            const from = draft.validCheckInFrom;
            const to = draft.validCheckInTo;
            if (!hasValue(from)) {
                errors.validCheckInFrom = 'Please fill in this field';
            }
            if (!hasValue(to)) {
                errors.validCheckInTo = 'Please fill in this field';
            } else if (end !== '' && to > end) {
                errors.validCheckInTo = 'Check-in cannot end after the event';
            } else if (begin !== '' && to <= begin) {
                errors.validCheckInTo = 'Check-in must end after the event starts';
            } else if (hasValue(from) && to <= from) {
                errors.validCheckInTo = 'The end of check-in must be after its start';
            }
        }
        return errors;
    }

    private visibilityValue(draft: CategoryDraft): string {
        if (draft.tokenGenerationRequested) {
            return 'hidden';
        }
        return 'public';
    }

    private visibilityHelp(draft: CategoryDraft): string {
        if (draft.tokenGenerationRequested) {
            return 'Only people with an invitation, the category link or an access code can buy it.';
        }
        return 'Anyone can buy it from the event page.';
    }

    private limitHelp(draft: CategoryDraft): string {
        if (draft.tokenGenerationRequested) {
            return 'Unlisted categories always have a limit: each ticket gets its own invitation code.';
        }
        return "These tickets are kept for this category. Other categories can't sell them, even if some are left over.";
    }

    private updateDraft<K extends keyof CategoryDraft>(key: K, value: CategoryDraft[K]): void {
        if (this.draft == null) {
            return;
        }
        this.draft = {
            ...this.draft,
            [key]: value,
            inheritedFields: this.draft.inheritedFields?.filter(field => field !== key),
        };
        const errors = { ...this.errors };
        delete errors[key];
        this.errors = errors;
    }

    private updateDescription(value: LocalizedContent): void {
        if (this.draft == null) {
            return;
        }
        this.draft = { ...this.draft, description: value };
    }

    /**
     * The languages already selected for the event, so that the category description
     * starts with the same translations
     */
    private contextLanguages(): ContentLanguage[] {
        const context = this.context;
        if (context == null) {
            return [];
        }
        return context.languages.filter((l) => context.selectedLocales.includes(l.locale));
    }

    private onVisibilityChange = (e: Event): void => {
        const hidden = (e.target as SlSelect).value === 'hidden';
        if (this.draft == null) {
            return;
        }
        // an unlisted category always has a limit: each ticket gets its own invitation code
        let bounded = this.draft.bounded;
        if (hidden) {
            bounded = true;
        }
        this.draft = { ...this.draft, tokenGenerationRequested: hidden, bounded };
    };

    private onBoundedChange = (e: Event): void => {
        this.updateDraft('bounded', (e.target as SlSwitch).checked);
    };

    private onSubmit = async (e: Event): Promise<void> => {
        e.preventDefault();
        const draft = this.draft;
        const context = this.context;
        if (draft == null || context == null || this.saving) {
            return;
        }
        const errors: Record<string, string> = {};
        if (draft.name.trim() === '') {
            errors.name = 'Please fill in this field';
        }
        if (context.format !== 'HYBRID' && draft.ticketAccessType !== '' && draft.ticketAccessType !== context.format) {
            errors.ticketAccessType = 'Select access matching the event format.';
        }
        if (context.format === 'HYBRID' && draft.ticketAccessType === '') {
            errors.ticketAccessType = 'Please select an option';
        }
        if (draft.bounded) {
            const maxTickets = Number.parseInt(draft.maxTickets, 10);
            if (draft.maxTickets.trim() === '' || Number.isNaN(maxTickets) || maxTickets < 1) {
                errors.maxTickets = 'Please enter a number of tickets, at least 1';
            } else {
                const seats = Number.parseInt(context.availableSeats, 10);
                const others = context.categories
                    .filter((c) => c.key !== draft.key && c.bounded)
                    .reduce((sum, c) => sum + (Number.parseInt(c.maxTickets, 10) || 0), 0);
                if (!Number.isNaN(seats) && others + maxTickets > seats) {
                    errors.maxTickets = `The categories cannot allocate more than the ${seats} seats of the event`;
                }
            }
        }
        if (draft.inception.trim() === '') {
            errors.inception = 'Please fill in this field';
        }
        if (draft.expiration.trim() === '') {
            errors.expiration = 'Please fill in this field';
        }
        if (errors.inception == null && errors.expiration == null && draft.inception >= draft.expiration) {
            errors.expiration = 'The end of the sale must be after its start';
        }
        if (errors.expiration == null && context.eventEnd !== '' && draft.expiration > context.eventEnd) {
            errors.expiration = 'The category cannot stay on sale after the end of the event';
        }
        if (!context.freeOfCharge) {
            const price = Number(draft.price);
            if (draft.price.trim() === '' || Number.isNaN(price) || price < 0) {
                errors.price = 'Please enter a price, 0 or more';
            }
        }
        if (draft.code.trim() !== '' && !CODE_PATTERN.test(draft.code.trim())) {
            errors.code = 'Spaces and other special characters are not allowed';
        }
        const validityErrors = this.validateValidity(draft, context);
        this.errors = { ...errors, ...validityErrors };
        if (Object.keys(errors).length > 0) {
            this.tabGroup?.show('general');
            return;
        }
        if (Object.keys(validityErrors).length > 0) {
            this.tabGroup?.show('validity');
            return;
        }
        const options = this.options;
        if (options != null) {
            if (!options.validate()) {
                this.tabGroup?.show('options');
                return;
            }
            this.saving = true;
            this.saveError = null;
            try {
                await options.save();
            } catch (error) {
                this.saveError = errorMessage(error, 'Failed to save the options of the category');
                return;
            } finally {
                this.saving = false;
            }
        }
        const saved = { ...draft, currency: context.currency };
        // the dates of the event apply
        if (this.validityMode === 'event') {
            saved.ticketValidityStart = '';
            saved.ticketValidityEnd = '';
        }
        if (this.checkInMode === 'anytime') {
            saved.validCheckInFrom = '';
            saved.validCheckInTo = '';
        }
        this.closeWith(saved);
    };

    private onRequestClose = (e: SlRequestCloseEvent): void => {
        // the dialog closes only from X and Cancel, so a stray click doesn't discard the input
        if (e.detail.source === 'overlay') {
            e.preventDefault();
        }
    };

    private onAfterHide = (e: Event): void => {
        if (e.target !== e.currentTarget) {
            return;
        }
        this.isOpen = false;
        this.draft = null;
        this.context = null;
        this.optionsTarget = null;
        this.saveError = null;
        this.errors = {};
        const resolve = this.resolve;
        this.resolve = null;
        // still pending: the dialog was closed without saving
        resolve?.(null);
    };

    private cancel = (): void => {
        void this.dialog?.hide();
    };

    private closeWith(draft: CategoryDraft): void {
        const resolve = this.resolve;
        this.resolve = null;
        resolve?.(draft);
        void this.dialog?.hide();
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-event-category-dialog': EventCategoryDialog;
    }
}
