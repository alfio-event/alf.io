import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
import type {SlSelect, SlSwitch} from '@shoelace-style/shoelace';
import {AlfioEvent} from '../../model/event.ts';
import {AttendeesImportRequest, ParsedAttendees} from '../../model/attendees-import.ts';
import {CategoryType} from '../../model/admin-reservation.ts';
import {AdditionalField, allowedValues} from '../../model/additional-field.ts';
import {EventService} from '../../service/event.ts';
import {AdditionalFieldService} from '../../service/additional-field.ts';
import {ConfigurationService} from '../../service/configuration.ts';
import {AttendeesImportService, REFERENCE_COLUMN, REQUIRED_COLUMNS} from '../../service/attendees-import.ts';
import {localizedTitle} from '../../service/reservation-format.ts';
import {downloadCsv, errorMessage} from '../../service/helpers.ts';
import {
    buildCategory,
    CATEGORY_TYPE_CHOICES,
    reservationExpiration,
    formatFinalPrice,
    vatDescription,
} from '../../service/admin-reservation.ts';
import {badges, base, choiceCards as choiceCardsStyles, csvImport as csvImportStyles, form, modernLayout, modernTable, purchaseContextListPage, retroCompat, row, textColors} from '../../styles.ts';
import {sectionCard} from '../../components/section-card.ts';
import {choiceCards} from '../../components/choice-cards.ts';
import {TicketCategoriesController, ticketCategorySelect} from '../../components/ticket-category-select.ts';
import {taskContent} from '../../components/task-content.ts';
import {FileUploadChangeEvent} from '../../components/file-upload.ts';
import {attendeeFieldsLink, csvImport} from '../../components/csv-import.ts';
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
import { navigate, adminUrl } from '../../admin-router';
import { t, tHtml, formatNumber } from '../../service/i18n';

// the backend refuses a single reservation with more attendees (see AdminReservationRequestManager)
const MAX_ATTENDEES_PER_RESERVATION = 100;
// rendering all the rows of a big file would make the page unresponsive
const PREVIEW_ROWS = 100;

interface LoadData {
    event: AlfioEvent;
    // the attendees' data to collect, which can be imported as additional columns
    fields: AdditionalField[];
}

interface Contact {
    firstName: string;
    lastName: string;
    emailAddress: string;
}

/**
 * The validated values of the form, managed by TanStackFormController. The switches are plain state.
 */
interface ImportFormValues {
    // attendees read from the uploaded file
    attendeesCount: number;
    existingCategoryId: string;
    newCategoryName: string;
    newCategoryPrice: string;
    contact: Contact;
    language: string;
}

/**
 * Imports the attendees listed in a CSV file. The reservations are created in the background:
 * once the request has been accepted, the page moves to the import status.
 */
