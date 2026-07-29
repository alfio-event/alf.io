import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task, TaskStatus} from '@lit/task';
import {fetchJson, formatDate, injectFontAwesome, renderEventActions, supportsOfflinePayments} from '../../service/helpers.ts';
import {EventStatistic} from '../../model/event.ts';
import {panelStyles, spacing} from '../../styles.ts';

@customElement('alfio-active-events-list')
export class ActiveEventsList extends LitElement {

    @state()
    isOwner: boolean = !!window.USER_IS_OWNER;

    private readonly loadedCounts: Set<string> = new Set();
    private readonly retryAttempts = new Map<string, number>();
    private readonly retryTimers = new Map<string, ReturnType<typeof setTimeout>>();
    private taskComplete: boolean = false;
    private connectionGeneration: number = 0;

    private readonly loadEventsTask = new Task<[], EventStatistic[]>(this,
        async () => {
            return await fetchJson<EventStatistic[]>('/admin/api/active-events');
        },
        () => []
    );

    static readonly styles = [panelStyles, spacing, css`
        :host {
            --event-list-image-width: 8.33333333%;
            --event-list-info-width: 50%;
            --event-list-actions-width: 41.66666667%;
        }
`];

    render() {
        return html`
            <div class="panel panel-default">
                <div class="panel-heading">
                    ${when(this.isOwner,
                        () => html`
                            <div class="hidden-xs hidden-sm pull-right">
                                <a class="btn btn-xs btn-success" href="#/events/new">
                                    <i class="fa fa-file-text-o"></i> create new event
                                </a>
                            </div>
                        `,
                        () => nothing)}
                    <h4 class="panel-title">Active Events</h4>
                </div>

                ${this.loadEventsTask.render({
                    initial: () => html`
                        <div class="panel-body text-center text-muted">
                            <div class="loading-spinner">
                                <sl-spinner></sl-spinner>
                            </div>
                        </div>
                    `,
                    error: () => html`
                        <div class="panel-body">
                            <div class="alert alert-danger">Failed to load events</div>
                        </div>
                    `,
                    complete: (events: EventStatistic[]) => html`
                        ${when(events.length === 0,
                            () => this.renderEmptyState(),
                            () => this.renderEvents(events, events.length <= 10))}
                    `
                })}
            </div>
        `;
    }

    private renderEmptyState(): TemplateResult {
        return when(this.isOwner,
            () => html`
                <div class="panel-body">
                    <a class="btn btn-success" href="#/events/new">Create new event</a>
                </div>
            `,
            () => html`
                <div class="panel-body">
                    <div class="alert alert-info">
                        <span><i class="fa fa-info-circle"></i> no active events have been found.</span>
                    </div>
                </div>
            `);
    }

    private renderEvents(events: EventStatistic[], displayImage: boolean): TemplateResult {
        return html`
            <ul class="list-group">
                ${repeat(events, (ev) => ev.id, (ev) => {
                    return html`
                        <li class="list-group-item">
                            <div class="row">
                                ${when(displayImage,
                                    () => html`
                                        <div class="col col-image hidden-xs hidden-sm">
                                            <h4>
                                                <a href="#/events/${ev.shortName}/detail">
                                                    ${when(ev.fileBlobId != null,
                                                        () => html`<img class="img-responsive" src="/file/${ev.fileBlobId}" alt=${ev.displayName}>`,
                                                        () => nothing)}
                                                    ${when(ev.fileBlobId == null,
                                                        () => html`<img class="img-responsive" src="/resources/images/sample-logo.png" alt="default">`,
                                                        () => nothing)}
                                                </a>
                                            </h4>
                                        </div>
                                    `,
                                    () => nothing)}
                                <div class="col col-info">
                                    <div class="list-group-item-heading event-title">
                                        <h4>
${when(ev.warningNeeded,
                                                 () => html`
                                                     <span class="label label-danger" @click=${(e: Event) => { e.preventDefault(); e.stopPropagation(); }}>
<sl-tooltip content="Something wrong is happening...">
                                                            <i class="fa fa-warning" style="cursor: help;" tabindex="0" aria-label="Warning: something wrong is happening"></i>
                                                        </sl-tooltip>
                                                     </span>
                                                 `,
                                                 () => nothing)}
${when(ev.status === 'DRAFT',
                                                 () => html`
                                                     <span class="label label-warning" @click=${(e: Event) => { e.preventDefault(); e.stopPropagation(); }}>
<sl-tooltip content="This event has not yet been published">
                                                            <i class="fa fa-eye-slash" style="cursor: help;" tabindex="0" aria-label="This event has not yet been published"></i>
                                                        </sl-tooltip>
                                                     </span>
                                                 `,
                                                 () => nothing)}
                                            <a href="#/events/${ev.shortName}/detail">${ev.displayName}</a>
                                        </h4>
                                    </div>
                                    <div class="list-group-item-text">
                                        ${formatDate(ev.formattedBegin)} / ${formatDate(ev.formattedEnd)}
                                    </div>
                                </div>
                                <div class="col col-actions text-right wMarginTop10px">
                                    ${this.renderActions(ev)}
                                </div>
                            </div>
                        </li>
                    `;
                })}
            </ul>
        `;
    }

