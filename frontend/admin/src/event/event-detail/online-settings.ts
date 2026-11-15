import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {TanStackFormController} from '@tanstack/lit-form';
import type {SlDialog, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {AlfioEvent, AlfioMetadata} from '../../model/event.ts';
import {ContentLanguage, LocalizedContent} from '../../model/purchase-context.ts';
import {EventService} from '../../service/event.ts';
import {UtilService} from '../../service/util.ts';
import {errorMessage} from '../../service/helpers.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {sectionCard} from '../../components/section-card.ts';
import {
    emailAddress,
    focusFirstError,
    FormMountController,
    INVALID_FORM_MESSAGE,
    requiredHttpsUrl,
    requiredValue,
    submitValidated,
    validatedInput,
    validateWith,
} from '../../components/form-validation.ts';
import type {MultiLanguageTextChange} from '../../components/multi-language-text.ts';
import '../../components/multi-language-text.ts';
import '../../components/localized-markdown.ts';
import '../../components/format-date.ts';
import {dateTimeLocalToModification, wallClockToDateTimeLocal} from '../event-create/category-dialog.ts';
import {badges, dialog, form, modernLayout, modernTable, row, textColors} from '../../styles.ts';

/**
 * What the online settings dialog edits: the settings of the event, or of one of its categories
 */
export interface OnlineSettingsContext {
    eventName: string;
    // set when editing the settings of a category instead of the event's
    categoryId: number | null;
    label: string;
    metadata: AlfioMetadata | null | undefined;
    // datetime-local values, used as default validity of a new meeting link
    eventBegin: string;
    eventEnd: string;
    // all the languages of the instance, and the ones of the event
    languages: ContentLanguage[];
    contextLanguages: ContentLanguage[];
}

interface CallLinkDraft {
    link: string;
    // datetime-local values
    validFrom: string;
    validTo: string;
}

interface OnlineSettingsValues {
    callLinks: CallLinkDraft[];
    requirements: LocalizedContent;
}

function emptyValues(): OnlineSettingsValues {
    return {callLinks: [], requirements: {}};
}

/**
 * The meeting links of an online event (or of a category) and the prerequisites for attending.
 * open() resolves with true once the settings have been saved, with false if the dialog was closed.
 */
@customElement('alfio-online-settings-dialog')
export class OnlineSettingsDialog extends LitElement {

    @query('sl-dialog') private dialog?: SlDialog;

    @state() private context: OnlineSettingsContext | null = null;
    @state() private saving = false;
    @state() private submitError: string | null = null;

    private resolve: ((saved: boolean) => void) | null = null;
    private saved = false;

    readonly #form = new TanStackFormController(this, {
        defaultValues: emptyValues(),
        onSubmit: async ({value}) => {
            await this.save(value);
        },
    });

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    static readonly styles = [
        dialog,
        modernLayout,
        form,
        row,
        textColors,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 60rem;
            }
            .dialog-form {
                display: grid;
                gap: var(--sl-spacing-large);
            }
            h3 {
                font-size: var(--sl-font-size-medium);
                margin: 0 0 var(--sl-spacing-2x-small);
            }
            .group-help {
                margin: 0 0 var(--sl-spacing-small);
            }
            .links {
                display: grid;
                gap: var(--sl-spacing-x-small);
            }
            .link-row {
                --alfio-custom-row-cols-layout: minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1fr) auto;
                --alfio-column-gap: var(--sl-spacing-small);
                align-items: start;
            }
            .link-row sl-input {
                margin-top: 0;
            }
            /* aligns the remove button with the inputs: the first row has the labels */
            .link-row sl-icon-button {
                margin-top: var(--sl-spacing-2x-small);
            }
            .link-row:first-child sl-icon-button {
                margin-top: calc(var(--sl-input-label-font-size-medium) * 1.5 + var(--sl-spacing-3x-small) + var(--sl-spacing-2x-small));
            }
            .add-link {
                justify-self: start;
            }
            .add-link::part(base) {
                padding-inline-start: 0;
            }
            .add-link::part(label) {
                padding-inline-start: var(--sl-spacing-x-small);
            }
            .footer-error {
                color: var(--sl-color-danger-600);
                font-size: var(--sl-font-size-small);
                text-align: end;
                margin-top: var(--sl-spacing-x-small);
            }
            @media (max-width: 767px) {
                .link-row {
                    padding-bottom: var(--sl-spacing-small);
                    border-bottom: 1px solid var(--sl-color-gray-200);
                }
                .link-row .label-hidden::part(form-control-label) {
                    position: static;
                    width: auto;
                    height: auto;
                    clip: auto;
                }
                .link-row sl-icon-button,
                .link-row:first-child sl-icon-button {
                    margin-top: 0;
                    justify-self: end;
                }
            }
        `,
    ];

    /**
     * Opens the dialog with the current settings; a new configuration starts with one meeting link,
     * valid for the whole event
     */
    public open(context: OnlineSettingsContext): Promise<boolean> {
        this.context = context;
        this.saving = false;
        this.submitError = null;
        this.saved = false;
        const values = this.valuesOf(context);
        this.#form.api.update({...this.#form.api.options, defaultValues: values});
        this.#form.api.reset(values);
        const promise = new Promise<boolean>((resolve) => {
            this.resolve = resolve;
        });
        void this.updateComplete.then(() => this.dialog?.show());
        return promise;
    }

    render(): TemplateResult {
        const context = this.context;
        return html`
            <sl-dialog class="responsive-dialog online-settings-dialog" label=${context?.label ?? 'Online settings'}
                       @sl-request-close=${this.onRequestClose}
                       @sl-after-hide=${this.onAfterHide}>
                ${when(context != null, () => this.renderForm(context!), () => nothing)}
            </sl-dialog>
        `;
    }

    private renderForm(context: OnlineSettingsContext): TemplateResult {
        const submitted = this.#form.api.state.submissionAttempts > 0;
        const links = this.#form.api.state.values.callLinks;
        return html`
            <form novalidate class="dialog-form" @submit=${this.onSubmit}>
                <section>
                    <h3>Meeting links</h3>
                    <p class="group-help text-muted">Attendees are sent to the link that is active when they join.</p>
                    <div class="links">
                        ${repeat(links, (_, index) => index, (_, index) => this.renderLink(index, links.length, submitted))}
                        <sl-button class="add-link" variant="text" @click=${this.addLink}>
                            <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                            Add meeting link
                        </sl-button>
                    </div>
                </section>
                <section>
                    ${this.#form.field({name: 'requirements'}, (field) => html`
                        <alfio-multi-language-text class="requirements"
                                                   label="Prerequisites for attending"
                                                   .card=${false}
                                                   markdown
                                                   .languages=${context.languages}
                                                   .contextLanguages=${context.contextLanguages}
                                                   .value=${field.state.value ?? {}}
                                                   @change=${(e: CustomEvent<MultiLanguageTextChange>) => field.handleChange(e.detail.value)}
                        ></alfio-multi-language-text>
                    `)}
                </section>
            </form>
            <div slot="footer">
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" @click=${this.close}>Cancel</sl-button>
                    <div></div>
                    <sl-button class="save-online-settings" variant="warning" size="large" ?loading=${this.saving}
                               @click=${this.onSubmit}>Save</sl-button>
                </div>
                ${when(this.submitError != null, () => html`<div class="footer-error">${this.submitError}</div>`, () => nothing)}
            </div>
        `;
    }

    private renderLink(index: number, count: number, submitted: boolean): TemplateResult {
        const path = `callLinks[${index}]` as const;
        // the labels of the first row act as column headers
        let labelClass = '';
        if (index > 0) {
            labelClass = 'label-hidden';
        }
        return html`
            <div class="row custom link-row">
                ${this.#form.field({name: `${path}.link`, validators: validateWith(requiredHttpsUrl)},
                    (field) => validatedInput(field, submitted, {
                        className: `meeting-link ${labelClass}`.trim(),
                        label: 'Meeting link',
                        type: 'url',
                        prefixIcon: 'link-45deg',
                    }))}
                ${this.#form.field({name: `${path}.validFrom`, validators: validateWith(requiredValue)},
                    (field) => validatedInput(field, submitted, {
                        className: `link-valid-from ${labelClass}`.trim(),
                        label: 'Active from',
                        type: 'datetime-local',
                    }))}
                ${this.#form.field({
                    name: `${path}.validTo`,
                    validators: {
                        onChangeListenTo: [`${path}.validFrom`],
                        onChange: ({value}) => this.validateValidTo(index, value),
                        onSubmit: ({value}) => this.validateValidTo(index, value),
                    },
                }, (field) => validatedInput(field, submitted, {
                    className: `link-valid-to ${labelClass}`.trim(),
                    label: 'Active until',
                    type: 'datetime-local',
                }))}
                <sl-icon-button class="remove-link" name="trash" label="Remove meeting link ${index + 1}"
                                ?disabled=${count <= 1}
                                @click=${() => this.removeLink(index)}></sl-icon-button>
            </div>
        `;
    }

    private validateValidTo(index: number, value: unknown): string | undefined {
        const missing = requiredValue(value);
        if (missing != null) {
            return missing;
        }
        const validFrom = this.#form.api.state.values.callLinks[index]?.validFrom ?? '';
        // datetime-local values compare as strings
        if (validFrom !== '' && String(value) <= validFrom) {
            return 'Please choose a time after the start';
        }
        return undefined;
    }

    private valuesOf(context: OnlineSettingsContext): OnlineSettingsValues {
        const existing = context.metadata?.onlineConfiguration?.callLinks ?? [];
        let callLinks: CallLinkDraft[] = existing.map((link) => ({
            link: link.link,
            validFrom: wallClockToDateTimeLocal(link.validFrom),
            validTo: wallClockToDateTimeLocal(link.validTo),
        }));
        if (callLinks.length === 0) {
            callLinks = [{link: '', validFrom: context.eventBegin, validTo: context.eventEnd}];
        }
        return {
            callLinks,
            requirements: {...(context.metadata?.requirementsDescriptions ?? {})},
        };
    }

    private addLink = (): void => {
        const links = this.#form.api.state.values.callLinks;
        let validFrom = this.context?.eventBegin ?? '';
        const last = links[links.length - 1];
        if (last != null && last.validTo !== '') {
            validFrom = last.validTo;
        }
        this.#form.api.pushFieldValue('callLinks', {link: '', validFrom, validTo: this.context?.eventEnd ?? ''});
    };

    private removeLink(index: number): void {
        // moves also the state of the validated fields of the following links
        void this.#form.api.removeFieldValue('callLinks', index);
    }

    private onSubmit = async (event: Event): Promise<void> => {
        event.preventDefault();
        if (this.saving) {
            return;
        }
        this.submitError = null;
        if (!await submitValidated(this.#form.api)) {
            this.submitError = INVALID_FORM_MESSAGE;
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    /**
     * Called by the form once all the fields are valid
     */
    private async save(values: OnlineSettingsValues): Promise<void> {
        const context = this.context;
        if (context == null) {
            return;
        }
        const requirementDescriptions: Record<string, string> = {};
        for (const [locale, text] of Object.entries(values.requirements ?? {})) {
            if (text != null && text.trim() !== '') {
                requirementDescriptions[locale] = text;
            }
        }
        this.saving = true;
        try {
            await EventService.updateOnlineSettings(context.eventName, context.categoryId, {
                callLinks: values.callLinks.map((link) => ({
                    link: link.link.trim(),
                    validFrom: dateTimeLocalToModification(link.validFrom)!,
                    validTo: dateTimeLocalToModification(link.validTo)!,
                })),
                requirementDescriptions,
            });
            this.saved = true;
            await this.dialog?.hide();
        } catch (error) {
            this.submitError = errorMessage(error, 'The online settings could not be saved');
        } finally {
            this.saving = false;
        }
    }

    private close = (): void => {
        void this.dialog?.hide();
    };

    // a click outside the dialog would discard what the user typed: only the close button and Cancel close it
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private onAfterHide = (event: Event): void => {
        // nested Shoelace components (e.g. tooltips, selects) emit their own sl-after-hide
        if (event.target !== event.currentTarget) {
            return;
        }
        this.context = null;
        this.resolve?.(this.saved);
        this.resolve = null;
    };
}

interface GuestValues {
    firstName: string;
    lastName: string;
    email: string;
}

/**
 * Asks the data of the guest a join link is created for (capability CREATE_GUEST_LINK).
 * open() resolves with the data, or with null if the dialog was closed.
 */
@customElement('alfio-guest-link-dialog')
export class GuestLinkDialog extends LitElement {

    @query('sl-dialog') private dialog?: SlDialog;

    @state() private isOpen = false;
    @state() private submitError: string | null = null;

    private resolve: ((guest: GuestValues | null) => void) | null = null;
    private result: GuestValues | null = null;

    readonly #form = new TanStackFormController(this, {
        defaultValues: {firstName: '', lastName: '', email: ''} as GuestValues,
        onSubmit: async ({value}) => {
            this.result = {...value};
            await this.dialog?.hide();
        },
    });

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    static readonly styles = [
        dialog,
        modernLayout,
        form,
        row,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 32rem;
            }
            .footer-error {
                color: var(--sl-color-danger-600);
                font-size: var(--sl-font-size-small);
                text-align: end;
                margin-top: var(--sl-spacing-x-small);
            }
        `,
    ];

    public open(): Promise<GuestValues | null> {
        this.result = null;
        this.submitError = null;
        this.#form.api.reset({firstName: '', lastName: '', email: ''});
        this.isOpen = true;
        const promise = new Promise<GuestValues | null>((resolve) => {
            this.resolve = resolve;
        });
        void this.updateComplete.then(() => this.dialog?.show());
        return promise;
    }

    render(): TemplateResult {
        const submitted = this.#form.api.state.submissionAttempts > 0;
        return html`
            <sl-dialog class="responsive-dialog" label="Create a join link for a guest"
                       @sl-request-close=${this.onRequestClose}
                       @sl-after-hide=${this.onAfterHide}>
                ${when(this.isOpen, () => html`
                    <form novalidate class="form-stack" @submit=${this.onSubmit}>
                        ${this.#form.field({name: 'firstName', validators: validateWith(requiredValue)},
                            (field) => validatedInput(field, submitted, {label: 'First name', className: 'guest-first-name'}))}
                        ${this.#form.field({name: 'lastName', validators: validateWith(requiredValue)},
                            (field) => validatedInput(field, submitted, {label: 'Last name', className: 'guest-last-name'}))}
                        ${this.#form.field({name: 'email', validators: validateWith(emailAddress)},
                            (field) => validatedInput(field, submitted, {label: 'E-mail', type: 'email', prefixIcon: 'envelope', className: 'guest-email'}))}
                    </form>
                `, () => nothing)}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => void this.dialog?.hide()}>Cancel</sl-button>
                        <div></div>
                        <sl-button variant="warning" size="large" @click=${this.onSubmit}>Create link</sl-button>
                    </div>
                    ${when(this.submitError != null, () => html`<div class="footer-error">${this.submitError}</div>`, () => nothing)}
                </div>
            </sl-dialog>
        `;
    }

    private onSubmit = async (event: Event): Promise<void> => {
        event.preventDefault();
        this.submitError = null;
        if (!await submitValidated(this.#form.api)) {
            this.submitError = INVALID_FORM_MESSAGE;
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private onAfterHide = (event: Event): void => {
        if (event.target !== event.currentTarget) {
            return;
        }
        this.isOpen = false;
        this.resolve?.(this.result);
        this.resolve = null;
    };
}

const STATIC_CAPABILITIES: Record<string, string> = {
    GENERATE_MEETING_LINK: 'Generate meeting link',
    CREATE_VIRTUAL_ROOM: 'Create virtual room',
    CREATE_GUEST_LINK: 'Create join link for a guest',
    CREATE_ANONYMOUS_GUEST_LINK: 'Create join link for an anonymous guest',
};

interface CapabilityAction {
    id: string;
    label: string;
    selector: string;
}

/**
 * The actions that the extensions offer for an online event: one per variant described by the
 * extension, or one per known capability
 */
export function capabilityActions(event: AlfioEvent): CapabilityAction[] {
    return (event.supportedCapabilities ?? []).flatMap((summary) => {
        if (summary.details != null && summary.details.length > 0) {
            return summary.details.map((details) => ({id: summary.capability, label: details.label, selector: details.selector ?? ''}));
        }
        const label = STATIC_CAPABILITIES[summary.capability];
        if (label == null) {
            return [];
        }
        return [{id: summary.capability, label, selector: ''}];
    });
}

/**
 * The online settings of an online or hybrid event, on the event detail page: meeting links,
 * prerequisites for attending, and the actions of the extensions. Dispatches a bubbling
 * "alfio-event-reload" event after a change.
 */
@customElement('alfio-event-online-settings')
export class EventOnlineSettings extends LitElement {

    @property({attribute: false}) event!: AlfioEvent;

    // all the languages of the instance
    @property({attribute: false}) languages: ContentLanguage[] = [];

    @state() private capabilityResult: string | null = null;
    @state() private runningCapability = false;

    static readonly styles = [
        badges,
        modernLayout,
        modernTable,
        textColors,
        css`
            :host {
                display: block;
                --alfio-section-body-padding: var(--sl-spacing-medium);
            }
            .section-body {
                display: grid;
                gap: var(--sl-spacing-medium);
            }
            h4 {
                margin: 0 0 var(--sl-spacing-x-small);
                font-size: var(--sl-font-size-medium);
            }
            .table {
                margin-bottom: 0;
            }
            .meeting-link {
                overflow-wrap: anywhere;
            }
            .capability-result {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                gap: var(--sl-spacing-small);
            }
            .capability-result code {
                overflow-wrap: anywhere;
            }
        `,
    ];

    render(): TemplateResult {
        const event = this.event;
        const metadata = event.metadata;
        const links = metadata?.onlineConfiguration?.callLinks ?? [];
        const requirements = metadata?.requirementsDescriptions ?? {};
        const actions = capabilityActions(event);
        const canEdit = event.visibleForCurrentUser;
        const headerActions = html`
            ${when(canEdit && actions.length > 0, () => html`
                <sl-dropdown hoist placement="bottom-end">
                    <sl-button slot="trigger" class="capabilities" size="small" caret ?loading=${this.runningCapability}>
                        <sl-icon slot="prefix" name="plug"></sl-icon>
                        Extensions
                    </sl-button>
                    <sl-menu @sl-select=${(e: CustomEvent) => void this.runCapability(actions[Number(e.detail.item.value)])}>
                        ${repeat(actions, (action) => `${action.id}-${action.selector}`, (action, index) => html`
                            <sl-menu-item value=${String(index)}>${action.label}</sl-menu-item>
                        `)}
                    </sl-menu>
                </sl-dropdown>
            `, () => nothing)}
            ${when(canEdit, () => html`
                <sl-button class="edit-online-settings" size="small" @click=${this.edit}>
                    <sl-icon slot="prefix" name="pencil"></sl-icon>
                    Edit
                </sl-button>
            `, () => nothing)}
        `;
        return html`
            ${sectionCard({icon: 'camera-video', title: 'Online settings', actions: headerActions}, html`
                ${when(links.length === 0, () => html`
                    <sl-alert open variant="warning" class="missing-meeting-links">
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        The meeting links are missing: add them before
                        <alfio-format-date date=${event.formattedBegin} time-zone=${event.timeZone}></alfio-format-date>,
                        or the attendees won't be able to join.
                    </sl-alert>
                `, () => html`
                    <div>
                        <h4>Meeting links</h4>
                        <div class="table-responsive">
                            <table class="table">
                                <thead>
                                    <tr>
                                        <th>Link</th>
                                        <th>Active from</th>
                                        <th>Active until</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${repeat(links, (link, index) => `${index}-${link.link}`, (link) => html`
                                        <tr>
                                            <td class="meeting-link"><a href=${link.link} target="_blank" rel="noopener">${link.link}</a></td>
                                            <td><alfio-format-date date=${link.validFrom} time-zone=${event.timeZone}></alfio-format-date></td>
                                            <td><alfio-format-date date=${link.validTo} time-zone=${event.timeZone}></alfio-format-date></td>
                                        </tr>
                                    `)}
                                </tbody>
                            </table>
                        </div>
                    </div>
                `)}
                ${when(Object.keys(requirements).length > 0, () => html`
                    <div>
                        <h4>Prerequisites for attending</h4>
                        <alfio-localized-markdown .value=${requirements} .languages=${event.contentLanguages ?? []}></alfio-localized-markdown>
                    </div>
                `, () => nothing)}
                ${when(this.capabilityResult != null, () => html`
                    <sl-alert open variant="primary" closable class="capability-result-alert"
                              @sl-after-hide=${() => { this.capabilityResult = null; }}>
                        <sl-icon slot="icon" name="info-circle"></sl-icon>
                        <div class="capability-result">
                            <code>${this.capabilityResult}</code>
                            <sl-button size="small" @click=${this.copyCapabilityResult}>
                                <sl-icon slot="prefix" name="clipboard"></sl-icon>
                                Copy
                            </sl-button>
                        </div>
                    </sl-alert>
                `, () => nothing)}
            `)}
            <alfio-online-settings-dialog></alfio-online-settings-dialog>
            <alfio-guest-link-dialog></alfio-guest-link-dialog>
        `;
    }

    private edit = async (): Promise<void> => {
        const dialogElement = this.renderRoot.querySelector('alfio-online-settings-dialog');
        if (dialogElement == null) {
            return;
        }
        const event = this.event;
        const saved = await dialogElement.open({
            eventName: event.shortName,
            categoryId: null,
            label: 'Online settings',
            metadata: event.metadata,
            eventBegin: wallClockToDateTimeLocal(event.formattedBegin),
            eventEnd: wallClockToDateTimeLocal(event.formattedEnd),
            languages: this.languages,
            contextLanguages: event.contentLanguages ?? [],
        });
        if (saved) {
            dispatchFeedback({type: 'success', message: 'The online settings have been saved'}, this);
            this.reload();
        }
    };

    private async runCapability(action: CapabilityAction | undefined): Promise<void> {
        if (action == null || this.runningCapability) {
            return;
        }
        const params: Record<string, string> = {};
        if (action.id === 'CREATE_GUEST_LINK') {
            const guest = await this.renderRoot.querySelector('alfio-guest-link-dialog')?.open();
            if (guest == null) {
                return;
            }
            params.firstName = guest.firstName;
            params.lastName = guest.lastName;
            params.email = guest.email;
        }
        params.selector = action.selector;
        this.runningCapability = true;
        try {
            const result = await EventService.executeCapability(this.event.shortName, action.id, params);
            if (action.id === 'GENERATE_MEETING_LINK') {
                // the link has been saved in the settings of the event
                dispatchFeedback({type: 'success', message: 'The meeting link has been generated'}, this);
                this.reload();
            } else {
                this.capabilityResult = result;
            }
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'The extension could not complete the action')}, this);
        } finally {
            this.runningCapability = false;
        }
    }

    private copyCapabilityResult = (): void => {
        const value = this.capabilityResult ?? '';
        void UtilService.copyValueToClipboard(() => value, 'Link', this);
    };

    private reload(): void {
        this.dispatchEvent(new CustomEvent('alfio-event-reload', {bubbles: true, composed: true}));
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-online-settings-dialog': OnlineSettingsDialog;
        'alfio-guest-link-dialog': GuestLinkDialog;
        'alfio-event-online-settings': EventOnlineSettings;
    }
}