@customElement('alfio-attendees-import')
export class AttendeesImport extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @state() private parsed: ParsedAttendees | null = null;
    @state() private parseError: string | null = null;
    @state() private categoryType: CategoryType = 'existing';
    @state() private oneReservationPerAttendee = false;
    @state() private forbidReassignment = false;
    @state() private sendTickets = false;
    @state() private addSeats = false;
    @state() private submitting = false;
    @state() private submitError: string | null = null;
    private readonly categories = new TicketCategoriesController(this);

    // each validator checks first whether its control applies (e.g. the contact, for a single reservation):
    // a field keeps its validators after its control has been hidden
    readonly #form = new TanStackFormController(this, {
        defaultValues: {
            attendeesCount: 0,
            existingCategoryId: '',
            newCategoryName: '',
            newCategoryPrice: '',
            contact: { firstName: '', lastName: '', emailAddress: '' },
            language: '',
        } as ImportFormValues,
        onSubmit: async ({value}) => {
            await this.save(value);
        },
    });

    constructor() {
        super();
        // see FormMountController: e.g. in "fast creation" mode, the first fields appear only after the reset
        new FormMountController(this, this.#form.api);
    }

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName]): Promise<LoadData> => {
            const [{ event }, sendTickets, oneReservationPerAttendee] = await Promise.all([
                EventService.load(eventName),
                booleanConfig(eventName, 'SEND_TICKETS_AFTER_IMPORT_ATTENDEE'),
                booleanConfig(eventName, 'CREATE_RESERVATION_FOR_EACH_IMPORTED_ATTENDEE'),
            ]);
            this.categories.set(event.ticketCategories);
            this.sendTickets = sendTickets;
            this.oneReservationPerAttendee = oneReservationPerAttendee;
            this.#form.api.setFieldValue('language', event.contentLanguages[0]?.locale ?? '', { dontUpdateMeta: true });
            const fields = (await AdditionalFieldService.loadAllByPurchaseContext(event))
                .filter(field => field.context === 'ATTENDEE')
                .sort((a, b) => a.order - b.order);
            return { event, fields };
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
        modernTable,
        purchaseContextListPage,
        choiceCardsStyles,
        csvImportStyles,
        css`
            :host {
                --alfio-section-body-padding: var(--sl-spacing-large);
            }
            .file-specs {
                margin-top: var(--sl-spacing-large);
            }
            .preview {
                margin-top: var(--sl-spacing-large);
            }
            .preview caption {
                caption-side: bottom;
                padding-top: var(--sl-spacing-small);
                text-align: start;
            }
            .options sl-switch {
                min-height: 0;
            }
            .footer sl-divider {
                margin-top: 0;
            }
        `,
    ];

    render(): TemplateResult {
        return taskContent(this.loadDataTask, this.loadDataTask.value ?? null, t('admin.load-error.event-retry'),
            data => this.renderContent(data));
    }

    private renderContent({ event, fields }: LoadData): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>${tHtml('admin.attendees-import.title', html`<i>${localizedTitle(event)}</i>`)}</h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">${t('admin.attendees-import.description')}</p>
                <form novalidate @submit=${(e: Event) => this.submit(e)}>
                    ${this.renderAttendees(event, fields)}
                    ${this.renderCategory(event)}
                    ${this.renderImportOptions()}
                    ${this.renderContact(event)}
                    ${when(this.submitError, () => html`
                        <sl-alert id="submit-error" variant="danger" open>
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            ${this.submitError}
                        </sl-alert>
                    `)}
                    <div class="footer">
                        <sl-divider></sl-divider>
                        <div class="row" style="--alfio-row-cols: 3">
                            <sl-button variant="default" size="large" href=${adminUrl(`/events/${event.shortName}/detail`)}>
                                ${t('admin.common.cancel')}
                            </sl-button>
                            <div></div>
                            <sl-button id="import-button" type="submit" variant="warning" size="large" ?loading=${this.submitting}>
                                <sl-icon slot="prefix" name="upload"></sl-icon>
                                ${t('admin.attendees-import.import')}
                            </sl-button>
                        </div>
                    </div>
                </form>
            </div>
        `;
    }

    private renderAttendees(event: AlfioEvent, fields: AdditionalField[]): TemplateResult {
        const count = this.parsed?.attendees.length ?? 0;
        let badge: TemplateResult | undefined;
        if (count > 0) {
            badge = html`<sl-badge id="attendees-count" variant="primary" pill>${count}</sl-badge>`;
        }
        return sectionCard({
            icon: 'file-earmark-spreadsheet',
            title: t('admin.event-menu.group.attendees'),
            badge,
        }, html`
            ${this.#form.field({ name: 'attendeesCount', validators: validateWith(value => this.validateAttendeesCount(value)) }, field => csvImport({
                templateDescription: templateDescription(event, fields),
                onDownloadTemplate: () => downloadCsv(`${event.shortName}-attendees.csv`, AttendeesImportService.template(fields.map(field => field.name))),
                onFileChange: (e: FileUploadChangeEvent) => this.onFileChange(e, fields),
                error: this.parseError ?? this.error(field.state.meta) ?? null,
                warning: skippedRowsWarning(this.parsed?.skippedRows ?? 0),
                specs: this.renderFileSpecs(event, fields),
            }))}
            ${when(count > 0, () => this.renderPreview(this.parsed!))}
        `);
    }

    private renderFileSpecs(event: AlfioEvent, fields: AdditionalField[]): TemplateResult {
        const fieldNames = fields.map(field => field.name);
        return html`
            <div class="bulk-specs">
                <p class="text-muted">
                    ${t('admin.csv-import.specs.template')}
                </p>
                <p class="text-muted">
                    ${tHtml('admin.csv-import.specs.format', html`<strong>${t('admin.csv-import.specs.with-header')}</strong>`, html`<code>,</code>`, html`<code>"</code>`)}
                    ${tHtml('admin.csv-import.specs.quotes', html`<code>""</code>`)}
                </p>
                <pre><span id="template-header">${requiredColumns(',', AttendeesImportService.quoteColumn)}${[REFERENCE_COLUMN, ...fieldNames].map(name => `,${AttendeesImportService.quoteColumn(name)}`)}</span>

