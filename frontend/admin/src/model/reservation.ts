export type ReservationStatus =
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

export interface PageAndContent<T> {
    left: T;
    right: number;
}

/**
 * Fields shared by every reservation row returned by the admin list endpoints
 * (reservations, confirmed payments)
 */
export interface ReservationSummary {
    id: string;
    invoiceNumber?: string | null;
    firstName?: string | null;
    lastName?: string | null;
    fullName?: string | null;
    email?: string | null;
    paymentMethod?: string | null;
    currencyCode?: string | null;
    eventPublicIdentifier?: string | null;
}

export interface ReservationPaymentDetail extends ReservationSummary {
    // already formatted by the backend
    paidAmount: string;
    transactionTimestamp: string;
    transactionNotes?: string | null;
}

export interface PaymentTransaction {
    timestamp: string;
    timestampEditable: boolean;
    notes?: string | null;
}

export interface PendingPaymentReservation extends ReservationSummary {
    validity: string;
    status: string;
}

export interface PendingPaymentTransaction {
    id: number;
    transactionId?: string | null;
    timestamp: string;
    priceInCents: number;
    currency: string;
    // already formatted by the backend
    formattedAmount: string;
    status: string;
}

export interface PendingPayment {
    ticketReservation: PendingPaymentReservation;
    transaction: PendingPaymentTransaction | null;
    ticketsCount: number;
}

export interface BulkConfirmationResult {
    // true if the payment has been confirmed
    left: boolean;
    // reservation ID, as written in the uploaded file
    middle: string;
    // error message
    right: string;
}
