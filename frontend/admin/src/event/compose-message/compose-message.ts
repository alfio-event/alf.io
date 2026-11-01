import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
import type {SlDialog, SlSelect, SlSwitch, SlTabGroup, SlTextarea} from '@shoelace-style/shoelace';
import {AlfioEvent} from '../../model/event.ts';
import {Organization} from '../../model/organization.ts';
import {MessageModification, MessagePreview} from '../../model/custom-message.ts';
import {EventService} from '../../service/event.ts';
import {CustomMessageService} from '../../service/custom-message.ts';
import {ConfirmationDialogService} from '../../service/confirmation-dialog.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {ContentLanguage} from '../../model/purchase-context.ts';
import {base, dialog, form, listGroup, modernLayout, retroCompat, row, textColors} from '../../styles.ts';
import {
    fieldError,
    focusFirstError,
    FormMountController,
    INVALID_FORM_MESSAGE,
    requiredValue,
    submitValidated,
    validatedInput,
    validateWith,
} from '../../components/form-validation.ts';

interface ComposeFormValues {
    // the selected categories, if the message is not for all the attendees
    categoryIds: string[];
    // one for each language of the event
    messages: MessageModification[];
}

interface LoadData {
    event: AlfioEvent;
    organization: Organization;
}

interface TemplateVariable {
    name: string;
    description: (data: LoadData) => string;
    tooltip?: string;
}

interface AttachmentTexts {
    label: string;
    helpText: string;
    previewNotice: string;
    previewIcon: string;
}

const TICKET_ATTACHMENT: AttachmentTexts = {
    label: 'Attach the attendee\'s ticket',
    helpText: 'The ticket will be sent as attachment',
    previewNotice: 'Ticket will be sent as attachment',
    previewIcon: 'paperclip'
};

const ONLINE_ACCESS_INFO: AttachmentTexts = {
    label: 'Include access information',
    helpText: 'Access information will be appended at the end of the email',
    previewNotice: 'Information on how to join the event online will be included at the end of the message',
    previewIcon: 'link-45deg'
};

/**
 * Restricts what the e-mail preview can load: styles, fonts and images only. Scripts are already blocked by the iframe sandbox,
 * this is an additional safety net in case the sandbox is relaxed in the future.
 */
const EMAIL_PREVIEW_CSP = "default-src 'none'; img-src http: https: data:; style-src 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com";

const TEMPLATE_VARIABLES: TemplateVariable[] = [
    {name: 'eventName', description: d => d.event.displayName, tooltip: 'the name of the event'},
    {name: 'fullName', description: () => 'John Doe', tooltip: 'Either the name of the attendee or the name of the reservation owner'},
    {name: 'organizationName', description: d => d.organization.name, tooltip: 'The name of the organization'},
    {name: 'organizationEmail', description: d => d.organization.email, tooltip: 'The organization e-mail address'},
    {name: 'reservationID', description: () => 'The reservation identifier'},
    {name: 'reservationURL', description: () => 'The URL of the reservation page'},
    {name: 'ticketURL', description: () => 'The URL of the ticket'},
    {name: 'ticketID', description: () => 'The ticket identifier'},
];

@customElement('alfio-compose-message')
export class ComposeMessage extends LitElement {

    @property({type: String, attribute: 'data-event-name'})
    eventName?: string;

    @query('sl-dialog#preview-dialog')
    previewDialog!: SlDialog;

    @query('sl-tab-group#messages-editor')
    messagesEditor!: SlTabGroup;