    private renderActions(ev: EventStatistic): TemplateResult {
        return html`
            ${renderEventActions(ev, true)}
            ${when(!ev.expired,
                () => html`<a class="btn btn-primary btn-xs" href="#/events/${ev.shortName}/check-in"><i class="fa fa-check"></i> Check-In</a>`,
                () => nothing)}
            ${when(ev.visibleForCurrentUser && supportsOfflinePayments(ev.allowedPaymentProxies),
                () => html`<a class="btn btn-warning btn-xs" href="#/events/${ev.shortName}/pending-payments"><i class="fa fa-dollar"></i> Pending payments <span class="badge pending-count" data-event-name="${ev.shortName}"></span></a>`,
                () => nothing)}
        `;
    }

    connectedCallback(): void {
        super.connectedCallback();
        this.connectionGeneration++;
        this.taskComplete = false;
        for (const timer of this.retryTimers.values()) {
            clearTimeout(timer);
        }
        this.retryTimers.clear();
        this.retryAttempts.clear();
        this.loadedCounts.clear();
        if (this.loadEventsTask.status === TaskStatus.COMPLETE) {
            this.taskComplete = true;
            const gen = this.connectionGeneration;
            this.updateComplete.then(() => { if (this.isConnected && this.connectionGeneration === gen) { this.loadPendingCounts(); } });
        }
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        this.connectionGeneration++;
        for (const timer of this.retryTimers.values()) {
            clearTimeout(timer);
        }
        this.retryTimers.clear();
        this.loadedCounts.clear();
        this.taskComplete = true;
    }

    firstUpdated(): void {
        injectFontAwesome(this.renderRoot as ShadowRoot);
    }

    updated(): void {
        if (this.loadEventsTask.status === TaskStatus.COMPLETE && !this.taskComplete) {
            this.taskComplete = true;
            this.loadPendingCounts();
        }
    }

    private async loadPendingCounts(): Promise<void> {
        const events = this.loadEventsTask.value ?? [];
        for (const ev of events) {
            this.fetchPendingCount(ev.shortName);
        }
    }

    private fetchPendingCount(shortName: string): void {
        const gen = this.connectionGeneration;
        if (this.loadedCounts.has(shortName)) {
            return;
        }
        const attempt = this.retryAttempts.get(shortName) ?? 0;
        if (attempt >= 3) {
            return;
        }
        this.retryAttempts.set(shortName, attempt + 1);
        const badges = this.renderRoot?.querySelectorAll(`.pending-count[data-event-name="${shortName}"]`);
        badges?.forEach(async (badge) => {
            try {
                const count = await fetchJson<number>(`/admin/api/events/${shortName}/pending-payments-count`);
                if (this.connectionGeneration === gen) {
                    badge.textContent = String(count);
                    this.loadedCounts.add(shortName);
                    this.retryAttempts.delete(shortName);
                }
            } catch {
                if (this.isConnected && this.connectionGeneration === gen) {
                    this.scheduleRetry(shortName);
                }
            }
        });
    }

    private scheduleRetry(shortName: string): void {
        const gen = this.connectionGeneration;
        const attempt = this.retryAttempts.get(shortName) ?? 0;
        if (attempt >= 3) {
            return;
        }
        const delay = Math.min(1000 * Math.pow(2, attempt), 5000);
        if (this.retryTimers.has(shortName)) {
            clearTimeout(this.retryTimers.get(shortName));
        }
        this.retryTimers.set(shortName, setTimeout(() => {
            this.retryTimers.delete(shortName);
            if (this.isConnected && this.connectionGeneration === gen) {
                this.fetchPendingCount(shortName);
            }
        }, delay));
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-active-events-list': ActiveEventsList;
    }
}
