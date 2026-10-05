import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task, TaskStatus} from '@lit/task';
import type {SlInput} from '@shoelace-style/shoelace';
import {AlfioEvent} from '../../model/event.ts';
import {PendingPayment} from '../../model/reservation.ts';
import {AlfioDialogClosed, dispatchFeedback} from '../../model/dom-events.ts';
import {EventService} from '../../service/event.ts';
import {ConfigurationService} from '../../service/configuration.ts';
import {PendingPaymentsService, refreshPendingPayments} from '../../service/pending-payments.ts';
import {
    formatAmount,
    formatFullName,
    localizedTitle,
    reservationIdentifier,
} from '../../service/reservation-format.ts';
import {nextSort, sortableHeader, sortItems, SortState, SortValue} from '../../service/table-sort.ts';
import {
    badges,
    base,
    modernLayout,
    modernTable,
    purchaseContextListPage,
    reservationTable,
    retroCompat,
    sortableTable,
    textColors,
} from '../../styles.ts';
import {EditPaymentDialog} from '../payments-list/edit-payment-dialog.ts';
import {MatchingTransactionDialog} from './matching-transaction-dialog.ts';
import {CancelPaymentDialog} from './cancel-payment-dialog.ts';
import '../payments-list/edit-payment-dialog.ts';
import '../../components/format-date.ts';
import './matching-transaction-dialog.ts';
import './cancel-payment-dialog.ts';
import './bulk-confirmation.ts';
import {emptyState} from '../../components/empty-state.ts';

interface PendingPaymentRow {
    payment: PendingPayment;
    // reservation ID or invoice number, depending on the configuration
    publicId: string;
    customer: string;
    overdue: boolean;
}

interface PendingPaymentsData {
    event: AlfioEvent;
    rows: PendingPaymentRow[];
}

type SortKey = 'id' | 'expiration' | 'customer' | 'email' | 'tickets' | 'amount';
type CancelAction = 'credit' | 'delete';

const CANCEL_FEEDBACK: Record<CancelAction, { success: string, error: string }> = {
    credit: { success: 'Credit note issued successfully', error: 'Failed to issue the credit note' },
    delete: { success: 'Reservation deleted successfully', error: 'Failed to delete the reservation' },
};

