import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
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
    CHECKBOX_OPTIONS_SEPARATOR,
    RESERVATION_ATTENDEE_COLUMNS,
    reservationExpiration,
    formatFinalPrice,
    vatDescription,
} from '../../service/admin-reservation.ts';
import {downloadCsv, errorMessage} from '../../service/helpers.ts';
import {AttendeesImportService} from '../../service/attendees-import.ts';
import {AdditionalFieldService} from '../../service/additional-field.ts';
import {UtilService} from '../../service/util.ts';
import {AdditionalField, allowedValues} from '../../model/additional-field.ts';
import {PromoCodeDiscount} from '../../model/promo-code.ts';
import {discountDescription, PromoCodeService} from '../../service/promo-code.ts';
import {localizedTitle} from '../../service/reservation-format.ts';
import {
    attendeeFields as attendeeFieldsStyles,
    badges,
    base,
    choiceCards as choiceCardsStyles,
    csvImport as csvImportStyles,
    form,
    modernLayout,
    purchaseContextListPage,
    retroCompat,
    row,
    textColors,
} from '../../styles.ts';
import {attendeeFields, attendeeFieldsOfAnyCategory, Country, fieldsForCategory, valuesForFields} from '../../components/attendee-fields.ts';
import {attendeeFieldsLink, csvImport} from '../../components/csv-import.ts';
import {sectionCard} from '../../components/section-card.ts';
import {TicketCategoriesController, ticketCategorySelect} from '../../components/ticket-category-select.ts';
import {ChoiceCardOption, choiceCards} from '../../components/choice-cards.ts';
import {taskContent} from '../../components/task-content.ts';
import {FileUploadChangeEvent} from '../../components/file-upload.ts';
import {
    BoundField,
    emailAddress,
    fieldError,
    requiredValue,
    ValidatedFieldMeta,
    validatedInput,
    ValidatedInputOptions,
    validateWith,
    FormMountController,
    submitValidated,
    focusFirstError,
    INVALID_FORM_MESSAGE,
} from '../../components/form-validation.ts';

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
    // the additional fields of the attendees are displayed
    showFields: boolean;
}

/**
 * The values of the form, managed by TanStackFormController
 */
interface ReservationFormValues {
    contact: ReservationAttendee;
    language: string;
    tickets: TicketsDraft[];
    // ID of the selected discount code, empty for none
    discountId: string;
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
        description: 'Import the attendees and their data from a file, then review them before saving.',
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
    // additional fields of the event
    @state() private fields: readonly AdditionalField[] = [];
    // for the "country" additional fields
    @state() private countries: readonly Country[] = [];
    private readonly categories = new TicketCategoriesController(this);
    // discount codes that can be applied, see renderDiscount()
    @state() private discounts: readonly PromoCodeDiscount[] = [];
    @state() private discountsError: string | null = null;
    @state() private refreshingDiscounts = false;
    @state() private submitting = false;
    @state() private submitError: string | null = null;
    private nextKey = 0;

