import { css, html, LitElement, nothing, TemplateResult } from 'lit';
import { customElement, property, query, state } from 'lit/decorators.js';
import { repeat } from 'lit/directives/repeat.js';
import { Task, TaskStatus } from '@lit/task';
import { AlfioEvent } from '../../model/event.ts';
import { EventService } from '../../service/event.ts';
import { badges, base, modernLayout, modernTable, retroCompat, textColors } from '../../styles.ts';
import type { SlInput } from '@shoelace-style/shoelace';

type ReservationStatus =
    | 'COMPLETE'
    | 'IN_PAYMENT'
    | 'EXTERNAL_PROCESSING_PAYMENT'
    | 'WAITING_EXTERNAL_CONFIRMATION'
    | 'OFFLINE_PAYMENT'
    | 'CUSTOM_OFFLINE_PAYMENT'
    | 'DEFERRED_OFFLINE_PAYMENT'
    | 'PENDING'
    | 'CREDIT_NOTE_ISSUED'
    | 'CANCELLED'
    | 'STUCK';
type TabName = 'completed' | 'payment-pending' | 'in-process' | 'credited' | 'cancelled';
interface Reservation {
    id: string;
    invoiceNumber?: string;
    firstName?: string;
    lastName?: string;
    fullName?: string;
    email?: string;
    paymentMethod?: string;
    paidAmount?: number;
    finalPriceCts: number;
    currencyCode?: string;
    confirmationTimestamp?: string;
    eventPublicIdentifier?: string;
}
interface PageAndContent<T> {
    left: T;
    right: number;
}
interface ReservationSection {
    name: TabName;
    label: string;
    icon: string;
    emptyMessage: string;
    statuses: ReservationStatus[];
}
interface ReservationListData {
    event: AlfioEvent | null;
    sections: Record<TabName | 'stuck', PageAndContent<Reservation[]>>;
}

const ITEMS_PER_PAGE = 50;
const SEARCH_DELAY_MS = 250;
const FIRST_PAGES: Record<TabName, number> = {
    completed: 1,
    'payment-pending': 1,
    'in-process': 1,
    credited: 1,
    cancelled: 1,
};
const sections: ReservationSection[] = [
    {
        name: 'completed',
        label: 'Completed',
        icon: 'check-circle',
        emptyMessage: 'No completed reservations have been found',
        statuses: ['COMPLETE'],
    },
    {
        name: 'payment-pending',
        label: 'Payment Pending',
        icon: 'cash-coin',
        emptyMessage: 'No reservations pending payment have been found',
        statuses: [
            'IN_PAYMENT',
            'EXTERNAL_PROCESSING_PAYMENT',
            'WAITING_EXTERNAL_CONFIRMATION',
            'OFFLINE_PAYMENT',
            'CUSTOM_OFFLINE_PAYMENT',
            'DEFERRED_OFFLINE_PAYMENT',
        ],
    },
    {
        name: 'in-process',
        label: 'In Process',
        icon: 'cart',
        emptyMessage: 'No reservations in process have been found',
        statuses: ['PENDING'],
    },
    {
        name: 'credited',
        label: 'Credit Note Issued',
        icon: 'arrow-counterclockwise',
        emptyMessage: 'No credited reservations have been found',
        statuses: ['CREDIT_NOTE_ISSUED'],
    },
    {
        name: 'cancelled',
        label: 'Cancelled',
        icon: 'ban',
        emptyMessage: 'No cancelled reservations have been found',
        statuses: ['CANCELLED'],
    },
];

