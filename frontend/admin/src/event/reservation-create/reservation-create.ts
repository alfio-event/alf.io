import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import type {SlInput, SlSelect, SlSwitch} from '@shoelace-style/shoelace';
import {AlfioEvent} from '../../model/event.ts';
import {
    AdminReservationRequest,
    CategoryType,
    ReservationAttendee,
    ReservationCreatedDetail,
    TicketAccessType,
} from '../../model/admin-reservation.ts';
import {EventService} from '../../service/event.ts';
import {
    AdminReservationService,
    buildCategory,
    CATEGORY_TYPE_CHOICES,
    reservationExpiration,
    formatFinalPrice,
    vatDescription,
} from '../../service/admin-reservation.ts';
import {errorMessage} from '../../service/helpers.ts';
import {localizedTitle} from '../../service/reservation-format.ts';
import {badges, base, choiceCards as choiceCardsStyles, form, modernLayout, purchaseContextListPage, retroCompat, row, textColors} from '../../styles.ts';
import {sectionCard} from '../../components/section-card.ts';
import {TicketCategoriesController, ticketCategorySelect} from '../../components/ticket-category-select.ts';
import {ChoiceCardOption, choiceCards} from '../../components/choice-cards.ts';
import {taskContent} from '../../components/task-content.ts';
import {FileUploadChangeEvent} from '../../components/file-upload.ts';
import '../../components/file-upload.ts';

type AttendeeStrategy = 'manual' | 'csv' | 'empty';

/**
 * Tickets of a single category, being edited
 */
interface TicketsDraft {
    // stable key for repeat()
    key: number;
    attendeeStrategy: AttendeeStrategy;
    attendees: ReservationAttendee[];
    // number of seats, for the "empty" strategy
    seats: string;
    csvMessage: string | null;
    csvError: string | null;
    categoryType: CategoryType;
    existingCategoryId: string;
    name: string;
    price: string;
    ticketAccessType: string;
    addSeatsIfNotAvailable: boolean;
}

const ATTENDEE_STRATEGIES: ChoiceCardOption<AttendeeStrategy>[] = [
    {
        value: 'manual',
        icon: 'person-vcard',
        title: 'Insert manually',
        description: "Type the attendees' names and e-mail addresses, one by one.",
    },
    {
        value: 'csv',
        icon: 'filetype-csv',
        title: 'Upload CSV',
        description: 'Import the attendees from a file, then review them before saving.',
    },
    {
        value: 'empty',
        icon: 'person-dash',
        title: 'Reserve empty seats',
        description: "The contact person fills in the attendees' data later, through the reservation link.",
    },
];

const TICKET_ACCESS_TYPES: { value: TicketAccessType, label: string }[] = [
    { value: 'IN_PERSON', label: 'In person' },
    { value: 'ONLINE', label: 'Online' },
];

/**
 * Creates a reservation for an event, with tickets of one or more (existing or new) categories.
 *
 * With "data-fast-creation", the first attendee is also the contact person, and the form is embedded in a dialog
 * (see AddAttendeesDialog):
 * instead of moving to the reservation, it fires "alfio-reservation-created" or "alfio-reservation-create-cancelled".
 */