    // each validator checks first whether its control applies: a field keeps its validators after its control has
    // been hidden (e.g. the names of the attendees, after choosing "Reserve empty seats")
    readonly #form = new TanStackFormController(this, {
        defaultValues: {
            contact: emptyAttendee(),
            language: '',
            tickets: [],
            discountId: '',
        } as ReservationFormValues,
        onSubmit: async ({value}) => {
            await this.save(value);
        },
    });

    constructor() {
        super();
        // see FormMountController: e.g. in "fast creation" mode, the first fields appear only after the reset
        new FormMountController(this, this.#form.api);
    }

    private get tickets(): TicketsDraft[] {
        return this.#form.api.state.values.tickets;
    }

    private get language(): string {
        return this.#form.api.state.values.language;
    }

    private get discountId(): string {
        return this.#form.api.state.values.discountId;
    }

    private readonly loadEventTask = new Task(this, {
        task: async ([eventName]): Promise<AlfioEvent> => {
            const { event } = await EventService.load(eventName);
            this.categories.set(event.ticketCategories);
            await Promise.all([this.loadFields(event), this.loadDiscounts(event)]);
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
        attendeeFieldsStyles,
        csvImportStyles,
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
            .attendee:first-child sl-icon-button,
            .attendee.with-fields .attendee-number,
            .attendee.with-fields sl-icon-button {
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
            /* each attendee is a block with its own labels: the additional fields follow its inputs */
            .attendee.with-fields + .attendee.with-fields {
                margin-top: var(--sl-spacing-small);
                padding-top: var(--sl-spacing-medium);
                border-top: 1px solid var(--sl-color-gray-200);
            }
            .attendee .attendee-fields {
                align-items: end;
            }
            @media (min-width: 768px) {
                /* on the columns of first name, last name and e-mail */
                .attendee .attendee-fields {
                    grid-column: 2 / 5;
                    grid-template-columns: subgrid;
                }
            }
            .no-discounts {
                margin: 0;
            }
            .discount-help {
                margin-top: var(--sl-input-spacing-small);
                font-size: var(--sl-input-help-text-font-size-medium);
                color: var(--sl-input-help-text-color);
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
                <form novalidate @submit=${(e: Event) => this.submit(e)}>
                    ${this.renderContact(event)}
                    <!-- keyed by index: the names of the validated fields contain the index of the tickets -->
                    ${repeat(this.tickets, (_, index) => index, (tickets, index) => this.renderTickets(event, tickets, index))}
                    <sl-button id="add-category" class="section-card-add" variant="default" size="large" @click=${this.addTickets}>
                        <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                        Add tickets for another category
                    </sl-button>
                    ${when(!event.freeOfCharge, () => this.renderDiscount(event))}
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
                                Save
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
                    Cancel
                </sl-button>
            `;
        }
        return html`
            <sl-button id="cancel-button" variant="default" size="large" href=${`#/events/${event.shortName}/detail`}>
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
                    ${this.#form.field({ name: 'contact.firstName', validators: validateWith(value => this.ifContact(() => requiredValue(value))) },
                        field => this.textInput(field, { id: 'contact-first-name', label: 'First name' }))}
                    ${this.#form.field({ name: 'contact.lastName', validators: validateWith(value => this.ifContact(() => requiredValue(value))) },
                        field => this.textInput(field, { id: 'contact-last-name', label: 'Last name' }))}
                </div>
                <div class="row">
                    ${this.#form.field({ name: 'contact.emailAddress', validators: validateWith(value => this.ifContact(() => emailAddress(value))) },
                        field => this.textInput(field, { id: 'contact-email', label: 'Email address', type: 'email', prefixIcon: 'envelope' }))}
                    ${when(multipleLanguages, () => this.renderLanguage(event))}
                </div>
            </div>
        `);
    }

    private renderLanguage(event: AlfioEvent): TemplateResult {
        return html`
            ${this.#form.field({ name: 'language', validators: validateWith(value => requiredValue(value)) }, field => {
                const error = this.error(field.state.meta);
                return html`
                    <sl-select id="language" label="Language" placeholder="Select a language" required hoist
                               class=${classMap({ error: error != null })} help-text=${error ?? ''}
                               .value=${field.state.value}
                               @sl-change=${(e: Event) => field.handleChange(String((e.target as SlSelect).value))}
                               @sl-blur=${() => field.handleBlur()}>
                        ${event.contentLanguages.map(language => html`<sl-option value=${language.locale}>${language.displayLanguage}</sl-option>`)}
                    </sl-select>
                `;
            })}
        `;
    }

    private textInput(field: BoundField<string>, options: ValidatedInputOptions): TemplateResult {
        return validatedInput(field, this.#form.api.state.submissionAttempts > 0, options);
    }

    private error(meta: ValidatedFieldMeta): string | undefined {
        return fieldError(meta, this.#form.api.state.submissionAttempts > 0);
    }

    // the contact person is asked only outside "fast creation"
    private ifContact(validate: () => string | undefined): string | undefined {
        if (this.fastCreation) {
            return undefined;
        }
        return validate();
    }

    /**
     * Runs the validation only if the control still applies to the tickets
     */
    private ifTickets(index: number, applies: (tickets: TicketsDraft) => boolean, validate: () => string | undefined): string | undefined {
        const tickets = this.tickets[index];
        if (tickets == null || !applies(tickets)) {
            return undefined;
        }
        return validate();
    }

    private renderTickets(event: AlfioEvent, tickets: TicketsDraft, index: number): TemplateResult {
        let actions: TemplateResult | undefined;
        if (index > 0) {
            actions = html`
                <sl-button class="remove-category" variant="default" size="small" @click=${() => this.removeTickets(index)}>
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
                ${this.renderAttendees(event, tickets, index)}
                <sl-divider></sl-divider>
                ${this.renderCategory(event, tickets, index)}
            </div>
        `);
    }

    private renderAttendees(event: AlfioEvent, tickets: TicketsDraft, index: number): TemplateResult {
        // "Upload CSV" is complete only once the file has been read: the attendees are then shown as inserted manually
        const validateStrategy = (value: unknown) => {
            if (value === 'csv') {
                return 'Please upload a CSV file, or choose another way to add the attendees';
            }
            return undefined;
        };
        return html`${this.#form.field({ name: `tickets[${index}].attendeeStrategy`, validators: validateWith(validateStrategy) }, field => choiceCards({
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
                ${this.renderAttendeesInput(event, tickets, index, this.error(field.state.meta))}
            `,
        }, attendeeStrategy => this.changeAttendeeStrategy(tickets, attendeeStrategy, field)))}`;
    }

    private renderAttendeesInput(event: AlfioEvent, tickets: TicketsDraft, index: number, strategyError: string | undefined): TemplateResult {
        if (tickets.attendeeStrategy === 'csv') {
            return this.renderCsvUpload(event, tickets, strategyError);
        }
        if (tickets.attendeeStrategy === 'empty') {
            const validateSeats = (value: unknown) => this.ifTickets(index, t => t.attendeeStrategy === 'empty', () => {
                const seats = Number(value);
                if (String(value ?? '').trim() === '' || !Number.isInteger(seats) || seats < 1) {
                    return 'Please enter a number of seats, at least 1';
                }
                return undefined;
            });
            return html`
                <div class="row">
                    ${this.#form.field({ name: `tickets[${index}].seats`, validators: validateWith(validateSeats) }, field => {
                        const error = this.error(field.state.meta);
                        return html`
                            <sl-input class=${classMap({ seats: true, error: error != null })} label="Number of seats" type="number" min="1" step="1" required
                                      help-text=${error ?? ''}
                                      .value=${field.state.value}
                                      @sl-input=${(e: Event) => this.changeSeats(tickets, (e.target as SlInput).value, field)}
                                      @sl-blur=${() => field.handleBlur()}>
                                <sl-icon slot="prefix" name="person-dash"></sl-icon>
                            </sl-input>
                        `;
                    })}
                </div>
            `;
        }
        const fields = this.fieldsFor(tickets);
        const notCollected = notCollectedFields(tickets.attendees, fields);
        return html`
            ${when(notCollected.length > 0, () => html`
                <sl-alert class="fields-not-collected" variant="primary" open>
                    <sl-icon slot="icon" name="info-circle"></sl-icon>
                    ${this.notCollectedMessage(tickets, notCollected)}
                </sl-alert>
            `)}
            ${when(fields.length > 0, () => html`
                <sl-switch class="show-fields" .checked=${tickets.showFields}
                           help-text="Fields left empty can be filled in later by the attendees."
                           @sl-change=${(e: Event) => this.updateTickets(tickets, { showFields: (e.target as SlSwitch).checked })}>
                    Fill in the additional fields
                </sl-switch>
            `)}
            <div class="attendees">
                ${repeat(tickets.attendees, (_, attendeeIndex) => attendeeIndex, (attendee, attendeeIndex) => this.renderAttendee(tickets, index, attendee, attendeeIndex, fields))}
                <sl-button class="add-attendee" variant="text" @click=${() => this.addAttendee(tickets)}>
                    <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                    Add attendee
                </sl-button>
            </div>
        `;
    }

    private renderAttendee(tickets: TicketsDraft, ticketsIndex: number, attendee: ReservationAttendee, index: number,
                           fields: readonly AdditionalField[]): TemplateResult {
        const number = index + 1;
        const showFields = tickets.showFields && fields.length > 0;
        // the labels of the first row act as column headers. With the additional fields, each attendee is a block
        // with its own labels
        let labelClass = '';
        if (index > 0 && !showFields) {
            labelClass = 'label-hidden';
        }
        let rowClass = 'row custom attendee';
        if (showFields) {
            rowClass += ' with-fields';
        }
        const update = (change: Partial<ReservationAttendee>) => this.updateAttendee(tickets, index, change);
        // only the attendees inserted manually (or read from a CSV file) are checked
        const ifAttendee = (validate: () => string | undefined) =>
            this.ifTickets(ticketsIndex, t => t.attendeeStrategy === 'manual' && index < t.attendees.length, validate);
        const path = `tickets[${ticketsIndex}].attendees[${index}]` as const;
        return html`
            <div class=${rowClass}>
                <span class="attendee-number">#${number}</span>
                ${this.#form.field({ name: `${path}.firstName`, validators: validateWith(value => ifAttendee(() => requiredValue(value))) },
                    field => this.textInput(field, { className: `attendee-first-name ${labelClass}`.trim(), label: 'First name' }))}
                ${this.#form.field({ name: `${path}.lastName`, validators: validateWith(value => ifAttendee(() => requiredValue(value))) },
                    field => this.textInput(field, { className: `attendee-last-name ${labelClass}`.trim(), label: 'Last name' }))}
                ${this.#form.field({ name: `${path}.emailAddress`, validators: validateWith(value => ifAttendee(() => emailAddress(value))) },
                    field => this.textInput(field, { className: `attendee-email ${labelClass}`.trim(), label: 'Email', type: 'email' }))}
                <sl-icon-button class="remove-attendee" name="trash" label=${`Remove attendee ${number}`}
                                ?disabled=${tickets.attendees.length === 1}
                                @click=${() => this.removeAttendee(tickets, index)}></sl-icon-button>
                ${when(showFields, () => attendeeFields({
                    fields,
                    values: attendee.additionalInfo ?? {},
                    countries: this.countries,
                    language: this.labelsLanguage(),
                }, additionalInfo => update({ additionalInfo })))}
            </div>
        `;
    }

    private renderCsvUpload(event: AlfioEvent, tickets: TicketsDraft, missingFileError: string | undefined): TemplateResult {
        const fields = this.templateFieldsFor(tickets);
        return csvImport({
            templateDescription: this.templateDescription(event, tickets, fields),
            onDownloadTemplate: () => downloadCsv(`${event.shortName}-reservation-attendees.csv`, AdminReservationService.attendeesTemplate(fields)),
            onFileChange: (e: FileUploadChangeEvent) => this.onFileChange(tickets, e),
            error: tickets.csvError ?? missingFileError ?? null,
            specs: this.renderFileSpecs(event, fields),
        });
    }

    private templateDescription(event: AlfioEvent, tickets: TicketsDraft, fields: readonly AdditionalField[]): TemplateResult {
        if (fields.length === 0) {
            return html`It has a column for the first name, the last name and the e-mail of the attendees.`;
        }
        if (this.selectedCategoryId(tickets) == null) {
            return html`It has a column for each of the ${attendeeFieldsLink(event.shortName)} of this event. Select the category first to get only its columns.`;
        }
        return html`It has a column for each of the ${attendeeFieldsLink(event.shortName)} of the category.`;
    }

    private renderFileSpecs(event: AlfioEvent, fields: readonly AdditionalField[]): TemplateResult {
        const fieldNames = fields.map(field => field.name);
        const fieldsWithOptions = fields.filter(field => allowedValues(field).length > 0);
        const checkboxes = fieldsWithOptions.filter(field => field.type === 'checkbox');
        const countries = fields.filter(field => field.type === 'country');
        const dates = fields.filter(field => field.type === 'input:dateOfBirth');
        return html`
            <div class="bulk-specs">
                <p class="text-muted">
                    The template already follows these rules: you need them only if your file is generated by another system.
                </p>
                <p class="text-muted">
                    A CSV file <strong>with header</strong>, using commas (<code>,</code>) as separator and double quotes (<code>"</code>) as quote character.
                    Double quotes within a value are written twice (<code>""</code>).
                    The rows without first name, last name or e-mail are skipped.
                </p>
                <pre><span class="template-header">${RESERVATION_ATTENDEE_COLUMNS.map((column, i) => html`${when(i > 0, () => ',')}<span class="text-success">${AttendeesImportService.quoteColumn(column)}</span>`)}${fieldNames.map(name => `,${AttendeesImportService.quoteColumn(name)}`)}</span>

${RESERVATION_ATTENDEE_COLUMNS.join(', ')}: required, in this order
${when(fieldNames.length > 0,
    () => html`${fieldNames.join(', ')}: optional, the ${attendeeFieldsLink(event.shortName)}${fieldsWithOptions.map(field => html`
${field.name}: one of ${allowedValues(field).join(', ')}`)}${checkboxes.map(field => html`
${field.name}: more options separated by ${CHECKBOX_OPTIONS_SEPARATOR}`)}${countries.map(field => html`
${field.name}: the ISO code of the country, e.g. CH`)}${dates.map(field => html`
${field.name}: a date, as YYYY-MM-DD`)}`,
    () => html`There are no ${attendeeFieldsLink(event.shortName)} for this event`)}</pre>
            </div>
        `;
    }

    private renderDiscount(event: AlfioEvent): TemplateResult {
        const selected = this.discounts.find(discount => String(discount.id) === this.discountId);
        const promoCodesLink = html`<a href=${`#/events/${event.shortName}/promo-codes`}>promo codes</a>`;
        return sectionCard({ icon: 'percent', title: 'Discount' }, html`
            <div class="form-stack">
                <div>
                    <div class="input-with-action">
                        <sl-select id="discount-code" label="Discount code" placeholder="No discount" clearable hoist
                                   aria-describedby="discount-help" ?disabled=${this.discounts.length === 0}
                                   .value=${this.discountId}
                                   @sl-change=${(e: Event) => this.#form.api.setFieldValue('discountId', String((e.target as SlSelect).value))}>
                            ${repeat(this.discounts, discount => discount.id, discount => html`
                                <sl-option value=${String(discount.id)}>${discount.promoCode} (${discountDescription(discount, event.currency)})</sl-option>
                            `)}
                        </sl-select>
                        <sl-button id="refresh-discounts" variant="default" ?loading=${this.refreshingDiscounts} @click=${() => this.refreshDiscounts(event)}>
                            <sl-icon slot="prefix" name="arrow-clockwise"></sl-icon>
                            Refresh
                        </sl-button>
                    </div>
                    <!-- below the row, not as help-text: the button stays aligned with the select -->
                    <div id="discount-help" class="discount-help">${this.discountHelp(event, selected)}</div>
                </div>
                ${when(this.discountsError, () => html`
                    <sl-alert variant="danger" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.discountsError}
                    </sl-alert>
                `)}
                ${when(this.discounts.length === 0 && this.discountsError == null, () => html`
                    <p class="text-muted no-discounts">There are no discount codes valid now. You can create them in the ${promoCodesLink} of the event.</p>
                `)}
            </div>
        `);
    }

    private discountHelp(event: AlfioEvent, discount: PromoCodeDiscount | undefined): string {
        if (discount == null) {
            return 'Discount codes of the event and of its organization, valid now';
        }
        let help = discount.description || discountDescription(discount, event.currency);
        if (discount.categories.length > 0) {
            const names = discount.categories.map(id => this.categories.find(String(id))?.name ?? `#${id}`);
            help += `. Applies only to the tickets of: ${names.join(', ')}`;
        }
        return help;
    }

    private renderCategory(event: AlfioEvent, tickets: TicketsDraft, index: number): TemplateResult {
        const validateCategory = (value: unknown) => this.ifTickets(index, t => t.categoryType === 'existing', () => {
            if (requiredValue(value) != null) {
                return 'Please select a category';
            }
            return undefined;
        });
        return choiceCards({
            name: `category-type-${tickets.key}`,
            className: 'category-type',
            label: 'Assign the attendees to',
            value: tickets.categoryType,
            options: CATEGORY_TYPE_CHOICES,
            panel: html`
                ${when(tickets.categoryType === 'existing',
                    () => this.#form.field({ name: `tickets[${index}].existingCategoryId`, validators: validateWith(validateCategory) },
                        field => ticketCategorySelect(this.categories, {
                            className: 'existing-category',
                            value: field.state.value,
                            error: this.error(field.state.meta),
                            onChange: existingCategoryId => field.handleChange(existingCategoryId),
                            onBlur: () => field.handleBlur(),
                            onRefresh: () => this.refreshCategories(event),
                        })),
                    () => this.renderNewCategory(event, tickets, index))}
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

    private renderNewCategory(event: AlfioEvent, tickets: TicketsDraft, index: number): TemplateResult {
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
                ${this.#form.field({ name: `tickets[${index}].name`, validators: validateWith(value => this.ifNewCategory(index, () => requiredValue(value))) },
                    field => this.textInput(field, { className: 'new-category-name', label: 'Name' }))}
                ${when(hybrid, () => this.#form.field({
                    name: `tickets[${index}].ticketAccessType`,
                    validators: validateWith(value => this.ifNewCategory(index, () => requiredValue(value))),
                }, field => {
                    const error = this.error(field.state.meta);
                    return html`
                        <sl-select class=${classMap({ 'ticket-access-type': true, error: error != null })} label="Ticket holders can access the event"
                                   placeholder="Select" required hoist help-text=${error ?? ''}
                                   .value=${field.state.value}
                                   @sl-change=${(e: Event) => field.handleChange(String((e.target as SlSelect).value))}
                                   @sl-blur=${() => field.handleBlur()}>
                            ${TICKET_ACCESS_TYPES.map(type => html`<sl-option value=${type.value}>${type.label}</sl-option>`)}
                        </sl-select>
                    `;
                }))}
                ${when(!event.freeOfCharge, () => this.renderPrice(event, tickets, index))}
            </div>
        `;
    }

    private renderPrice(event: AlfioEvent, tickets: TicketsDraft, index: number): TemplateResult {
        const validatePrice = (value: unknown) => this.ifNewCategory(index, () => {
            const price = Number(value);
            if (String(value ?? '').trim() === '' || Number.isNaN(price) || price < 0) {
                return 'Please enter a price, 0 or more';
            }
            return undefined;
        });
        return html`
            ${this.#form.field({ name: `tickets[${index}].price`, validators: validateWith(validatePrice) }, field => {
                const error = this.error(field.state.meta);
                return html`
                    <sl-input class=${classMap({ 'new-category-price': true, error: error != null })} label="Ticket price" type="number" min="0" step="0.01" required
                              help-text=${error ?? ''}
                              .value=${field.state.value}
                              @sl-input=${(e: Event) => field.handleChange((e.target as SlInput).value)}
                              @sl-blur=${() => field.handleBlur()}>
                        <span slot="suffix">${event.currency}</span>
                    </sl-input>
                `;
            })}
            <sl-input class="final-price" label="Final ticket price" readonly help-text=${vatDescription(event)}
                     .value=${formatFinalPrice(event, tickets.price)}></sl-input>
        `;
    }

    private reset(event: AlfioEvent): void {
        let language = '';
        if (event.contentLanguages.length === 1) {
            language = event.contentLanguages[0].locale;
        }
        const values: ReservationFormValues = {
            contact: emptyAttendee(),
            language,
            tickets: [this.newTickets()],
            discountId: '',
        };
        this.#form.api.update({ ...this.#form.api.options, defaultValues: values });
        this.#form.api.reset(values);
        this.submitError = null;
    }

    private ifNewCategory(index: number, validate: () => string | undefined): string | undefined {
        return this.ifTickets(index, t => t.categoryType === 'new', validate);
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
            showFields: false,
        };
    }

    /**
     * Loads the additional fields of the event. Without them, the attendees' additional fields can't be filled in.
     */
    private async loadFields(event: AlfioEvent): Promise<void> {
        try {
            this.fields = await AdditionalFieldService.loadAllByPurchaseContext(event);
        } catch (error) {
            console.error('cannot load the additional fields', error);
            this.fields = [];
            return;
        }
        if (this.fields.some(field => field.type === 'country')) {
            try {
                this.countries = await UtilService.countries(event.contentLanguages[0]?.locale ?? 'en');
            } catch (error) {
                // the ISO code can be typed instead
                console.error('cannot load the countries', error);
            }
        }
    }

    /**
     * Loads the discount codes. Without them, the reservation can still be created without discount.
     */
    private async loadDiscounts(event: AlfioEvent): Promise<void> {
        if (event.freeOfCharge) {
            return;
        }
        try {
            this.discounts = await PromoCodeService.listApplicableDiscounts(event);
            this.discountsError = null;
        } catch (error) {
            this.discountsError = errorMessage(error, 'The discount codes cannot be loaded.');
        }
        // the selected code could have expired or been removed in the meantime
        if (this.discountId !== '' && !this.discounts.some(discount => String(discount.id) === this.discountId)) {
            this.#form.api.setFieldValue('discountId', '');
        }
    }

    private async refreshDiscounts(event: AlfioEvent): Promise<void> {
        this.refreshingDiscounts = true;
        try {
            await this.loadDiscounts(event);
        } finally {
            this.refreshingDiscounts = false;
        }
    }

    /**
     * The additional fields of the attendees of the category
     */
    private fieldsFor(tickets: TicketsDraft): AdditionalField[] {
        return fieldsForCategory(this.fields, this.selectedCategoryId(tickets));
    }

    /**
     * The columns of the CSV template: the fields of the category, or all of them until an existing category is selected
     */
    private templateFieldsFor(tickets: TicketsDraft): AdditionalField[] {
        let fields = attendeeFieldsOfAnyCategory(this.fields);
        if (this.selectedCategoryId(tickets) != null) {
            fields = this.fieldsFor(tickets);
        }
        return [...fields].sort((a, b) => a.order - b.order);
    }

    private notCollectedMessage(tickets: TicketsDraft, fieldNames: string[]): string {
        const names = fieldNames.join(', ');
        if (this.selectedCategoryId(tickets) == null) {
            return `Select the category of the tickets: the imported values of ${names} are saved only if the category collects them.`;
        }
        return `The category doesn't collect ${names}: the imported values will not be saved.`;
    }

    private selectedCategoryId(tickets: TicketsDraft): number | null {
        if (tickets.categoryType === 'existing' && tickets.existingCategoryId !== '') {
            return Number(tickets.existingCategoryId);
        }
        return null;
    }

    private labelsLanguage(): string {
        return this.language || 'en';
    }

    private async refreshCategories(event: AlfioEvent): Promise<void> {
        await this.categories.refresh(event.shortName);
        // a category could have been removed in the meantime
        this.tickets.forEach((tickets, index) => {
            if (tickets.existingCategoryId !== '' && !this.categories.contains(tickets.existingCategoryId)) {
                this.#form.api.setFieldValue(`tickets[${index}].existingCategoryId`, '');
            }
        });
    }

    private addTickets = (): void => {
        this.#form.api.pushFieldValue('tickets', this.newTickets());
    };

    private removeTickets(index: number): void {
        // moves also the state of the validated fields of the following tickets
        void this.#form.api.removeFieldValue('tickets', index);
    }

    /**
     * The tickets are identified by key: the change may be applied after an asynchronous operation (e.g. reading
     * a file), when their index may have changed
     */
    private updateTickets(tickets: TicketsDraft, change: Partial<TicketsDraft>): void {
        const index = this.indexOf(tickets);
        if (index < 0) {
            return;
        }
        const updated = this.tickets.map((t, i) => {
            if (i === index) {
                return { ...t, ...change };
            }
            return t;
        });
        this.#form.api.setFieldValue('tickets', updated, { dontUpdateMeta: true });
    }

    private indexOf(tickets: TicketsDraft): number {
        return this.tickets.findIndex(t => t.key === tickets.key);
    }

    private changeAttendeeStrategy(tickets: TicketsDraft, attendeeStrategy: AttendeeStrategy, field: BoundField<AttendeeStrategy>): void {
        const change: Partial<TicketsDraft> = { csvError: null, csvMessage: null };
        if (attendeeStrategy === 'empty') {
            change.seats = String(tickets.attendees.length);
        }
        this.updateTickets(tickets, change);
        field.handleChange(attendeeStrategy);
    }

    private changeSeats(tickets: TicketsDraft, seats: string, field: BoundField<string>): void {
        field.handleChange(seats);
        const requested = Number.parseInt(seats, 10);
        if (Number.isNaN(requested) || requested < 1) {
            return;
        }
        // the seats are reserved without the attendees' data
        const attendees = Array.from({ length: requested }, () => emptyAttendee());
        this.updateTickets(tickets, { attendees });
    }

    private addAttendee(tickets: TicketsDraft): void {
        this.#form.api.pushFieldValue(`tickets[${this.indexOf(tickets)}].attendees`, emptyAttendee());
    }

    private removeAttendee(tickets: TicketsDraft, index: number): void {
        // moves also the state of the validated fields of the following attendees
        void this.#form.api.removeFieldValue(`tickets[${this.indexOf(tickets)}].attendees`, index);
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

    private async onFileChange(tickets: TicketsDraft, e: FileUploadChangeEvent): Promise<void> {
        const file = e.detail.file;
        if (file == null) {
            this.updateTickets(tickets, { csvError: null });
            return;
        }
        try {
            // the category can still change: all the fields are accepted, and the ones of other categories are ignored when saving
            const parsed = AdminReservationService.parseAttendees(await file.text(), attendeeFieldsOfAnyCategory(this.fields));
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
            const showFields = tickets.showFields || parsed.additionalFields.length > 0;
            this.updateTickets(tickets, { attendees: parsed.attendees, csvError: null, csvMessage, showFields });
            // validates the field again: the "Upload CSV" error, if any, no longer applies
            this.#form.api.setFieldValue(`tickets[${this.indexOf(tickets)}].attendeeStrategy`, 'manual');
        } catch (error) {
            this.updateTickets(tickets, { csvError: errorMessage(error, 'The file cannot be read.') });
        }
    }

    private cancel = (): void => {
        this.dispatchEvent(new CustomEvent('alfio-reservation-create-cancelled', { bubbles: true, composed: true }));
    };

    private async submit(e: Event): Promise<void> {
        e.preventDefault();
        e.stopImmediatePropagation();
        if (this.submitting) {
            return;
        }
        this.submitError = null;
        if (!await submitValidated(this.#form.api)) {
            this.submitError = INVALID_FORM_MESSAGE;
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    }

    /**
     * Called by the form once all the fields are valid
     */
    private async save(values: ReservationFormValues): Promise<void> {
        const event = this.loadEventTask.value;
        if (event == null) {
            return;
        }
        this.submitting = true;
        try {
            const reservationId = await AdminReservationService.create(event.shortName, this.buildRequest(event, values));
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

    /**
     * The attendees, with the values of the additional fields that apply to their category, if displayed
     */
    private attendeesRequest(tickets: TicketsDraft): ReservationAttendee[] {
        const fields = this.fieldsFor(tickets);
        return tickets.attendees.map(attendee => {
            const { additionalInfo, ...data } = attendee;
            if (!tickets.showFields || fields.length === 0 || additionalInfo == null || tickets.attendeeStrategy !== 'manual') {
                return data;
            }
            return { ...data, additionalInfo: valuesForFields(fields, additionalInfo) };
        });
    }

    private buildRequest(event: AlfioEvent, values: ReservationFormValues): AdminReservationRequest {
        let contact = values.contact;
        if (this.fastCreation) {
            contact = values.tickets[0].attendees[0];
        }
        const customerData = { firstName: contact.firstName.trim(), lastName: contact.lastName.trim(), emailAddress: contact.emailAddress.trim() };
        const discount = this.discounts.find(d => String(d.id) === values.discountId);
        return {
            promoCode: discount?.promoCode,
            expiration: reservationExpiration(event),
            customerData,
            language: values.language,
            ticketsInfo: values.tickets.map(tickets => ({
                category: buildCategory(event, tickets),
                attendees: this.attendeesRequest(tickets),
                addSeatsIfNotAvailable: tickets.addSeatsIfNotAvailable,
            })),
        };
    }
}

/**
 * The fields with a value (e.g. imported from a CSV file) that the category of the tickets doesn't collect
 */
function notCollectedFields(attendees: readonly ReservationAttendee[], fields: readonly AdditionalField[]): string[] {
    const collected = new Set(fields.map(field => field.name));
    const names = new Set<string>();
    for (const attendee of attendees) {
        for (const [name, values] of Object.entries(attendee.additionalInfo ?? {})) {
            if (!collected.has(name) && values.some(value => value != null && value !== '')) {
                names.add(name);
            }
        }
    }
    return [...names];
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
