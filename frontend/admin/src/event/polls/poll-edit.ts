import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
import type {SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {AlfioEvent} from '../../model/event.ts';
import {LocalizedContent} from '../../model/purchase-context.ts';
import {firstLocalized, isCompleteLocalized, Poll, PollOption} from '../../model/poll.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {EventService} from '../../service/event.ts';
import {PollService} from '../../service/poll.ts';
import {errorMessage, supportedLanguages} from '../../service/helpers.ts';
import {FormMountController, fieldError, focusFirstError, submitValidated, validateWith} from '../../components/form-validation.ts';
import {taskContent} from '../../components/task-content.ts';
import {sectionCard} from '../../components/section-card.ts';
import {ChoiceCardOption, choiceCards} from '../../components/choice-cards.ts';
import {MultiLanguageTextChange, MultiLanguageTextLocalesChange} from '../../components/multi-language-text.ts';
import '../../components/multi-language-text.ts';
import {
    badges,
    base,
    choiceCards as choiceCardsStyles,
    dialog,
    form,
    modernLayout,
    purchaseContextListPage,
    retroCompat,
    row,
    textColors,
} from '../../styles.ts';
import { navigate } from '../../admin-router';
import { t } from '../../service/i18n';

interface PollForm {
    accessRestricted: boolean;
    title: LocalizedContent;
    description: LocalizedContent;
    options: PollOption[];
}

type AccessType = 'all' | 'restricted';

const ACCESS_CHOICES: ChoiceCardOption<AccessType>[] = [
    {
        value: 'all',
        icon: 'people',
        title: t('admin.poll-edit.access.all'),
        description: t('admin.poll-edit.access.all.description'),
    },
    {
        value: 'restricted',
        icon: 'person-lock',
        title: t('admin.poll-edit.access.restricted'),
        description: t('admin.poll-edit.access.restricted.description'),
    },
];

interface LoadedPoll {
    event: AlfioEvent;
    poll: Poll;
}

const TITLE_MESSAGE = t('admin.poll-edit.error.question');
const OPTIONS_MESSAGE = t('admin.poll-edit.error.options');

/**
 * Creates or edits a poll (/admin/events/{eventName}/polls/new, /admin/events/{eventName}/polls/edit/{pollId})
 */
@customElement('alfio-poll-edit')
export class PollEdit extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @property({ type: Number, attribute: 'data-poll-id' }) pollId = 0;

    @state() private lastData: LoadedPoll | null = null;
    @state() private saving = false;
    // the option being edited in the dialog: index in the options array, or null when adding a new one
    @state() private optionIndex: number | null = null;
    @state() private optionDraft: PollOption | null = null;
    @state() private optionError = '';

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName, pollId]): Promise<LoadedPoll> => {
            const eventWithOrg = await EventService.load(eventName);
            let poll: Poll;
            if (pollId > 0) {
                poll = await PollService.load(eventName, pollId);
            } else {
                poll = { id: null, title: {}, description: {}, order: 0, options: [], accessRestricted: false, status: 'DRAFT' };
            }
            return { event: eventWithOrg.event, poll };
        },
        args: () => [this.eventName, this.pollId] as const,
        onComplete: (data) => {
            this.lastData = data;
            this.#form.api.reset({
                accessRestricted: data.poll.accessRestricted,
                title: data.poll.title,
                description: data.poll.description,
                options: data.poll.options,
            });
        },
    });

    readonly #form = new TanStackFormController(this, {
        defaultValues: { accessRestricted: false, title: {}, description: {}, options: [] } as PollForm,
        onSubmit: async ({ value }) => {
            await this.save(value);
        },
    });

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        choiceCardsStyles,
        form,
        dialog,
        modernLayout,
        purchaseContextListPage,
        row,
        css`
            /* the choice cards are not a section card: give the block the same space below it */
            .access-choice {
                margin-bottom: var(--sl-spacing-medium);
            }
            .access-choice > p {
                margin-bottom: var(--sl-spacing-small);
            }
            .option-row {
                display: flex;
                align-items: center;
                justify-content: space-between;
                gap: var(--sl-spacing-medium);
                padding: var(--sl-spacing-small) 0;
                border-top: 1px solid var(--sl-color-gray-200);
            }
            .option-row:first-of-type {
                border-top: 0;
                padding-top: 0;
            }
            .option-title {
                font-weight: var(--sl-font-weight-semibold);
            }
            .option-description {
                color: var(--sl-color-neutral-600);
                font-size: var(--sl-font-size-small);
            }
            .form-error-text {
                color: var(--sl-color-danger-600);
                font-size: var(--sl-font-size-small);
                margin-top: var(--sl-spacing-x-small);
            }
        `,
    ];

    render(): TemplateResult {
        return html`
            ${taskContent(this.loadDataTask, this.lastData, t('admin.poll-edit.load-failed'), (data) =>
                this.renderForm(data),
            )}
            <sl-dialog class="responsive-dialog option-dialog" label=${when(this.optionIndex == null, () => t('admin.poll-edit.add-option'), () => t('admin.poll-edit.edit-option'))}
                       ?open=${this.optionDraft != null}
                       @sl-request-close=${this.onRequestClose}
                       @sl-hide=${() => { this.optionDraft = null; this.optionError = ''; }}>
                ${when(this.optionDraft != null, () => this.renderOptionDialog(), () => nothing)}
            </sl-dialog>
        `;
    }

    private renderForm(data: LoadedPoll): TemplateResult {
        const event = data.event;
        const poll = data.poll;
        const editing = poll.id != null;
        const submitted = this.#form.api.state.submissionAttempts > 0;
        return html`
            <div class="container">
                <form @submit=${(e: Event) => { e.preventDefault(); void this.submit(); }}>
                    <div class="page-title-row">
                        <h1>${when(editing, () => t('admin.page-title.edit-poll'), () => t('admin.poll-edit.create'))}</h1>
                    </div>
                    <hr class="page-separator" />
                    <div class="access-choice">
                        <p class="text-muted">${t('admin.poll-edit.access.help')}</p>
                        ${this.#form.field({ name: 'accessRestricted' }, (field) => {
                            let accessType: AccessType = 'all';
                            if (field.state.value === true) {
                                accessType = 'restricted';
                            }
                            return choiceCards({
                                name: 'poll-access-type',
                                className: 'access-type',
                                label: t('admin.poll-edit.access.question'),
                                value: accessType,
                                options: ACCESS_CHOICES,
                            }, (value) => field.handleChange(value === 'restricted'));
                        })}
                    </div>
                    ${this.#form.field({
                        name: 'title',
                        validators: validateWith((value) => isCompleteLocalized(value as LocalizedContent) ? undefined : TITLE_MESSAGE),
                    }, (field) => html`
                        <alfio-multi-language-text
                            label=${t('admin.poll-edit.question')}
                            icon="chat-left-question"
                            .multiline=${false}
                            .languages=${supportedLanguages()}
                            .contextLanguages=${event.contentLanguages}
                            .value=${field.state.value ?? {}}
                            error=${fieldError(field.state.meta, submitted) ?? ''}
                            @change=${(e: CustomEvent<MultiLanguageTextChange>) => field.handleChange(e.detail.value)}
                            @locales-change=${(e: CustomEvent<MultiLanguageTextLocalesChange>) => this.syncLocales('title', e)}
                        ></alfio-multi-language-text>
                    `)}
                    ${this.#form.field({ name: 'description' }, (field) => html`
                        <alfio-multi-language-text
                            label=${t('admin.common.description')}
                            icon="text-paragraph"
                            .languages=${supportedLanguages()}
                            .contextLanguages=${event.contentLanguages}
                            .value=${field.state.value ?? {}}
                            @change=${(e: CustomEvent<MultiLanguageTextChange>) => field.handleChange(e.detail.value)}
                            @locales-change=${(e: CustomEvent<MultiLanguageTextLocalesChange>) => this.syncLocales('description', e)}
                        ></alfio-multi-language-text>
                    `)}
                    ${this.#form.field({
                        name: 'options',
                        validators: validateWith((value) => {
                            const options = value as PollOption[] | undefined;
                            if (options == null || options.length === 0 || !options.every((o) => isCompleteLocalized(o.title))) {
                                return OPTIONS_MESSAGE;
                            }
                            return undefined;
                        }),
                    }, (field) => html`
                        ${sectionCard({ icon: 'list-ul', title: t('admin.categories.options') }, html`
                            <p class="text-muted">${t('admin.poll-edit.options.help')}</p>
                            ${when((field.state.value ?? []).length === 0, () => html`<p class="text-muted">${t('admin.poll-edit.options.none')}</p>`)}
                            ${repeat(field.state.value ?? [], (option, index) => option.id ?? index, (option, index) => html`
                                <div class="option-row" data-option-index=${index}>
                                    <div>
                                        <div class="option-title">
                                            ${firstLocalized(option.title, event.firstContentLanguage.locale)}
                                            ${when(Object.keys(option.title).length > 1, () => html`
                                                <sl-badge variant="primary" pill>+${Object.keys(option.title).length - 1}</sl-badge>
                                            `)}
                                        </div>
                                        <div class="option-description">${firstLocalized(option.description, event.firstContentLanguage.locale)}</div>
                                    </div>
                                    <sl-button class="edit-option" size="small" variant="default" outline
                                               @click=${() => this.openOptionDialog(index)}>
                                        <sl-icon slot="prefix" name="pencil"></sl-icon>
                                        ${t('admin.common.edit')}
                                    </sl-button>
                                </div>
                            `)}
                            <sl-button class="add-option" variant="text" @click=${() => this.openOptionDialog(null)}>
                                <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                                ${t('admin.poll-edit.add-option')}
                            </sl-button>
                            ${when(fieldError(field.state.meta, submitted) != null, () => html`
                                <div class="form-error-text">${fieldError(field.state.meta, submitted)}</div>
                            `)}
                        `)}
                    `)}
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" type="button" @click=${() => this.cancel()}>${t('admin.common.cancel')}</sl-button>
                        <div>
                            ${when(submitted && !this.#form.api.state.isValid, () => html`
                                <span class="form-error-text">${t('admin.validation.invalid-form')}</span>
                            `)}
                        </div>
                        <sl-button id="save-poll" variant="warning" size="large" type="submit" ?loading=${this.saving}>${t('admin.common.save')}</sl-button>
                    </div>
                </form>
            </div>
        `;
    }

    private renderOptionDialog(): TemplateResult {
        const draft = this.optionDraft!;
        const event = this.lastData?.event;
        return html`
            <p class="text-muted">${t('admin.poll-edit.option.help')}</p>
            <div class="form-stack">
                <alfio-multi-language-text
                    label=${t('admin.poll-edit.option.title')}
                    .card=${false}
                    .multiline=${false}
                    .languages=${supportedLanguages()}
                    .contextLanguages=${event?.contentLanguages}
                    .value=${draft.title}
                    error=${this.optionError}
                    @change=${(e: CustomEvent<MultiLanguageTextChange>) => this.updateOptionDraft('title', e.detail.value)}
                ></alfio-multi-language-text>
                <alfio-multi-language-text
                    label=${t('admin.common.description')}
                    .card=${false}
                    .languages=${supportedLanguages()}
                    .contextLanguages=${event?.contentLanguages}
                    .value=${draft.description}
                    @change=${(e: CustomEvent<MultiLanguageTextChange>) => this.updateOptionDraft('description', e.detail.value)}
                ></alfio-multi-language-text>
            </div>
            <div slot="footer">
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" @click=${() => { this.optionDraft = null; this.optionError = ''; }}>${t('admin.common.cancel')}</sl-button>
                    <div></div>
                    <sl-button variant="warning" size="large" @click=${() => this.saveOption()}>${t('admin.common.save')}</sl-button>
                </div>
            </div>
        `;
    }

    // the dialog has a form: a stray click on the overlay must not discard what the user typed
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private openOptionDialog(index: number | null): void {
        const options = this.#form.api.getFieldValue('options') ?? [];
        if (index != null) {
            this.optionDraft = { ...options[index], title: { ...options[index].title }, description: { ...options[index].description } };
        } else {
            this.optionDraft = { id: null, title: {}, description: {} };
        }
        this.optionIndex = index;
        this.optionError = '';
    }

    private updateOptionDraft(field: 'title' | 'description', value: LocalizedContent): void {
        if (this.optionDraft != null) {
            this.optionDraft = { ...this.optionDraft, [field]: value };
        }
    }

    private saveOption(): void {
        const draft = this.optionDraft;
        if (draft == null) {
            return;
        }
        if (!isCompleteLocalized(draft.title)) {
            this.optionError = TITLE_MESSAGE;
            return;
        }
        const options = [...(this.#form.api.getFieldValue('options') ?? [])];
        if (this.optionIndex != null) {
            options[this.optionIndex] = draft;
        } else {
            options.push(draft);
        }
        this.#form.api.setFieldValue('options', options);
        this.optionDraft = null;
        this.optionError = '';
    }

    /**
     * Applies the language selection of one multi-language field to the other one, keeping its texts
     */
    private syncLocales(source: 'title' | 'description', e: CustomEvent<MultiLanguageTextLocalesChange>): void {
        const target = source === 'title' ? 'description' : 'title';
        const current = this.#form.api.getFieldValue(target) ?? {};
        const next: LocalizedContent = {};
        for (const locale of e.detail.locales) {
            next[locale] = current[locale] ?? '';
        }
        this.#form.api.setFieldValue(target, next);
    }

    private async submit(): Promise<void> {
        const valid = await submitValidated(this.#form.api);
        if (!valid) {
            focusFirstError(this.renderRoot);
        }
    }

    private async save(value: PollForm): Promise<void> {
        const loaded = this.lastData;
        if (loaded == null) {
            return;
        }
        this.saving = true;
        try {
            const payload: Poll = { ...loaded.poll, ...value };
            let pollId = payload.id;
            if (pollId != null) {
                await PollService.update(this.eventName, payload);
            } else {
                pollId = await PollService.create(this.eventName, payload);
            }
            navigate(`/events/${this.eventName}/polls/view/${pollId}`);
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorMessage(e, t('admin.poll-edit.save-failed')) }, this);
            this.saving = false;
        }
    }

    private cancel(): void {
        const pollId = this.lastData?.poll.id;
        if (pollId != null) {
            navigate(`/events/${this.eventName}/polls/view/${pollId}`);
        } else {
            navigate(`/events/${this.eventName}/polls/list`);
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-poll-edit': PollEdit;
    }
}
