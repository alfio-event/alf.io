import {DateTimeModification} from './event.ts';
import {ReservationStatus} from './reservation.ts';

export type VatStatus = 'NONE' | 'INCLUDED' | 'NOT_INCLUDED' | 'INCLUDED_EXEMPT' | 'NOT_INCLUDED_EXEMPT';
export type TicketStatus = 'FREE' | 'PENDING' | 'TO_BE_PAID' | 'ACQUIRED' | 'CANCELLED' | 'CHECKED_IN' | 'EXPIRED'
    | 'INVALIDATED' | 'RELEASED' | 'PRE_RESERVED';

export interface ReservationDetail {
    id: string;
    // instant
    validity: string;
    status: ReservationStatus;
    firstName: string | null;
    lastName: string | null;
    email: string | null;
    billingAddress: string | null;
    userLanguage: string | null;
    paymentMethod: string | null;
    invoiceNumber: string | null;
    hasInvoiceNumber: boolean;
    invoiceRequested: boolean;
    vatStatus: VatStatus | null;
    vatNr: string | null;
    vatCountryCode: string | null;
    currencyCode: string | null;
}

export interface ItalianEInvoicing {
    fiscalCode: string | null;
    referenceType: 'ADDRESSEE_CODE' | 'PEC' | 'NONE' | null;
    addresseeCode: string | null;
    pec: string | null;
    splitPayment: boolean;
}

export interface InvoicingAdditionalInfo {
    italianEInvoicing: ItalianEInvoicing | null;
}

/**
 * Billing information collected by the checkout
 */
export interface AdditionalInfo {
    // the customer is a company (otherwise a private person)
    addCompanyBillingDetails: boolean | null;
    billingAddressCompany: string | null;
    billingAddressLine1: string | null;
    billingAddressLine2: string | null;
    billingAddressZip: string | null;
    billingAddressCity: string | null;
    billingAddressState: string | null;
    invoicingAdditionalInfo: InvoicingAdditionalInfo | null;
}

export interface SummaryRow {
    name: string;
    price: string;
    amount: number;
    subTotal: string;
    type: 'TICKET' | 'SUBSCRIPTION' | 'PROMOTION_CODE' | 'DYNAMIC_DISCOUNT' | 'ADDITIONAL_SERVICE' | 'APPLIED_SUBSCRIPTION' | 'TAX_DETAIL';
}

export interface OrderSummary {
    summary: SummaryRow[];
    totalPrice: string;
    totalVAT: string;
    vatPercentage: string;
    vatStatus: VatStatus;
    vatExempt: boolean;
}

export interface Ticket {
    id: number;
    uuid: string;
    status: TicketStatus;
    firstName: string | null;
    lastName: string | null;
    email: string | null;
    // already formatted by the backend
    formattedFinalPrice: string;
    currencyCode: string | null;
}

export interface Subscription {
    pin: string;
    firstName: string | null;
    lastName: string | null;
    email: string | null;
    // instants
    validityFrom: string | null;
    validityTo: string | null;
}

export interface SubscriptionWithUsageDetails {
    subscription: Subscription;
    usageDetails: { total: number | null, used: number, available: number | null };
    reservations: unknown[];
}

/**
 * Returned by GET /admin/api/reservation/{type}/{publicIdentifier}/{reservationId}
 */
export interface ReservationDescriptor {
    reservation: ReservationDetail;
    additionalInfo: AdditionalInfo | null;
    orderSummary: OrderSummary;
    ticketsByCategory: { key: { id: number, name: string }, value: Ticket[] }[];
    subscriptionDetails: SubscriptionWithUsageDetails | null;
}

export interface ReservationTransaction {
    id: number;
    transactionId: string | null;
    timestamp: string;
    priceInCents: number;
    currency: string;
    // already formatted by the backend
    formattedAmount: string;
    status: string;
    complete: boolean;
    potentialMatch: boolean;
    metadata: Record<string, string> | null;
}

export interface PaymentInfo {
    paymentMethod: string | null;
    transaction: ReservationTransaction | null;
    // amounts already formatted by the backend
    paymentInformation: { paidAmount: string, refundedAmount: string | null } | null;
    supportRefund: boolean;
}

export type CheckInAuditType = 'CHECK_IN' | 'MANUAL_CHECK_IN' | 'REVERT_CHECK_IN' | 'BADGE_SCAN';

export interface AuditEntry {
    eventType: string;
    eventTime: string;
    entityType: string;
    entityId: string;
    modifications: unknown[] | null;
    username: string | null;
    firstName: string | null;
    lastName: string | null;
    email: string | null;
}

export type BillingDocumentType = 'INVOICE' | 'RECEIPT' | 'CREDIT_NOTE';

export interface BillingDocument {
    id: number;
    type: BillingDocumentType;
    number: string;
    generationTimestamp: string;
    status: 'VALID' | 'NOT_VALID';
}

export interface AdditionalFieldValue {
    name: string;
    value: string | null;
    type: string;
    description: Record<string, { label: string, restrictedValuesDescription: Record<string, string> | null }>;
}

export interface FullTicketData {
    firstName: string | null;
    lastName: string | null;
    email: string | null;
    ticketFieldConfigurationBeforeStandard: AdditionalFieldValue[];
    ticketFieldConfigurationAfterStandard: AdditionalFieldValue[];
}

export interface CustomerData {
    firstName: string;
    lastName: string;
    emailAddress: string;
    billingAddress: string;
    userLanguage: string;
    vatNr: string;
    vatCountryCode: string;
    invoicingAdditionalInfo: InvoicingAdditionalInfo | null;
    // null: only the free text "billingAddress" is updated
    billingDetails: CustomerBillingDetails | null;
}

export interface CustomerBillingDetails {
    company: boolean;
    companyName: string;
    addressLine1: string;
    addressLine2: string;
    zip: string;
    city: string;
    state: string;
}

export interface AttendeeModification {
    ticketId: number;
    firstName: string;
    lastName: string;
    emailAddress: string;
}

export interface SubscriptionDetailsModification {
    firstName: string;
    lastName: string;
    email: string;
    maxAllowed: number | null;
    validityFrom: DateTimeModification | null;
    validityTo: DateTimeModification | null;
}

/**
 * Payload of POST /admin/api/reservation/{type}/{publicIdentifier}/{reservationId} (AdminReservationModification)
 */
export interface ReservationModification {
    expiration: DateTimeModification;
    customerData: CustomerData;
    language: string;
    updateContactData: boolean;
    updateAdvancedBillingOptions: boolean;
    advancedBillingOptions: { vatApplied: 'Y' | 'N' | null };
    ticketsInfo: {
        category: { existingCategoryId: number, name: string },
        attendees: AttendeeModification[],
        updateAttendees: boolean,
    }[];
    subscriptionDetails: SubscriptionDetailsModification | null;
}

/**
 * Backend operations return a Result: errors are described in "errors"
 */
export interface ApiResult<T> {
    success: boolean;
    data: T;
    errors: { description: string }[] | null;
}
