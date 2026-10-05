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
