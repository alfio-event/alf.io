import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import type {SlCheckbox, SlInput, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {AlfioEvent} from '../../model/event.ts';
import {firstLocalized, Poll, PollParticipant} from '../../model/poll.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {EventService} from '../../service/event.ts';
import {PollService} from '../../service/poll.ts';
import {errorMessage} from '../../service/helpers.ts';
import {emptyState} from '../../components/empty-state.ts';
import {taskContent} from '../../components/task-content.ts';
import {sectionCard} from '../../components/section-card.ts';
import {badges, base, dialog, form, modernLayout, modernTable, purchaseContextListPage, retroCompat, row, textColors} from '../../styles.ts';
import { adminUrl } from '../../admin-router';
import { t, tHtml } from '../../service/i18n';

const SEARCH_DELAY_MS = 300;

interface ParticipantsData {
    event: AlfioEvent;
    poll: Poll;
    participants: PollParticipant[];
}

/**
 * The allowed participants of a restricted poll (/admin/events/{eventName}/polls/{pollId}/participants)
 */
@customElement('alfio-poll-participants')
export class PollParticipants extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @property({ type: Number, attribute: 'data-poll-id' }) pollId = 0;

    @state() private lastData: ParticipantsData | null = null;
    // client-side filter of the participants table
    @state() private filter = '';
    @state() private busyId: number | null = null;

    // "Add participants" dialog
    @state() private dialogOpen = false;
    @state() private searchTerm = '';
    @state() private results: PollParticipant[] = [];
    @state() private selected = new Set<number>();
    @state() private searching = false;
    @state() private adding = false;
    private searchTimer: number | undefined;

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName, pollId]): Promise<ParticipantsData> => {
            const [eventWithOrg, poll, participants] = await Promise.all([
                EventService.load(eventName),
                PollService.load(eventName, pollId),
                PollService.loadParticipants(eventName, pollId),
            ]);
            return { event: eventWithOrg.event, poll, participants };
        },
        args: () => [this.eventName, this.pollId] as const,
        onComplete: (data) => {
            this.lastData = data;
        },
    });

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        form,
        dialog,
        modernLayout,
        modernTable,
        purchaseContextListPage,
        row,
        css`
            .filter-input {
                flex: 1 1 20rem;
            }
        `,
    ];

    disconnectedCallback(): void {
        super.disconnectedCallback();
        if (this.searchTimer !== undefined) {
            window.clearTimeout(this.searchTimer);
            this.searchTimer = undefined;
        }
    }

    render(): TemplateResult {
        return html`
            ${taskContent(this.loadDataTask, this.lastData, t('admin.poll-participants.load-failed'), (data) =>
                this.renderContent(data),
            )}
            <sl-dialog class="responsive-dialog add-participants" label=${t('admin.poll-participants.add')} style="--alfio-dialog-max-width: 60rem"
                       ?open=${this.dialogOpen}
                       @sl-request-close=${this.onRequestClose}
                       @sl-hide=${() => this.closeDialog()}>
                ${when(this.dialogOpen, () => this.renderDialog(), () => nothing)}
            </sl-dialog>
        `;
    }

    private renderContent(data: ParticipantsData): TemplateResult {
        const poll = data.poll;
        const firstLocale = data.event.firstContentLanguage.locale;
        const filtered = this.filteredParticipants(data.participants);
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>${tHtml('admin.poll-participants.title', html`<i>${firstLocalized(poll.title, firstLocale)}</i>`)}</h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">${t('admin.poll-participants.description')}</p>
                <div class="filter-toolbar">
                    <div class="filter-left">
                        <sl-input class="filter-input label-hidden" label=${t('admin.poll-participants.filter')} placeholder=${t('admin.poll-participants.filter')}
                                  clearable .value=${this.filter} @sl-input=${(e: Event) => { this.filter = (e.target as SlInput).value; }}
                                  @sl-clear=${() => { this.filter = ''; }}>
                            <sl-icon slot="prefix" name="search"></sl-icon>
                        </sl-input>
                    </div>
                    <div class="filter-right">
                        <sl-button class="add-participants-btn" variant="success" size="large" @click=${() => this.openDialog()}>
                            <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                            ${t('admin.poll-participants.add')}
                        </sl-button>
                    </div>
                </div>
                ${sectionCard({
                    icon: 'people',
                    title: t('admin.poll.participants'),
                    badge: html`<sl-badge variant="primary" pill>${data.participants.length}</sl-badge>`,
                }, html`
                    ${when(data.participants.length === 0,
                        () => emptyState(t('admin.poll-participants.none')),
                        () => when(filtered.length === 0,
                            () => emptyState(t('admin.poll-participants.no-results')),
                            () => html`
                                <div class="table-responsive">
                                    <table class="table table-striped participants-table table-stacked">
                                        <thead>
                                            <tr>
                                                <th>#</th>
                                                <th>${t('admin.common.first-name')}</th>
                                                <th>${t('admin.common.last-name')}</th>
                                                <th>${t('admin.common.email')}</th>
                                                <th class="hide-small">${t('admin.attendees-import.category')}</th>
                                                <th><span class="sr-only">${t('admin.common.actions')}</span></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            ${repeat(filtered, (p) => p.id, (p, index) => html`
                                                <tr data-participant-id=${p.id}>
                                                    <td data-label="#">${index + 1}</td>
                                                    <td data-label=${t('admin.common.first-name')}>${p.firstName}</td>
                                                    <td data-label=${t('admin.common.last-name')}>${p.lastName}</td>
                                                    <td data-label=${t('admin.common.email')}>${p.emailAddress}</td>
                                                    <td class="hide-small" data-label=${t('admin.attendees-import.category')}>${p.categoryName}</td>
                                                    <td class="actions-cell">
                                                        <sl-button class="remove-participant" size="small" variant="danger" outline
                                                                   ?disabled=${this.busyId != null}
                                                                   ?loading=${this.busyId === p.id}
                                                                   @click=${() => void this.removeParticipant(p)}>
                                                            <sl-icon slot="prefix" name="trash"></sl-icon>
                                                            ${t('admin.common.remove')}
                                                        </sl-button>
                                                    </td>
                                                </tr>
                                            `)}
                                        </tbody>
                                    </table>
                                </div>
                            `))}
                `)}
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" href=${adminUrl(`/events/${this.eventName}/polls/view/${poll.id}`)}>${t('admin.common.back')}</sl-button>
                    <div></div>
                    <div></div>
                </div>
            </div>
        `;
    }

    private renderDialog(): TemplateResult {
        const allSelected = this.results.length > 0 && this.results.every((p) => this.selected.has(p.id));
        return html`
            <sl-input label=${t('admin.poll-participants.search')} placeholder=${t('admin.poll-participants.search.placeholder')} clearable
                      .value=${this.searchTerm} @sl-input=${this.onSearchInput} @sl-clear=${this.onSearchInput}>
                <sl-icon slot="prefix" name="search"></sl-icon>
                ${when(this.searching, () => html`<sl-spinner slot="suffix"></sl-spinner>`)}
            </sl-input>
            ${when(this.results.length > 0, () => html`
                <div class="table-responsive">
                    <table class="table table-striped search-results">
                        <thead>
                            <tr>
                                <th>${t('admin.common.first-name')}</th>
                                <th>${t('admin.common.last-name')}</th>
                                <th>${t('admin.common.email')}</th>
                                <th class="hide-small">${t('admin.attendees-import.category')}</th>
                                <th>
                                    <sl-checkbox ?checked=${allSelected} aria-label=${t('admin.common.select-all')}
                                                 @sl-change=${(e: Event) => this.toggleAll((e.target as SlCheckbox).checked)}></sl-checkbox>
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            ${repeat(this.results, (p) => p.id, (p) => html`
                                <tr data-participant-id=${p.id}>
                                    <td>${p.firstName}</td>
                                    <td>${p.lastName}</td>
                                    <td>${p.emailAddress}</td>
                                    <td class="hide-small">${p.categoryName}</td>
                                    <td>
                                        <sl-checkbox ?checked=${this.selected.has(p.id)} aria-label=${t('admin.poll-participants.select', `${p.firstName} ${p.lastName}`)}
                                                     @sl-change=${(e: Event) => this.toggle(p, (e.target as SlCheckbox).checked)}></sl-checkbox>
                                    </td>
                                </tr>
                            `)}
                        </tbody>
                    </table>
                </div>
            `, () => when(this.searchTerm.trim() !== '', () => html`<p class="text-muted">${t('admin.poll-participants.no-attendees')}</p>`, () => nothing))}
            <div slot="footer">
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" @click=${() => this.closeDialog()}>${t('admin.common.close')}</sl-button>
                    <div></div>
                    <sl-button class="confirm-add" variant="warning" size="large"
                               ?disabled=${this.selected.size === 0}
                               ?loading=${this.adding}
                               @click=${() => void this.addSelected()}>
                        ${when(this.selected.size > 0, () => t('admin.poll-participants.add-count', this.selected.size), () => t('admin.common.add'))}
                    </sl-button>
                </div>
            </div>
        `;
    }

    private filteredParticipants(participants: PollParticipant[]): PollParticipant[] {
        const term = this.filter.trim().toLowerCase();
        if (term === '') {
            return participants;
        }
        return participants.filter((p) =>
            p.firstName.toLowerCase().includes(term)
            || p.lastName.toLowerCase().includes(term)
            || p.emailAddress.toLowerCase().includes(term)
            || p.categoryName.toLowerCase().includes(term),
        );
    }

    private async removeParticipant(participant: PollParticipant): Promise<void> {
        this.busyId = participant.id;
        try {
            const remaining = await PollService.removeParticipants(this.eventName, this.pollId, [participant.id]);
            if (this.lastData != null) {
                this.lastData = { ...this.lastData, participants: remaining };
            }
            dispatchFeedback({ type: 'success', message: t('admin.poll-participants.removed', `${participant.firstName} ${participant.lastName}`) }, this);
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorMessage(e, t('admin.poll-participants.remove-failed')) }, this);
        } finally {
            this.busyId = null;
        }
    }

    // the dialog has a search and a selection: a stray click on the overlay must not discard them
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private openDialog(): void {
        this.dialogOpen = true;
        this.searchTerm = '';
        this.results = [];
        this.selected = new Set();
    }

    private closeDialog(): void {
        this.dialogOpen = false;
        this.searchTerm = '';
        this.results = [];
        this.selected = new Set();
        if (this.searchTimer !== undefined) {
            window.clearTimeout(this.searchTimer);
            this.searchTimer = undefined;
        }
    }

    private onSearchInput = (e: Event): void => {
        this.searchTerm = (e.target as SlInput).value;
        if (this.searchTimer !== undefined) {
            window.clearTimeout(this.searchTimer);
        }
        this.searchTimer = window.setTimeout(() => void this.search(), SEARCH_DELAY_MS);
    };

    private async search(): Promise<void> {
        const term = this.searchTerm.trim();
        if (term === '') {
            this.results = [];
            return;
        }
        this.searching = true;
        try {
            this.results = await PollService.searchTickets(this.eventName, this.pollId, term);
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorMessage(e, t('admin.poll-participants.search-failed')) }, this);
        } finally {
            this.searching = false;
        }
    }

    private toggle(participant: PollParticipant, checked: boolean): void {
        const next = new Set(this.selected);
        if (checked) {
            next.add(participant.id);
        } else {
            next.delete(participant.id);
        }
        this.selected = next;
    }

    private toggleAll(checked: boolean): void {
        const next = new Set(this.selected);
        for (const p of this.results) {
            if (checked) {
                next.add(p.id);
            } else {
                next.delete(p.id);
            }
        }
        this.selected = next;
    }

    private async addSelected(): Promise<void> {
        if (this.selected.size === 0) {
            return;
        }
        this.adding = true;
        try {
            await PollService.addParticipants(this.eventName, this.pollId, [...this.selected]);
            dispatchFeedback({ type: 'success', message: t('admin.poll-participants.added', { count: this.selected.size }) }, this);
            this.closeDialog();
            void this.loadDataTask.run();
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorMessage(e, t('admin.poll-participants.add-failed')) }, this);
        } finally {
            this.adding = false;
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-poll-participants': PollParticipants;
    }
}
