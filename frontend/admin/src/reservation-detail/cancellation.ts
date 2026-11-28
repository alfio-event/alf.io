import {html} from 'lit';
import {CancellationChoice, CancellationRequest} from '../components/cancellation-dialog.ts';
import {PaymentInfo, ReservationDetail, Ticket} from '../model/reservation-detail.ts';
import {formatAmount, formatFullName, shortReservationId} from '../service/reservation-format.ts';
import '../components/format-date.ts';
import '../components/payment-method.ts';
import { t } from '../service/i18n';

const NOTIFY_LABEL = t('admin.pending-payments.delete.notify');

/**
 * A credit note can be issued if the customer has requested an invoice, and the payment has been received
 */
export function canGenerateCreditNote(reservation: Pick<ReservationDetail, 'invoiceRequested' | 'status'>): boolean {
    return reservation.invoiceRequested && reservation.status !== 'OFFLINE_PAYMENT';
}

/**
 * Cancels the whole reservation, or issues a credit note for it (credit = true)
 */
export function cancelReservationRequest(reservation: ReservationDetail,
                                         paymentInfo: PaymentInfo | null,
                                         credit: boolean,
                                         timeZone: string): CancellationRequest {
    const id = shortReservationId(reservation.id);
    const paid = paidAmount(paymentInfo, reservation.currencyCode);
    const supportRefund = paymentInfo?.supportRefund ?? false;
    const creditNote = canGenerateCreditNote(reservation);
    const transaction = paymentInfo?.transaction;
    let warning: string | undefined;
    if (!credit && paymentInfo?.paymentInformation?.paidAmount !== '0.00' && !supportRefund) {
        warning = t('admin.cancellation.no-automatic-refund');
    }
    const base = {
        details: [
            { label: t('admin.reservation.payment-method'), value: html`<alfio-payment-method method=${paymentInfo?.paymentMethod ?? ''}></alfio-payment-method>` },
            transaction != null && {
                label: t('admin.cancellation.transaction-date'),
                value: html`<alfio-format-date date=${transaction.timestamp} time-zone=${timeZone}></alfio-format-date>`,
            },
            paid !== '' && { label: t('admin.matching-transaction.paid-amount'), value: paid },
        ],
        warning,
        defaults: { refund: true, issueCreditNote: creditNote, notify: false },
    };
    if (credit) {
        return {
            ...base,
            icon: 'arrow-counterclockwise',
            title: t('admin.pending-payments.credit.title', id),
            description: t('admin.cancellation.credit.description'),
            action: t('admin.pending-payments.issue-credit-note'),
            variant: 'warning',
            options: {
                refund: refundLabel(supportRefund, paid),
                notify: t('admin.pending-payments.credit.notify'),
            },
        };
    }
    return {
        ...base,
        icon: 'trash',
        title: t('admin.cancellation.cancel.title', id),
        description: t('admin.cancellation.cancel.description'),
        action: t('admin.reservation.cancel'),
        variant: 'danger',
        options: {
            refund: refundLabel(supportRefund, paid),
            issueCreditNote: creditNoteLabel(creditNote && !supportRefund, paid),
            notify: NOTIFY_LABEL,
        },
    };
}

/**
 * Removes a single ticket from the reservation. The ticket may also be a CategoryTicket
 * (GET /events/{eventName}/category/{categoryId}/ticket), which carries a fullName too
 */
export function removeTicketRequest(reservation: Pick<ReservationDetail, 'id' | 'invoiceRequested' | 'status' | 'currencyCode'>,
                                    paymentInfo: PaymentInfo | null,
                                    ticket: Ticket & { fullName?: string | null }): CancellationRequest {
    const price = formatAmount(ticket.formattedFinalPrice, ticket.currencyCode);
    const supportRefund = paymentInfo?.supportRefund ?? false;
    const creditNote = canGenerateCreditNote(reservation);
    return {
        icon: 'trash',
        title: t('admin.cancellation.remove.title', formatFullName(ticket)),
        description: t('admin.cancellation.remove.description', shortReservationId(reservation.id)),
        action: t('admin.cancellation.remove'),
        variant: 'danger',
        details: [
            { label: t('admin.common.ticket'), value: ticket.uuid, monospace: true },
            { label: t('admin.common.attendee'), value: formatFullName(ticket) },
            { label: t('admin.common.email'), value: ticket.email ?? '' },
            { label: t('admin.matching-transaction.paid-amount'), value: price },
        ],
        options: {
            refund: refundLabel(supportRefund, price),
            issueCreditNote: creditNoteLabel(creditNote && !supportRefund, price),
            notify: t('admin.cancellation.remove.notify'),
        },
        defaults: { refund: false, issueCreditNote: creditNote, notify: false },
    };
}

/**
 * Adjusts the options chosen for a reservation cancellation
 */
export function cancellationChoice(choice: CancellationChoice, paymentInfo: PaymentInfo | null): CancellationChoice {
    if (paymentInfo?.supportRefund && !choice.refund) {
        // don't issue a credit note if there's nothing to credit
        return { ...choice, issueCreditNote: false };
    }
    return choice;
}

/**
 * Adjusts the options chosen for a ticket removal: credit notes are generated only for refunded tickets, so if the
 * payment provider cannot refund the payment, the refund is registered anyway in order to issue the credit note.
 */
export function ticketRemovalChoice(choice: CancellationChoice, paymentInfo: PaymentInfo | null): CancellationChoice {
    if (!paymentInfo?.supportRefund && choice.issueCreditNote) {
        return { ...choice, refund: true };
    }
    return choice;
}

function paidAmount(paymentInfo: PaymentInfo | null, currencyCode: string | null): string {
    const currency = paymentInfo?.transaction?.currency ?? currencyCode;
    return formatAmount(paymentInfo?.paymentInformation?.paidAmount, currency);
}

function refundLabel(supportRefund: boolean, amount: string): string | undefined {
    if (supportRefund) {
        return t('admin.cancellation.refund', amount);
    }
    return undefined;
}

function creditNoteLabel(available: boolean, amount: string): string | undefined {
    if (available) {
        return t('admin.cancellation.credit-note', amount);
    }
    return undefined;
}
