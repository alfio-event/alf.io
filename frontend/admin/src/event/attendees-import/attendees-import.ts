import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import type {SlInput, SlRadioGroup, SlSelect, SlSwitch} from '@shoelace-style/shoelace';
import {AlfioEvent, DateTimeModification} from '../../model/event.ts';
import {AttendeesImportRequest, ImportCategory, ParsedAttendees} from '../../model/attendees-import.ts';
import {AdditionalField} from '../../model/additional-field.ts';
import {EventService} from '../../service/event.ts';
import {AdditionalFieldService} from '../../service/additional-field.ts';
import {ConfigurationService} from '../../service/configuration.ts';
import {AttendeesImportService, REFERENCE_COLUMN, REQUIRED_COLUMNS} from '../../service/attendees-import.ts';
import {formatAmount, localizedTitle} from '../../service/reservation-format.ts';
import {downloadCsv} from '../../service/helpers.ts';
import {badges, base, form, modernLayout, modernTable, purchaseContextListPage, retroCompat, row, textColors} from '../../styles.ts';
import {sectionCard} from '../../components/section-card.ts';
import {taskContent} from '../../components/task-content.ts';
import {FileUploadChangeEvent} from '../../components/file-upload.ts';
import '../../components/file-upload.ts';

// the backend refuses a single reservation with more attendees (see AdminReservationRequestManager)
const MAX_ATTENDEES_PER_RESERVATION = 100;
// rendering all the rows of a big file would make the page unresponsive
const PREVIEW_ROWS = 100;

type CategoryType = 'existing' | 'new';

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
 * Imports the attendees listed in a CSV file. The reservations are created in the background:
 * once the request has been accepted, the page moves to the import status.
 */
