import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {AlfioEvent} from '../../model/event.ts';
import {firstLocalized, Poll, PollStatus} from '../../model/poll.ts';
import {EventService} from '../../service/event.ts';
import {PollService} from '../../service/poll.ts';
import {emptyState} from '../../components/empty-state.ts';
import {taskContent} from '../../components/task-content.ts';
import {sectionCard} from '../../components/section-card.ts';
import {statusBadge, StatusBadgeConfig} from '../../components/status-badge.ts';
import {badges, base, modernLayout, modernTable, purchaseContextListPage, retroCompat} from '../../styles.ts';
import { adminUrl } from '../../admin-router';
import { t } from '../../service/i18n';

const STATUS_BADGES: Record<PollStatus, StatusBadgeConfig> = {
    DRAFT: { label: t('admin.polls.status.draft'), variant: 'neutral', icon: 'pencil' },
    OPEN: { label: t('admin.polls.status.open'), variant: 'success', icon: 'check-circle' },
    CLOSED: { label: t('admin.polls.status.closed'), variant: 'neutral', icon: 'x-circle' },
};

interface PollsListData {
    event: AlfioEvent;
    polls: Poll[];
}

/**
 * The polls of an event (/admin/events/{eventName}/polls/list)
 */
@customElement('alfio-polls-list')
export class PollsList extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';

    @state() private lastData: PollsListData | null = null;

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName]): Promise<PollsListData> => {
            const [eventWithOrg, polls] = await Promise.all([
                EventService.load(eventName),
                PollService.loadList(eventName),
            ]);
            const sorted = [...polls].sort((a, b) => (a.order - b.order) || ((a.id ?? 0) - (b.id ?? 0)));
            return { event: eventWithOrg.event, polls: sorted };
        },
        args: () => [this.eventName] as const,
        onComplete: (data) => {
            this.lastData = data;
        },
    });

    static readonly styles = [
        base,
        retroCompat,
        badges,
        modernLayout,
        modernTable,
        purchaseContextListPage,
        css`
            .poll-description {
                display: block;
                color: var(--sl-color-neutral-600);
                font-size: var(--sl-font-size-small);
            }
        `,
    ];

    render(): TemplateResult {
        return taskContent(this.loadDataTask, this.lastData, t('admin.polls.load-failed'), (data) =>
            this.renderContent(data),
        );
    }

    private renderContent(data: PollsListData): TemplateResult {
        const firstLocale = data.event.firstContentLanguage.locale;
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>${t('admin.event.menu.polls')}</h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">${t('admin.polls.description')}</p>
                <div class="filter-toolbar">
                    <div class="filter-left"></div>
                    <div class="filter-right">
                        <sl-button class="create-poll" variant="success" size="large" href=${adminUrl(`/events/${this.eventName}/polls/new`)}>
                            <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                            ${t('admin.polls.create')}
                        </sl-button>
                    </div>
                </div>
                ${sectionCard({
                    icon: 'bar-chart',
                    title: t('admin.event.menu.polls'),
                    badge: html`<sl-badge variant="primary" pill>${data.polls.length}</sl-badge>`,
                }, html`
                    ${when(data.polls.length === 0,
                        () => emptyState(t('admin.polls.none')),
                        () => html`
                            <div class="table-responsive">
                                <table class="table table-striped polls-table">
                                    <thead>
                                        <tr>
                                            <th>${t('admin.page-title.poll')}</th>
                                            <th>${t('admin.common.status')}</th>
                                            <th><span class="sr-only">${t('admin.common.actions')}</span></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        ${repeat(data.polls, (poll) => poll.id, (poll) => html`
                                            <tr data-poll-id=${poll.id}>
                                                <td>
                                                    <a href=${adminUrl(`/events/${this.eventName}/polls/view/${poll.id}`)}>${firstLocalized(poll.title, firstLocale)}</a>
                                                    <span class="poll-description">${firstLocalized(poll.description, firstLocale)}</span>
                                                </td>
                                                <td>${statusBadge(poll.status, STATUS_BADGES[poll.status], 'poll-status')}</td>
                                                <td class="actions-cell">
                                                    <sl-button size="small" variant="default" outline
                                                               href=${adminUrl(`/events/${this.eventName}/polls/view/${poll.id}`)}>
                                                        <sl-icon slot="prefix" name="eye"></sl-icon>
                                                        ${t('admin.common.view')}
                                                    </sl-button>
                                                </td>
                                            </tr>
                                        `)}
                                    </tbody>
                                </table>
                            </div>
                        `)}
                `)}
            </div>
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-polls-list': PollsList;
    }
}