    readonly #form = new TanStackFormController(this, {
        defaultValues: { categoryIds: [], messages: [] } as ComposeFormValues,
        onSubmit: async ({value}) => {
            await this.showPreview(value);
        },
    });

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    @state()
    private submitError: string | null = null;

    private languages: ContentLanguage[] = [];

    @state()
    private allAttendees = true;


    @state()
    private attachTicket = false;

    @state()
    private preview: MessagePreview | null = null;

    @state()
    private loadingPreview = false;

    @state()
    private sending = false;

    private loadDataTask = new Task(this,
        async ([eventName]) => {
            const [eventWithOrganization, languages] = await Promise.all([
                EventService.load(eventName),
                CustomMessageService.loadLanguages(eventName)
            ]);
            this.languages = languages;
            this.#form.api.reset({
                categoryIds: [],
                messages: languages.map(l => ({
                    locale: l.language,
                    subject: '',
                    text: '',
                    subjectExample: 'An important message from {{eventName}}',
                    textExample: '{{organizationName}} <{{organizationEmail}}>',
                    attachTicket: false
                })),
            });
            this.allAttendees = true;
            this.attachTicket = false;
            return eventWithOrganization;
        },
        () => [this.eventName ?? ''] as const
    );

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        form,
        dialog,
        row,
        listGroup,
        modernLayout,
        css`
            /* a language whose fields have errors, see messageHasError() */
            sl-tab.has-error::part(base) {
                color: var(--sl-color-danger-600);
            }
            :host {
                display: block;
                --alfio-section-body-padding: var(--sl-spacing-medium);
            }

            .page-title-row {
                flex-direction: column;
                gap: var(--sl-spacing-2x-small);
            }

            .layout {
                display: grid;
                gap: var(--sl-spacing-large);
                align-items: start;
            }

            @media only screen and (min-width: 992px) {
                .layout {
                    grid-template-columns: minmax(0, 3fr) minmax(0, 1fr);
                }
            }

            .compose-form sl-switch {
                min-height: 0;
                padding-bottom: 0;
            }

            .compose-form sl-divider {
                margin: 0;
            }

            sl-tab-panel::part(base) {
                padding-bottom: 0;
            }

            .variables-intro {
                margin: 0 0 var(--sl-spacing-small);
                font-size: var(--sl-font-size-small);
            }

            .variables-list .list-group-item {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-x-small);
            }

            .variable {
                flex: 1;
                min-width: 0;
            }

            .variable code {
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-small);
                font-weight: var(--sl-font-weight-semibold);
                color: var(--sl-color-primary-700);
            }

            .variable small {
                display: block;
                color: var(--sl-color-neutral-600);
                font-size: var(--sl-font-size-small);
                overflow-wrap: anywhere;
            }

            .affected-users {
                margin-bottom: var(--sl-spacing-medium);
            }

            .preview-text::part(textarea) {
                white-space: pre-wrap;
            }

            .preview-label {
                display: block;
                margin-bottom: var(--sl-spacing-3x-small);
                font-size: var(--sl-input-label-font-size-medium);
            }

            .preview-html {
                display: block;
                width: 100%;
                height: 32rem;
                border: solid var(--sl-input-border-width) var(--sl-input-border-color);
                border-radius: var(--sl-input-border-radius-medium);
                background: white;
            }
        `
    ];

    render(): TemplateResult {
        return this.loadDataTask.render({
            pending: () => html`<div class="empty-state"><sl-spinner style="font-size: var(--sl-font-size-2x-large);"></sl-spinner></div>`,
            error: () => html`<sl-alert open variant="danger" class="first-element"><sl-icon name="exclamation-triangle" slot="icon"></sl-icon>Failed to load event data.</sl-alert>`,
            complete: data => this.renderContent(data)
        }) ?? html``;
    }

    private renderContent(data: LoadData): TemplateResult {
        const attachment = this.attachmentTexts(data.event);
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>Send a custom message to attendees</h1>
                    <span class="text-muted">Write a custom message and send it to all the attendees or only to those of specific categories</span>
                </div>
                <hr class="page-separator">
                <div class="layout">
                    <section class="section-card">
                        <div class="section-header">
                            <sl-icon name="envelope"></sl-icon>
                            <h3>Message</h3>
                        </div>
                        <div class="section-body">
                            <form class="compose-form form-stack" novalidate @submit=${(e: Event) => { e.preventDefault(); void this.submit(); }}>
                                <sl-switch name="allAttendees" help-text="Or select specific categories" .checked=${this.allAttendees}
                                           @sl-change=${(e: Event) => this.toggleAllAttendees((e.target as SlSwitch).checked)}>
                                    Send to all attendees
                                </sl-switch>
                                ${when(!this.allAttendees, () => this.#form.field({
                                    name: 'categoryIds',
                                    validators: validateWith(value => this.validateCategories(value)),
                                }, field => {
                                    const error = fieldError(field.state.meta, this.submitted);
                                    return html`
                                        <sl-select name="categories" label="Categories" placeholder="Select categories" multiple clearable required max-options-visible="3"
                                                   class=${classMap({ error: error != null })}
                                                   help-text=${error ?? ''}
                                                   .value=${field.state.value}
                                                   @sl-change=${(e: Event) => field.handleChange([...(e.target as SlSelect).value as string[]])}
                                                   @sl-blur=${() => field.handleBlur()}>
                                            <sl-icon name="people" slot="prefix"></sl-icon>
                                            ${repeat(data.event.ticketCategories, c => c.id, c => html`<sl-option value=${String(c.id)}>${c.name}</sl-option>`)}
                                        </sl-select>
                                    `;
                                }))}
                                <!-- keyed by index: the names of the validated fields contain the index of the message -->
                                <sl-tab-group id="messages-editor">
                                    ${repeat(this.messages, (_, index) => index, (m, index) => html`
                                        <sl-tab slot="nav" panel=${m.locale} class=${classMap({ 'has-error': this.messageHasError(index) })}>${this.languageName(m.locale)}</sl-tab>
                                    `)}
                                    ${repeat(this.messages, (_, index) => index, (m, index) => html`
                                        <sl-tab-panel name=${m.locale}>
                                            <div class="form-stack">
                                                ${this.#form.field({ name: `messages[${index}].subject`, validators: validateWith(value => requiredValue(value)) },
                                                    field => validatedInput(field, this.submitted, { name: `subject-${m.locale}`, label: 'Subject', placeholder: m.subjectExample }))}
                                                ${this.#form.field({ name: `messages[${index}].text`, validators: validateWith(value => requiredValue(value)) }, field => {
                                                    const error = fieldError(field.state.meta, this.submitted);
                                                    return html`
                                                        <sl-textarea label="Message" name="message-${m.locale}" rows="15" resize="auto" required
                                                                     class=${classMap({ error: error != null })}
                                                                     help-text=${error ?? ''}
                                                                     placeholder=${m.textExample} .value=${field.state.value}
                                                                     @sl-input=${(e: Event) => field.handleChange((e.target as SlTextarea).value)}
                                                                     @sl-blur=${() => field.handleBlur()}></sl-textarea>
                                                    `;
                                                })}
                                            </div>
                                        </sl-tab-panel>
                                    `)}
                                </sl-tab-group>
                                <sl-switch name="attachTicket" .checked=${this.attachTicket}
                                           help-text=${attachment.helpText}
                                           @sl-change=${(e: Event) => { this.attachTicket = (e.target as SlSwitch).checked; }}>
                                    ${attachment.label}
                                </sl-switch>
                                ${when(this.submitError, () => html`
                                    <sl-alert id="submit-error" variant="danger" open>
                                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                                        ${this.submitError}
                                    </sl-alert>
                                `)}
                                <sl-divider></sl-divider>
                                <div class="row" style="--alfio-row-cols: 3">
                                    <sl-button variant="default" size="large" href="#/events/${data.event.shortName}/detail">
                                        Cancel
                                    </sl-button>
                                    <div></div>
                                    <sl-button id="preview-button" type="submit" variant="warning" size="large" ?loading=${this.loadingPreview}>
                                        <sl-icon name="eye" slot="prefix"></sl-icon>Preview
                                    </sl-button>
                                </div>
                            </form>
                        </div>
                    </section>
                    ${this.renderVariables(data)}
                </div>
            </div>
            ${this.renderPreviewDialog(data)}
        `;
    }

    private renderVariables(data: LoadData): TemplateResult {
        return html`
            <section class="section-card">
                <div class="section-header">
                    <sl-icon name="braces"></sl-icon>
                    <h3>Available variables</h3>
                </div>
                <div class="section-body">
                    <p class="variables-intro text-muted">
                        The syntax is defined by <a href="https://mustache.github.io/mustache.5.html" target="_blank" rel="noopener">Mustache</a>,
                        with some <a href="https://github.com/samskivert/jmustache#limitations" target="_blank" rel="noopener">limitations</a>
                    </p>
                    <ul class="variables-list list-group">
                        ${TEMPLATE_VARIABLES.map(v => html`
                            <li class="list-group-item">
                                <div class="variable">
                                    ${when(v.tooltip,
                                        () => html`<sl-tooltip content=${v.tooltip!}><code>{{${v.name}}}</code></sl-tooltip>`,
                                        () => html`<code>{{${v.name}}}</code>`)}
                                    <small>${v.description(data)}</small>
                                </div>
                                <sl-copy-button hoist value="{{${v.name}}}" copy-label="Copy {{${v.name}}}"></sl-copy-button>
                            </li>
                        `)}
                    </ul>
                </div>
            </section>
        `;
    }

    private renderPreviewDialog(data: LoadData): TemplateResult {
        const categoryNames = data.event.ticketCategories
            .filter(c => this.categoryIds.includes(c.id))
            .map(c => `"${c.name}"`)
            .join(', ');
        const attachment = this.attachmentTexts(data.event);
        return html`
            <sl-dialog id="preview-dialog" class="responsive-dialog" style="--alfio-dialog-max-width: 56rem" label="Message Preview"
                       @sl-request-close=${(e: CustomEvent) => {
                           // a send in progress must not be interrupted
                           if (this.sending || e.detail.source === 'overlay') {
                               e.preventDefault();
                           }
                       }}
                       @sl-after-hide=${(e: Event) => { if (e.target === this.previewDialog) { this.preview = null; } }}>
                <div slot="label" class="dialog-title">
                    <sl-icon name="eye"></sl-icon>
                    <strong>Message Preview</strong>
                </div>
                ${when(this.preview, preview => html`
                    <sl-alert open variant=${this.affectedUsersVariant(preview.affectedUsers)} class="affected-users">
                        <sl-icon name="people" slot="icon"></sl-icon>
                        Potentially affected users: <strong>${preview.affectedUsers}</strong>${when(categoryNames, () => html` of ${this.categoriesLabel()} ${categoryNames}`)}
                    </sl-alert>
                    <sl-tab-group>
                        ${repeat(preview.preview, m => m.locale, m => html`<sl-tab slot="nav" panel=${m.locale}>${this.languageName(m.locale)}</sl-tab>`)}
                        ${repeat(preview.preview, m => m.locale, m => html`
                            <sl-tab-panel name=${m.locale}>
                                <div class="form-stack">
                                    <sl-input class="preview-subject" label="Subject" readonly filled .value=${m.subjectExample}></sl-input>
                                    ${when(preview.htmlPreview?.[m.locale], htmlPreview => html`
                                        <div>
                                            <span class="preview-label">Message</span>
                                            ${this.renderHtmlPreview(htmlPreview)}
                                        </div>
                                        <sl-details summary="Plain text version">
                                            <sl-textarea class="preview-text" readonly filled resize="auto" rows="3" .value=${m.textExample}></sl-textarea>
                                        </sl-details>
                                    `, () => html`
                                        <sl-textarea class="preview-text" label="Message" readonly filled resize="auto" rows="3" .value=${m.textExample}></sl-textarea>
                                    `)}
                                    ${when(m.attachTicket, () => html`
                                        <sl-alert open variant="neutral">
                                            <sl-icon name=${attachment.previewIcon} slot="icon"></sl-icon>
                                            ${attachment.previewNotice}
                                        </sl-alert>
                                    `)}
                                </div>
                            </sl-tab-panel>
                        `)}
                    </sl-tab-group>
                `)}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" ?disabled=${this.sending} @click=${() => this.previewDialog.hide()}>
                            Cancel
                        </sl-button>
                        <div></div>
                        <sl-button id="send-button" variant="warning" size="large" ?loading=${this.sending} @click=${() => this.send()}>
                            <sl-icon name="send" slot="prefix"></sl-icon>Send
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    /**
     * The e-mail is rendered in a sandboxed iframe with no permissions other than opening links in a new tab:
     * it has an opaque origin, so it cannot access the admin session, and it cannot run scripts or submit forms.
     */
    private renderHtmlPreview(htmlPreview: string): TemplateResult {
        return html`
            <iframe class="preview-html" title="E-mail preview" referrerpolicy="no-referrer"
                    sandbox="allow-popups allow-popups-to-escape-sandbox"
                    .srcdoc=${this.withPreviewCsp(htmlPreview)}></iframe>
        `;
    }

    private withPreviewCsp(htmlPreview: string): string {
        const meta = `<meta http-equiv="Content-Security-Policy" content="${EMAIL_PREVIEW_CSP}">`;
        const head = /<head(\s[^>]*)?>/i.exec(htmlPreview);
        if (head == null) {
            return meta + htmlPreview;
        }
        // the policy must be declared before any other resource
        const index = head.index + head[0].length;
        return htmlPreview.slice(0, index) + meta + htmlPreview.slice(index);
    }

    private languageName(locale: string): string {
        return this.languages.find(l => l.language === locale)?.displayLanguage ?? locale;
    }

    private attachmentTexts(event: AlfioEvent): AttachmentTexts {
        if (event.online) {
            return ONLINE_ACCESS_INFO;
        }
        return TICKET_ATTACHMENT;
    }

    private affectedUsersVariant(affectedUsers: number): 'primary' | 'warning' {
        if (affectedUsers > 0) {
            return 'primary';
        }
        return 'warning';
    }

    private categoriesLabel(): string {
        if (this.categoryIds.length > 1) {
            return 'categories';
        }
        return 'category';
    }

    private get messages(): MessageModification[] {
        return this.#form.api.state.values.messages;
    }

    /**
     * The selected categories, empty if the message is for all the attendees
     */
    private get categoryIds(): number[] {
        if (this.allAttendees) {
            return [];
        }
        return this.#form.api.state.values.categoryIds.map(Number);
    }

    private get submitted(): boolean {
        return this.#form.api.state.submissionAttempts > 0;
    }

    private validateCategories(value: unknown): string | undefined {
        // an empty selection would send the message to all the attendees
        if (!this.allAttendees && (!Array.isArray(value) || value.length === 0)) {
            return 'Please select at least one category';
        }
        return undefined;
    }

    // the tab of a language with an error is highlighted, since its fields may be hidden
    private messageHasError(index: number): boolean {
        return ['subject', 'text'].some(name => {
            const meta = this.#form.api.getFieldMeta(`messages[${index}].${name}` as `messages[${number}].subject`);
            return meta != null && fieldError(meta, this.submitted) != null;
        });
    }

    private toggleAllAttendees(allAttendees: boolean): void {
        this.allAttendees = allAttendees;
        if (allAttendees) {
            this.#form.api.setFieldValue('categoryIds', [], { dontUpdateMeta: true });
        }
    }

    private async submit(): Promise<void> {
        if (this.loadingPreview) {
            return;
        }
        this.submitError = null;
        if (await submitValidated(this.#form.api)) {
            return;
        }
        this.submitError = INVALID_FORM_MESSAGE;
        // shows the first language with an error: its fields are hidden in the other tabs
        const invalid = this.messages.findIndex((_, index) => this.messageHasError(index));
        if (invalid >= 0) {
            this.messagesEditor.show(this.messages[invalid].locale);
        }
        await this.updateComplete;
        focusFirstError(this.renderRoot);
    }

    /**
     * Called by the form once all the fields are valid
     */
    private async showPreview(values: ComposeFormValues): Promise<void> {
        if (this.eventName == null) {
            return;
        }
        this.loadingPreview = true;
        try {
            const messages = values.messages.map(m => ({...m, attachTicket: this.attachTicket}));
            const response = await CustomMessageService.preview(this.eventName, this.categoryIds, messages);
            if (response.ok) {
                this.preview = await response.json();
                await this.updateComplete;
                await this.previewDialog.show();
            } else {
                dispatchFeedback({type: 'danger', message: await this.errorMessage(response, 'Cannot generate the preview')}, this);
            }
        } catch {
            dispatchFeedback({type: 'danger', message: 'Cannot generate the preview'}, this);
        } finally {
            this.loadingPreview = false;
        }
    }

    private async send(): Promise<void> {
        const preview = this.preview;
        if (this.sending || preview == null || this.eventName == null) {
            return;
        }
        if (preview.affectedUsers === 0) {
            const confirmed = await ConfirmationDialogService.requestConfirm('No recipients',
                'No one will receive this message. Do you really want to continue?', 'warning');
            if (!confirmed) {
                return;
            }
        }
        this.sending = true;
        try {
            const response = await CustomMessageService.send(this.eventName, this.categoryIds, preview.preview);
            if (response.ok) {
                this.sending = false;
                await this.previewDialog.hide();
                dispatchFeedback({type: 'success', message: 'Messages have been enqueued'}, this);
            } else {
                dispatchFeedback({type: 'danger', message: await this.errorMessage(response, 'Cannot send the messages')}, this);
            }
        } catch {
            dispatchFeedback({type: 'danger', message: 'Cannot send the messages'}, this);
        } finally {
            this.sending = false;
        }
    }

    private async errorMessage(response: Response, fallback: string): Promise<string> {
        let text = (await response.text()).trim();
        if (text.startsWith('"')) {
            // the error could be serialized as a JSON string
            text = JSON.parse(text);
        }
        if (text.length === 0 || text.startsWith('{')) {
            return fallback;
        }
        return text;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-compose-message': ComposeMessage;
    }
}
