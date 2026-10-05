import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {AlfioEvent} from '../../model/event.ts';
import {PageAndContent, ReservationPaymentDetail} from '../../model/reservation.ts';
import {AlfioDialogClosed} from '../../model/dom-events.ts';
import {EventService} from '../../service/event.ts';
import {ConfigurationService} from '../../service/configuration.ts';
import {fetchJson, readRouteParams, replaceRouteParams, toPageNumber} from '../../service/helpers.ts';
import {
    formatAmount,
    formatFullName,
    localizedTitle,
    reservationIdentifier,
} from '../../service/reservation-format.ts';
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
import {AlfioPageChange} from '../../components/pagination-bar.ts';
import {EditPaymentDialog} from './edit-payment-dialog.ts';
import '../../components/pagination-bar.ts';
import '../../components/payment-method.ts';
import '../../components/format-date.ts';
import './edit-payment-dialog.ts';
import {emptyState} from '../../components/empty-state.ts';
import {ListSearchController} from '../../components/list-search.ts';
import {taskContent} from '../../components/task-content.ts';

interface PaymentsListData {
    event: AlfioEvent;
    useInvoiceNumberAsId: boolean;
    payments: PageAndContent<ReservationPaymentDetail[]>;
}

const ITEMS_PER_PAGE = 50;

@customElement('alfio-payments-list')
export class PaymentsList extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @state() private search = '';
    @state() private page = 1;
    @state() private lastData: PaymentsListData | null = null;
    @query('alfio-edit-payment-dialog') private editDialog!: EditPaymentDialog;

    private readonly searchController = new ListSearchController(this, (search) => {
        this.search = search;
        this.page = 1;
        this.syncLocation();
    });

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName, search, page]): Promise<PaymentsListData> => {
            const params = new URLSearchParams({ page: String(page - 1), search });
            const [eventWithOrganization, useInvoiceNumberAsId, payments] = await Promise.all([
                EventService.load(eventName),
                ConfigurationService.useInvoiceNumberAsId(eventName),
                fetchJson<PageAndContent<ReservationPaymentDetail[]>>(`${this.baseUrl()}/list?${params}`),
            ]);
            return { event: eventWithOrganization.event, useInvoiceNumberAsId, payments };
        },
        args: () => [this.eventName, this.search, this.page] as const,
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
            .table > thead > tr > th:nth-child(1) {
                width: 10%;
            }
            .table > thead > tr > th:nth-child(2) {
                width: 16%;
            }
            .table > thead > tr > th:nth-child(3) {
                width: 20%;
            }
            .table > thead > tr > th:nth-child(4) {
                width: 9%;
            }
            .table > thead > tr > th:nth-child(5) {
                width: 11%;
            }
            .table > thead > tr > th:nth-child(6) {
                width: 13%;
            }
            .table > thead > tr > th:nth-child(7) {
                width: 14%;
            }
            .table > thead > tr > th:nth-child(8) {
                width: 7%;
            }
            .notes {
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
                white-space: pre-line;
                overflow-wrap: anywhere;
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        const params = readRouteParams();
        this.search = params.get('search') ?? '';
        this.searchController.init(this.search);
        this.page = toPageNumber(params.get('page'));
    }

    render(): TemplateResult {
        return taskContent(this.loadDataTask, this.lastData, 'Failed to load payments. Please try again.',
            (data) => this.renderContent(data));
    }

    private renderContent(data: PaymentsListData): TemplateResult {
        const payments = data.payments;
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>Confirmed Payments for <i>${localizedTitle(data.event)}</i></h1>
                </div>
                <hr class="page-separator" />
                <div class="filter-toolbar">
                    <div class="filter-left">${this.searchController.render('payments')}</div>
                    ${when(payments.right > 0, () => html`
                        <div class="filter-right">
                            <sl-button variant="default" href=${this.exportHref()} target="_blank" rel="noopener">
                                <sl-icon slot="prefix" name="download"></sl-icon>
                                Export
                            </sl-button>
                        </div>
                    `)}
                </div>
                ${when(payments.right === 0,
                    () => html`
                        <section class="section-card">
                            ${emptyState('No payments found')}
                        </section>
                    `,
                    () => html`
                        ${this.renderTable(payments.left, data)}
                        <alfio-pagination-bar
                            .page=${this.page}
                            .total=${payments.right}
                            page-size=${ITEMS_PER_PAGE}
                            item-label="payments"
                            @alfio-page-change=${this.onPageChange}
                        ></alfio-pagination-bar>
                    `)}
            </div>
            <alfio-edit-payment-dialog
                purchase-context-type="event"
                public-identifier=${this.eventName}
                time-zone=${data.event.timeZone}
                @alfio-dialog-closed=${this.onEditDialogClosed}
            ></alfio-edit-payment-dialog>
        `;
    }

    private renderTable(payments: ReservationPaymentDetail[], data: PaymentsListData): TemplateResult {
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
                            <th class="hide-small">Date</th>
                            <th class="hide-small">Notes</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(payments, (payment) => payment.id, (payment) => html`
                            <tr>
                                <td class="reservation-id">
                                    <a href=${this.reservationHref(payment.id)}>
                                        ${reservationIdentifier(payment, data.useInvoiceNumberAsId)}
                                    </a>
                                </td>
                                <td>${formatFullName(payment)}</td>
                                <td class="email hide-small">${payment.email ?? ''}</td>
                                <td class="hide-small">
                                    <alfio-payment-method method=${payment.paymentMethod ?? ''}></alfio-payment-method>
                                </td>
                                <td class="amount">${formatAmount(payment.paidAmount, payment.currencyCode)}</td>
                                <td class="timestamp hide-small">
                                    <alfio-format-date
                                        date=${payment.transactionTimestamp}
                                        time-zone=${data.event.timeZone}
                                    ></alfio-format-date>
                                </td>
                                <td class="notes hide-small">${payment.transactionNotes ?? ''}</td>
                                <td class="actions-cell">
                                    <sl-button size="small" variant="default" outline
                                               @click=${() => this.editDialog.open(payment.id)}>
                                        <sl-icon slot="prefix" name="pencil"></sl-icon>
                                        Edit
                                    </sl-button>
                                </td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>
        `;
    }

    private onPageChange = (event: AlfioPageChange): void => {
        this.page = event.detail.page;
        this.syncLocation();
    };

    private onEditDialogClosed = (event: AlfioDialogClosed): void => {
        if (event.detail.success) {
            this.loadDataTask.run();
        }
    };

    private syncLocation(): void {
        replaceRouteParams(new URLSearchParams({ page: String(this.page), search: this.search }));
    }

    private baseUrl(): string {
        return `/admin/api/payments/event/${encodeURIComponent(this.eventName)}`;
    }

    private exportHref(): string {
        return `${this.baseUrl()}/download?${new URLSearchParams({ search: this.search })}`;
    }

    private reservationHref(reservationId: string): string {
        return `#/events/${this.eventName}/reservation/${reservationId}`;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-payments-list': PaymentsList;
    }
}
