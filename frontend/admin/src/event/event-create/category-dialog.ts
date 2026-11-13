import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import type {SlDialog, SlInput, SlSelect, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {ContentLanguage, DateTimeModification, LocalizedContent} from '../../model/purchase-context.ts';
import {EventFormat, TicketCategoryModificationPayload} from '../../model/event-modification.ts';
import {dialog as dialogStyles, form, modernLayout, row, textColors} from '../../styles.ts';
import {sectionCard} from '../../components/section-card.ts';
import '../../components/multi-language-text.ts';
import type {MultiLanguageTextChange} from '../../components/multi-language-text.ts';

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
 * A new category: on sale from now until the event starts, dynamic allocation, price = regular price
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
        ticketCheckInStrategy: null,
        metadata,
    };
}

const TICKET_ACCESS_TYPES = [
    { value: 'IN_PERSON', label: 'In person' },
    { value: 'ONLINE', label: 'Online' },
];

/**
 * Dialog to add or edit a ticket category of the event being created. open() resolves with the
 * edited draft, or with null when the user cancels
 */
@customElement('alfio-event-category-dialog')
export class EventCategoryDialog extends LitElement {

    @query('sl-dialog') private dialog?: SlDialog;

    @state() private isOpen = false;
    @state() private draft: CategoryDraft | null = null;
    @state() private context: CategoryDialogContext | null = null;
    // validation messages, by field
    @state() private errors: Record<string, string> = {};

    private resolve: ((result: CategoryDraft | null) => void) | null = null;

    static readonly styles = [
        dialogStyles,
        modernLayout,
        form,
        row,
        textColors,
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
            sl-input.error, sl-select.error, sl-textarea.error {
                --sl-input-border-color: var(--sl-color-danger-600);
            }
        `,
    ];

    /**
     * Opens the dialog to edit the given draft (a fresh one when adding a category)
     */
    public open(context: CategoryDialogContext, existing: CategoryDraft): Promise<CategoryDraft | null> {
        this.context = context;
        // a new category gets one empty description per language selected for the event
        const description: LocalizedContent = { ...existing.description };
        for (const locale of context.selectedLocales) {
            if (description[locale] == null) {
                description[locale] = '';
            }
        }
        this.draft = { ...existing, description };
        this.errors = {};
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
        let label = 'New category';
        if (draft.name.trim() !== '') {
            label = `Edit category ${draft.name}`;
        }
        return html`
            <sl-dialog class="responsive-dialog" style="--alfio-dialog-max-width: 60rem" label=${label}
                       @sl-request-close=${this.onRequestClose} @sl-after-hide=${this.onAfterHide}>
                <form class="dialog-form" novalidate @submit=${this.onSubmit}>
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
                            <div class="row" style="--alfio-row-cols: 2">
                                <sl-select id="tokenGenerationRequested" label="Visibility" required hoist
                                           .value=${this.visibilityValue(draft)}
                                           @sl-change=${this.onVisibilityChange}>
                                    <sl-option value="public">Public</sl-option>
                                    <sl-option value="hidden">Hidden (access code required)</sl-option>
                                </sl-select>
                                <sl-select id="bounded" label="Ticket allocation strategy" required hoist
                                           .value=${this.allocationValue(draft)}
                                           @sl-change=${this.onBoundedChange}>
                                    <sl-option value="dynamic">Grow dynamically</sl-option>
                                    <sl-option value="fixed">Fixed number of tickets</sl-option>
                                </sl-select>
                            </div>
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
                                <p class="text-muted">Access URL: <code>${context.baseUrl}/e/${context.shortName}/c/${draft.code.trim()}</code></p>
                            `)}
                        </div>
                    `)}
                </form>
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${this.cancel}>Cancel</sl-button>
                        <div></div>
                        <sl-button id="category-save" variant="warning" size="large" @click=${this.onSubmit}>Save</sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private visibilityValue(draft: CategoryDraft): string {
        if (draft.tokenGenerationRequested) {
            return 'hidden';
        }
        return 'public';
    }

    private allocationValue(draft: CategoryDraft): string {
        if (draft.bounded) {
            return 'fixed';
        }
        return 'dynamic';
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
        // a hidden category needs a fixed number of tickets to work
        let bounded = this.draft.bounded;
        if (hidden) {
            bounded = true;
        }
        this.draft = { ...this.draft, tokenGenerationRequested: hidden, bounded };
    };

    private onBoundedChange = (e: Event): void => {
        const bounded = (e.target as SlSelect).value === 'fixed';
        this.updateDraft('bounded', bounded);
    };

    private onSubmit = (e: Event): void => {
        e.preventDefault();
        const draft = this.draft;
        const context = this.context;
        if (draft == null || context == null) {
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
        this.errors = errors;
        if (Object.keys(errors).length > 0) {
            return;
        }
        this.closeWith({...draft, currency: context.currency});
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
