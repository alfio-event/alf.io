import {html} from 'lit';
import {CancellationChoice, CancellationRequest} from '../components/cancellation-dialog.ts';
import {PaymentInfo, ReservationDetail, Ticket} from '../model/reservation-detail.ts';
import {formatAmount, formatFullName, shortReservationId} from '../service/reservation-format.ts';
import '../components/format-date.ts';
import '../components/payment-method.ts';

const NOTIFY_LABEL = 'Send a notification email to the reservation contact person';

/**
 * A credit note can be issued if the customer has requested an invoice, and the payment has been received
 */
export function canGenerateCreditNote(reservation: ReservationDetail): boolean {
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
        warning = 'This reservation cannot be refunded automatically.';
    }
    const base = {
        details: [
            { label: 'Payment method', value: html`<alfio-payment-method method=${paymentInfo?.paymentMethod ?? ''}></alfio-payment-method>` },
            transaction != null && {
                label: 'Transaction date',
                value: html`<alfio-format-date date=${transaction.timestamp} time-zone=${timeZone}></alfio-format-date>`,
            },
            paid !== '' && { label: 'Paid amount', value: paid },
        ],
        warning,
        defaults: { refund: true, issueCreditNote: creditNote, notify: false },
    };
    if (credit) {
        return {
            ...base,
            icon: 'arrow-counterclockwise',
            title: `Issue credit note for ${id}?`,
            description: 'A credit note will be generated, and the reservation will be marked as credited.',
            action: 'Issue credit note',
            variant: 'warning',
            options: {
                refund: refundLabel(supportRefund, paid),
                notify: 'Send the credit note to the reservation contact person',
            },
        };
    }
    return {
        ...base,
        icon: 'trash',
        title: `Cancel reservation ${id}?`,
        description: 'All the tickets will be released. This operation cannot be undone.',
        action: 'Cancel reservation',
        variant: 'danger',
        options: {
            refund: refundLabel(supportRefund, paid),
            issueCreditNote: creditNoteLabel(creditNote && !supportRefund, paid),
            notify: NOTIFY_LABEL,
        },
    };
}

/**
 * Removes a single ticket from the reservation
 */
export function removeTicketRequest(reservation: ReservationDetail,
                                    paymentInfo: PaymentInfo | null,
                                    ticket: Ticket): CancellationRequest {
    const price = formatAmount(ticket.formattedFinalPrice, ticket.currencyCode);
    const supportRefund = paymentInfo?.supportRefund ?? false;
    const creditNote = canGenerateCreditNote(reservation);
    return {
        icon: 'trash',
        title: `Remove ticket of ${formatFullName(ticket)}?`,
        description: `The ticket will be removed from reservation ${shortReservationId(reservation.id)} and released.`,
        action: 'Remove ticket',
        variant: 'danger',
        details: [
            { label: 'Ticket', value: ticket.uuid, monospace: true },
            { label: 'Attendee', value: formatFullName(ticket) },
            { label: 'E-mail', value: ticket.email ?? '' },
            { label: 'Paid amount', value: price },
        ],
        options: {
            refund: refundLabel(supportRefund, price),
            issueCreditNote: creditNoteLabel(creditNote && !supportRefund, price),
            notify: 'Send a notification email to the ticket holder',
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
        return `Refund ${amount}`;
    }
    return undefined;
}

function creditNoteLabel(available: boolean, amount: string): string | undefined {
    if (available) {
        return `Issue a credit note for ${amount}`;
    }
    return undefined;
}
