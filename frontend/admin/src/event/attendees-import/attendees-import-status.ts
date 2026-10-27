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
        return taskContent(this.loadEventTask, this.loadEventTask.value ?? null, 'Failed to load the event. Please try again.',
            event => this.renderContent(event));
    }

    private renderContent(event: AlfioEvent): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>Import status for <i>${localizedTitle(event)}</i></h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">The reservations are created in the background (one for each attendee, or one for all of them): the import goes on even if you leave this page.</p>
                ${when(this.statsError, () => html`
                    <sl-alert variant="danger" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        Failed to load the import status. Please reload the page.
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
        const percentage = total > 0 ? Math.floor(processed * 100 / total) : 0;
        return sectionCard({
            icon: !completed ? 'hourglass-split' : stats.countError > 0 ? 'exclamation-triangle' : 'check-circle',
            title: completed ? 'Import completed' : 'Import in progress',
        }, html`
            <div class="import-status">
                <sl-progress-bar value=${percentage} label="Processed reservations">${processed} / ${total}</sl-progress-bar>
                <div class="counters">
                    <sl-badge id="imported-count" variant="success" pill>${stats.countSuccess} created</sl-badge>
                    ${when(stats.countError > 0, () => html`<sl-badge id="failed-count" variant="danger" pill>${stats.countError} failed</sl-badge>`)}
                    ${when(stats.countPending > 0, () => html`<sl-badge variant="neutral" pill>${stats.countPending} pending</sl-badge>`)}
                </div>
                ${when(completed, () => html`
                    ${stats.countError === 0
                        ? html`
                            <sl-alert id="import-result" variant="success" open>
                                <sl-icon slot="icon" name="check2-circle"></sl-icon>
                                All the reservations have been created.
                            </sl-alert>`
                        : html`
                            <sl-alert id="import-result" variant="warning" open>
                                <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                                ${stats.countError} of ${total} reservations could not be created. The application log contains the details.
                            </sl-alert>`}
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" href=${`#/events/${event.shortName}/detail`}>
                            <sl-icon slot="prefix" name="arrow-left"></sl-icon>
                            Back to event detail
                        </sl-button>
                        <div></div>
                        <sl-button variant="primary" size="large" href=${`#/events/${event.shortName}/reservation/import`}>
                            <sl-icon slot="prefix" name="upload"></sl-icon>
                            Import other attendees
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
