import { css, html, LitElement, nothing, TemplateResult } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { repeat } from 'lit/directives/repeat.js';
import { Task } from '@lit/task';
import { AlfioEvent } from '../../model/event.ts';
import { EventService } from '../../service/event.ts';
import { ConfigurationService } from '../../service/configuration.ts';
import {
    badges,
    base,
    modernLayout,
    modernTable,
    purchaseContextListPage,
    reservationTable,
    retroCompat,
    textColors,
} from '../../styles.ts';
import { PageAndContent, ReservationStatus, ReservationSummary } from '../../model/reservation.ts';
import { readRouteParams, replaceRouteParams, toPageNumber } from '../../service/helpers.ts';
import {
    formatAmount,
    formatFullName,
    localizedTitle,
    reservationIdentifier,
} from '../../service/reservation-format.ts';
import { AlfioPageChange } from '../../components/pagination-bar.ts';
import '../../components/pagination-bar.ts';
import '../../components/payment-method.ts';
import '../../components/format-date.ts';
import {emptyState} from '../../components/empty-state.ts';
import { ListSearchController } from '../../components/list-search.ts';
import { taskContent } from '../../components/task-content.ts';

type TabName = 'completed' | 'payment-pending' | 'in-process' | 'credited' | 'cancelled';
interface Reservation extends ReservationSummary {
    paidAmount?: number;
    finalPriceCts: number;
    confirmationTimestamp?: string;
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
    useInvoiceNumberAsId: boolean;
    sections: Record<TabName | 'stuck', PageAndContent<Reservation[]>>;
}

