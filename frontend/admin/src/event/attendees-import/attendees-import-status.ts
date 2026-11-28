import {css, html, LitElement, PropertyValues, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {AlfioEvent} from '../../model/event.ts';
import {AttendeesImportStats} from '../../model/attendees-import.ts';
import {EventService} from '../../service/event.ts';
import {AttendeesImportService} from '../../service/attendees-import.ts';
import {localizedTitle} from '../../service/reservation-format.ts';
import {badges, base, modernLayout, purchaseContextListPage, retroCompat, row, textColors} from '../../styles.ts';
import {sectionCard} from '../../components/section-card.ts';
import {taskContent} from '../../components/task-content.ts';
import { adminUrl } from '../../admin-router';
import { t, tHtml, formatNumber } from '../../service/i18n';

const REFRESH_INTERVAL_MS = 1000;

/**
 * Progress of an attendees import: the reservations are created in the background, the status is refreshed until all of them are processed
 */
@customElement('alfio-attendees-import-status')
export class AttendeesImportStatus extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @property({ type: String, attribute: 'data-request-id' }) requestId = '';
    @state() private stats: AttendeesImportStats | null = null;
    @state() private statsError = false;
    private refreshTimer: ReturnType<typeof setTimeout> | undefined;
    // incremented when the request changes, to discard the responses of the previous one
    private refreshRun = 0;

    private readonly loadEventTask = new Task(this, {
        task: async ([eventName]): Promise<AlfioEvent> => (await EventService.load(eventName)).event,
        args: () => [this.eventName] as const,
    });

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        row,
        modernLayout,
        purchaseContextListPage,
        css`
            :host {
                --alfio-section-body-padding: var(--sl-spacing-large);
            }
            .import-status {
                display: grid;
                gap: var(--sl-spacing-medium);
            }
            .counters {
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-x-small);
            }
            sl-progress-bar {
                --height: var(--sl-spacing-large);
            }
        `,
    ];

    protected updated(changed: PropertyValues<this>): void {
        if (changed.has('eventName') || changed.has('requestId')) {
            clearTimeout(this.refreshTimer);
            this.stats = null;
            void this.refresh(++this.refreshRun);
        }
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        clearTimeout(this.refreshTimer);
    }

    render(): TemplateResult {
        return taskContent(this.loadEventTask, this.loadEventTask.value ?? null, t('admin.load-error.event-retry'),
            event => this.renderContent(event));
    }

    private renderContent(event: AlfioEvent): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>${tHtml('admin.attendees-import.status.title', html`<i>${localizedTitle(event)}</i>`)}</h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">${t('admin.attendees-import.status.description')}</p>
                ${when(this.statsError, () => html`
                    <sl-alert variant="danger" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${t('admin.attendees-import.status.load-failed')}
                    </sl-alert>
                `)}
                ${when(this.stats != null,
                    () => this.renderStats(event, this.stats!),
                    () => when(!this.statsError, () => html`<div class="loading"><sl-spinner></sl-spinner></div>`))}
            </div>
        `;
    }

    private renderStats(event: AlfioEvent, stats: AttendeesImportStats): TemplateResult {
        const processed = stats.countSuccess + stats.countError;
        const total = processed + stats.countPending;
        const completed = stats.countPending === 0;
        let percentage = 0;
        if (total > 0) {
            percentage = Math.floor(processed * 100 / total);
        }
        let icon = 'hourglass-split';
        let title = t('admin.attendees-import.status.in-progress');
        if (completed) {
            title = t('admin.attendees-import.status.completed');
            icon = 'check-circle';
            if (stats.countError > 0) {
                icon = 'exclamation-triangle';
            }
        }
        return sectionCard({ icon, title }, html`
            <div class="import-status">
                <sl-progress-bar value=${percentage} label=${t('admin.attendees-import.status.processed')}>${processed} / ${total}</sl-progress-bar>
                <div class="counters">
                    <sl-badge id="imported-count" variant="success" pill>${t('admin.attendees-import.status.created', formatNumber(stats.countSuccess))}</sl-badge>
                    ${when(stats.countError > 0, () => html`<sl-badge id="failed-count" variant="danger" pill>${t('admin.attendees-import.status.failed', formatNumber(stats.countError))}</sl-badge>`)}
                    ${when(stats.countPending > 0, () => html`<sl-badge variant="neutral" pill>${t('admin.attendees-import.status.pending', formatNumber(stats.countPending))}</sl-badge>`)}
                </div>
                ${when(completed, () => html`
                    ${when(stats.countError === 0,
                        () => html`
                            <sl-alert id="import-result" variant="success" open>
                                <sl-icon slot="icon" name="check2-circle"></sl-icon>
                                ${t('admin.attendees-import.status.all-created')}
                            </sl-alert>`,
                        () => html`
                            <sl-alert id="import-result" variant="warning" open>
                                <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                                ${t('admin.attendees-import.status.some-failed', formatNumber(stats.countError), formatNumber(total))}
                            </sl-alert>`)}
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" href=${adminUrl(`/events/${event.shortName}/detail`)}>
                            <sl-icon slot="prefix" name="arrow-left"></sl-icon>
                            ${t('admin.attendees-import.status.back')}
                        </sl-button>
                        <div></div>
                        <sl-button variant="primary" size="large" href=${adminUrl(`/events/${event.shortName}/reservation/import`)}>
                            <sl-icon slot="prefix" name="upload"></sl-icon>
                            ${t('admin.attendees-import.status.import-more')}
                        </sl-button>
                    </div>
                `)}
            </div>
        `);
    }

    private async refresh(run: number): Promise<void> {
        let stats: AttendeesImportStats;
        try {
            stats = await AttendeesImportService.loadStats(this.eventName, this.requestId);
        } catch (e) {
            if (run === this.refreshRun) {
                console.error('error while loading the import status', e);
                this.statsError = true;
            }
            return;
        }
        if (run !== this.refreshRun) {
            return;
        }
        this.stats = stats;
        this.statsError = false;
        if (stats.countPending > 0 && this.isConnected) {
            this.refreshTimer = setTimeout(() => this.refresh(run), REFRESH_INTERVAL_MS);
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-attendees-import-status': AttendeesImportStatus;
    }
}