${tHtml('admin.csv-import.specs.required-columns', requiredColumns(', '))}
${t('admin.attendees-import.specs.reference', REFERENCE_COLUMN)}
${when(fieldNames.length > 0,
    () => html`${tHtml('admin.csv-import.specs.optional-columns', fieldNames.join(', '), attendeeFieldsLink(event.shortName))}${fields.filter(hasRestrictedValues).map(field => html`
${t('admin.csv-import.specs.one-of', field.name, allowedValues(field).join(', '))}`)}`,
    () => html`${tHtml('admin.csv-import.specs.no-fields', attendeeFieldsLink(event.shortName))}`)}</pre>
            </div>
        `;
    }

    private renderPreview(parsed: ParsedAttendees): TemplateResult {
        const rows = parsed.attendees.slice(0, PREVIEW_ROWS);
        const hasReference = rows.some(attendee => attendee.reference);
        return html`
            <div class="preview table-responsive">
                <table class="table table-striped">
                    ${when(parsed.attendees.length > PREVIEW_ROWS, () => html`
                        <caption class="text-muted">${t('admin.attendees-import.preview', PREVIEW_ROWS, formatNumber(parsed.attendees.length))}</caption>
                    `)}
                    <thead>
                        <tr>
                            <th>${t('admin.common.name')}</th>
                            <th>${t('admin.common.email-short')}</th>
                            <th>${t('admin.reservation-create.language')}</th>
                            ${when(hasReference, () => html`<th>${t('admin.attendees-import.reference')}</th>`)}
                            ${parsed.additionalFields.map(field => html`<th>${field}</th>`)}
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(rows, (_, index) => index, attendee => html`
                            <tr class="attendee">
                                <td>${attendee.firstName} ${attendee.lastName}</td>
                                <td>${attendee.emailAddress}</td>
                                <td>${attendee.language}</td>
                                ${when(hasReference, () => html`<td>${attendee.reference ?? ''}</td>`)}
                                ${parsed.additionalFields.map(field => html`<td>${attendee.additionalInfo[field]?.join(', ')}</td>`)}
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>
        `;
    }

    private renderCategory(event: AlfioEvent): TemplateResult {
        return sectionCard({ icon: 'tags', title: t('admin.attendees-import.category') }, html`
            ${choiceCards({
                name: 'category-type',
                className: 'category-type',
                label: t('admin.reservation-create.assign-to'),
                value: this.categoryType,
                options: CATEGORY_TYPE_CHOICES,
                panel: html`
                    ${when(this.categoryType === 'existing',
                        () => this.#form.field({ name: 'existingCategoryId', validators: validateWith(value => this.validateExistingCategory(value)) },
                            field => ticketCategorySelect(this.categories, {
                                id: 'existing-category',
                                value: field.state.value,
                                error: this.error(field.state.meta),
                                onChange: existingCategoryId => field.handleChange(existingCategoryId),
                                onBlur: () => field.handleBlur(),
                                onRefresh: () => this.refreshCategories(event),
                            })),
                        () => this.renderNewCategory(event))}
                `,
            }, this.onCategoryTypeChange)}
        `);
    }

    private renderNewCategory(event: AlfioEvent): TemplateResult {
        const nameInput = this.#form.field({
            name: 'newCategoryName',
            validators: validateWith(value => this.ifNewCategory(() => requiredValue(value))),
        }, field => this.textInput(field, { id: 'new-category-name', label: t('admin.common.name') }));
        if (event.freeOfCharge) {
            return html`${nameInput}`;
        }
        const validatePrice = (value: unknown) => this.ifNewCategory(() => {
            const price = Number(value);
            if (String(value ?? '').trim() === '' || Number.isNaN(price) || price < 0) {
                return t('admin.category-dialog.error.price');
            }
            return undefined;
        });
        return html`
            <div class="row" style="--alfio-row-cols: 3">
                ${nameInput}
                ${this.#form.field({ name: 'newCategoryPrice', validators: validateWith(validatePrice) },
                    field => this.textInput(field, { id: 'new-category-price', label: t('admin.event-detail.ticket-price'), type: 'number', suffix: event.currency }))}
                <sl-input label=${t('admin.event-detail.final-price')} readonly
                          help-text=${vatDescription(event)}
                          .value=${formatFinalPrice(event, this.#form.api.state.values.newCategoryPrice)}></sl-input>
            </div>
        `;
    }

    private renderImportOptions(): TemplateResult {
        const count = this.parsed?.attendees.length ?? 0;
        const newCategory = this.categoryType === 'new';
        const tooManyAttendees = !this.oneReservationPerAttendee && count > MAX_ATTENDEES_PER_RESERVATION;
        let oneReservationHelp = t('admin.attendees-import.one-reservation.help');
        let tooManyAttendeesHint = t('admin.attendees-import.too-many.hint');
        if (newCategory) {
            oneReservationHelp = t('admin.attendees-import.one-reservation.new-category');
            tooManyAttendeesHint = t('admin.attendees-import.too-many.hint-new-category');
        }
        return sectionCard({ icon: 'sliders', title: t('admin.categories.options') }, html`
            <div class="form-stack options">
                <sl-switch id="one-reservation-per-attendee" .checked=${this.oneReservationPerAttendee} ?disabled=${newCategory}
                           help-text=${oneReservationHelp}
                           @sl-change=${(e: Event) => { this.oneReservationPerAttendee = (e.target as SlSwitch).checked; }}>
                    ${t('admin.attendees-import.one-reservation')}
                </sl-switch>
                ${when(tooManyAttendees, () => html`
                    <sl-alert variant="warning" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${t('admin.attendees-import.too-many', MAX_ATTENDEES_PER_RESERVATION)}
                        ${tooManyAttendeesHint}
                    </sl-alert>
                `)}
                <sl-switch id="forbid-reassignment" .checked=${this.forbidReassignment}
                           help-text=${t('admin.attendees-import.forbid-reassignment.help')}
                           @sl-change=${(e: Event) => { this.forbidReassignment = (e.target as SlSwitch).checked; }}>
                    ${t('admin.tickets.lock.help')}
                </sl-switch>
                <sl-switch id="send-tickets" .checked=${this.sendTickets}
                           help-text=${t('admin.attendees-import.send-tickets.help')}
                           @sl-change=${(e: Event) => { this.sendTickets = (e.target as SlSwitch).checked; }}>
                    ${t('admin.attendees-import.send-tickets')}
                </sl-switch>
                <sl-switch id="add-seats" .checked=${this.addSeats}
                           help-text=${t('admin.reservation-create.extra-seats.help')}
                           @sl-change=${(e: Event) => { this.addSeats = (e.target as SlSwitch).checked; }}>
                    ${t('admin.reservation-create.extra-seats')}
                </sl-switch>
                ${when(this.addSeats && !newCategory, () => html`
                    <sl-alert variant="warning" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${t('admin.reservation-create.extra-seats.warning')}
                    </sl-alert>
                `)}
            </div>
        `);
    }

    private renderContact(event: AlfioEvent): TemplateResult {
        const singleReservation = !this.oneReservationPerAttendee;
        const multipleLanguages = event.contentLanguages.length > 1;
        if (!singleReservation && !multipleLanguages) {
            return html``;
        }
        let title = t('admin.reservation-create.language');
        if (singleReservation) {
            title = t('admin.attendees-import.contact');
        }
        return sectionCard({ icon: 'person-vcard', title }, html`
            <div class="form-stack">
                ${when(singleReservation, () => html`
                    <div class="row">
                        ${this.#form.field({ name: 'contact.firstName', validators: validateWith(value => this.ifSingleReservation(() => requiredValue(value))) },
                            field => this.textInput(field, { id: 'contact-first-name', label: t('admin.common.first-name') }))}
                        ${this.#form.field({ name: 'contact.lastName', validators: validateWith(value => this.ifSingleReservation(() => requiredValue(value))) },
                            field => this.textInput(field, { id: 'contact-last-name', label: t('admin.common.last-name') }))}
                    </div>
                    <div class="row">
                        ${this.#form.field({ name: 'contact.emailAddress', validators: validateWith(value => this.ifSingleReservation(() => emailAddress(value))) },
                            field => this.textInput(field, { id: 'contact-email', label: t('admin.common.email-address'), type: 'email', prefixIcon: 'envelope' }))}
                        ${when(multipleLanguages, () => this.renderLanguage(event))}
                    </div>
                `, () => this.renderLanguage(event))}
            </div>
        `);
    }

    private renderLanguage(event: AlfioEvent): TemplateResult {
        return html`
            ${this.#form.field({ name: 'language', validators: validateWith(value => requiredValue(value)) }, field => {
                const error = this.error(field.state.meta);
                return html`
                    <sl-select id="language" label=${t('admin.reservation-create.language')} required hoist class=${classMap({ error: error != null })}
                               help-text=${error ?? t('admin.attendees-import.language.help')}
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

    private validateAttendeesCount(value: unknown): string | undefined {
        const count = Number(value ?? 0);
        if (count === 0) {
            return t('admin.attendees-import.error.file');
        }
        if (!this.oneReservationPerAttendee && count > MAX_ATTENDEES_PER_RESERVATION) {
            return t('admin.attendees-import.error.too-many', MAX_ATTENDEES_PER_RESERVATION);
        }
        return undefined;
    }

    private validateExistingCategory(value: unknown): string | undefined {
        if (this.categoryType !== 'existing' || requiredValue(value) == null) {
            return undefined;
        }
        return t('admin.reservation-create.error.category');
    }

    private ifNewCategory(validate: () => string | undefined): string | undefined {
        if (this.categoryType !== 'new') {
            return undefined;
        }
        return validate();
    }

    // the contact person is asked only when all the attendees go in the same reservation
    private ifSingleReservation(validate: () => string | undefined): string | undefined {
        if (this.oneReservationPerAttendee) {
            return undefined;
        }
        return validate();
    }

    private async refreshCategories(event: AlfioEvent): Promise<void> {
        await this.categories.refresh(event.shortName);
        // the selected category could have been removed in the meantime
        const existingCategoryId = this.#form.api.state.values.existingCategoryId;
        if (existingCategoryId !== '' && !this.categories.contains(existingCategoryId)) {
            this.#form.api.setFieldValue('existingCategoryId', '');
        }
    }

    private onCategoryTypeChange = (categoryType: CategoryType): void => {
        this.categoryType = categoryType;
        if (this.categoryType === 'new') {
            // a new category for each attendee doesn't make sense
            this.oneReservationPerAttendee = false;
        }
    };

    private async onFileChange(e: FileUploadChangeEvent, fields: AdditionalField[]): Promise<void> {
        const file = e.detail.file;
        this.parsed = null;
        this.parseError = null;
        this.submitError = null;
        this.#form.api.setFieldValue('attendeesCount', 0, { dontUpdateMeta: true });
        if (file == null) {
            return;
        }
        try {
            const parsed = AttendeesImportService.parse(await file.text());
            if (parsed.attendees.length === 0) {
                this.parseError = t('admin.csv-import.error.empty');
                return;
            }
            // the backend refuses the whole import (case-insensitive match)
            const knownFields = new Set(fields.map(field => field.name.toLowerCase()));
            const unknownColumns = parsed.additionalFields.filter(name => !knownFields.has(name.toLowerCase()));
            if (unknownColumns.length > 0) {
                this.parseError = t('admin.reservation-import.error.unknown-columns', unknownColumns.join(', '));
                return;
            }
            this.parsed = parsed;
            if (parsed.attendees.length > MAX_ATTENDEES_PER_RESERVATION && this.categoryType === 'existing') {
                this.oneReservationPerAttendee = true;
            }
            this.#form.api.setFieldValue('attendeesCount', parsed.attendees.length);
        } catch (error) {
            this.parseError = errorMessage(error, t('admin.csv-import.error.read'));
        }
    }

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
    private async save(values: ImportFormValues): Promise<void> {
        const event = this.loadDataTask.value?.event;
        if (event == null || this.parsed == null) {
            return;
        }
        this.submitting = true;
        try {
            const requestId = await AttendeesImportService.submit(event.shortName, this.buildRequest(event, this.parsed, values), this.oneReservationPerAttendee);
            navigate(`/events/${event.shortName}/reservation/import/${requestId}/status`);
        } catch (error) {
            this.submitError = errorMessage(error, t('admin.error.unexpected-retry'));
        } finally {
            this.submitting = false;
        }
    }

    private buildRequest(event: AlfioEvent, parsed: ParsedAttendees, values: ImportFormValues): AttendeesImportRequest {
        const category = buildCategory(event, {
            categoryType: this.categoryType,
            existingCategoryId: values.existingCategoryId,
            name: values.newCategoryName,
            price: values.newCategoryPrice,
        });
        let customerData = values.contact;
        if (this.oneReservationPerAttendee) {
            // each attendee is the contact of their own reservation
            customerData = { firstName: '', lastName: '', emailAddress: '' };
        }
        return {
            expiration: reservationExpiration(event),
            customerData,
            language: values.language,
            ticketsInfo: [{
                category,
                attendees: parsed.attendees.map(attendee => ({ ...attendee, forbidReassignment: this.forbidReassignment })),
                addSeatsIfNotAvailable: this.addSeats,
            }],
            notification: { customer: false, attendees: this.sendTickets },
        };
    }
}

async function booleanConfig(eventName: string, key: string): Promise<boolean> {
    try {
        // the value is plain text: "true" is parsed as a boolean
        return String(await ConfigurationService.loadSingleConfig(eventName, key)) === 'true';
    } catch {
        return false;
    }
}

function templateDescription(event: AlfioEvent, fields: AdditionalField[]): TemplateResult {
    if (fields.length > 0) {
        return html`${tHtml('admin.attendees-import.template.fields', attendeeFieldsLink(event.shortName))}`;
    }
    return html`${t('admin.attendees-import.template.basic')}`;
}

function skippedRowsWarning(skippedRows: number): string | null {
    if (skippedRows === 0) {
        return null;
    }
    return t('admin.attendees-import.skipped', { count: skippedRows });
}

function requiredColumns(separator: string, format: (column: string) => string = column => column): TemplateResult[] {
    return REQUIRED_COLUMNS.map((column, i) => html`${when(i > 0, () => separator)}<span class="text-success">${format(column)}</span>`);
}

function hasRestrictedValues(field: AdditionalField): boolean {
    return allowedValues(field).length > 0;
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-attendees-import': AttendeesImport;
    }
}
