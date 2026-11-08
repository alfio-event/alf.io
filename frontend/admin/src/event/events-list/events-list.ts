import {html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {fetchJson, supportsOfflinePayments} from '../../service/helpers.ts';
import {EventStatistic} from '../../model/event.ts';
import {badges, cardList} from '../../styles.ts';
import '../../components/format-date.ts';
import '../../components/pending-payments-count.ts';

let sharedActiveEventsPromise: Promise<EventStatistic[]> | null = null;

async function fetchActiveEvents(): Promise<EventStatistic[]> {
    if (sharedActiveEventsPromise == null) {
        sharedActiveEventsPromise = fetchJson<EventStatistic[]>('/admin/api/active-events').then((res) => {
            sharedActiveEventsPromise = null;
            return res;
        }).catch((err) => {
            sharedActiveEventsPromise = null;
            throw err;
        });
    }
    return sharedActiveEventsPromise;
}

@customElement('alfio-events-list')
export class EventsList extends LitElement {

    @state()
    isOwner: boolean = !!window.USER_IS_OWNER;

    private readonly loadEventsTask = new Task(this,
        async () => {
            const all = await fetchActiveEvents();
            return all.filter(ev => !ev.expired);
        },
        () => []
    );

    static readonly styles = [badges, cardList];

    render() {
        return this.renderActive();
    }

    private renderActive() {
        return html`
            <sl-card>
                <div slot="header" class="card-header">
                    <h2>Your events</h2>
                    ${when(this.isOwner,
                        () => html`<sl-button href="#/events/new" variant="success" size="medium"><sl-icon name="plus-circle" slot="prefix"></sl-icon>Create event</sl-button>`,
                        () => nothing)}
                </div>

                ${this.loadEventsTask.render({
                    initial: () => this.renderSpinner(),
                    running: () => this.renderSpinner(),
                    error: () => html`
                        <div class="state-message">
                            <sl-alert open variant="danger"><sl-icon name="exclamation-triangle" slot="icon"></sl-icon>Failed to load events</sl-alert>
                        </div>
                    `,
                    complete: (events: EventStatistic[]) => {
                        if (events.length === 0) {
                            return this.renderEmptyActive();
                        }
                        return this.renderEvents(events, false);
                    }
                })}
                ${when(this.loaded && !this.loading && !this._error && this.events.length > 0,
                    () => this.renderEvents(this.events, true),
                    () => nothing)}
                <div slot="footer" class="card-footer">
                    ${when(!this.loaded,
                        () => html`<sl-button variant="text" size="small" @click=${() => this.loadExpiredEvents()}>Show older events<sl-icon name="chevron-down" slot="suffix"></sl-icon></sl-button>`,
                        () => when(this.loading,
                            () => html`<sl-spinner></sl-spinner>`,
                            () => when(this._error,
                                () => html`<span class="payment-warning">Failed to load older events</span>`,
                                () => nothing)))}
                </div>
            </sl-card>
        `;
    }

    private renderSpinner() {
        return html`
            <div class="state-message">
                <sl-spinner></sl-spinner>
            </div>
        `;
    }

    private renderEmptyActive(): TemplateResult {
        return when(this.isOwner,
            () => html`
                <div class="state-message">
                    <sl-button href="#/events/new" variant="success" size="large"><sl-icon name="plus-circle" slot="prefix"></sl-icon> Create new event</sl-button>
                </div>
            `,
            () => html`
                <div class="state-message">
                    <sl-alert open variant="primary"><sl-icon name="info-circle" slot="icon"></sl-icon>no active events have been found.</sl-alert>
                </div>
            `);
    }

    private renderEvents(events: EventStatistic[], expired: boolean): TemplateResult {
        return html`
            ${repeat(events, (ev) => ev.id, (ev) => {
                    const sold = ev.soldTickets + ev.checkedInTickets;
                    const percentage = ev.availableSeats > 0 ? Math.min(100, Math.round(sold * 100 / ev.availableSeats)) : 0;
                    const remaining = Math.max(0, ev.availableSeats - sold);
                    return html`
                        <a class="event-row" href="#/events/${ev.shortName}/detail" aria-label="Open ${ev.displayName}">
                            <div class="event-logo ${expired || ev.fileBlobId == null ? 'placeholder' : ''}">
                                ${when(!expired && ev.fileBlobId != null,
                                    () => html`<img src="/file/${ev.fileBlobId}" alt="${ev.displayName} logo">`,
                                    () => html`<sl-icon name="image" aria-label="No event logo"></sl-icon>`)}
                            </div>
                            <div class="event-main">
                                <div class="event-heading">
                                    <span class="event-title">${ev.displayName}</span>
                                            ${when(ev.warningNeeded,
                                              () => html`
                                                  <sl-badge variant="warning" pill>
                                                     <sl-tooltip content="Please check the Event detail">
                                                         <sl-icon name="exclamation-triangle" style="cursor: help;" tabindex="0" aria-label="Warning: please check event detail"></sl-icon>
                                                     </sl-tooltip>
                                                  </sl-badge>
                                              `,
                                              () => nothing)}
                                            ${when(!expired && ev.status === 'DRAFT',
                                              () => html`
                                                  <sl-badge variant="warning" pill>
                                                     <sl-icon name="eye-slash" aria-hidden="true"></sl-icon>
                                                     Hidden
                                                  </sl-badge>
                                              `,
                                              () => when(!expired && ev.status === 'PUBLIC',
                                                  () => html`<sl-badge variant="success" pill>Live</sl-badge>`,
                                                  () => nothing))}
                                </div>
                                <div class="event-date">
                                    <sl-icon name="calendar-event" aria-hidden="true"></sl-icon>
                                    <alfio-format-date date=${ev.formattedBegin} end=${ev.formattedEnd} time-zone=${ev.timeZone}></alfio-format-date>
                                </div>
                                <div class="sales-row">
                                    <div class="ticket-summary">
                                        <div class="sales-progress">
                                            <div class="sales-meta">
                                                <span>${sold} of ${ev.availableSeats} tickets sold</span>
                                                <span>${remaining > 0 && percentage >= 90 ? `${remaining} left` : `${percentage}%`}</span>
                                            </div>
                                            <sl-progress-bar class=${percentage >= 90 ? 'nearly-full' : ''} value=${percentage} aria-label="${percentage}% of tickets sold"></sl-progress-bar>
                                        </div>
                                    </div>
                                    ${when(!expired && ev.visibleForCurrentUser && supportsOfflinePayments(ev.allowedPaymentProxies),
                                        () => html`<alfio-pending-payments-count event-name=${ev.shortName} display="summary"></alfio-pending-payments-count>`,
                                        () => nothing)}
                                </div>
                            </div>
                        </a>
                    `;
                })}
        `;
    }

    @state()
    loaded: boolean = false;

    @state()
    events: EventStatistic[] = [];

    @state()
    loading: boolean = false;

    @state()
    _error: boolean = false;

    private async loadExpiredEvents(): Promise<void> {
        this.loaded = true;
        this.loading = true;
        try {
            this.events = await fetchJson<EventStatistic[]>('/admin/api/expired-events');
        } catch (e) {
            console.error('Failed to load expired events', e);
            this._error = true;
        } finally {
            this.loading = false;
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-events-list': EventsList;
    }
}
