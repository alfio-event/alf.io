import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
import type {GroupMemberModification, GroupModification} from '../model/group.ts';
import {GroupService} from '../service/group.ts';
import {downloadCsv, errorMessage} from '../service/helpers.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {ConfirmationDialogService} from '../service/confirmation-dialog.ts';
import {badges, base, choiceCards as choiceCardsStyles, csvImport as csvImportStyles, form, modernLayout, row, textColors} from '../styles.ts';
import {sectionCard} from '../components/section-card.ts';
import {taskContent} from '../components/task-content.ts';
import {choiceCards, ChoiceCardOption} from '../components/choice-cards.ts';
import {csvImport} from '../components/csv-import.ts';
import {FileUploadChangeEvent} from '../components/file-upload.ts';
import {
    emailAddress,
    focusFirstError,
    FormMountController,
    INVALID_FORM_MESSAGE,
    requiredValue,
    submitValidated,
    validatedInput,
    validateWith,
} from '../components/form-validation.ts';
import { navigate, adminUrl } from '../admin-router';
import { t, tHtml } from '../service/i18n';

/**
 * A member of the group being edited. A member already saved on the server has an id and is
 * shown read-only (it can only be removed, with its own endpoint); a new one is editable.
 */
interface GroupMemberDraft {
    id: number | null;
    value: string;
    description: string;
}

interface GroupFormValues {
    name: string;
    description: string;
    items: GroupMemberDraft[];
}

function emptyMember(): GroupMemberDraft {
    return {id: null, value: '', description: ''};
}

function emptyFormValues(): GroupFormValues {
    return {name: '', description: '', items: [emptyMember()]};
}

function skippedRowsWarning(skippedRows: number): string {
    return t('admin.group.csv.skipped', { count: skippedRows });
}

type MembersInputMode = 'manual' | 'csv';

const MEMBERS_INPUT_MODES: ChoiceCardOption<MembersInputMode>[] = [
    {
        value: 'manual',
        icon: 'person-vcard',
        title: t('admin.reservation-create.attendees.manual'),
        description: t('admin.group.manual.description'),
    },
    {
        value: 'csv',
        icon: 'filetype-csv',
        title: t('admin.reservation-create.attendees.csv'),
        description: t('admin.group.csv.description'),
    },
];

/**
 * The create/edit page of a group: name, description and its members (added one by one or
 * imported from a CSV file). Hosted by the "groups.new" and "groups.edit" routes.
 */
@customElement('alfio-group-edit')
export class GroupEdit extends LitElement {

    @property({type: Number, attribute: 'data-organization-id'})
    organizationId = 0;

    @property({type: Number, attribute: 'data-group-id'})
    groupId = 0;

    @property({type: Boolean, attribute: 'data-create-new'})
    createNew = false;

    @state()
    private saving = false;

    @state()
    private submitError: string | null = null;

    @state()
    private csvError: string | null = null;

    @state()
    private csvWarning: string | null = null;

    @state()
    private csvMessage: string | null = null;

    // how the new members are added: typed in the form, or read from an uploaded CSV file
    @state()
    private membersMode: MembersInputMode = 'manual';

