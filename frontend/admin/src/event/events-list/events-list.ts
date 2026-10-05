import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {fetchJson, supportsOfflinePayments} from '../../service/helpers.ts';
import {EventStatistic} from '../../model/event.ts';
import {badges} from '../../styles.ts';
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

    static readonly styles = [badges, css`
        :host {
            display: block;
            margin-block: var(--sl-spacing-large);
            color: var(--sl-color-neutral-900);
        }

        *, *::before, *::after {
            box-sizing: border-box;
        }

        sl-card {
            width: 100%;
        }

        sl-card::part(base) {
            overflow: hidden;
            border-color: var(--sl-color-gray-200);
            border-radius: var(--sl-border-radius-large);
            box-shadow: var(--sl-shadow-x-small);
        }

        sl-card::part(header),
        sl-card::part(footer) {
            padding: var(--sl-spacing-medium) var(--sl-spacing-large);
            border-color: var(--sl-color-gray-200);
        }

        sl-card::part(body) {
            padding: 0;
        }

        .card-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--sl-spacing-medium);
        }

        .card-header h2 {
            margin: 0;
            font-size: var(--sl-font-size-large);
            font-weight: var(--sl-font-weight-semibold);
        }

        .event-row {
            display: grid;
            grid-template-columns: auto minmax(0, 1fr);
            gap: var(--sl-spacing-medium);
            padding: var(--sl-spacing-large);
            color: inherit;
            text-decoration: none;
            transition: background-color var(--sl-transition-fast);
        }

        .event-row:hover {
            background: var(--sl-color-primary-50);
        }

        .event-row:focus-visible {
            outline: var(--sl-focus-ring-width) solid var(--sl-focus-ring-color);
            outline-offset: calc(-1 * var(--sl-focus-ring-width));
        }

        .event-row + .event-row {
            border-top: 1px solid var(--sl-color-gray-200);
        }

        .event-logo {
            display: grid;
            place-items: center;
            width: 44px;
            height: 44px;
            border-radius: var(--sl-border-radius-medium);
            overflow: hidden;
        }

        .event-logo.placeholder {
            color: var(--sl-color-gray-400);
            background: var(--sl-color-gray-50);
            border: 1px solid var(--sl-color-gray-200);
            font-size: var(--sl-font-size-large);
        }

        .event-logo img {
            display: block;
            width: 100%;
            height: 100%;
            object-fit: contain;
        }

        .event-main {
            min-width: 0;
        }

        .event-heading {
            display: flex;
            align-items: center;
            flex-wrap: wrap;
            gap: var(--sl-spacing-x-small);
            min-height: var(--sl-input-height-small);
        }

        .event-title {
            color: var(--sl-color-neutral-900);
            font-size: var(--sl-font-size-large);
            font-weight: var(--sl-font-weight-semibold);
            text-decoration: none;
        }

        .event-row:hover .event-title {
            color: var(--sl-color-primary-700);
            text-decoration: underline;
        }

        .event-date,
        .ticket-summary,
        .sales-meta {
            display: flex;
            align-items: center;
            gap: var(--sl-spacing-2x-small);
        }

        .event-date {
            margin-top: var(--sl-spacing-2x-small);
            color: var(--sl-color-gray-600);
            font-size: var(--sl-font-size-small);
        }

        .sales-row {
            display: grid;
            grid-template-columns: minmax(180px, 1fr) minmax(200px, 1.5fr);
            align-items: end;
            gap: var(--sl-spacing-large);
            margin-top: var(--sl-spacing-small);
            max-width: 720px;
        }

        .sales-meta {
            justify-content: space-between;
            color: var(--sl-color-gray-600);
            font-size: var(--sl-font-size-x-small);
            margin-bottom: var(--sl-spacing-2x-small);
        }

        .sales-progress {
            width: 100%;
        }

        sl-progress-bar {
            --height: var(--sl-spacing-2x-small);
            --indicator-color: var(--sl-color-primary-600);
            --track-color: var(--sl-color-gray-100);
        }

        sl-progress-bar.nearly-full {
            --indicator-color: var(--sl-color-warning-600);
        }

        .payment-warning {
            color: var(--sl-color-warning-700);
            font-size: var(--sl-font-size-small);
        }

        alfio-pending-payments-count {
            align-self: center;
        }

        .card-footer sl-button::part(base) {
            padding-inline: 0;
        }

        .state-message {
            padding: var(--sl-spacing-large);
        }

        @media (max-width: 640px) {
            .event-row {
                padding: var(--sl-spacing-medium);
            }

            .sales-row {
                grid-template-columns: 1fr;
                gap: var(--sl-spacing-small);
            }
        }
    `];

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