@customElement('alfio-attendees-import')
export class AttendeesImport extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @state() private parsed: ParsedAttendees | null = null;
    @state() private parseError: string | null = null;
    @state() private categoryType: CategoryType = 'existing';
    @state() private existingCategoryId = '';
    @state() private newCategoryName = '';
    @state() private newCategoryPrice = '';
    @state() private oneReservationPerAttendee = false;
    @state() private forbidReassignment = false;
    @state() private sendTickets = false;
    @state() private addSeats = false;
    @state() private contact: Contact = { firstName: '', lastName: '', emailAddress: '' };
    @state() private language = '';
    @state() private submitting = false;
    @state() private submitError: string | null = null;

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName]): Promise<LoadData> => {
            const [{ event }, sendTickets, oneReservationPerAttendee] = await Promise.all([
                EventService.load(eventName),
                booleanConfig(eventName, 'SEND_TICKETS_AFTER_IMPORT_ATTENDEE'),
                booleanConfig(eventName, 'CREATE_RESERVATION_FOR_EACH_IMPORTED_ATTENDEE'),
            ]);
            this.sendTickets = sendTickets;
            this.oneReservationPerAttendee = oneReservationPerAttendee;
            this.language = event.contentLanguages[0]?.locale ?? '';
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
        css`
            :host {
                --alfio-section-body-padding: var(--sl-spacing-large);
            }
            .import-steps {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(min(100%, 20rem), 1fr));
                gap: var(--sl-spacing-large);
            }
            .import-step {
                display: grid;
                grid-template-rows: auto 1fr;
                gap: var(--sl-spacing-medium);
                margin: 0;
                padding: var(--sl-spacing-large);
                border-radius: var(--sl-input-border-radius-large);
            }
            .import-step.upload {
                /* the alerts go below the drop zone */
                grid-template-rows: auto;
                align-content: start;
                border: 1px solid var(--sl-color-gray-200);
            }
            /* centered like the content of the drop zone next to it */
            .template-download {
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                gap: var(--sl-spacing-medium);
                text-align: center;
            }
            .template-download > sl-icon {
                font-size: var(--sl-font-size-4x-large);
                color: var(--sl-color-primary-600);
            }
            .import-step h4 {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-small);
                margin: 0;
            }
            .import-step p {
                margin: 0;
            }
            .step-number {
                display: inline-flex;
                align-items: center;
                justify-content: center;
                flex: none;
                width: var(--sl-spacing-x-large);
                height: var(--sl-spacing-x-large);
                border-radius: var(--sl-border-radius-circle);
                background: var(--sl-color-primary-600);
                color: var(--sl-color-neutral-0);
                font-size: var(--sl-font-size-small);
            }
            .file-specs {
                display: block;
                margin-top: var(--sl-spacing-large);
            }
            .bulk-specs p {
                margin: 0 0 var(--sl-spacing-x-small);
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
        return taskContent(this.loadDataTask, this.loadDataTask.value ?? null, 'Failed to load the event. Please try again.',
            data => this.renderContent(data));
    }

    private renderContent({ event, fields }: LoadData): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>Import attendees for <i>${localizedTitle(event)}</i></h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">Create reservations for attendees registered elsewhere, by uploading a CSV file.</p>
                <form @submit=${(e: Event) => this.submit(e, event)}>
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
                            <sl-button variant="default" size="large" href=${`#/events/${event.shortName}/detail`}>
                                <sl-icon slot="prefix" name="x-circle"></sl-icon>
                                Cancel
                            </sl-button>
                            <div></div>
                            <sl-button id="import-button" type="submit" variant="warning" size="large" ?loading=${this.submitting}>
                                <sl-icon slot="prefix" name="upload"></sl-icon>
                                Import
                            </sl-button>
                        </div>
                    </div>
                </form>
            </div>
        `;
    }

    private renderAttendees(event: AlfioEvent, fields: AdditionalField[]): TemplateResult {
        const count = this.parsed?.attendees.length ?? 0;
        return sectionCard({
            icon: 'file-earmark-spreadsheet',
            title: 'Attendees',
            badge: count > 0 ? html`<sl-badge id="attendees-count" variant="primary" pill>${count}</sl-badge>` : undefined,
        }, html`
            <div class="import-steps">
                <div class="import-step featured-card">
                    <h4><span class="step-number">1</span>Download the template</h4>
                    <div class="template-download">
                        <sl-icon name="file-earmark-spreadsheet"></sl-icon>
                        <p class="text-muted">
                            ${fields.length > 0
                                ? html`It has a column for each of the ${fieldsLink(event)} of this event, so that you don't miss any of them.`
                                : html`It has all the columns that the import needs.`}
                        </p>
                        <sl-button id="download-template" variant="primary" size="large"
                                   @click=${() => downloadCsv(`${event.shortName}-attendees.csv`, AttendeesImportService.template(fields.map(field => field.name)))}>
                            <sl-icon slot="prefix" name="download"></sl-icon>
                            Download template
                        </sl-button>
                    </div>
                </div>
                <div class="import-step upload">
                    <h4><span class="step-number">2</span>Fill it in and upload it</h4>
                    <alfio-file-upload id="attendees-file" accept=".csv,text/csv" label="Choose the filled-in template or drag it here"
                                       @change=${(e: FileUploadChangeEvent) => this.onFileChange(e, fields)}></alfio-file-upload>
                    ${when(this.parseError, () => html`
                        <sl-alert id="parse-error" variant="danger" open>
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            ${this.parseError}
                        </sl-alert>
                    `)}
                    ${when((this.parsed?.skippedRows ?? 0) > 0, () => html`
                        <sl-alert variant="warning" open>
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            ${this.parsed!.skippedRows} rows have been skipped, because they don't contain all the required columns.
                        </sl-alert>
                    `)}
                </div>
            </div>
            <sl-details summary="File specifications" class="file-specs">
                ${this.renderFileSpecs(event, fields)}
            </sl-details>
            ${when(count > 0, () => this.renderPreview(this.parsed!))}
        `);
    }

    private renderFileSpecs(event: AlfioEvent, fields: AdditionalField[]): TemplateResult {
        const fieldNames = fields.map(field => field.name);
        return html`
            <div class="bulk-specs">
                <p class="text-muted">
                    The template already follows these rules: you need them only if your file is generated by another system.
                </p>
                <p class="text-muted">
                    A CSV file <strong>with header</strong>, using commas (<code>,</code>) as separator and double quotes (<code>"</code>) as quote character.
                    Double quotes within a value are written twice (<code>""</code>).
                </p>
                <pre><span id="template-header">${requiredColumns(',', AttendeesImportService.quoteColumn)}${[REFERENCE_COLUMN, ...fieldNames].map(name => `,${AttendeesImportService.quoteColumn(name)}`)}</span>

${requiredColumns(', ')}: required, in this order
${REFERENCE_COLUMN}: optional, the attendee ID in your system
${fieldNames.length > 0
    ? html`${fieldNames.join(', ')}: optional, the ${fieldsLink(event)}${fields.filter(hasRestrictedValues).map(field => html`
${field.name}: one of ${allowedValues(field).join(', ')}`)}`
    : html`There are no ${fieldsLink(event)} for this event`}</pre>
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
                        <caption class="text-muted">Showing the first ${PREVIEW_ROWS} of ${parsed.attendees.length} attendees</caption>
                    `)}
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Email</th>
                            <th>Language</th>
                            ${when(hasReference, () => html`<th>Reference</th>`)}
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
        return sectionCard({ icon: 'tags', title: 'Ticket category' }, html`
            <div class="form-stack">
                <sl-radio-group id="category-type" label="Assign the attendees to" value=${this.categoryType}
                                @sl-change=${this.onCategoryTypeChange}>
                    <sl-radio-button value="existing">
                        <sl-icon slot="prefix" name="paperclip"></sl-icon>
                        An existing category
                    </sl-radio-button>
                    <sl-radio-button value="new">
                        <sl-icon slot="prefix" name="plus-square"></sl-icon>
                        A new restricted category
                    </sl-radio-button>
                </sl-radio-group>
                ${when(this.categoryType === 'existing',
                    () => html`
                        <sl-select id="existing-category" label="Category" placeholder="Select a category" required
                                   .value=${this.existingCategoryId}
                                   @sl-change=${(e: Event) => { this.existingCategoryId = String((e.target as SlSelect).value); }}>
                            ${repeat(event.ticketCategories, category => category.id, category => html`
                                <sl-option value=${String(category.id)}>${category.name}</sl-option>
                            `)}
                        </sl-select>
                    `,
                    () => this.renderNewCategory(event))}
                <sl-details summary="How are the seats assigned?">
                    <p><strong>Existing category</strong>: the attendees get seats of the category. If there aren't enough seats in the category,
                        it's extended with seats not allocated to other categories, or with extra seats if "Add extra seats" is enabled.</p>
                    <p><strong>New restricted category</strong>: a category is created for these attendees only. If there aren't enough seats in the event,
                        extra seats are added if "Add extra seats" is enabled.</p>
                    <p>Otherwise, the reservation is not created.</p>
                </sl-details>
            </div>
        `);
    }

    private renderNewCategory(event: AlfioEvent): TemplateResult {
        const nameInput = html`
            <sl-input id="new-category-name" label="Name" required .value=${this.newCategoryName}
                      @sl-input=${(e: Event) => { this.newCategoryName = (e.target as SlInput).value; }}></sl-input>
        `;
        if (event.freeOfCharge) {
            return nameInput;
        }
        const price = Number.parseFloat(this.newCategoryPrice);
        return html`
            <div class="row" style="--alfio-row-cols: 3">
                ${nameInput}
                <sl-input id="new-category-price" label="Ticket price" type="number" min="0" step="0.01" required .value=${this.newCategoryPrice}
                          @sl-input=${(e: Event) => { this.newCategoryPrice = (e.target as SlInput).value; }}>
                    <span slot="suffix">${event.currency}</span>
                </sl-input>
                <sl-input label="Final price" readonly
                          help-text=${event.vatIncluded ? 'VAT included' : `Including ${event.vatPercentage}% VAT`}
                          .value=${Number.isNaN(price) ? '' : formatAmount(finalPrice(event, price), event.currency)}></sl-input>
            </div>
        `;
    }

    private renderImportOptions(): TemplateResult {
        const count = this.parsed?.attendees.length ?? 0;
        const newCategory = this.categoryType === 'new';
        const tooManyAttendees = !this.oneReservationPerAttendee && count > MAX_ATTENDEES_PER_RESERVATION;
        return sectionCard({ icon: 'sliders', title: 'Options' }, html`
            <div class="form-stack options">
                <sl-switch id="one-reservation-per-attendee" .checked=${this.oneReservationPerAttendee} ?disabled=${newCategory}
                           help-text=${newCategory ? 'Not available for a new category' : 'Each attendee is the contact person of their reservation'}
                           @sl-change=${(e: Event) => { this.oneReservationPerAttendee = (e.target as SlSwitch).checked; }}>
                    Create one reservation per attendee
                </sl-switch>
                ${when(tooManyAttendees, () => html`
                    <sl-alert variant="warning" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        A reservation can contain at most ${MAX_ATTENDEES_PER_RESERVATION} attendees.
                        ${newCategory ? 'Import the attendees in smaller files.' : 'Create one reservation per attendee instead.'}
                    </sl-alert>
                `)}
                <sl-switch id="forbid-reassignment" .checked=${this.forbidReassignment}
                           help-text="The attendees can't transfer their ticket to someone else"
                           @sl-change=${(e: Event) => { this.forbidReassignment = (e.target as SlSwitch).checked; }}>
                    Forbid ticket reassignment
                </sl-switch>
                <sl-switch id="send-tickets" .checked=${this.sendTickets}
                           @sl-change=${(e: Event) => { this.sendTickets = (e.target as SlSwitch).checked; }}>
                    Send tickets via email
                </sl-switch>
                <sl-switch id="add-seats" .checked=${this.addSeats}
                           help-text="Increases the number of seats of the event if there aren't enough"
                           @sl-change=${(e: Event) => { this.addSeats = (e.target as SlSwitch).checked; }}>
                    Add extra seats if needed
                </sl-switch>
                ${when(this.addSeats && !newCategory, () => html`
                    <sl-alert variant="warning" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        If you add seats to a public category and the reservation expires, the added seats will be released to the public.
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
        return sectionCard({ icon: 'person-vcard', title: singleReservation ? 'Reservation contact' : 'Language' }, html`
            <div class="form-stack">
                ${when(singleReservation, () => html`
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
                `, () => this.renderLanguage(event))}
            </div>
        `);
    }

    private renderLanguage(event: AlfioEvent): TemplateResult {
        return html`
            <sl-select id="language" label="Language" required help-text="Used for the attendees without a language"
                       .value=${this.language}
                       @sl-change=${(e: Event) => { this.language = String((e.target as SlSelect).value); }}>
                ${event.contentLanguages.map(language => html`<sl-option value=${language.locale}>${language.displayLanguage}</sl-option>`)}
            </sl-select>
        `;
    }

    private updateContact(change: Partial<Contact>): void {
        this.contact = { ...this.contact, ...change };
    }

    private onCategoryTypeChange = (e: Event): void => {
        this.categoryType = (e.target as SlRadioGroup).value as CategoryType;
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
        if (file == null) {
            return;
        }
        try {
            const parsed = AttendeesImportService.parse(await file.text());
            if (parsed.attendees.length === 0) {
                this.parseError = 'The file does not contain any attendee.';
                return;
            }
            // the backend refuses the whole import (case-insensitive match)
            const knownFields = new Set(fields.map(field => field.name.toLowerCase()));
            const unknownColumns = parsed.additionalFields.filter(name => !knownFields.has(name.toLowerCase()));
            if (unknownColumns.length > 0) {
                this.parseError = `These columns are not attendees' data to collect of this event: ${unknownColumns.join(', ')}. Please use the template.`;
                return;
            }
            this.parsed = parsed;
            if (parsed.attendees.length > MAX_ATTENDEES_PER_RESERVATION && this.categoryType === 'existing') {
                this.oneReservationPerAttendee = true;
            }
        } catch (error) {
            this.parseError = error instanceof Error ? error.message : 'The file cannot be read.';
        }
    }

    private async submit(e: Event, event: AlfioEvent): Promise<void> {
        e.preventDefault();
        if (this.submitting) {
            return;
        }
        if (this.parsed == null) {
            this.submitError = 'Please upload a CSV file with the attendees.';
            return;
        }
        if (!this.oneReservationPerAttendee && this.parsed.attendees.length > MAX_ATTENDEES_PER_RESERVATION) {
            this.submitError = `A reservation can contain at most ${MAX_ATTENDEES_PER_RESERVATION} attendees.`;
            return;
        }
        this.submitting = true;
        this.submitError = null;
        try {
            const requestId = await AttendeesImportService.submit(event.shortName, this.buildRequest(event, this.parsed), this.oneReservationPerAttendee);
            window.location.hash = `#/events/${event.shortName}/reservation/import/${requestId}/status`;
        } catch (error) {
            this.submitError = error instanceof Error ? error.message : 'Unexpected error. Please retry and/or check the logs.';
        } finally {
            this.submitting = false;
        }
    }

    private buildRequest(event: AlfioEvent, parsed: ParsedAttendees): AttendeesImportRequest {
        const category: ImportCategory = this.categoryType === 'existing'
            ? { existingCategoryId: Number(this.existingCategoryId) }
            : { name: this.newCategoryName, price: event.freeOfCharge ? undefined : Number.parseFloat(this.newCategoryPrice) };
        return {
            expiration: endOfTomorrow(),
            // each attendee is the contact of their own reservation
            customerData: this.oneReservationPerAttendee ? { firstName: '', lastName: '', emailAddress: '' } : this.contact,
            language: this.language,
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

function fieldsLink(event: AlfioEvent): TemplateResult {
    return html`<a href=${`#/events/${event.shortName}/attendee-data-to-collect`}>attendees' data to collect</a>`;
}

function requiredColumns(separator: string, format: (column: string) => string = column => column): TemplateResult[] {
    return REQUIRED_COLUMNS.map((column, i) => html`${i > 0 ? separator : ''}<span class="text-success">${format(column)}</span>`);
}

function hasRestrictedValues(field: AdditionalField): boolean {
    return allowedValues(field).length > 0;
}

function allowedValues(field: AdditionalField): string[] {
    return (field.restrictedValues ?? []).filter(value => !(field.disabledValues ?? []).includes(value));
}

function finalPrice(event: AlfioEvent, price: number): number {
    return event.vatIncluded ? price : price * (1 + event.vatPercentage / 100);
}

/**
 * The import creates reservations that expire at the end of the next day
 */
function endOfTomorrow(): DateTimeModification {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const pad = (n: number) => String(n).padStart(2, '0');
    return {
        date: `${tomorrow.getFullYear()}-${pad(tomorrow.getMonth() + 1)}-${pad(tomorrow.getDate())}`,
        time: '23:59',
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-attendees-import': AttendeesImport;
    }
}
