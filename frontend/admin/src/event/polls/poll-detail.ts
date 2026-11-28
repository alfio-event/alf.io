import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import type {ChartData, ChartOptions} from 'chart.js';
import {AlfioEvent} from '../../model/event.ts';
import {firstLocalized, formatPercentage, Poll, PollOption, PollStatistics} from '../../model/poll.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {EventService} from '../../service/event.ts';
import {PollService} from '../../service/poll.ts';
import {errorMessage} from '../../service/helpers.ts';
import {ConfirmationDialogService} from '../../service/confirmation-dialog.ts';
import {taskContent} from '../../components/task-content.ts';
import {sectionCard} from '../../components/section-card.ts';
import {detailList} from '../../components/detail-list.ts';
import {slColor} from '../../components/chart.ts';
import '../../components/chart.ts';
import {
    badges,
    base,
    detailList as detailListStyles,
    dialog,
    modernLayout,
    modernTable,
    purchaseContextListPage,
    retroCompat,
    row,
    textColors,
} from '../../styles.ts';
import { navigate, adminUrl } from '../../admin-router';
import { t, tHtml, formatNumber } from '../../service/i18n';

const STATS_REFRESH_MS = 2000;

interface PollDetailData {
    event: AlfioEvent;
    poll: Poll;
}

/**
 * One poll of an event (/admin/events/{eventName}/polls/view/{pollId}): status, question, options,
 * live results and the link to the participants management.
 */