@customElement('alfio-pending-payments')
export class PendingPayments extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @state() private search = '';
    @state() private sort: SortState<SortKey> = { sortKey: 'expiration', sortDirection: 'asc' };
    @state() private busy = false;
    @state() private lastData: PendingPaymentsData | null = null;
    @query('alfio-edit-payment-dialog') private confirmDialog!: EditPaymentDialog;
    @query('alfio-matching-transaction-dialog') private matchingDialog!: MatchingTransactionDialog;
    @query('alfio-cancel-payment-dialog') private cancelDialog!: CancelPaymentDialog;

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName]): Promise<PendingPaymentsData> => {
            const [eventWithOrganization, useInvoiceNumberAsId, payments] = await Promise.all([
                EventService.load(eventName),
                ConfigurationService.useInvoiceNumberAsId(eventName),
                PendingPaymentsService.load(eventName),
            ]);
            const now = Date.now();
            const rows = payments.map(payment => ({
                payment,
                publicId: reservationIdentifier(payment.ticketReservation, useInvoiceNumberAsId),
                customer: formatFullName(payment.ticketReservation),
                overdue: new Date(payment.ticketReservation.validity).getTime() < now,
            }));
            return { event: eventWithOrganization.event, rows };
        },
        args: () => [this.eventName] as const,
        // Keep results mounted during refreshes.
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
        sortableTable,
        modernLayout,
        purchaseContextListPage,
        reservationTable,
        css`
            alfio-bulk-confirmation {
                margin-top: var(--sl-spacing-x-large);
            }
            .page-description {
                margin: 0 0 var(--sl-spacing-medium);
            }
            .result-count {
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
                white-space: nowrap;
            }
            .expiration {
                white-space: nowrap;
            }
            .expiration sl-badge {
                margin-inline-start: var(--sl-spacing-2x-small);
            }
            .tickets {
                text-align: right;
            }
            .table > thead > tr > th.tickets,
            .table > thead > tr > th.amount {
                text-align: right;
            }
            .table > thead > tr > th.tickets .sort-button,
            .table > thead > tr > th.amount .sort-button {
                flex-direction: row-reverse;
            }
            .actions-cell {
                flex-wrap: nowrap;
            }
        `,
    ];

    render(): TemplateResult {
        return html`
            ${when(this.loadDataTask.status === TaskStatus.ERROR, () => html`
                <sl-alert open variant="danger">
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    Failed to load pending payments. Please try again.
                </sl-alert>
            `)}
            ${when(this.lastData,
                () => this.renderContent(this.lastData!),
                () => this.renderLoading())}
        `;
    }

    private renderLoading(): TemplateResult | typeof nothing {
        if (this.loadDataTask.status === TaskStatus.ERROR) {
            return nothing;
        }
        return html`<div class="loading"><sl-spinner></sl-spinner></div>`;
    }

    private renderContent(data: PendingPaymentsData): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>Pending Payments for <i>${localizedTitle(data.event)}</i></h1>
                </div>
                <p class="page-description text-muted">Confirm them one at a time, or upload a file to confirm them in bulk.</p>
                <hr class="page-separator" />
                ${when(data.rows.length === 0,
                    () => html`
                        <section class="section-card">
                            ${emptyState('No pending payments found')}
                        </section>
                    `,
                    () => this.renderPendingPayments(data))}
                <alfio-bulk-confirmation event-name=${this.eventName}
                                         @alfio-payments-confirmed=${this.reload}></alfio-bulk-confirmation>
            </div>
            <alfio-edit-payment-dialog
                purchase-context-type="event"
                public-identifier=${this.eventName}
                time-zone=${data.event.timeZone}
                @alfio-dialog-closed=${this.onConfirmDialogClosed}
            ></alfio-edit-payment-dialog>
            <alfio-matching-transaction-dialog time-zone=${data.event.timeZone}></alfio-matching-transaction-dialog>
            <alfio-cancel-payment-dialog></alfio-cancel-payment-dialog>
        `;
    }

    private renderPendingPayments(data: PendingPaymentsData): TemplateResult {
        const rows = this.visibleRows(data.rows);
        return html`
            <div class="filter-toolbar">
                <div class="filter-left">
                    <sl-input
                        class="list-search"
                        label="Filter payments"
                        placeholder="Filter Payments"
                        clearable
                        .value=${this.search}
                        @sl-input=${(e: Event) => { this.search = (e.target as SlInput).value; }}
                    >
                        <sl-icon slot="prefix" name="search"></sl-icon>
                    </sl-input>
                </div>
                <div class="filter-right">
                    <span class="result-count">${this.resultCount(rows.length, data.rows.length)}</span>
                </div>
            </div>
            ${when(rows.length === 0,
                () => html`
                    <section class="section-card">
                        ${emptyState('No pending payments match your filter')}
                    </section>
                `,
                () => this.renderTable(rows, data))}
        `;
    }

    private renderTable(rows: PendingPaymentRow[], data: PendingPaymentsData): TemplateResult {
        const onSort = (sortKey: SortKey) => {
            this.sort = nextSort(this.sort, sortKey);
        };
        return html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            ${sortableHeader('ID', 'id', this.sort, onSort)}
                            ${sortableHeader('Expires', 'expiration', this.sort, onSort)}
                            ${sortableHeader('Customer', 'customer', this.sort, onSort)}
                            ${sortableHeader('Email', 'email', this.sort, onSort, 'hide-small')}
                            ${sortableHeader('Tickets', 'tickets', this.sort, onSort, 'tickets hide-small')}
                            ${sortableHeader('Amount', 'amount', this.sort, onSort, 'amount')}
                            <th aria-label="Actions"></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(rows, (row) => row.payment.ticketReservation.id, (row) => this.renderRow(row, data))}
                    </tbody>
                </table>
            </div>
        `;
    }

    private renderRow(row: PendingPaymentRow, data: PendingPaymentsData): TemplateResult {
        const reservation = row.payment.ticketReservation;
        const transaction = row.payment.transaction;
        return html`
            <tr>
                <td class="reservation-id">
                    <a href=${this.reservationHref(reservation.id)}>${row.publicId}</a>
                </td>
                <td class="expiration">
                    <alfio-format-date date=${reservation.validity} time-zone=${data.event.timeZone}></alfio-format-date>
                    ${when(row.overdue, () => html`<sl-badge variant="danger" pill>Overdue</sl-badge>`)}
                </td>
                <td>${row.customer}</td>
                <td class="email hide-small">${reservation.email ?? ''}</td>
                <td class="tickets hide-small">${row.payment.ticketsCount}</td>
                <td class="amount">${when(transaction, () => formatAmount(transaction!.formattedAmount, transaction!.currency))}</td>
                <td>
                    <div class="actions-cell">
                        ${when(this.hasMatchingTransaction(row.payment), () => html`
                            <sl-button size="small" variant="primary" outline ?disabled=${this.busy}
                                       @click=${() => this.reviewMatchingTransaction(row.payment)}>
                                <sl-icon slot="prefix" name="arrow-left-right"></sl-icon>
                                Review match
                            </sl-button>
                        `)}
                        <sl-button size="small" variant="success" outline ?disabled=${this.busy}
                                   @click=${() => this.confirmDialog.openForConfirmation(reservation.id)}>
                            <sl-icon slot="prefix" name="check2"></sl-icon>
                            Confirm
                        </sl-button>
                        <sl-dropdown hoist placement="bottom-end">
                            <sl-icon-button slot="trigger" name="three-dots-vertical" label="More actions for ${row.publicId}"
                                            ?disabled=${this.busy}></sl-icon-button>
                            <sl-menu @sl-select=${(e: CustomEvent) => this.cancel(row.payment, e.detail.item.value)}>
                                ${when(this.canIssueCreditNote(row.payment), () => html`
                                    <sl-menu-item value="credit">
                                        <sl-icon slot="prefix" name="arrow-counterclockwise"></sl-icon>
                                        Issue credit note
                                    </sl-menu-item>
                                `)}
                                <sl-menu-item value="delete" class="danger">
                                    <sl-icon slot="prefix" name="trash"></sl-icon>
                                    Delete
                                </sl-menu-item>
                            </sl-menu>
                        </sl-dropdown>
                    </div>
                </td>
            </tr>
        `;
    }

    private visibleRows(rows: PendingPaymentRow[]): PendingPaymentRow[] {
        const term = this.search.trim().toLowerCase();
        const matching = rows.filter(row => term === '' || this.searchableText(row).includes(term));
        return sortItems(matching, this.sort, (row, key) => this.sortValue(row, key));
    }

    private searchableText(row: PendingPaymentRow): string {
        const reservation = row.payment.ticketReservation;
        return [row.publicId, reservation.id, row.customer, reservation.email, reservation.invoiceNumber, row.payment.transaction?.formattedAmount]
            .filter(value => value != null)
            .join(' ')
            .toLowerCase();
    }

    private sortValue(row: PendingPaymentRow, sortKey: SortKey): SortValue {
        const reservation = row.payment.ticketReservation;
        switch (sortKey) {
            case 'id': return row.publicId;
            case 'expiration': return new Date(reservation.validity).getTime();
            case 'customer': return row.customer;
            case 'email': return reservation.email;
            case 'tickets': return row.payment.ticketsCount;
            case 'amount': return row.payment.transaction?.priceInCents ?? -1;
        }
    }

    private resultCount(visible: number, total: number): string {
        if (visible === total) {
            return `${total} pending`;
        }
        return `${visible} of ${total} pending`;
    }

    private hasMatchingTransaction(payment: PendingPayment): boolean {
        return payment.transaction?.status === 'OFFLINE_PENDING_REVIEW';
    }

    private canIssueCreditNote(payment: PendingPayment): boolean {
        const reservation = payment.ticketReservation;
        return reservation.status === 'OFFLINE_PAYMENT' && !!reservation.invoiceNumber;
    }

    private async reviewMatchingTransaction(payment: PendingPayment): Promise<void> {
        const choice = await this.matchingDialog.open(payment);
        const reservationId = payment.ticketReservation.id;
        if (choice === 'confirm') {
            await this.confirmDialog.openForConfirmation(reservationId);
        } else if (choice === 'discard') {
            await this.perform(
                () => PendingPaymentsService.discardMatchingTransaction(this.eventName, reservationId, payment.transaction!.id),
                'Transaction flagged as not valid',
                'Failed to flag the transaction as not valid');
        }
    }

    private async cancel(payment: PendingPayment, action: CancelAction): Promise<void> {
        const reservationId = payment.ticketReservation.id;
        const credit = action === 'credit';
        const choice = await this.cancelDialog.open(reservationId, credit);
        if (choice == null) {
            return;
        }
        const feedback = CANCEL_FEEDBACK[action];
        await this.perform(
            () => PendingPaymentsService.cancel(this.eventName, reservationId, credit, choice.notify),
            feedback.success,
            feedback.error);
    }

    private async perform(action: () => Promise<void>, successMessage: string, errorMessage: string): Promise<void> {
        this.busy = true;
        try {
            await action();
            dispatchFeedback({ type: 'success', message: successMessage }, this);
        } catch {
            dispatchFeedback({ type: 'danger', message: errorMessage }, this);
        } finally {
            this.busy = false;
            this.reload();
        }
    }

    private onConfirmDialogClosed = (event: AlfioDialogClosed): void => {
        if (event.detail.success) {
            refreshPendingPayments(this.eventName);
            this.reload();
        }
    };

    private reload = (): void => {
        void this.loadDataTask.run();
    };

    private reservationHref(reservationId: string): string {
        return `#/events/${this.eventName}/reservation/${reservationId}`;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-pending-payments': PendingPayments;
    }
}