@customElement('alfio-reservation-create')
export class ReservationCreate extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @property({ type: Boolean, attribute: 'data-fast-creation' }) fastCreation = false;
    @state() private contact: ReservationAttendee = emptyAttendee();
    @state() private language = '';
    @state() private tickets: TicketsDraft[] = [];
    private readonly categories = new TicketCategoriesController(this);
    @state() private submitting = false;
    @state() private submitError: string | null = null;
    private nextKey = 0;

    private readonly loadEventTask = new Task(this, {
        task: async ([eventName]): Promise<AlfioEvent> => {
            const { event } = await EventService.load(eventName);
            this.categories.set(event.ticketCategories);
            this.reset(event);
            return event;
        },
        args: () => [this.eventName] as const,
    });

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        form,
        badges,
        row,
        modernLayout,
        purchaseContextListPage,
        choiceCardsStyles,
        css`
            :host {
                --alfio-section-body-padding: var(--sl-spacing-large);
            }
            .tickets-block sl-divider {
                --spacing: var(--sl-spacing-large);
            }
            .attendees {
                display: grid;
                gap: var(--sl-spacing-x-small);
            }
            .attendees {
                --attendee-number-width: 2rem;
            }
            .attendee {
                --alfio-custom-row-cols-layout: var(--attendee-number-width) 1fr 1fr 1fr auto;
                --alfio-column-gap: var(--sl-spacing-small);
                align-items: end;
            }
            .attendee sl-input {
                margin-top: 0;
            }
            .attendee-number {
                align-self: center;
                color: var(--sl-color-gray-500);
                font-size: var(--sl-font-size-small);
                text-align: end;
            }
            /* keeps the rows aligned with the first one, which has the labels */
            .attendee:first-child .attendee-number,
            .attendee:first-child sl-icon-button {
                align-self: end;
                padding-bottom: var(--sl-spacing-x-small);
            }
            /* under the last row, aligned with the inputs */
            .add-attendee {
                justify-self: start;
                margin-inline-start: calc(var(--attendee-number-width) + var(--sl-spacing-small));
            }
            .add-attendee::part(base) {
                padding-inline-start: 0;
            }
            .add-attendee::part(label) {
                padding-inline-start: var(--sl-spacing-x-small);
            }
            .footer sl-divider {
                margin-top: 0;
            }
            @media (max-width: 767px) {
                .attendee-number {
                    text-align: start;
                }
                .add-attendee {
                    margin-inline-start: 0;
                }
            }
        `,
    ];

    render(): TemplateResult {
        return taskContent(this.loadEventTask, this.loadEventTask.value ?? null, 'Failed to load the event. Please try again.',
            event => this.renderContent(event));
    }

    private renderContent(event: AlfioEvent): TemplateResult {
        let layoutClass = 'container';
        if (this.fastCreation) {
            layoutClass = 'embedded';
        }
        return html`
            <div class=${layoutClass}>
                ${this.renderTitle(event)}
                <form @submit=${(e: Event) => this.submit(e, event)}>
                    ${this.renderContact(event)}
                    ${repeat(this.tickets, tickets => tickets.key, (tickets, index) => this.renderTickets(event, tickets, index))}
                    <sl-button id="add-category" class="section-card-add" variant="default" size="large" @click=${this.addTickets}>
                        <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                        Add tickets for another category
                    </sl-button>
                    ${when(this.submitError, () => html`
                        <sl-alert id="submit-error" variant="danger" open closable @sl-after-hide=${() => { this.submitError = null; }}>
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            ${this.submitError}
                        </sl-alert>
                    `)}
                    <div class="footer">
                        <sl-divider></sl-divider>
                        <div class="row" style="--alfio-row-cols: 3">
                            ${this.renderCancelButton(event)}
                            <div></div>
                            <sl-button id="save-button" type="submit" variant="warning" size="large" ?loading=${this.submitting}>
                                <sl-icon slot="prefix" name="check-circle"></sl-icon>
                                Save and continue
                            </sl-button>
                        </div>
                    </div>
                </form>
            </div>
        `;
    }

    // in "fast creation" mode, the dialog hosting the form has its own title
    private renderTitle(event: AlfioEvent): TemplateResult {
        if (this.fastCreation) {
            return html``;
        }
        return html`
            <div class="page-title-row secondary">
                <h1>Create reservation for <i>${localizedTitle(event)}</i></h1>
            </div>
            <hr class="page-separator" />
            <p class="page-description text-muted">
                You can create tickets for one or more <i>ticket categories</i>, either existing or dedicated (new).
            </p>
        `;
    }

    private renderCancelButton(event: AlfioEvent): TemplateResult {
        if (this.fastCreation) {
            return html`
                <sl-button id="cancel-button" variant="default" size="large" @click=${this.cancel}>
                    <sl-icon slot="prefix" name="x-circle"></sl-icon>
                    Cancel
                </sl-button>
            `;
        }
        return html`
            <sl-button id="cancel-button" variant="default" size="large" href=${`#/events/${event.shortName}/detail`}>
                <sl-icon slot="prefix" name="x-circle"></sl-icon>
                Cancel
            </sl-button>
        `;
    }

    private renderContact(event: AlfioEvent): TemplateResult {
        const multipleLanguages = event.contentLanguages.length > 1;
        if (this.fastCreation) {
            // the first attendee is the contact person
            if (!multipleLanguages) {
                return html``;
            }
            return sectionCard({ icon: 'translate', title: 'Language' }, html`
                <div class="row">${this.renderLanguage(event)}</div>
            `);
        }
        return sectionCard({ icon: 'person-vcard', title: 'Contact info' }, html`
            <div class="form-stack">
                <div class="row">
                    <sl-input id="contact-first-name" label="First name" required .value=${this.contact.firstName}
                              @sl-input=${(e: Event) => this.updateContact({ firstName: (e.target as SlInput).value })}></sl-input>
                    <sl-input id="contact-last-name" label="Last name" required .value=${this.contact.lastName}
                              @sl-input=${(e: Event) => this.updateContact({ lastName: (e.target as SlInput).value })}></sl-input>
                </div>
                <div class="row">
                    <sl-input id="contact-email" label="Email address" type="email" required .value=${this.contact.emailAddress}
                              @sl-input=${(e: Event) => this.updateContact({ emailAddress: (e.target as SlInput).value })}>
                        <sl-icon slot="prefix" name="envelope"></sl-icon>
                    </sl-input>
                    ${when(multipleLanguages, () => this.renderLanguage(event))}
                </div>
            </div>
        `);
    }

    private renderLanguage(event: AlfioEvent): TemplateResult {
        return html`
            <sl-select id="language" label="Language" placeholder="Select a language" required hoist .value=${this.language}
                       @sl-change=${(e: Event) => { this.language = String((e.target as SlSelect).value); }}>
                ${event.contentLanguages.map(language => html`<sl-option value=${language.locale}>${language.displayLanguage}</sl-option>`)}
            </sl-select>
        `;
    }

    private renderTickets(event: AlfioEvent, tickets: TicketsDraft, index: number): TemplateResult {
        let actions: TemplateResult | undefined;
        if (index > 0) {
            actions = html`
                <sl-button class="remove-category" variant="default" size="small" @click=${() => this.removeTickets(tickets)}>
                    <sl-icon slot="prefix" name="trash"></sl-icon>
                    Remove
                </sl-button>
            `;
        }
        return sectionCard({
            icon: 'ticket-perforated',
            title: `Tickets for ${categoryDescription(this.categories, tickets, index)}`,
            badge: html`<sl-badge class="attendees-count" variant="primary" pill>${tickets.attendees.length}</sl-badge>`,
            actions,
        }, html`
            <div class="tickets-block" data-index=${index}>
                ${this.renderAttendees(tickets)}
                <sl-divider></sl-divider>
                ${this.renderCategory(event, tickets)}
            </div>
        `);
    }

    private renderAttendees(tickets: TicketsDraft): TemplateResult {
        return choiceCards({
            name: `attendee-strategy-${tickets.key}`,
            className: 'attendee-strategy',
            label: 'How do you want to add the attendees?',
            value: tickets.attendeeStrategy,
            options: ATTENDEE_STRATEGIES,
            panel: html`
                ${when(tickets.csvMessage, () => html`
                    <sl-alert class="csv-message" variant="primary" open closable
                              @sl-after-hide=${() => this.updateTickets(tickets, { csvMessage: null })}>
                        <sl-icon slot="icon" name="info-circle"></sl-icon>
                        ${tickets.csvMessage}
                    </sl-alert>
                `)}
                ${this.renderAttendeesInput(tickets)}
            `,
        }, attendeeStrategy => this.changeAttendeeStrategy(tickets, attendeeStrategy));
    }

    private renderAttendeesInput(tickets: TicketsDraft): TemplateResult {
        if (tickets.attendeeStrategy === 'csv') {
            return this.renderCsvUpload(tickets);
        }
        if (tickets.attendeeStrategy === 'empty') {
            return html`
                <div class="row">
                    <sl-input class="seats" label="Number of seats" type="number" min="1" step="1" required .value=${tickets.seats}
                              @sl-input=${(e: Event) => this.changeSeats(tickets, (e.target as SlInput).value)}>
                        <sl-icon slot="prefix" name="person-dash"></sl-icon>
                    </sl-input>
                </div>
            `;
        }
        return html`
            <div class="attendees">
                ${repeat(tickets.attendees, (_, index) => index, (attendee, index) => this.renderAttendee(tickets, attendee, index))}
                <sl-button class="add-attendee" variant="text" @click=${() => this.addAttendee(tickets)}>
                    <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                    Add attendee
                </sl-button>
            </div>
        `;
    }

    private renderAttendee(tickets: TicketsDraft, attendee: ReservationAttendee, index: number): TemplateResult {
        const number = index + 1;
        // the labels of the first row act as column headers
        let labelClass = '';
        if (index > 0) {
            labelClass = 'label-hidden';
        }
        const update = (change: Partial<ReservationAttendee>) => this.updateAttendee(tickets, index, change);
        return html`
            <div class="row custom attendee">
                <span class="attendee-number">#${number}</span>
                <sl-input class="attendee-first-name ${labelClass}" label="First name" required .value=${attendee.firstName}
                          @sl-input=${(e: Event) => update({ firstName: (e.target as SlInput).value })}></sl-input>
                <sl-input class="attendee-last-name ${labelClass}" label="Last name" required .value=${attendee.lastName}
                          @sl-input=${(e: Event) => update({ lastName: (e.target as SlInput).value })}></sl-input>
                <sl-input class="attendee-email ${labelClass}" label="Email" type="email" required .value=${attendee.emailAddress}
                          @sl-input=${(e: Event) => update({ emailAddress: (e.target as SlInput).value })}></sl-input>
                <sl-icon-button class="remove-attendee" name="trash" label=${`Remove attendee ${number}`}
                                ?disabled=${tickets.attendees.length === 1}
                                @click=${() => this.removeAttendee(tickets, index)}></sl-icon-button>
            </div>
        `;
    }

    private renderCsvUpload(tickets: TicketsDraft): TemplateResult {
        return html`
            <div class="bulk-specs">
                <p class="text-muted">
                    A CSV file <strong>without header</strong>, using commas (<code>,</code>) as separator,
                    double quotes (<code>"</code>) as quote character and backslash (<code>\\</code>) as escape character.
                    Each row describes an attendee, who will receive the ticket at the given e-mail address:
                </p>
                <pre><span class="text-success">FirstName</span>,<span class="text-success">LastName</span>,<span class="text-success">email@host.tld</span></pre>
            </div>
            <alfio-file-upload class="attendees-file" accept=".csv,text/csv" label="Choose a CSV file or drag it here"
                               @change=${(e: FileUploadChangeEvent) => this.onFileChange(tickets, e)}></alfio-file-upload>
            ${when(tickets.csvError, () => html`
                <sl-alert class="csv-error" variant="danger" open>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${tickets.csvError}
                </sl-alert>
            `)}
        `;
    }

    private renderCategory(event: AlfioEvent, tickets: TicketsDraft): TemplateResult {
        return choiceCards({
            name: `category-type-${tickets.key}`,
            className: 'category-type',
            label: 'Assign the attendees to',
            value: tickets.categoryType,
            options: CATEGORY_TYPE_CHOICES,
            panel: html`
                ${when(tickets.categoryType === 'existing',
                    () => ticketCategorySelect(this.categories, {
                        className: 'existing-category',
                        value: tickets.existingCategoryId,
                        onChange: existingCategoryId => this.updateTickets(tickets, { existingCategoryId }),
                        onRefresh: () => this.refreshCategories(event),
                    }),
                    () => this.renderNewCategory(event, tickets))}
                <sl-switch class="add-seats" .checked=${tickets.addSeatsIfNotAvailable}
                           help-text="Increases the number of seats of the event if there aren't enough. Otherwise, the reservation is not created."
                           @sl-change=${(e: Event) => this.updateTickets(tickets, { addSeatsIfNotAvailable: (e.target as SlSwitch).checked })}>
                    Add extra seats if needed
                </sl-switch>
                ${when(tickets.addSeatsIfNotAvailable && tickets.categoryType === 'existing', () => html`
                    <sl-alert variant="warning" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        If you add seats to a public category and the reservation expires, the added seats will be released to the public.
                    </sl-alert>
                `)}
            `,
        }, categoryType => this.updateTickets(tickets, { categoryType }));
    }

    private renderNewCategory(event: AlfioEvent, tickets: TicketsDraft): TemplateResult {
        const hybrid = event.format === 'HYBRID';
        let columns = 1;
        if (hybrid) {
            columns++;
        }
        if (!event.freeOfCharge) {
            columns += 2;
        }
        return html`
            <div class="row" style="--alfio-row-cols: ${columns}">
                <sl-input class="new-category-name" label="Name" required .value=${tickets.name}
                          @sl-input=${(e: Event) => this.updateTickets(tickets, { name: (e.target as SlInput).value })}></sl-input>
                ${when(hybrid, () => html`
                    <sl-select class="ticket-access-type" label="Ticket holders can access the event" placeholder="Select" required hoist
                               .value=${tickets.ticketAccessType}
                               @sl-change=${(e: Event) => this.updateTickets(tickets, { ticketAccessType: String((e.target as SlSelect).value) })}>
                        ${TICKET_ACCESS_TYPES.map(type => html`<sl-option value=${type.value}>${type.label}</sl-option>`)}
                    </sl-select>
                `)}
                ${when(!event.freeOfCharge, () => this.renderPrice(event, tickets))}
            </div>
        `;
    }

    private renderPrice(event: AlfioEvent, tickets: TicketsDraft): TemplateResult {
        return html`
            <sl-input class="new-category-price" label="Ticket price" type="number" min="0" step="0.01" required .value=${tickets.price}
                      @sl-input=${(e: Event) => this.updateTickets(tickets, { price: (e.target as SlInput).value })}>
                <span slot="suffix">${event.currency}</span>
            </sl-input>
            <sl-input class="final-price" label="Final ticket price" readonly help-text=${vatDescription(event)}
                     .value=${formatFinalPrice(event, tickets.price)}></sl-input>
        `;
    }

    private reset(event: AlfioEvent): void {
        this.contact = emptyAttendee();
        this.language = '';
        if (event.contentLanguages.length === 1) {
            this.language = event.contentLanguages[0].locale;
        }
        this.tickets = [this.newTickets()];
        this.submitError = null;
    }

    private newTickets(): TicketsDraft {
        return {
            key: this.nextKey++,
            attendeeStrategy: 'manual',
            attendees: [emptyAttendee()],
            seats: '1',
            csvMessage: null,
            csvError: null,
            categoryType: 'existing',
            existingCategoryId: '',
            name: '',
            price: '',
            ticketAccessType: '',
            addSeatsIfNotAvailable: false,
        };
    }

    private async refreshCategories(event: AlfioEvent): Promise<void> {
        await this.categories.refresh(event.shortName);
        // a category could have been removed in the meantime
        this.tickets = this.tickets.map(tickets => {
            if (tickets.existingCategoryId !== '' && !this.categories.contains(tickets.existingCategoryId)) {
                return { ...tickets, existingCategoryId: '' };
            }
            return tickets;
        });
    }

    private addTickets = (): void => {
        this.tickets = [...this.tickets, this.newTickets()];
    };

    private removeTickets(tickets: TicketsDraft): void {
        this.tickets = this.tickets.filter(t => t !== tickets);
    }

    private updateTickets(tickets: TicketsDraft, change: Partial<TicketsDraft>): void {
        this.tickets = this.tickets.map(t => {
            if (t === tickets) {
                return { ...t, ...change };
            }
            return t;
        });
    }

    private changeAttendeeStrategy(tickets: TicketsDraft, attendeeStrategy: AttendeeStrategy): void {
        const change: Partial<TicketsDraft> = { attendeeStrategy, csvError: null, csvMessage: null };
        if (attendeeStrategy === 'empty') {
            change.seats = String(tickets.attendees.length);
        }
        this.updateTickets(tickets, change);
    }

    private changeSeats(tickets: TicketsDraft, seats: string): void {
        const requested = Number.parseInt(seats, 10);
        if (Number.isNaN(requested) || requested < 1) {
            this.updateTickets(tickets, { seats });
            return;
        }
        // the seats are reserved without the attendees' data
        const attendees = Array.from({ length: requested }, () => emptyAttendee());
        this.updateTickets(tickets, { seats, attendees });
    }

    private addAttendee(tickets: TicketsDraft): void {
        this.updateTickets(tickets, { attendees: [...tickets.attendees, emptyAttendee()] });
    }

    private removeAttendee(tickets: TicketsDraft, index: number): void {
        this.updateTickets(tickets, { attendees: tickets.attendees.filter((_, i) => i !== index) });
    }

    private updateAttendee(tickets: TicketsDraft, index: number, change: Partial<ReservationAttendee>): void {
        const attendees = tickets.attendees.map((attendee, i) => {
            if (i === index) {
                return { ...attendee, ...change };
            }
            return attendee;
        });
        this.updateTickets(tickets, { attendees });
    }

    private updateContact(change: Partial<ReservationAttendee>): void {
        this.contact = { ...this.contact, ...change };
    }

    private async onFileChange(tickets: TicketsDraft, e: FileUploadChangeEvent): Promise<void> {
        const file = e.detail.file;
        if (file == null) {
            this.updateTickets(tickets, { csvError: null });
            return;
        }
        try {
            const parsed = AdminReservationService.parseAttendees(await file.text());
            if (parsed.attendees.length === 0) {
                this.updateTickets(tickets, { csvError: 'The file does not contain any attendee.' });
                return;
            }
            let imported = `${parsed.attendees.length} attendees have`;
            if (parsed.attendees.length === 1) {
                imported = '1 attendee has';
            }
            let csvMessage = `${imported} been imported from ${file.name}. Please review them before saving.`;
            if (parsed.skippedRows === 1) {
                csvMessage += ' 1 row has been skipped, because it doesn\'t contain first name, last name and e-mail.';
            } else if (parsed.skippedRows > 1) {
                csvMessage += ` ${parsed.skippedRows} rows have been skipped, because they don't contain first name, last name and e-mail.`;
            }
            // the imported attendees can be reviewed and edited as if they had been inserted manually
            this.updateTickets(tickets, { attendees: parsed.attendees, attendeeStrategy: 'manual', csvError: null, csvMessage });
        } catch (error) {
            this.updateTickets(tickets, { csvError: errorMessage(error, 'The file cannot be read.') });
        }
    }

    private cancel = (): void => {
        this.dispatchEvent(new CustomEvent('alfio-reservation-create-cancelled', { bubbles: true, composed: true }));
    };

    private async submit(e: Event, event: AlfioEvent): Promise<void> {
        e.preventDefault();
        if (this.submitting) {
            return;
        }
        const missingCsv = this.tickets.findIndex(tickets => tickets.attendeeStrategy === 'csv');
        if (missingCsv >= 0) {
            this.submitError = `Please upload a CSV file for ${categoryDescription(this.categories, this.tickets[missingCsv], missingCsv)}, or choose another way to add the attendees.`;
            return;
        }
        this.submitting = true;
        this.submitError = null;
        try {
            const reservationId = await AdminReservationService.create(event.shortName, this.buildRequest(event));
            this.onCreated({ reservationId, eventName: event.shortName });
        } catch (error) {
            this.submitError = errorMessage(error, 'Unexpected error. Please retry and/or check the logs.');
        } finally {
            this.submitting = false;
        }
    }

    private onCreated(detail: ReservationCreatedDetail): void {
        if (this.fastCreation) {
            this.dispatchEvent(new CustomEvent<ReservationCreatedDetail>('alfio-reservation-created', {
                detail,
                bubbles: true,
                composed: true,
            }));
            return;
        }
        window.location.hash = `#/events/${detail.eventName}/reservation/${detail.reservationId}?fromCreation=true`;
    }

    private buildRequest(event: AlfioEvent): AdminReservationRequest {
        let customerData = this.contact;
        if (this.fastCreation) {
            customerData = { ...this.tickets[0].attendees[0] };
        }
        return {
            expiration: reservationExpiration(event),
            customerData,
            language: this.language,
            ticketsInfo: this.tickets.map(tickets => ({
                category: buildCategory(event, tickets),
                attendees: tickets.attendees,
                addSeatsIfNotAvailable: tickets.addSeatsIfNotAvailable,
            })),
        };
    }
}

function emptyAttendee(): ReservationAttendee {
    return { firstName: '', lastName: '', emailAddress: '' };
}

function categoryDescription(categories: TicketCategoriesController, tickets: TicketsDraft, index: number): string {
    const fallback = `category #${index + 1}`;
    if (tickets.categoryType === 'existing') {
        const category = categories.find(tickets.existingCategoryId);
        return category?.name ?? fallback;
    }
    return tickets.name.trim() || fallback;
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-reservation-create': ReservationCreate;
    }
    interface GlobalEventHandlersEventMap {
        'alfio-reservation-created': CustomEvent<ReservationCreatedDetail>;
        'alfio-reservation-create-cancelled': CustomEvent<void>;
    }
}