@customElement('alfio-poll-detail')
export class PollDetail extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @property({ type: Number, attribute: 'data-poll-id' }) pollId = 0;

    @state() private lastData: PollDetailData | null = null;
    @state() private statistics: PollStatistics | null = null;
    @state() private busy = false;
    @state() private presentationOpen = false;

    private statsTimer: number | undefined;

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName, pollId]): Promise<PollDetailData> => {
            const [eventWithOrg, poll] = await Promise.all([
                EventService.load(eventName),
                PollService.load(eventName, pollId),
            ]);
            return { event: eventWithOrg.event, poll };
        },
        args: () => [this.eventName, this.pollId] as const,
        onComplete: (data) => {
            this.lastData = data;
            this.loadStatistics();
        },
    });

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        modernLayout,
        modernTable,
        detailListStyles,
        dialog,
        row,
        purchaseContextListPage,
        css`
            .status-actions {
                display: flex;
                flex-wrap: wrap;
                justify-content: center;
                gap: var(--sl-spacing-small);
                margin-top: var(--sl-spacing-medium);
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
            .chart-container {
                height: 16rem;
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        // refresh the results of an open poll while the page is visible
        this.statsTimer = window.setInterval(() => this.loadStatistics(), STATS_REFRESH_MS);
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        if (this.statsTimer !== undefined) {
            window.clearInterval(this.statsTimer);
            this.statsTimer = undefined;
        }
    }

    render(): TemplateResult {
        return html`
            ${taskContent(this.loadDataTask, this.lastData, t('admin.poll-edit.load-failed'), (data) =>
                this.renderContent(data),
            )}
            <sl-dialog class="responsive-dialog poll-presentation" label=${this.pollTitle()}
                       ?open=${this.presentationOpen}
                       @sl-hide=${() => { this.presentationOpen = false; }}>
                ${when(this.presentationOpen, () => this.renderPresentation(), () => nothing)}
            </sl-dialog>
        `;
    }

    private renderContent(data: PollDetailData): TemplateResult {
        const poll = data.poll;
        const event = data.event;
        const firstLocale = event.firstContentLanguage.locale;
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>${firstLocalized(poll.title, firstLocale)}</h1>
                </div>
                <hr class="page-separator" />
                ${this.renderStatusBanner(poll)}
                ${when(this.statistics != null, () => sectionCard({
                    icon: 'bar-chart',
                    title: t('admin.poll.results'),
                    actions: html`
                        <sl-button size="small" variant="default" outline @click=${() => { this.presentationOpen = true; }}>
                            <sl-icon slot="prefix" name="easel"></sl-icon>
                            ${t('admin.poll.presentation')}
                        </sl-button>
                    `,
                }, this.renderVotesTable(data, this.statistics!)))}
                ${when(poll.accessRestricted, () => sectionCard({
                    icon: 'people',
                    title: t('admin.poll.participants'),
                }, html`
                    <p class="text-muted">${t('admin.poll.participants.help')}</p>
                    <sl-button class="manage-participants" variant="primary" size="small" href=${adminUrl(`/events/${this.eventName}/polls/${poll.id}/participants`)}>
                        <sl-icon slot="prefix" name="gear"></sl-icon>
                        ${t('admin.poll.participants.manage')}
                    </sl-button>
                `))}
                ${sectionCard({ icon: 'chat-left-question', title: t('admin.poll-edit.question') }, html`
                    <p class="text-muted">${t('admin.poll.question.help')}</p>
                    ${detailList(event.contentLanguages.map((lang) => ({
                        label: lang.displayLanguage,
                        value: poll.title[lang.locale] ?? '',
                    })))}
                `)}
                ${sectionCard({ icon: 'text-paragraph', title: t('admin.common.description') }, html`
                    <p class="text-muted">${t('admin.poll.description.help')}</p>
                    ${detailList(event.contentLanguages.map((lang) => ({
                        label: lang.displayLanguage,
                        value: poll.description[lang.locale] ?? '',
                    })))}
                `)}
                ${sectionCard({ icon: 'list-ul', title: t('admin.categories.options') }, html`
                    <p class="text-muted">${t('admin.poll-edit.options.help')}</p>
                    ${when(poll.options.length === 0,
                        () => html`<p class="text-muted">${t('admin.poll-edit.options.none')}</p>`,
                        () => html`
                            ${repeat(poll.options, (option) => option.id ?? Object.keys(option.title).join(), (option) =>
                                this.renderOption(data, option))}
                        `)}
                `)}
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" href=${adminUrl(`/events/${this.eventName}/polls/list`)}>${t('admin.common.back')}</sl-button>
                    <div></div>
                    <sl-button variant="warning" size="large" href=${adminUrl(`/events/${this.eventName}/polls/edit/${poll.id}`)}>${t('admin.common.edit')}</sl-button>
                </div>
            </div>
        `;
    }

    private renderStatusBanner(poll: Poll): TemplateResult {
        let variant: 'warning' | 'success' | 'neutral' = 'neutral';
        let message = t('admin.poll.status.closed');
        let icon = 'info-circle';
        if (poll.status === 'DRAFT') {
            variant = 'warning';
            message = t('admin.poll.status.draft');
        } else if (poll.status === 'OPEN') {
            variant = 'success';
            message = t('admin.poll.status.open');
            icon = 'check-circle';
        }
        return html`
            <sl-alert open variant=${variant}>
                <sl-icon slot="icon" name=${icon}></sl-icon>
                <strong>${message}</strong>
                <div class="status-actions">
                    ${when(poll.status === 'OPEN', () => html`
                        <sl-button variant="default" size="large" ?disabled=${this.busy} ?loading=${this.busy}
                                   @click=${() => void this.changeStatus('CLOSED')}>
                            ${t('admin.common.close')}
                        </sl-button>
                    `, () => html`
                        <sl-button variant="success" size="large" ?disabled=${this.busy} ?loading=${this.busy}
                                   @click=${() => void this.changeStatus('OPEN')}>
                            ${t('admin.poll.open')}
                        </sl-button>
                    `)}
                    ${when(poll.status !== 'OPEN', () => html`
                        <sl-button variant="danger" size="large" ?disabled=${this.busy}
                                   @click=${() => void this.deletePoll(poll)}>
                            <sl-icon slot="prefix" name="trash"></sl-icon>
                            ${t('admin.common.delete')}
                        </sl-button>
                    `)}
                </div>
            </sl-alert>
        `;
    }

    private renderOption(data: PollDetailData, option: PollOption): TemplateResult {
        const firstLocale = data.event.firstContentLanguage.locale;
        const title = firstLocalized(option.title, firstLocale);
        const translations = Object.keys(option.title).length - 1;
        return html`
            <div class="option-row" data-option-id=${option.id ?? nothing}>
                <div>
                    <div class="option-title">
                        ${title}
                        ${when(translations > 0, () => html`<sl-badge variant="primary" pill>+${translations}</sl-badge>`)}
                    </div>
                    <div class="option-description">${firstLocalized(option.description, firstLocale)}</div>
                </div>
                <sl-button class="remove-option" size="small" variant="danger" outline
                           ?disabled=${this.busy}
                           @click=${() => void this.removeOption(data, option)}>
                    <sl-icon slot="prefix" name="trash"></sl-icon>
                    ${t('admin.common.remove')}
                </sl-button>
            </div>
        `;
    }

    private renderVotesTable(data: PollDetailData, statistics: PollStatistics): TemplateResult {
        const poll = data.poll;
        const firstLocale = data.event.firstContentLanguage.locale;
        const rows = statistics.optionStatistics.map((stat) => {
            const option = poll.options.find((o) => o.id === stat.optionId);
            return {
                title: option != null ? firstLocalized(option.title, firstLocale) : `#${stat.optionId}`,
                votes: stat.votes,
                percentage: stat.percentage,
            };
        });
        // options without votes yet
        poll.options.forEach((option) => {
            if (!statistics.optionStatistics.some((stat) => stat.optionId === option.id)) {
                rows.push({ title: firstLocalized(option.title, firstLocale), votes: 0, percentage: '0' });
            }
        });
        return html`
            <div class="table-responsive">
                <table class="table table-striped votes-table">
                    <thead>
                        <tr>
                            <th>${t('admin.poll.option')}</th>
                            <th class="text-right">${t('admin.poll.votes')}</th>
                            <th class="text-right">%</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(rows, (row) => row.title, (row) => html`
                            <tr>
                                <td>${row.title}</td>
                                <td class="text-right">${row.votes}</td>
                                <td class="text-right">${formatPercentage(row.percentage)}%</td>
                            </tr>
                        `)}
                    </tbody>
                    <tfoot>
                        <tr>
                            <th>${t('admin.poll.total')}</th>
                            <th class="text-right">${formatNumber(statistics.totalVotes)}</th>
                            <th class="text-right">100%</th>
                        </tr>
                    </tfoot>
                </table>
            </div>
            <p class="text-muted">
                ${t('admin.poll.participation', formatNumber(statistics.totalVotes), formatNumber(statistics.allowedParticipants), formatPercentage(statistics.participationPercentage))}
            </p>
        `;
    }

    private renderPresentation(): TemplateResult {
        const data = this.lastData;
        const statistics = this.statistics;
        if (data == null) {
            return html`${nothing}`;
        }
        const poll = data.poll;
        const firstLocale = data.event.firstContentLanguage.locale;
        const optionTitle = (optionId: number): string => {
            const option = poll.options.find((o) => o.id === optionId);
            return option != null ? firstLocalized(option.title, firstLocale) : `#${optionId}`;
        };
        const chartData: ChartData = {
            labels: statistics?.optionStatistics.map((s) => optionTitle(s.optionId)) ?? [],
            datasets: [{
                label: t('admin.poll.votes'),
                data: statistics?.optionStatistics.map((s) => s.votes) ?? [],
                backgroundColor: slColor('--sl-color-primary-500', '#4a90d9'),
                borderWidth: 1,
            }],
        };
        const chartOptions: ChartOptions = {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                x: { beginAtZero: true, ticks: { precision: 0 } },
            },
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: (item) => ` ${item.label}: ${item.parsed.x}`,
                    },
                },
            },
        };
        return html`
            <p class="text-center">
                ${tHtml('admin.poll.total-votes', html`<strong>${formatNumber(statistics?.totalVotes ?? 0)}</strong>`)}
                <span class="text-muted">/ ${statistics?.allowedParticipants ?? 0}</span>
                — ${formatPercentage(statistics?.participationPercentage ?? '0')}%
            </p>
            <div class="chart-container">
                <alfio-chart type="bar" .data=${chartData} .options=${chartOptions} aria-label=${t('admin.poll.chart')}></alfio-chart>
            </div>
            ${when(statistics != null, () => this.renderVotesTable(data, statistics!))}
        `;
    }

    private pollTitle(): string {
        const data = this.lastData;
        if (data == null) {
            return '';
        }
        return firstLocalized(data.poll.title, data.event.firstContentLanguage.locale);
    }

    private loadStatistics(): void {
        const data = this.lastData;
        if (data == null || data.poll.status === 'DRAFT') {
            return;
        }
        PollService.loadStatistics(this.eventName, this.pollId)
            .then((statistics) => {
                this.statistics = statistics;
            })
            .catch(() => {
                // a failed refresh must not interrupt the page: the next timer tick retries
            });
    }

    private async changeStatus(status: 'OPEN' | 'CLOSED'): Promise<void> {
        this.busy = true;
        try {
            const updated = await PollService.updateStatus(this.eventName, this.pollId, status);
            if (this.lastData != null) {
                this.lastData = { ...this.lastData, poll: updated };
            }
            this.statistics = null;
            this.loadStatistics();
            dispatchFeedback({
                type: 'success',
                message: statusChangedMessage(status),
            }, this);
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorMessage(e, t('admin.poll.status-change-failed')) }, this);
        } finally {
            this.busy = false;
        }
    }

    private async removeOption(data: PollDetailData, option: PollOption): Promise<void> {
        const firstLocale = data.event.firstContentLanguage.locale;
        const title = firstLocalized(option.title, firstLocale);
        const confirmed = await ConfirmationDialogService.requestConfirm(
            t('admin.poll.remove-option'),
            t('admin.poll.remove-option.text', title),
            'danger',
        );
        if (!confirmed) {
            return;
        }
        this.busy = true;
        try {
            const updated = await PollService.removeOption(this.eventName, this.pollId, option.id!);
            this.lastData = { ...data, poll: updated };
            this.loadStatistics();
            dispatchFeedback({ type: 'success', message: t('admin.poll.option-removed') }, this);
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorMessage(e, t('admin.poll.remove-option-failed')) }, this);
        } finally {
            this.busy = false;
        }
    }

    private async deletePoll(poll: Poll): Promise<void> {
        const firstLocale = this.lastData?.event.firstContentLanguage.locale ?? 'en';
        const confirmed = await ConfirmationDialogService.requestConfirm(
            t('admin.poll.delete'),
            t('admin.poll.delete.text', firstLocalized(poll.title, firstLocale)),
            'danger',
        );
        if (!confirmed) {
            return;
        }
        this.busy = true;
        try {
            await PollService.deletePoll(this.eventName, this.pollId);
            navigate(`/events/${this.eventName}/polls/list`);
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorMessage(e, t('admin.poll.delete-failed')) }, this);
            this.busy = false;
        }
    }
}

function statusChangedMessage(status: 'OPEN' | 'CLOSED'): string {
    if (status === 'OPEN') {
        return t('admin.poll.now-open');
    }
    return t('admin.poll.now-closed');
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-poll-detail': PollDetail;
    }
}