@customElement('alfio-reservations-list')
export class ReservationsList extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @property({ type: String, attribute: 'data-purchase-context-type' }) purchaseContextType: 'event' | 'subscription' =
        'event';
    @property({ type: String, attribute: 'data-title' }) purchaseContextTitle = '';
    @property({ type: Number, attribute: 'data-organization-id' }) organizationId?: number;
    @property({ type: Boolean, attribute: 'data-completed-only' }) completedOnly = false;
    @property({ type: Array, attribute: 'data-reservations' }) reservations: Reservation[] = [];
    @state() private search = '';
    private searchInput = '';
    @state() private lastData: ReservationListData | null = null;
    @state() private selectedTab: TabName = 'completed';
    @state() private pages = { ...FIRST_PAGES };
    private searchTimer: ReturnType<typeof setTimeout> | undefined;
    @query('sl-input.reservation-search') private searchField!: SlInput;

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName, purchaseContextType, search, pages]): Promise<ReservationListData> => {
            const baseUrl = `/admin/api/reservation/${purchaseContextType}/${encodeURIComponent(eventName)}/reservations/list`;
            const [event, ...results] = await Promise.all([
                purchaseContextType === 'event'
                    ? EventService.load(eventName).then((result) => result.event)
                    : Promise.resolve(null),
                ...sections.map((section) =>
                    this.loadReservations(baseUrl, pages[section.name], search, section.statuses),
                ),
                this.loadReservations(baseUrl, 1, search, ['STUCK']),
            ]);
            const [completedData, paymentPendingData, inProcessData, creditedData, cancelledData, stuckData] =
                results as PageAndContent<Reservation[]>[];
            return {
                event,
                sections: {
                    completed: completedData,
                    'payment-pending': paymentPendingData,
                    'in-process': inProcessData,
                    credited: creditedData,
                    cancelled: cancelledData,
                    stuck: stuckData,
                },
            };
        },
        args: () => [this.eventName, this.purchaseContextType, this.search, this.pages] as const,
        // Task invokes this only for the latest request. Keep results mounted during refreshes.
        onComplete: (data) => {
            this.lastData = data;
        },
    });

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        modernTable,
        modernLayout,
        css`
            :host {
                display: block;
            }
            .container {
                width: min(1140px, calc(100% - 2 * var(--sl-spacing-medium)));
                margin-left: 122px;
            }
            .page-title-row {
                margin-top: calc(var(--alfio-page-top-margin) + var(--sl-spacing-small));
            }
            .page-title-row h1 {
                font-size: var(--sl-font-size-2x-large);
                font-weight: var(--sl-font-weight-normal);
            }
            .page-title-row h1 i {
                font-style: italic;
            }
            .filter-toolbar {
                padding: 0;
                background: transparent;
                border: 0;
                border-radius: 0;
                margin-bottom: var(--sl-spacing-small);
            }
            .reservation-search {
                flex: 1 1 20rem;
                margin-top: 0;
            }
            .reservation-search::part(form-control-label) {
                position: absolute;
                width: 1px;
                height: 1px;
                overflow: hidden;
                clip: rect(0 0 0 0);
                white-space: nowrap;
            }
            .stuck-reservations {
                border-color: var(--sl-color-warning-300);
            }
            .stuck-reservations .section-header {
                background: var(--sl-color-warning-50);
                border-bottom-color: var(--sl-color-warning-200);
            }
            .stuck-reservations .section-header sl-icon {
                color: var(--sl-color-warning-700);
            }
            .stuck-message {
                margin: 0;
                padding: var(--sl-spacing-medium);
                color: var(--sl-color-gray-700);
            }
            .reservation-tabs {
                margin-top: var(--sl-spacing-medium);
            }
            .reservation-tabs::part(nav) {
                border-bottom-color: var(--sl-color-gray-300);
            }
            .tab-label {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
            }
            .tab-label sl-badge {
                margin-inline-start: var(--sl-spacing-2x-small);
            }
            .reservation-tabs .section-card {
                border: 0;
                border-radius: 0;
                margin: 0;
                overflow: visible;
            }
            .reservation-tabs .section-body {
                padding: 0;
            }
            .reservation-tabs .empty-state sl-icon {
                --sl-icon-size: var(--sl-font-size-4x-large);
                font-size: var(--sl-font-size-4x-large);
            }
            .table-responsive {
                margin: 0;
                width: 100%;
            }
            .table > thead > tr > th {
                padding: var(--sl-spacing-small);
                font-size: var(--sl-font-size-medium);
                text-transform: none;
                letter-spacing: normal;
            }
            .table > tbody > tr > td {
                padding: var(--sl-spacing-small);
            }
            .table > thead > tr > th:nth-child(1) {
                width: 10%;
            }
            .table > thead > tr > th:nth-child(2) {
                width: 24%;
            }
            .table > thead > tr > th:nth-child(3) {
                width: 27%;
            }
            .table > thead > tr > th:nth-child(4) {
                width: 9%;
            }
            .table > thead > tr > th:nth-child(5) {
                width: 12%;
            }
            .table > thead > tr > th:nth-child(6) {
                width: 18%;
            }
            .reservation-id {
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-small);
            }
            .reservation-id a {
                color: var(--sl-color-primary-600);
                text-decoration: none;
                font-weight: var(--sl-font-weight-normal);
            }
            .reservation-id a:hover {
                color: var(--sl-color-primary-700);
                text-decoration: underline;
            }
            .email {
                min-width: 19rem;
                white-space: nowrap;
            }
            .amount,
            .confirmation {
                white-space: nowrap;
            }
            .pagination-bar {
                display: flex;
                justify-content: center;
                align-items: center;
                flex-wrap: wrap;
                gap: var(--sl-spacing-x-small);
                padding-block: var(--sl-spacing-large);
            }
            .pagination-summary {
                flex-basis: 100%;
                text-align: center;
                color: var(--sl-color-neutral-600);
                font-size: var(--sl-font-size-small);
                margin-top: var(--sl-spacing-x-small);
            }
            .pagination-ellipsis {
                color: var(--sl-color-neutral-500);
                padding-inline: var(--sl-spacing-x-small);
            }
            .loading {
                display: grid;
                place-items: center;
                min-height: 12rem;
            }
            @media (max-width: 1200px) {
                .container {
                    margin-inline: auto;
                }
            }
            @media (max-width: 700px) {
                .hide-small {
                    display: none;
                }
                .section-body {
                    padding: var(--sl-spacing-x-small);
                }
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        this.loadDataTask.autoRun = !this.completedOnly;
        const params = new URLSearchParams(window.location.hash.split('?')[1] ?? '');
        this.search = params.get('search') ?? '';
        this.searchInput = this.search;
        this.selectedTab = this.toTabName(params.get('t'));
        this.pages = {
            completed: this.toPage(params.get('page')),
            'payment-pending': this.toPage(params.get('pendingPaymentPage')),
            'in-process': this.toPage(params.get('pendingPage')),
            credited: this.toPage(params.get('creditedPage')),
            cancelled: this.toPage(params.get('cancelledPage')),
        };
    }
    disconnectedCallback(): void {
        super.disconnectedCallback();
        if (this.searchTimer !== undefined) clearTimeout(this.searchTimer);
    }
    render(): TemplateResult {
        if (this.completedOnly) {
            return this.reservations.length === 0
                ? html`
                      <div class="empty-state">
                          <sl-icon name="inbox"></sl-icon>
                          <span>No reservations completed so far</span>
                      </div>
                  `
                : this.renderTable(this.reservations, null);
        }
        const error =
            this.loadDataTask.status === TaskStatus.ERROR
                ? html`
                      <sl-alert open variant="danger">
                          <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                          Failed to load reservations. Please try again.
                      </sl-alert>
                  `
                : nothing;
        return html`
            ${error}
            ${this.lastData
                ? this.renderContent(this.lastData)
                : this.loadDataTask.status === TaskStatus.ERROR
                  ? nothing
                  : this.renderLoading()}
        `;
    }
    private renderLoading(): TemplateResult {
        return html`
            <div class="loading"><sl-spinner></sl-spinner></div>
        `;
    }
    private renderContent(data: ReservationListData): TemplateResult {
        const stuck = data.sections.stuck;
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>
                        Reservations for
                        <i>
                            ${this.purchaseContextTitle ||
                            (data.event ? this.localizedTitle(data.event) : this.eventName)}
                        </i>
                    </h1>
                </div>
                <hr class="page-separator" />
                <div class="filter-toolbar">
                    <div class="filter-left">
                        <sl-input
                            class="reservation-search"
                            label="Filter reservations"
                            placeholder="Filter Reservations"
                            clearable
                            .value=${this.searchInput}
                            @sl-input=${this.onSearchInput}
                            @sl-clear=${this.clearSearch}
                        >
                            <sl-icon slot="prefix" name="search"></sl-icon>
                        </sl-input>
                    </div>
                </div>
                ${stuck.right > 0
                    ? html`
                          <section class="section-card stuck-reservations">
                              <div class="section-header">
                                  <sl-icon name="exclamation-triangle"></sl-icon>
                                  <h3>Stuck reservations</h3>
                                  <sl-badge variant="warning" pill>${stuck.right}</sl-badge>
                              </div>
                              <p class="stuck-message">
                                  Please check your payment provider’s data and confirm or cancel the following
                                  reservations.
                              </p>
                              ${this.renderTable(stuck.left, data.event)}
                          </section>
                      `
                    : nothing}
                <sl-tab-group class="reservation-tabs" .activeTab=${this.selectedTab} @sl-tab-show=${this.onTabShow}>
                    ${repeat(
                        sections,
                        (section) => section.name,
                        (section) => html`
                            <sl-tab slot="nav" panel=${section.name}>
                                <span class="tab-label">
                                    <sl-icon name=${section.icon}></sl-icon>
                                    ${section.label}
                                    <sl-badge variant="primary" pill>${data.sections[section.name].right}</sl-badge>
                                </span>
                            </sl-tab>
                            <sl-tab-panel name=${section.name}>
                                ${this.renderSection(section, data.sections[section.name], data.event)}
                            </sl-tab-panel>
                        `,
                    )}
                </sl-tab-group>
            </div>
        `;
    }
    private renderSection(
        section: ReservationSection,
        data: PageAndContent<Reservation[]>,
        event: AlfioEvent | null,
    ): TemplateResult {
        return html`
            <section class="section-card">
                <div class="section-body">
                    ${data.right === 0
                        ? html`
                              <div class="empty-state">
                                  <sl-icon name="inbox"></sl-icon>
                                  <span>${section.emptyMessage}</span>
                              </div>
                          `
                        : html`
                              ${this.renderTable(data.left, event)}${this.renderPagination(section.name, data.right)}
                          `}
                </div>
            </section>
        `;
    }
    private renderTable(reservations: Reservation[], event: AlfioEvent | null): TemplateResult {
        return html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            <th>Id</th>
                            <th>Customer’s name</th>
                            <th class="hide-small">Customer’s email</th>
                            <th class="hide-small">Payment</th>
                            <th>Amount</th>
                            <th class="hide-small">Confirmation</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(
                            reservations,
                            (reservation) => reservation.id,
                            (reservation) => html`
                                <tr>
                                    <td class="reservation-id">
                                        <a href=${this.reservationHref(reservation, event)}>
                                            ${this.reservationIdentifier(reservation)}
                                        </a>
                                    </td>
                                    <td>${this.fullName(reservation)}</td>
                                    <td class="email hide-small">${reservation.email ?? ''}</td>
                                    <td class="hide-small">${reservation.paymentMethod ?? ''}</td>
                                    <td class="amount">
                                        ${reservation.finalPriceCts > 0 ? this.formatAmount(reservation) : ''}
                                    </td>
                                    <td class="confirmation hide-small">
                                        ${this.formatDate(reservation.confirmationTimestamp, event?.timeZone ?? 'UTC')}
                                    </td>
                                </tr>
                            `,
                        )}
                    </tbody>
                </table>
            </div>
        `;
    }
    private renderPagination(name: TabName, total: number): TemplateResult {
        const page = this.pages[name];
        const lastPage = Math.max(1, Math.ceil(total / ITEMS_PER_PAGE));
        const visiblePages = Array.from({length: lastPage}, (_, index) => index + 1)
            .filter(number => number === 1 || number === lastPage || Math.abs(number - page) <= 2);
        return html`
            <nav class="pagination-bar" aria-label="${name} reservations pages">
                <sl-button
                    size="large"
                    variant="default"
                    outline
                    ?disabled=${page === 1}
                    @click=${() => this.changePage(name, page - 1)}
                >
                    <sl-icon slot="prefix" name="chevron-left"></sl-icon>
                    Previous
                </sl-button>
                ${visiblePages.map((number, index) => html`
                    ${index > 0 && number - visiblePages[index - 1] > 1
                        ? html`<span class="pagination-ellipsis" aria-hidden="true">…</span>`
                        : nothing}
                    <sl-button
                        size="large"
                        variant=${number === page ? 'primary' : 'default'}
                        ?outline=${number !== page}
                        aria-label="Page ${number}"
                        aria-current=${number === page ? 'page' : nothing}
                        @click=${() => this.changePage(name, number)}
                    >${number}</sl-button>
                `)}
                <sl-button
                    size="large"
                    variant="default"
                    outline
                    ?disabled=${page >= lastPage}
                    @click=${() => this.changePage(name, page + 1)}
                >
                    Next
                    <sl-icon slot="suffix" name="chevron-right"></sl-icon>
                </sl-button>
                <span class="pagination-summary">Page ${page} of ${lastPage} · ${total.toLocaleString()} reservations</span>
            </nav>
        `;
    }
    private async loadReservations(
        baseUrl: string,
        page: number,
        search: string,
        statuses: ReservationStatus[],
    ): Promise<PageAndContent<Reservation[]>> {
        const params = new URLSearchParams({ page: String(page - 1), search });
        statuses.forEach((status) => params.append('status', status));
        const response = await fetch(`${baseUrl}?${params}`, { credentials: 'include' });
        if (!response.ok) throw new Error('Failed to load reservations');
        return response.json();
    }

    private onSearchInput = (event: Event): void => {
        this.searchInput = (event.target as SlInput).value;
        clearTimeout(this.searchTimer);
        this.searchTimer = setTimeout(() => this.applySearch(), SEARCH_DELAY_MS);
    };

    private applySearch(): void {
        clearTimeout(this.searchTimer);
        if (this.search === this.searchInput) return;
        this.search = this.searchInput;
        this.pages = { ...FIRST_PAGES };
        this.syncLocation();
    }

    private clearSearch = (): void => {
        this.searchInput = '';
        this.applySearch();
        this.searchField.focus();
    };
    private onTabShow = (event: CustomEvent<{ name: string }>): void => {
        this.selectedTab = event.detail.name as TabName;
        this.syncLocation();
    };
    private changePage(name: TabName, page: number): void {
        this.pages = { ...this.pages, [name]: page };
        this.syncLocation();
    }
    private syncLocation(): void {
        const params = new URLSearchParams({
            pendingPage: String(this.pages['in-process']),
            pendingPaymentPage: String(this.pages['payment-pending']),
            cancelledPage: String(this.pages.cancelled),
            creditedPage: String(this.pages.credited),
            page: String(this.pages.completed),
            search: this.search,
            t: String(sections.findIndex((section) => section.name === this.selectedTab) + 1),
        });
        const route = window.location.hash.split('?')[0];
        window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}${route}?${params}`);
    }
    private fullName(reservation: Reservation): string {
        return reservation.firstName && reservation.lastName
            ? `${reservation.firstName} ${reservation.lastName}`
            : (reservation.fullName ?? '');
    }
    private reservationIdentifier(reservation: Reservation): string {
        return reservation.invoiceNumber ?? reservation.id.substring(0, 8).toUpperCase();
    }
    private reservationHref(reservation: Reservation, event: AlfioEvent | null): string {
        return this.purchaseContextType === 'subscription' && !this.completedOnly
            ? `#/subscriptions/${this.organizationId}/${this.eventName}/reservation/${reservation.id}`
            : `#/events/${reservation.eventPublicIdentifier ?? event?.shortName ?? this.eventName}/reservation/${reservation.id}`;
    }
    private formatAmount(reservation: Reservation): string {
        return reservation.paidAmount == null || reservation.currencyCode == null
            ? ''
            : `${reservation.currencyCode} ${new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(reservation.paidAmount)}`;
    }
    private formatDate(value: string | undefined, timeZone: string): string {
        if (!value) return '';
        const normalized = /(?:Z|[+-]\d\d:\d\d)$/i.test(value) ? value : `${value.replace(' ', 'T')}Z`;
        const parts = new Intl.DateTimeFormat('en-GB', {
            timeZone,
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            hourCycle: 'h23',
        }).formatToParts(new Date(normalized));
        const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value ?? '';
        return `${part('day')}.${part('month')}.${part('year')} ${part('hour')}:${part('minute')}`;
    }
    private localizedTitle(event: AlfioEvent): string {
        return Object.values(event.title)[0] ?? event.shortName;
    }
    private toPage(value: string | null): number {
        const page = Number(value);
        return Number.isInteger(page) && page > 0 ? page : 1;
    }
    private toTabName(value: string | null): TabName {
        return sections[Number(value) - 1]?.name ?? 'completed';
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-reservations-list': ReservationsList;
    }
}
