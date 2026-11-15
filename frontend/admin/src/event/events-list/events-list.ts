import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task, TaskStatus} from '@lit/task';
import {fetchJson, supportsOfflinePayments} from '../../service/helpers.ts';
import {EventStatistic} from '../../model/event.ts';
import {badges, cardList, modernLayout} from '../../styles.ts';
import {sectionCard} from '../../components/section-card.ts';
import {emptyState} from '../../components/empty-state.ts';
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

    static readonly styles = [badges, cardList, modernLayout, css`
        :host {
            margin-block: 0;
        }

        .list-card {
            --alfio-section-body-padding: 0;
        }

        .older-events {
            padding: var(--sl-spacing-small) var(--sl-spacing-large);
            border-top: 1px solid var(--sl-color-gray-200);
        }
    `];

    render() {
        return this.renderActive();
    }

    private renderActive() {
        const events = this.loadEventsTask.value;
        let count: TemplateResult | undefined = undefined;
        if (events != null && this.loadEventsTask.status === TaskStatus.COMPLETE) {
            count = html`<sl-badge variant="primary" pill>${events.length}</sl-badge>`;
        }
        return html`
            ${when(this.isOwner, () => html`
                <div class="filter-toolbar" style="justify-content: flex-end;">
                    <div class="filter-right">
                        <sl-button href="#/events/new" variant="success" size="large">
                            <sl-icon name="plus-circle" slot="prefix"></sl-icon>
                            Add new
                        </sl-button>
                    </div>
                </div>
            `)}
            ${sectionCard({icon: 'calendar-event', title: 'Active events', badge: count, className: 'list-card'}, html`
                ${this.loadEventsTask.render({
                    initial: () => this.renderSpinner(),
                    running: () => this.renderSpinner(),
                    error: () => html`
                        <div class="state-message">
                            <sl-alert open variant="danger"><sl-icon name="exclamation-triangle" slot="icon"></sl-icon>Failed to load events</sl-alert>
                        </div>
                    `,
                    complete: (loaded: EventStatistic[]) => {
                        if (loaded.length === 0) {
                            return emptyState('No active events have been found');
                        }
                        return this.renderEvents(loaded, false);
                    }
                })}
                ${when(this.loaded && !this.loading && !this._error && this.events.length > 0,
                    () => this.renderEvents(this.events, true))}
                <div class="older-events">
                    ${when(!this.loaded,
                        () => html`<sl-button variant="text" size="small" @click=${() => this.loadExpiredEvents()}>Show older events<sl-icon name="chevron-down" slot="suffix"></sl-icon></sl-button>`,
                        () => when(this.loading,
                            () => html`<sl-spinner></sl-spinner>`,
                            () => when(this._error, () => html`
                                <sl-alert open variant="danger">
                                    <sl-icon name="exclamation-triangle" slot="icon"></sl-icon>
                                    Failed to load older events
                                </sl-alert>
                            `)))}
                </div>
            `)}
        `;
    }

    private renderSpinner() {
        return html`
            <div class="state-message">
                <sl-spinner></sl-spinner>
            </div>
        `;
    }

    private renderEvents(events: EventStatistic[], expired: boolean): TemplateResult {
        return html`
            ${repeat(events, (ev) => ev.id, (ev) => {
                    const sold = ev.soldTickets + ev.checkedInTickets;
                    const percentage = this.soldPercentage(sold, ev.availableSeats);
                    const remaining = Math.max(0, ev.availableSeats - sold);
                    const nearlyFull = percentage >= 90;
                    const showLogo = !expired && ev.fileBlobId != null;
                    return html`
                        <a class="event-row" href="#/events/${ev.shortName}/detail" aria-label="Open ${ev.displayName}">
                            <div class=${classMap({ 'event-logo': true, placeholder: !showLogo })}>
                                ${when(showLogo,
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
                                              `)}
                                            ${when(!expired && ev.status === 'DRAFT',
                                              () => html`
                                                  <sl-badge variant="neutral" pill>
                                                     <sl-icon name="eye-slash" aria-hidden="true"></sl-icon>
                                                     Unlisted
                                                  </sl-badge>
                                              `,
                                              () => when(!expired && ev.status === 'PUBLIC',
                                                  () => html`<sl-badge variant="success" pill>Live</sl-badge>`))}
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
                                                <span>${when(remaining > 0 && nearlyFull, () => `${remaining} left`, () => `${percentage}%`)}</span>
                                            </div>
                                            <sl-progress-bar class=${classMap({ 'nearly-full': nearlyFull })} value=${percentage} aria-label="${percentage}% of tickets sold"></sl-progress-bar>
                                        </div>
                                    </div>
                                    ${when(!expired && ev.visibleForCurrentUser && supportsOfflinePayments(ev.allowedPaymentProxies),
                                        () => html`<alfio-pending-payments-count event-name=${ev.shortName} display="summary"></alfio-pending-payments-count>`)}
                                </div>
                            </div>
                        </a>
                    `;
                })}
        `;
    }

    private soldPercentage(sold: number, availableSeats: number): number {
        if (availableSeats <= 0) {
            return 0;
        }
        return Math.min(100, Math.round(sold * 100 / availableSeats));
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