const ITEMS_PER_PAGE = 50;
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
    @state() private lastData: ReservationListData | null = null;
    @state() private selectedTab: TabName = 'completed';
    @state() private pages = { ...FIRST_PAGES };

    private readonly searchController = new ListSearchController(this, (search) => {
        this.search = search;
        this.pages = { ...FIRST_PAGES };
        this.syncLocation();
    });

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName, purchaseContextType, search, pages]): Promise<ReservationListData> => {
            const baseUrl = `/admin/api/reservation/${purchaseContextType}/${encodeURIComponent(eventName)}/reservations/list`;
            const [event, useInvoiceNumberAsId, ...results] = await Promise.all([
                purchaseContextType === 'event'
                    ? EventService.load(eventName).then((result) => result.event)
                    : Promise.resolve(null),
                this.loadUseInvoiceNumberAsId(eventName, purchaseContextType),
                ...sections.map((section) =>
                    this.loadReservations(baseUrl, pages[section.name], search, section.statuses),
                ),
                this.loadReservations(baseUrl, 1, search, ['STUCK']),
            ]);
            const [completedData, paymentPendingData, inProcessData, creditedData, cancelledData, stuckData] =
                results as PageAndContent<Reservation[]>[];
            return {
                event,
                useInvoiceNumberAsId,
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
        purchaseContextListPage,
        reservationTable,
        css`
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
                margin-top: 0;
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
                width: 16%;
            }
            .table > thead > tr > th:nth-child(7) {
                width: 7%;
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        this.loadDataTask.autoRun = !this.completedOnly;
        const params = readRouteParams();
        this.search = params.get('search') ?? '';
        this.searchController.init(this.search);
        this.selectedTab = this.toTabName(params.get('t'));
        this.pages = {
            completed: toPageNumber(params.get('page')),
            'payment-pending': toPageNumber(params.get('pendingPaymentPage')),
            'in-process': toPageNumber(params.get('pendingPage')),
            credited: toPageNumber(params.get('creditedPage')),
            cancelled: toPageNumber(params.get('cancelledPage')),
        };
    }
    render(): TemplateResult {
        if (this.completedOnly) {
            // The host interpolates an empty attribute when it has no reservations yet,
            // which Lit's Array converter turns into null.
            const reservations = this.reservations ?? [];
            return reservations.length === 0
                ? html`
                      ${emptyState('No reservations completed so far')}
                  `
                : this.renderTable(reservations, null);
        }
        return taskContent(this.loadDataTask, this.lastData, 'Failed to load reservations. Please try again.', (data) =>
            this.renderContent(data),
        );
    }
    private renderContent(data: ReservationListData): TemplateResult {
        const stuck = data.sections.stuck;
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>
                        Reservations for
                        <i>
                            ${this.purchaseContextTitle ||
                            (data.event ? localizedTitle(data.event) : this.eventName)}
                        </i>
                    </h1>
                </div>
                <hr class="page-separator" />
                <div class="filter-toolbar">
                    <div class="filter-left">${this.searchController.render('reservations')}</div>
                    ${this.purchaseContextType === 'event' && !data.event?.expired
                        ? html`
                              <div class="filter-right">
                                  <sl-button variant="success" size="large" href=${this.newReservationHref()}>
                                      <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                                      Create Reservation
                                  </sl-button>
                              </div>
                          `
                        : nothing}
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
                              ${emptyState(section.emptyMessage)}
                          `
                        : html`
                              ${this.renderTable(data.left, event)}
                              <alfio-pagination-bar
                                  .page=${this.pages[section.name]}
                                  .total=${data.right}
                                  page-size=${ITEMS_PER_PAGE}
                                  item-label="${section.label.toLowerCase()} reservations"
                                  @alfio-page-change=${(e: AlfioPageChange) => this.changePage(section.name, e.detail.page)}
                              ></alfio-pagination-bar>
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
                            <th>ID</th>
                            <th>Customer</th>
                            <th class="hide-small">Email</th>
                            <th class="hide-small">Payment</th>
                            <th>Amount</th>
                            <th class="hide-small">Confirmation</th>
                            <th>Actions</th>
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
                                            ${reservationIdentifier(reservation, this.lastData?.useInvoiceNumberAsId ?? false)}
                                        </a>
                                    </td>
                                    <td>${formatFullName(reservation)}</td>
                                    <td class="email hide-small">${reservation.email ?? ''}</td>
                                    <td class="hide-small">
                                        <alfio-payment-method method=${reservation.paymentMethod ?? ''}></alfio-payment-method>
                                    </td>
                                    <td class="amount">
                                        ${reservation.finalPriceCts > 0
                                            ? formatAmount(reservation.paidAmount, reservation.currencyCode)
                                            : ''}
                                    </td>
                                    <td class="timestamp hide-small">
                                        <alfio-format-date
                                            date=${reservation.confirmationTimestamp ?? ''}
                                            time-zone=${event?.timeZone ?? 'UTC'}
                                        ></alfio-format-date>
                                    </td>
                                    <td class="actions-cell">
                                        <sl-button
                                            size="small"
                                            variant="default"
                                            outline
                                            href=${this.reservationHref(reservation, event)}
                                        >
                                            <sl-icon slot="prefix" name="eye"></sl-icon>
                                            View
                                        </sl-button>
                                    </td>
                                </tr>
                            `,
                        )}
                    </tbody>
                </table>
            </div>
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
        replaceRouteParams(params);
    }
    private async loadUseInvoiceNumberAsId(
        eventName: string,
        purchaseContextType: 'event' | 'subscription',
    ): Promise<boolean> {
        if (purchaseContextType !== 'event') {
            return false;
        }
        return ConfigurationService.useInvoiceNumberAsId(eventName);
    }
    private reservationHref(reservation: Reservation, event: AlfioEvent | null): string {
        return this.purchaseContextType === 'subscription' && !this.completedOnly
            ? `#/subscriptions/${this.organizationId}/${this.eventName}/reservation/${reservation.id}`
            : `#/events/${reservation.eventPublicIdentifier ?? event?.shortName ?? this.eventName}/reservation/${reservation.id}`;
    }
    private newReservationHref(): string {
        return `#/events/${this.eventName}/reservation/new`;
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
