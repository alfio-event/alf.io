import {PurchaseContextType} from '../model/purchase-context.ts';
import {EmailMessage} from '../model/email-message.ts';
import {
    ApiResult,
    AuditEntry,
    BillingDocument,
    FullTicketData,
    PaymentInfo,
    ReservationDescriptor,
    ReservationModification,
} from '../model/reservation-detail.ts';
import {fetchJson, postJson, putJson, callDelete} from './helpers.ts';

export interface CancelReservationOptions {
    // true: issue a credit note, false: cancel the reservation
    credit: boolean;
    refund: boolean;
    notify: boolean;
    issueCreditNote: boolean;
}

export interface RemoveTicketOptions {
    refund: boolean;
    notify: boolean;
    issueCreditNote: boolean;
}

/**
 * Operations on a single reservation, for events and subscriptions.
 * Mutating operations reject with the error returned by the backend.
 */
export class ReservationService {

    constructor(private readonly purchaseContextType: PurchaseContextType,
                private readonly publicIdentifier: string,
                private readonly reservationId: string) {
    }

    load(): Promise<ReservationDescriptor> {
        return this.getData('');
    }

    paymentInfo(): Promise<PaymentInfo> {
        return this.getData('/payment-info');
    }

    audit(): Promise<AuditEntry[]> {
        return this.getData('/audit');
    }

    billingDocuments(): Promise<BillingDocument[]> {
        return this.getData('/billing-documents');
    }

    emails(): Promise<EmailMessage[]> {
        return this.getData('/email-list');
    }

    ticketsWithAdditionalData(): Promise<number[]> {
        return fetchJson(this.url('/tickets-with-additional-data'));
    }

    async fullTicketData(ticketUuid: string): Promise<FullTicketData> {
        return ensureOk(await fetch(this.url(`/ticket/${ticketUuid}/full-data`), { credentials: 'include' }));
    }

    billingDocumentUrl(documentId: number): string {
        return this.url(`/billing-document/${documentId}`);
    }

    update(modification: ReservationModification): Promise<boolean> {
        return result(postJson(this.url(''), modification));
    }

    /**
     * Marks the reservation as completed. Use EditPaymentDialog to confirm offline payments.
     */
    confirm(): Promise<unknown> {
        return result(putJson(this.url('/confirm'), null));
    }

    notifyCustomer(): Promise<boolean> {
        return result(putJson(this.url('/notify'), { notification: { customer: true, attendees: false } }));
    }

    notifyAttendees(ticketIds: number[]): Promise<boolean> {
        return result(putJson(this.url('/notify-attendees'), ticketIds));
    }

    refund(amount: string): Promise<boolean> {
        return result(postJson(this.url('/refund'), { amount }));
    }

    cancel(options: CancelReservationOptions): Promise<boolean> {
        const params = new URLSearchParams({
            refund: String(options.refund),
            notify: String(options.notify),
            issueCreditNote: String(options.issueCreditNote),
        });
        let operation = 'cancel';
        if (options.credit) {
            operation = 'credit';
        }
        return result(postJson(this.url(`/${operation}?${params}`), null));
    }

    /**
     * @returns true if a credit note has been generated
     */
    async removeTicket(ticketId: number, options: RemoveTicketOptions): Promise<boolean> {
        const removal = await result<{ creditNoteGenerated: boolean }>(postJson(this.url('/remove-tickets'), {
            ticketIds: [ticketId],
            refundTo: { [ticketId]: options.refund },
            notify: options.notify,
            issueCreditNote: options.issueCreditNote,
        }));
        return removal.creditNoteGenerated;
    }

    regenerateBillingDocument(): Promise<boolean> {
        return result(putJson(this.url('/regenerate-billing-document'), {}));
    }

    async invalidateBillingDocument(documentId: number): Promise<void> {
        await ensureOk(await callDelete(this.billingDocumentUrl(documentId)));
    }

    async restoreBillingDocument(documentId: number): Promise<void> {
        await ensureOk(await putJson(`${this.billingDocumentUrl(documentId)}/restore`, null));
    }

    private getData<T>(suffix: string): Promise<T> {
        return result(fetch(this.url(suffix), { credentials: 'include' }));
    }

    private url(suffix: string): string {
        return `/admin/api/reservation/${this.purchaseContextType}/${encodeURIComponent(this.publicIdentifier)}/${this.reservationId}${suffix}`;
    }
}

async function ensureOk<T>(response: Response): Promise<T> {
    if (!response.ok) {
        throw new Error((await response.text()) || `unexpected status ${response.status}`);
    }
    return response.json();
}

async function result<T>(request: Promise<Response>): Promise<T> {
    const body = await ensureOk<ApiResult<T>>(await request);
    if (!body.success) {
        throw new Error(body.errors?.map(e => e.description).join(', ') || 'An unexpected error has occurred');
    }
    return body.data;
}
