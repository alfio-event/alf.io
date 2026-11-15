import {css, html, LitElement, nothing, PropertyValues, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import type {ChartData, ChartOptions} from 'chart.js';
import type {SlRadioGroup} from '@shoelace-style/shoelace';
import {AlfioEvent, TicketsStatistics} from '../../model/event.ts';
import {EventService} from '../../service/event.ts';
import {errorMessage, supportsOfflinePayments} from '../../service/helpers.ts';
import {DATE_FORMAT_CHANGED_EVENT, formatDate} from '../../service/date-format.ts';
import {subscribePendingPayments} from '../../service/pending-payments.ts';
import {sectionCard} from '../../components/section-card.ts';
import {emptyState} from '../../components/empty-state.ts';
import {slColor} from '../../components/chart.ts';
import type {AlfioChartType} from '../../components/chart.ts';
import '../../components/chart.ts';
import {allocationColor, allocationSegments} from './ticket-allocation.ts';
import {modernLayout, textColors} from '../../styles.ts';

type TrendMode = 'twoWeeks' | 'all';

interface StatTile {
    className: string;
    icon: string;
    label: string;
    value: TemplateResult | string;
    // the page that lists what the number counts
    href?: string;
    // shown only when the sidebar of the event, which already shows it, is hidden
    smallScreensOnly?: boolean;
}

function pad(n: number): string {
    return String(n).padStart(2, '0');
}

function localDateString(date: Date): string {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/**
 * Formats a "yyyy-MM-dd" date (a day, or the first day of a week/month bucket) for a chart axis, with the
 * admin-wide format without the year. The value is a wall-clock day, so it is never shifted by a time zone
 */
function formatChartDate(date: string): string {
    return formatDate(date, {style: 'day-month'}) || date;
}

function formatNumber(amount: number): string {
    return new Intl.NumberFormat('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(amount);
}

/**
 * The statistics of the event detail page: the headline numbers and the graphs (ticket status and
 * reservations trend), rendered with Chart.js (see components/chart.ts).
 */
@customElement('alfio-event-statistics')
export class EventStatistics extends LitElement {

    @property({attribute: false}) event!: AlfioEvent;

    @state() private waitingQueueCount: number | null = null;
    @state() private pendingPaymentsCount: number | null = null;
    @state() private trendMode: TrendMode = 'twoWeeks';
    @state() private statistics: TicketsStatistics | null = null;
    @state() private statisticsError: string | null = null;

    private unsubscribePendingPayments: (() => void) | null = null;

    static readonly styles = [
        modernLayout,
        textColors,
        css`
            :host {
                display: block;
                --alfio-section-body-padding: var(--sl-spacing-medium);
            }
            .section-body {
                display: grid;
                gap: var(--sl-spacing-medium);
            }
            .stats-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
                gap: var(--sl-spacing-small);
            }
            .stat {
                display: block;
                background: var(--sl-color-neutral-0);
                border: 1px solid var(--sl-color-gray-200);
                border-radius: var(--sl-input-border-radius);
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                color: inherit;
                text-decoration: none;
            }
            a.stat {
                transition: border-color var(--sl-transition-fast), background-color var(--sl-transition-fast);
            }
            a.stat:hover {
                border-color: var(--sl-color-primary-300);
                background: var(--sl-color-primary-50);
            }
            a.stat:focus-visible {
                outline: var(--sl-focus-ring);
                outline-offset: var(--sl-focus-ring-offset);
            }
            .stat-label {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }
            a.stat .stat-label sl-icon.go {
                margin-inline-start: auto;
                color: var(--sl-color-primary-600);
            }
            .stat-value {
                font-size: var(--sl-font-size-x-large);
                font-weight: var(--sl-font-weight-semibold);
                line-height: var(--sl-line-height-dense);
                margin-top: var(--sl-spacing-x-small);
                white-space: nowrap;
            }
            .stat-currency {
                font-size: var(--sl-font-size-medium);
                font-weight: var(--sl-font-weight-normal);
                color: var(--sl-color-gray-600);
                margin-inline-end: var(--sl-spacing-2x-small);
            }
            .small-screens-only {
                display: none;
            }
            /* the sidebar of the event, which shows these numbers, is hidden on small screens */
            @media (max-width: 991px) {
                .small-screens-only {
                    display: block;
                }
            }
            .graphs {
                display: grid;
                grid-template-columns: minmax(0, 1fr);
                gap: var(--sl-spacing-medium);
            }
            @media (min-width: 992px) {
                .graphs {
                    grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
                }
            }
            .graph-panel {
                display: flex;
                flex-direction: column;
                background: var(--sl-color-neutral-0);
                border: 1px solid var(--sl-color-gray-200);
                border-radius: var(--sl-input-border-radius);
                padding: var(--sl-spacing-medium);
                min-width: 0;
            }
            .graph-header {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                justify-content: space-between;
                gap: var(--sl-spacing-x-small);
                margin-bottom: var(--sl-spacing-small);
                min-height: var(--sl-input-height-small);
            }
            .graph-header h4 {
                margin: 0;
                font-size: var(--sl-font-size-medium);
            }
            .graph-canvas {
                position: relative;
                height: 14rem;
            }
            .graph-panel .empty-state {
                flex: 1;
            }
            .legend {
                list-style: none;
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-x-small) var(--sl-spacing-medium);
                padding: 0;
                margin: var(--sl-spacing-small) 0 0;
                font-size: var(--sl-font-size-small);
                color: var(--sl-color-gray-700);
            }
            .not-on-sale-hint {
                margin: var(--sl-spacing-x-small) 0 0;
                font-size: var(--sl-font-size-small);
            }
            .legend .swatch {
                display: inline-block;
                width: 0.8em;
                height: 0.8em;
                border-radius: var(--sl-border-radius-small);
                margin-inline-end: var(--sl-spacing-2x-small);
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener(DATE_FORMAT_CHANGED_EVENT, this.onDateFormatChanged);
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        window.removeEventListener(DATE_FORMAT_CHANGED_EVENT, this.onDateFormatChanged);
        this.unsubscribePendingPayments?.();
        this.unsubscribePendingPayments = null;
    }

    // the labels of the chart follow the admin-wide date format
    private onDateFormatChanged = (): void => {
        this.requestUpdate();
    };

    protected willUpdate(changed: PropertyValues): void {
        if (changed.has('event') && this.event != null) {
            void this.loadWaitingQueueCount();
            void this.loadStatistics();
            this.subscribePendingPayments();
        }
        if (changed.has('trendMode') && changed.get('trendMode') != null) {
            void this.loadStatistics();
        }
    }

    private subscribePendingPayments(): void {
        this.unsubscribePendingPayments?.();
        this.unsubscribePendingPayments = subscribePendingPayments(this.event.shortName, (count) => {
            this.pendingPaymentsCount = count;
        });
    }

    private async loadWaitingQueueCount(): Promise<void> {
        try {
            this.waitingQueueCount = await EventService.loadWaitingQueueCount(this.event.shortName);
        } catch {
            this.waitingQueueCount = null;
        }
    }

    private async loadStatistics(): Promise<void> {
        this.statisticsError = null;
        try {
            if (this.trendMode === 'twoWeeks') {
                const to = new Date();
                const from = new Date();
                from.setDate(from.getDate() - 15);
                this.statistics = await EventService.loadTicketsStatistics(this.event.shortName, localDateString(from), localDateString(to));
            } else {
                this.statistics = await EventService.loadTicketsStatistics(this.event.shortName);
            }
        } catch (error) {
            this.statistics = null;
            this.statisticsError = errorMessage(error, 'Failed to load the reservations statistics');
        }
    }

    render(): TemplateResult {
        return sectionCard({icon: 'bar-chart', title: 'Statistics'}, html`
            <div class="stats-grid">
                ${repeat(this.tiles(), (tile) => tile.className, (tile) => this.renderTile(tile))}
            </div>
            <div class="graphs">
                ${this.renderTicketStatus()}
                ${this.renderTrend()}
            </div>
        `);
    }

    private tiles(): StatTile[] {
        const event = this.event;
        const confirmed = (event.soldTickets ?? 0) + (event.checkedInTickets ?? 0);
        const tiles: StatTile[] = [
            {
                className: 'tickets-confirmed',
                icon: 'ticket',
                label: 'Tickets confirmed',
                value: String(confirmed),
                href: `#/events/${event.shortName}/reservations/`,
            },
            {
                className: 'tickets-pending',
                icon: 'hourglass-split',
                label: 'Tickets pending',
                value: String(event.pendingTickets ?? 0),
                href: `#/events/${event.shortName}/reservations/`,
            },
        ];
        if (!event.freeOfCharge) {
            tiles.push({
                className: 'gross-income',
                icon: 'cash-stack',
                label: 'Gross income',
                value: html`<span class="stat-currency">${event.currency}</span>${formatNumber(event.grossIncome ?? 0)}`,
            });
        }
        let waitingQueue = '—';
        if (this.waitingQueueCount != null) {
            waitingQueue = String(this.waitingQueueCount);
        }
        tiles.push({
            className: 'waiting-list',
            icon: 'people',
            label: 'Waiting list',
            value: waitingQueue,
            href: `#/events/${event.shortName}/waiting-queue`,
            smallScreensOnly: true,
        });
        if (supportsOfflinePayments(event.allowedPaymentProxies ?? [])) {
            let pendingPayments = '—';
            if (this.pendingPaymentsCount != null) {
                pendingPayments = String(this.pendingPaymentsCount);
            }
            tiles.push({
                className: 'pending-payments',
                icon: 'credit-card',
                label: 'Pending payments',
                value: pendingPayments,
                href: `#/events/${event.shortName}/pending-payments/`,
                smallScreensOnly: true,
            });
        }
        return tiles;
    }

    private renderTile(tile: StatTile): TemplateResult {
        let className = `stat ${tile.className}`;
        if (tile.smallScreensOnly) {
            className += ' small-screens-only';
        }
        const content = html`
            <span class="stat-label">
                <sl-icon name=${tile.icon} aria-hidden="true"></sl-icon>
                ${tile.label}
                ${when(tile.href != null, () => html`<sl-icon class="go" name="chevron-right" aria-hidden="true"></sl-icon>`, () => nothing)}
            </span>
            <div class="stat-value">${tile.value}</div>
        `;
        return html`
            ${when(tile.href != null,
                () => html`<a class=${className} href=${tile.href!}>${content}</a>`,
                () => html`<div class=${className}>${content}</div>`)}
        `;
    }

    private renderTicketStatus(): TemplateResult {
        const event = this.event;
        const slices = allocationSegments({
            checkedIn: event.checkedInTickets ?? 0,
            sold: event.soldTickets ?? 0,
            pending: (event.pendingTickets ?? 0) + (event.releasedTickets ?? 0),
            availableFixed: event.notSoldTickets ?? 0,
            availableDynamic: event.dynamicAllocation ?? 0,
            notAssigned: event.notAllocatedTickets ?? 0,
        });
        const data: ChartData = {
            labels: slices.map((slice) => slice.label),
            datasets: [{
                data: slices.map((slice) => slice.value),
                backgroundColor: slices.map((slice) => slice.color),
                borderWidth: 1,
                borderColor: slColor('--sl-color-neutral-0', '#ffffff'),
            }],
        };
        const options: ChartOptions = {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {display: false},
                tooltip: {
                    callbacks: {
                        label: (item) => ` ${item.label}: ${item.parsed}`,
                    },
                },
            },
        };
        return html`
            <div class="graph-panel ticket-status">
                <div class="graph-header">
                    <h4>Ticket status</h4>
                </div>
                ${when(slices.length > 0, () => html`
                    <div class="graph-canvas">
                        <alfio-chart type="doughnut" .data=${data} .options=${options} aria-label="Ticket status of the event"></alfio-chart>
                    </div>
                    <ul class="legend">
                        ${repeat(slices, (slice) => slice.key, (slice) => html`
                            <li><span class="swatch" style="background: ${slice.color}"></span>${slice.label}: ${slice.value}</li>
                        `)}
                    </ul>
                    ${when((event.notAllocatedTickets ?? 0) > 0, () => html`
                        <p class="not-on-sale-hint text-muted">
                            No category can sell the ${event.notAllocatedTickets} tickets not on sale. Add a category without a limit, or raise a limit.
                        </p>
                    `, () => nothing)}
                `, () => emptyState('No tickets yet'))}
            </div>
        `;
    }

    private renderTrend(): TemplateResult {
        const statistics = this.statistics;
        // the backend returns one row per day, also for the days without reservations
        const hasData = statistics != null && [...statistics.sold, ...statistics.reserved].some((row) => row.count > 0);
        return html`
            <div class="graph-panel reservations-trend">
                <div class="graph-header">
                    <h4>Reservations trend</h4>
                    <sl-radio-group class="trend-period label-hidden" label="Period" size="small" value=${this.trendMode}
                                    @sl-change=${(e: Event) => { this.trendMode = (e.target as SlRadioGroup).value as TrendMode; }}>
                        <sl-radio-button value="twoWeeks">Last two weeks</sl-radio-button>
                        <sl-radio-button value="all">Entire sale period</sl-radio-button>
                    </sl-radio-group>
                </div>
                ${when(this.statisticsError != null, () => html`
                    <sl-alert open variant="danger">
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.statisticsError}
                        <sl-button size="small" @click=${() => void this.loadStatistics()}>Retry</sl-button>
                    </sl-alert>
                `, () => nothing)}
                ${when(hasData,
                    () => this.renderTrendChart(statistics!),
                    () => when(statistics != null, () => emptyState('No reservations in this period'), () => nothing))}
            </div>
        `;
    }

    private renderTrendChart(statistics: TicketsStatistics): TemplateResult {
        let chartType: AlfioChartType = 'line';
        if (this.trendMode === 'twoWeeks') {
            chartType = 'bar';
        }
        const labels = Array.from(new Set([...statistics.sold, ...statistics.reserved].map((s) => s.date))).sort();
        const countOf = (rows: { date: string; count: number }[], label: string): number =>
            rows.find((row) => row.date === label)?.count ?? 0;
        // the same colors as the ticket status chart: created reservations are pending, confirmed ones are sold
        const reservedColor = allocationColor('pending');
        const soldColor = allocationColor('sold');
        const data: ChartData = {
            labels: labels.map(formatChartDate),
            datasets: [
                {
                    label: 'Created',
                    data: labels.map((label) => countOf(statistics.reserved, label)),
                    backgroundColor: reservedColor,
                    borderColor: reservedColor,
                },
                {
                    label: 'Confirmed',
                    data: labels.map((label) => countOf(statistics.sold, label)),
                    backgroundColor: soldColor,
                    borderColor: soldColor,
                },
            ],
        };
        const options: ChartOptions = {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {mode: 'index', intersect: false},
            plugins: {
                legend: {position: 'bottom', labels: {boxWidth: 12}},
                tooltip: {
                    callbacks: {
                        label: (item) => ` ${item.dataset.label} reservations: ${item.parsed.y}`,
                    },
                },
            },
            scales: {
                y: {beginAtZero: true, ticks: {precision: 0}},
            },
        };
        return html`
            <div class="graph-canvas">
                <alfio-chart type=${chartType} .data=${data} .options=${options}
                             aria-label="Reservations created and confirmed per day"></alfio-chart>
            </div>
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-event-statistics': EventStatistics;
    }
}
