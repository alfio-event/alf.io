import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
import {AlfioEvent, TicketCategory} from '../../model/event.ts';
import {SendCodeModification, SpecialPrice} from '../../model/special-price.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {EventService} from '../../service/event.ts';
import {CSV_HEADER, invitationsCsv, SpecialPriceService} from '../../service/special-price.ts';
import {ConfirmationDialogService} from '../../service/confirmation-dialog.ts';
import {localizedTitle} from '../../service/reservation-format.ts';
import {downloadCsv, errorMessage} from '../../service/helpers.ts';
import {
    badges,
    base,
    choiceCards as choiceCardsStyles,
    csvImport as csvImportStyles,
    form,
    listTabs,
    modernLayout,
    modernTable,
    purchaseContextListPage,
    retroCompat,
    row,
    textColors,
} from '../../styles.ts';
import {sectionCard} from '../../components/section-card.ts';
import {taskContent} from '../../components/task-content.ts';
import {emptyState} from '../../components/empty-state.ts';
import {csvImport} from '../../components/csv-import.ts';
import {choiceCards, ChoiceCardOption} from '../../components/choice-cards.ts';
import {FileUploadChangeEvent} from '../../components/file-upload.ts';
import {
    emailAddress,
    FormMountController,
    focusFirstError,
    INVALID_FORM_MESSAGE,
    requiredValue,
    submitValidated,
    validateWith,
    validatedInput,
    validatedSelect,
} from '../../components/form-validation.ts';
import '../../components/format-date.ts';

type TabName = 'invitations' | 'history';

type InputMode = 'manual' | 'csv';

interface LoadData {
    event: AlfioEvent;
    category: TicketCategory | undefined;
    sentCodes: SpecialPrice[];
}

/**
 * One invitee of the manual form: the code can be blank, the backend assigns a free one.
 */
interface InviteeDraft {
    code: string;
    assignee: string;
    email: string;
    language: string;
}

interface InvitationsFormValues {
    invitees: InviteeDraft[];
}

const INPUT_MODES: ChoiceCardOption<InputMode>[] = [
    {
        value: 'manual',
        icon: 'person-vcard',
        title: 'Insert manually',
        description: 'Type the invitees one by one, then review them before sending.',
    },
    {
        value: 'csv',
        icon: 'filetype-csv',
        title: 'Upload CSV',
        description: 'Import the invitees from a file, then review them before sending.',
    },
];

/**
 * Sends the invitation emails for the reserved codes of a restricted ticket category: the invitees are
 * typed in a form or listed in a CSV file, reviewed before sending, and the invitations are sent from the
 * preview. The "History" tab lists the codes already sent, and lets the organizer clear their recipient.
 */