    readonly #form = new TanStackFormController(this, {
        defaultValues: emptyFormValues(),
        onSubmit: async ({value}) => {
            await this.save(value);
        },
    });

    private readonly loadGroupTask = new Task(this, {
        task: async ([organizationId, groupId, createNew]): Promise<GroupModification | null> => {
            if (!(organizationId > 0)) {
                // the organization id is still being interpolated by AngularJS
                return null;
            }
            if (createNew) {
                return {id: null, name: '', description: '', organizationId, items: []};
            }
            return await GroupService.loadGroupDetail(organizationId, groupId);
        },
        // the attributes are interpolated by AngularJS after the element is connected: wait for real values
        args: () => [this.organizationId, this.groupId, this.createNew] as const,
        onComplete: (group) => {
            if (group != null) {
                this.initForm(group);
            }
        },
    });

    static readonly styles = [
        base,
        textColors,
        badges,
        form,
        row,
        modernLayout,
        choiceCardsStyles,
        csvImportStyles,
        css`
            :host {
                display: block;
            }

            .page-title-row sl-button {
                margin-inline-start: auto;
            }

            .members {
                display: grid;
                gap: var(--sl-spacing-x-small);
            }

            .member-row {
                --alfio-custom-row-cols-layout: minmax(0, 1fr) minmax(0, 1fr) auto;
                --alfio-column-gap: var(--sl-spacing-small);
                align-items: end;
            }

            .member-row sl-input {
                margin-top: 0;
            }

            /* keeps the button of the first row aligned with its inputs, which have the labels */
            .member-row:first-child sl-icon-button,
            .member-row:first-child sl-button {
                align-self: end;
                padding-bottom: var(--sl-spacing-x-small);
            }

            /* under the last row, aligned with the inputs */
            .add-member {
                justify-self: start;
                margin-top: var(--sl-spacing-x-small);
            }

            .add-member::part(base) {
                padding-inline-start: 0;
            }

            .add-member::part(label) {
                padding-inline-start: var(--sl-spacing-x-small);
            }

            sl-alert {
                margin-top: var(--sl-spacing-medium);
            }

            .footer {
                margin-top: var(--sl-spacing-medium);
            }
        `,
    ];

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    render(): TemplateResult {
        return html`
            <div class="page-title-row secondary">
                <h1>${when(this.createNew, () => t('admin.group.create'), () => t('admin.page-title.edit-group'))}</h1>
                ${when(!this.createNew,
                    () => html`
                        <sl-button id="delete-group" variant="danger" @click=${this.deleteGroup}>
                            <sl-icon slot="prefix" name="trash"></sl-icon>${t('admin.common.delete')}
                        </sl-button>
                    `,
                    () => nothing)}
            </div>
            <hr class="page-separator">
            ${taskContent(this.loadGroupTask, this.loadGroupTask.value ?? null,
                t('admin.group.load-failed'),
                () => this.renderForm())}
        `;
    }

    private renderForm(): TemplateResult {
        const submitted = this.#form.api.state.submissionAttempts > 0;
        const items = this.#form.api.state.values.items;
        return html`
            <form novalidate @submit=${this.submit}>
                ${sectionCard({icon: 'info-circle', title: t('admin.group.details')}, html`
                    <div class="row" style="--alfio-row-cols: 2">
                        ${this.#form.field({name: 'name', validators: validateWith(requiredValue)},
                            field => validatedInput(field, submitted, {id: 'group-name', label: t('admin.common.name'), prefixIcon: 'tag'}))}
                        ${this.#form.field({name: 'description', validators: validateWith(requiredValue)},
                            field => validatedInput(field, submitted, {id: 'group-description', label: t('admin.common.description'), prefixIcon: 'card-text'}))}
                    </div>
                `)}
                ${sectionCard({
                    icon: 'people',
                    title: t('admin.group.members'),
                    badge: html`<sl-badge id="members-count" variant="primary" pill>${items.length}</sl-badge>`,
                }, html`
                    ${choiceCards({
                        name: 'members-input-mode',
                        className: 'members-input-mode',
                        label: t('admin.group.members.question'),
                        value: this.membersMode,
                        options: MEMBERS_INPUT_MODES,
                        panel: html`
                            ${when(this.membersMode === 'manual',
                                () => this.renderMembers(items, submitted),
                                () => this.renderCsvImport())}
                        `,
                    }, mode => this.changeMembersMode(mode))}
                `)}
                ${when(this.submitError != null,
                    () => html`
                        <sl-alert id="submit-error" variant="danger" open closable @sl-after-hide=${() => { this.submitError = null; }}>
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            ${this.submitError}
                        </sl-alert>
                    `,
                    () => nothing)}
                <div class="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" href=${adminUrl(`/group/${this.organizationId}/all`)}>${t('admin.common.cancel')}</sl-button>
                        <div></div>
                        <sl-button id="save-group" type="submit" variant="warning" size="large" ?loading=${this.saving}>${t('admin.common.save')}</sl-button>
                    </div>
                </div>
            </form>
        `;
    }

    /**
     * The members typed in one by one, as the attendees of the reservation creation: one row of
     * fields per member, the labels of the first row acting as column headers
     */
    private renderMembers(items: GroupMemberDraft[], submitted: boolean): TemplateResult {
        return html`
            <div class="members">
                ${repeat(items, (_, index) => index, (item, index) => this.renderMember(item, index, submitted))}
                <sl-button id="add-member" class="add-member" variant="text" @click=${this.addMember}>
                    <sl-icon slot="prefix" name="plus-circle"></sl-icon>${t('admin.group.add-member')}
                </sl-button>
            </div>
        `;
    }

    private renderCsvImport(): TemplateResult {
        return html`
            ${when(this.csvMessage != null,
                () => html`
                    <sl-alert class="csv-message" variant="primary" open closable @sl-after-hide=${() => { this.csvMessage = null; }}>
                        <sl-icon slot="icon" name="info-circle"></sl-icon>
                        ${this.csvMessage}
                    </sl-alert>
                `,
                () => nothing)}
            ${csvImport({
                templateDescription: t('admin.group.csv.template'),
                onDownloadTemplate: () => downloadCsv('group-members.csv', GroupService.csvTemplate()),
                onFileChange: (e: FileUploadChangeEvent) => this.onCsvFileChange(e),
                error: this.csvError,
                warning: this.csvWarning,
                specs: this.renderCsvSpecs(),
            })}
        `;
    }

    private renderMember(item: GroupMemberDraft, index: number, submitted: boolean): TemplateResult {
        const path = `items[${index}]` as const;
        // a member already saved is read-only: it can only be removed with its own endpoint
        const saved = item.id != null;
        // the labels of the first row act as column headers
        let labelClass = '';
        if (index > 0) {
            labelClass = 'label-hidden';
        }
        const itemsCount = this.#form.api.state.values.items.length;
        return html`
            <div class="row custom member-row">
                ${this.#form.field({name: `${path}.value`, validators: validateWith(value => this.validateMemberValue(index, value))},
                    field => validatedInput(field, submitted, {
                        id: `member-email-${index}`,
                        className: `member-email ${labelClass}`.trim(),
                        label: t('admin.common.email'),
                        type: 'email',
                        prefixIcon: 'envelope',
                        readonly: saved,
                    }))}
                ${this.#form.field({name: `${path}.description`},
                    field => validatedInput(field, submitted, {
                        id: `member-description-${index}`,
                        className: `member-description ${labelClass}`.trim(),
                        label: t('admin.common.description'),
                        optional: true,
                        prefixIcon: 'card-text',
                        readonly: saved,
                    }))}
                ${when(saved,
                    () => html`
                        <sl-button class="deactivate-member" variant="danger" size="small"
                                   ?disabled=${itemsCount <= 1}
                                   @click=${() => this.deactivateMember(item)}>
                            <sl-icon slot="prefix" name="trash"></sl-icon>${t('admin.common.delete')}
                        </sl-button>
                    `,
                    () => html`
                        <sl-icon-button class="remove-member" name="trash" label=${t('admin.group.remove-member', index + 1)}
                                        ?disabled=${itemsCount <= 1}
                                        @click=${() => this.removeMember(index)}></sl-icon-button>
                    `)}
            </div>
        `;
    }

    private renderCsvSpecs(): TemplateResult {
        return html`
            <div class="bulk-specs">
                <p class="text-muted">
                    ${t('admin.csv-import.specs.template')}
                </p>
                <p class="text-muted">
                    ${tHtml('admin.csv-import.specs.format', html`<strong>${t('admin.csv-import.specs.with-header')}</strong>`, html`<code>,</code>`, html`<code>"</code>`)}
                    ${tHtml('admin.csv-import.specs.quotes', html`<code>""</code>`)}
                </p>
                <pre><span id="template-header">"email","description"</span>

${t('admin.group.csv.email-column')}
${t('admin.group.csv.description-column')}</pre>
            </div>
        `;
    }

    /**
     * Only the members added in the form are validated: the saved ones are read-only
     */
    private validateMemberValue(index: number, value: unknown): string | undefined {
        const item = this.#form.api.state.values.items[index];
        if (item == null || item.id != null) {
            return undefined;
        }
        return emailAddress(value);
    }

    private addMember = (): void => {
        this.#form.api.pushFieldValue('items', emptyMember());
    };

    private removeMember(index: number): void {
        // moves also the state of the validated fields of the following members
        void this.#form.api.removeFieldValue('items', index);
    }

    private changeMembersMode(mode: MembersInputMode): void {
        this.membersMode = mode;
        this.csvError = null;
        this.csvWarning = null;
        this.csvMessage = null;
    }

    private async onCsvFileChange(e: FileUploadChangeEvent): Promise<void> {
        this.csvError = null;
        this.csvWarning = null;
        this.csvMessage = null;
        const file = e.detail.file;
        if (file == null) {
            return;
        }
        try {
            const parsed = GroupService.parseCsv(await file.text());
            if (parsed.items.length === 0) {
                if (parsed.skippedRows > 0) {
                    this.csvError = t('admin.group.csv.all-skipped', { count: parsed.skippedRows });
                } else {
                    this.csvError = t('admin.group.csv.empty');
                }
                return;
            }
            // the parsed members are appended to the ones already in the form
            const current = this.#form.api.state.values.items;
            const imported: GroupMemberDraft[] = parsed.items.map(item => ({
                id: null,
                value: item.value ?? '',
                description: item.description ?? '',
            }));
            this.#form.api.setFieldValue('items', [...current, ...imported], {dontUpdateMeta: true});
            this.csvMessage = t('admin.group.csv.imported', { count: imported.length, 1: file.name });
            if (parsed.skippedRows > 0) {
                this.csvWarning = skippedRowsWarning(parsed.skippedRows);
            }
        } catch (error) {
            this.csvError = errorMessage(error, t('admin.group.csv.read-failed'));
        }
    }

    private submit = async (event: Event): Promise<void> => {
        event.preventDefault();
        if (this.saving) {
            return;
        }
        this.submitError = null;
        if (!await submitValidated(this.#form.api)) {
            this.submitError = INVALID_FORM_MESSAGE;
            if (this.membersMode === 'csv') {
                // show the member rows, so that the errors are visible
                this.membersMode = 'manual';
            }
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    private async save(values: GroupFormValues): Promise<void> {
        this.saving = true;
        try {
            const items: GroupMemberModification[] = values.items.map(item => {
                let description: string | null = item.description;
                if (description.trim() === '') {
                    description = null;
                }
                return {id: item.id, value: item.value, description};
            });
            let id: number | null = this.groupId;
            if (this.createNew) {
                id = null;
            }
            const payload: GroupModification = {
                id,
                name: values.name,
                description: values.description,
                organizationId: this.organizationId,
                items,
            };
            if (this.createNew) {
                await GroupService.create(this.organizationId, payload);
                dispatchFeedback({type: 'success', message: t('admin.group.created', values.name)}, this);
            } else {
                await GroupService.update(this.organizationId, this.groupId, payload);
                dispatchFeedback({type: 'success', message: t('admin.group.updated', values.name)}, this);
            }
            navigate(`/group/${this.organizationId}/all`);
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.group.save-failed'))}, this);
        } finally {
            this.saving = false;
        }
    }

    private deleteGroup = async (): Promise<void> => {
        const name = this.#form.api.state.values.name;
        const confirmed = await ConfirmationDialogService.requestConfirm(
            t('admin.group.delete.title'),
            t('admin.group.delete.text', name),
            'danger');
        if (!confirmed) {
            return;
        }
        try {
            await GroupService.deactivateGroup(this.organizationId, this.groupId);
            dispatchFeedback({type: 'success', message: t('admin.group.deleted', name)}, this);
            navigate(`/group/${this.organizationId}/all`);
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.group.error.delete'))}, this);
        }
    };

    private deactivateMember = async (item: GroupMemberDraft): Promise<void> => {
        if (item.id == null) {
            return;
        }
        const confirmed = await ConfirmationDialogService.requestConfirm(
            t('admin.group.remove-member.title'),
            t('admin.group.remove-member.text', item.value),
            'danger');
        if (!confirmed) {
            return;
        }
        try {
            await GroupService.deactivateMember(this.organizationId, this.groupId, item.id);
            dispatchFeedback({type: 'success', message: t('admin.group.member-removed', item.value)}, this);
            // reload the group, as the member is gone from the server
            this.loadGroupTask.run();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.group.error.remove-member'))}, this);
        }
    };

    private initForm(group: GroupModification): void {
        const items: GroupMemberDraft[] = (group.items ?? []).map(item => ({
            id: item.id ?? null,
            value: item.value ?? '',
            description: item.description ?? '',
        }));
        if (items.length === 0) {
            items.push(emptyMember());
        }
        const values: GroupFormValues = {
            name: group.name ?? '',
            description: group.description ?? '',
            items,
        };
        this.#form.api.update({...this.#form.api.options, defaultValues: values});
        this.#form.api.reset(values);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-group-edit': GroupEdit;
    }
}