@customElement('alfio-send-invitations')
export class SendInvitations extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @property({ type: String, attribute: 'data-category-id' }) categoryId = '';
    @state() private selectedTab: TabName = 'invitations';
    // how the invitees are added: typed in the form, or read from an uploaded CSV file
    @state() private addMode: InputMode = 'manual';
    // the rows read from the input (form or file), shown for review before sending
    @state() private preview: SendCodeModification[] | null = null;
    @state() private uploading = false;
    @state() private uploadError: string | null = null;
    // why the manual form cannot be reviewed (validation or server error)
    @state() private reviewError: string | null = null;
    @state() private sending = false;
    @state() private sendError: string | null = null;

    private defaultLanguage = '';
    private formReady = false;

    readonly #form = new TanStackFormController(this, {
        defaultValues: { invitees: [] } as InvitationsFormValues,
        onSubmit: async ({ value }) => {
            await this.reviewInvitations(value.invitees);
        },
    });

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName, categoryId]): Promise<LoadData> => {
            const [{ event }, sentCodes] = await Promise.all([
                EventService.load(eventName),
                SpecialPriceService.loadSentCodes(eventName, Number(categoryId)),
            ]);
            if (!this.formReady) {
                // the task runs again after sending or clearing: the form is reset only once
                this.resetForm(event);
            }
            return { event, category: event.ticketCategories.find(c => c.id === Number(categoryId)), sentCodes };
        },
        args: () => [this.eventName, this.categoryId] as const,
    });

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        row,
        form,
        choiceCardsStyles,
        modernLayout,
        modernTable,
        purchaseContextListPage,
        listTabs,
        csvImportStyles,
        css`
            :host {
                --alfio-section-body-padding: var(--sl-spacing-large);
            }
            .upload-progress {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-small);
                margin-bottom: var(--sl-spacing-medium);
                color: var(--sl-color-gray-600);
            }
            /* the listTabs block removes the padding of the section cards in the tab panels, which the
               upload steps and the footer of the preview need */
            .list-tabs .section-body {
                padding: var(--alfio-section-body-padding, var(--sl-spacing-large));
            }
            .preview-footer sl-divider {
                margin-top: var(--sl-spacing-large);
            }
            sl-alert {
                margin-top: var(--sl-spacing-medium);
            }
            /* the invitees of the manual form, as the attendees of the reservation creation: one row of
               fields per invitee, the labels of the first row acting as column headers */
            .invitees {
                display: grid;
                gap: var(--sl-spacing-x-small);
                --invitee-number-width: 2rem;
            }
            .invitee {
                --alfio-custom-row-cols-layout: var(--invitee-number-width) 1fr 1.4fr 1.4fr minmax(8rem, 10rem) auto;
                --alfio-column-gap: var(--sl-spacing-small);
                align-items: end;
            }
            .invitee sl-input,
            .invitee sl-select {
                margin-top: 0;
            }
            /* every field of a row reserves the help-text line, so that the controls stay aligned even when
               only one field shows a message: the code select always has one, an invalid input shows its error */
            .invitee sl-input::part(form-control-help-text),
            .invitee sl-select::part(form-control-help-text) {
                display: block;
                min-height: 1lh;
                margin-top: var(--sl-spacing-3x-small);
                font-size: var(--sl-input-help-text-font-size-medium);
            }
            .invitee-number {
                align-self: center;
                color: var(--sl-color-gray-500);
                font-size: var(--sl-font-size-small);
                text-align: end;
            }
            /* keeps the rows aligned with the first one, which has the labels: the number and the remove
               button sit at the bottom of the row, at the level of the inputs (above the reserved help-text line) */
            .invitee:first-child .invitee-number,
            .invitee:first-child sl-icon-button {
                align-self: end;
                padding-bottom: calc(1lh + var(--sl-spacing-3x-small));
            }
            /* under the last row, aligned with the inputs */
            .add-invitee {
                justify-self: start;
                margin-inline-start: calc(var(--invitee-number-width) + var(--sl-spacing-small));
            }
            .add-invitee::part(base) {
                padding-inline-start: 0;
            }
            .add-invitee::part(label) {
                padding-inline-start: var(--sl-spacing-x-small);
            }
            .input-footer sl-divider {
                margin-top: var(--sl-spacing-large);
            }
            @media (max-width: 767px) {
                .invitee-number {
                    text-align: start;
                }
                .add-invitee {
                    margin-inline-start: 0;
                }
            }
        `,
    ];

    render(): TemplateResult {
        return taskContent(this.loadDataTask, this.loadDataTask.value ?? null, 'Failed to load the invitations. Please try again.',
            data => this.renderContent(data));
    }

    private renderContent({ event, category, sentCodes }: LoadData): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>Send invitations for <i>${category?.name ?? localizedTitle(event)}</i></h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">Send an email with the reserved code to each invitee, typed in one by one or read from a CSV file.</p>
                <sl-tab-group @sl-tab-show=${this.onTabShow}>
                    <sl-tab slot="nav" panel="invitations" ?active=${this.selectedTab === 'invitations'}>
                        <span class="tab-label">
                            <sl-icon name="envelope"></sl-icon>
                            Invitations
                        </span>
                    </sl-tab>
                    <sl-tab slot="nav" panel="history" ?active=${this.selectedTab === 'history'}>
                        <span class="tab-label">
                            <sl-icon name="clock-history"></sl-icon>
                            History
                            <sl-badge variant="primary" pill>${sentCodes.length}</sl-badge>
                        </span>
                    </sl-tab>
                    <sl-tab-panel name="invitations">
                        ${when(this.preview == null,
                            () => this.renderInput(event),
                            () => this.renderPreview())}
                    </sl-tab-panel>
                    <sl-tab-panel name="history">
                        ${this.renderHistory(event, sentCodes)}
                    </sl-tab-panel>
                </sl-tab-group>
            </div>
        `;
    }

    /**
     * The codes of the category that are still free: not sent and not assigned to anyone.
     */
    private freeCodes(): string[] {
        const category = this.loadDataTask.value?.category;
        return (category?.tokenStatus ?? [])
            .filter(token => token.status === 'FREE' && token.sentTimestamp == null)
            .map(token => token.code);
    }

    private renderInput(event: AlfioEvent): TemplateResult {
        const freeCodes = this.freeCodes();
        return sectionCard({ icon: 'people', title: 'Invitees' }, html`
            ${choiceCards({
                name: 'input-mode',
                className: 'input-mode',
                label: 'How do you want to add the invitees?',
                value: this.addMode,
                options: INPUT_MODES,
                panel: html`
                    ${when(this.uploading, () => html`
                        <div class="upload-progress">
                            <sl-spinner></sl-spinner>
                            <span>Preparing the invitations…</span>
                        </div>
                    `)}
                    ${when(this.addMode === 'manual',
                        () => this.renderManualForm(event, freeCodes),
                        () => this.renderCsvImport(event))}
                `,
            }, mode => this.changeInputMode(mode))}
        `);
    }

    /**
     * The invitees typed in one by one, as the attendees of the reservation creation: one row of fields
     * per invitee, reviewed before sending.
     */
    private renderManualForm(event: AlfioEvent, freeCodes: string[]): TemplateResult {
        const invitees = this.#form.api.state.values.invitees;
        const submitted = this.#form.api.state.submissionAttempts > 0;
        return html`
            <div class="invitees">
                ${repeat(invitees, (_, index) => index, (_, index) => this.renderInvitee(event, index, submitted, freeCodes))}
                <sl-button class="add-invitee" variant="text" @click=${this.addInvitee}>
                    <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                    Add invitee
                </sl-button>
            </div>
            ${when(this.reviewError, () => html`
                <sl-alert class="review-error" variant="danger" open>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${this.reviewError}
                </sl-alert>
            `)}
            <div class="input-footer">
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <div></div>
                    <div></div>
                    <sl-button id="review-button" variant="warning" size="large" ?loading=${this.uploading} @click=${this.review}>Review</sl-button>
                </div>
            </div>
        `;
    }

    private renderInvitee(event: AlfioEvent, index: number, submitted: boolean, freeCodes: string[]): TemplateResult {
        // the labels of the first row act as column headers
        const labelClass = index > 0 ? 'label-hidden' : '';
        const path = `invitees[${index}]` as const;
        // the validators run only while the manual form is shown
        const ifManual = (validate: () => string | undefined) => this.addMode === 'manual' ? validate() : undefined;
        return html`
            <div class="row custom invitee">
                <span class="invitee-number">#${index + 1}</span>
                ${this.#form.field({
                    name: `${path}.code`,
                    validators: {
                        onChange: ({value}: {value: unknown}) => ifManual(() => this.duplicateCodeError(index, value)),
                        onSubmit: ({value}: {value: unknown}) => ifManual(() => this.duplicateCodeError(index, value)),
                    },
                }, field => validatedSelect(field, submitted, {
                    id: `invitee-code-${index}`,
                    className: `invitee-code ${labelClass}`.trim(),
                    label: 'Code',
                    optional: true,
                    helpText: 'Automatic assigns a free code',
                }, html`
                    <sl-option value="">Automatic</sl-option>
                    ${freeCodes.map(code => html`<sl-option value=${code}>${code}</sl-option>`)}
                `))}
                ${this.#form.field({ name: `${path}.assignee`, validators: validateWith(value => ifManual(() => requiredValue(value))) },
                    field => validatedInput(field, submitted, {
                        id: `invitee-assignee-${index}`,
                        className: `invitee-assignee ${labelClass}`.trim(),
                        label: 'Assignee',
                    }))}
                ${this.#form.field({ name: `${path}.email`, validators: validateWith(value => ifManual(() => emailAddress(value))) },
                    field => validatedInput(field, submitted, {
                        id: `invitee-email-${index}`,
                        className: `invitee-email ${labelClass}`.trim(),
                        label: 'Email',
                        type: 'email',
                        prefixIcon: 'envelope',
                    }))}
                ${this.#form.field({ name: `${path}.language`, validators: validateWith(value => ifManual(() => requiredValue(value))) },
                    field => validatedSelect(field, submitted, {
                        id: `invitee-language-${index}`,
                        className: `invitee-language ${labelClass}`.trim(),
                        label: 'Language',
                        placeholder: 'Select a language',
                    }, html`
                        ${event.contentLanguages.map(language => html`<sl-option value=${language.locale}>${language.displayLanguage}</sl-option>`)}
                    `))}
                <sl-icon-button class="remove-invitee" name="trash" label=${`Remove invitee ${index + 1}`}
                                ?disabled=${this.#form.api.state.values.invitees.length === 1}
                                @click=${() => this.removeInvitee(index)}></sl-icon-button>
            </div>
        `;
    }

    private renderCsvImport(event: AlfioEvent): TemplateResult {
        return csvImport({
            templateDescription: 'The first line is the header, then one line per invitee. Leave the code blank to assign a free one automatically.',
            onDownloadTemplate: () => downloadCsv(`${event.shortName}-invitations.csv`, SpecialPriceService.template()),
            onFileChange: (e: FileUploadChangeEvent) => this.onFileChange(e),
            error: this.uploadError,
            specs: this.renderFileSpecs(),
        });
    }

    private renderFileSpecs(): TemplateResult {
        return html`
            <div class="bulk-specs">
                <p class="text-muted">The template already follows these rules: you need them only if the file is generated by another system.</p>
                <p class="text-muted">
                    A CSV file <strong>with header</strong>, using commas (<code>,</code>) as separator,
                    double quotes (<code>"</code>) as quote character and backslash (<code>\\</code>) as escape character.
                </p>
                <p class="text-muted">
                    The file must be <strong>UTF-8</strong> encoded, so that names like Müller or 田中 are read correctly:
                    the template already is — in Excel, keep saving it as "CSV UTF-8".
                </p>
                <pre>${CSV_HEADER.map((name, index) => html`${when(index > 0, () => ',')}<code>${name}</code>`)}
<code>ABCD</code>,<code>Name Surname</code>,<code>email@host.tld</code>,<code>en</code>

the first line is the header, then one line per invitee:

<code>code</code> is the code to assign, blank if automatic
<code>assignee</code> is the full name of the invitee
<code>email</code> is the email of the invitee (the invitation is sent to this address)
<code>language</code> is the ISO language (e.g. it, en, de) of the message</pre>
            </div>
        `;
    }

    private renderPreview(): TemplateResult {
        const preview = this.preview ?? [];
        return sectionCard({
            icon: 'eye',
            title: 'Review the invitations',
            badge: html`<sl-badge id="preview-count" variant="primary" pill>${preview.length}</sl-badge>`,
        }, html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            <th>Code</th>
                            <th>Assignee</th>
                            <th>Email</th>
                            <th>Language</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(preview, (_, index) => index, row => html`
                            <tr class="invitation">
                                <td>${row.code}</td>
                                <td>${row.assignee}</td>
                                <td>${row.email}</td>
                                <td>${row.language}</td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>
            ${when(this.sendError, () => html`
                <sl-alert id="send-error" variant="danger" open>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${this.sendError}
                </sl-alert>
            `)}
            <div class="preview-footer">
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button id="cancel-send" variant="default" size="large" @click=${this.cancelPreview}>Cancel</sl-button>
                    <div></div>
                    <sl-button id="send-button" variant="warning" size="large" ?loading=${this.sending} @click=${this.send}>Send</sl-button>
                </div>
            </div>
        `);
    }

    private renderHistory(event: AlfioEvent, sentCodes: SpecialPrice[]): TemplateResult {
        return sectionCard({
            icon: 'clock-history',
            title: 'Sent invitations',
            badge: html`<sl-badge id="sent-count" variant="primary" pill>${sentCodes.length}</sl-badge>`,
        }, html`
            ${when(sentCodes.length > 0, () => html`
                <div class="table-responsive">
                    <table class="table table-striped">
                        <thead>
                            <tr>
                                <th>Code</th>
                                <th>Recipient</th>
                                <th>Sent on</th>
                                <th><span class="sr-only">Actions</span></th>
                            </tr>
                        </thead>
                        <tbody>
                            ${repeat(sentCodes, code => code.id, code => html`
                                <tr class="sent-code">
                                    <td>${code.code}</td>
                                    <td>${code.recipientName} <span class="text-muted">&lt;${code.recipientEmail}&gt;</span></td>
                                    <td><alfio-format-date date=${code.sentTimestamp ?? ''} time-zone=${event.timeZone}>—</alfio-format-date></td>
                                    <td class="actions-cell">
                                        ${when(code.status === 'FREE', () => html`
                                            <sl-button class="clear-recipient" variant="danger" size="small" @click=${() => this.clearRecipient(code)}>
                                                <sl-icon slot="prefix" name="trash"></sl-icon>
                                                Clear
                                            </sl-button>
                                        `)}
                                    </td>
                                </tr>
                            `)}
                        </tbody>
                    </table>
                </div>
            `, () => emptyState('No invitations have been sent yet.'))}
        `);
    }

    private onTabShow = (e: CustomEvent<{ name: string }>): void => {
        this.selectedTab = e.detail.name as TabName;
    };

    private resetForm(event: AlfioEvent): void {
        this.defaultLanguage = event.firstContentLanguage?.locale ?? event.contentLanguages[0]?.locale ?? '';
        const values: InvitationsFormValues = { invitees: [this.emptyInvitee()] };
        this.#form.api.update({ ...this.#form.api.options, defaultValues: values });
        this.#form.api.reset(values);
        this.formReady = true;
    }

    private emptyInvitee(): InviteeDraft {
        return { code: '', assignee: '', email: '', language: this.defaultLanguage };
    }

    private changeInputMode(mode: InputMode): void {
        this.addMode = mode;
        this.uploadError = null;
        this.reviewError = null;
    }

    private addInvitee = (): void => {
        this.#form.api.pushFieldValue('invitees', this.emptyInvitee());
    };

    private removeInvitee(index: number): void {
        // moves also the state of the validated fields of the following invitees
        void this.#form.api.removeFieldValue('invitees', index);
    }

    /**
     * The error of a code field when the same code is picked in more than one row: only the rows after the
     * first one are marked, so of two rows with the same code the second one gets the error.
     */
    private duplicateCodeError(index: number, value: unknown): string | undefined {
        const code = String(value ?? '').trim();
        if (code === '') {
            return undefined;
        }
        const firstRowWithCode = this.#form.api.state.values.invitees.findIndex(i => i.code.trim() === code);
        if (firstRowWithCode !== -1 && firstRowWithCode < index) {
            return 'Code already picked';
        }
        return undefined;
    }

    /**
     * Validates the manual form; if valid, the form's onSubmit prepares the invitations.
     */
    private review = (): void => {
        this.reviewError = null;
        if (!submitValidated(this.#form.api)) {
            this.reviewError = INVALID_FORM_MESSAGE;
            focusFirstError(this.renderRoot);
        }
    };

    /**
     * Builds the CSV from the invitees of the manual form and sends it to the backend like an uploaded file:
     * the rows are then shown for review before sending.
     */
    private async reviewInvitations(invitees: InviteeDraft[]): Promise<void> {
        this.reviewError = null;
        this.uploading = true;
        try {
            // "Automatic" picks the next free code of the category here, so that a code is never
            // assigned to two invitees
            const freeCodes = this.freeCodes();
            const used = new Set<string>();
            const rows: SendCodeModification[] = invitees.map(i => {
                let code = i.code.trim();
                if (code === '') {
                    code = freeCodes.find(c => !used.has(c)) ?? '';
                }
                if (code !== '') {
                    used.add(code);
                }
                return { code: code === '' ? null : code, assignee: i.assignee, email: i.email, language: i.language };
            });
            const csv = invitationsCsv(rows);
            const codes = await SpecialPriceService.linkCodesFromCsv(this.eventName, Number(this.categoryId), csv);
            if (codes.length === 0) {
                this.reviewError = 'There is no invitee to send.';
                return;
            }
            this.preview = codes;
        } catch (error) {
            this.reviewError = errorMessage(error, 'The invitations cannot be prepared. Please check the rows and retry.');
        } finally {
            this.uploading = false;
        }
    }

    private async onFileChange(e: FileUploadChangeEvent): Promise<void> {
        const file = e.detail.file;
        this.preview = null;
        this.uploadError = null;
        this.reviewError = null;
        this.sendError = null;
        if (file == null) {
            return;
        }
        this.uploading = true;
        try {
            const codes = await SpecialPriceService.linkCodes(this.eventName, Number(this.categoryId), file);
            if (codes.length === 0) {
                this.uploadError = 'The file does not contain any invitation.';
                return;
            }
            this.preview = codes;
        } catch (error) {
            this.uploadError = errorMessage(error, 'The file cannot be read.');
        } finally {
            this.uploading = false;
        }
    }

    private cancelPreview = (): void => {
        this.preview = null;
        this.sendError = null;
    };

    private async send(): Promise<void> {
        if (this.sending || this.preview == null) {
            return;
        }
        this.sending = true;
        this.sendError = null;
        try {
            await SpecialPriceService.sendCodes(this.eventName, Number(this.categoryId), this.preview);
            dispatchFeedback({ type: 'success', message: 'Invitations have been sent.' }, this);
            this.preview = null;
            await this.loadDataTask.run();
            // start over with empty rows: the codes picked in the form are taken now
            const event = this.loadDataTask.value?.event;
            if (event != null) {
                this.resetForm(event);
            }
        } catch (error) {
            this.sendError = errorMessage(error, 'The invitations have not been sent. Please retry.');
        } finally {
            this.sending = false;
        }
    }

    private async clearRecipient(code: SpecialPrice): Promise<void> {
        const confirmed = await ConfirmationDialogService.requestConfirm(
            'Clear the recipient',
            `About to clear the recipient for code ${code.code}. The code becomes free again. Are you sure?`,
            'danger');
        if (!confirmed) {
            return;
        }
        try {
            await SpecialPriceService.clearRecipient(this.eventName, Number(this.categoryId), code.id);
            dispatchFeedback({ type: 'success', message: `The recipient of code ${code.code} has been cleared.` }, this);
            await this.loadDataTask.run();
        } catch (error) {
            dispatchFeedback({ type: 'danger', message: errorMessage(error, 'The recipient has not been cleared.') }, this);
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-send-invitations': SendInvitations;
    }
}
