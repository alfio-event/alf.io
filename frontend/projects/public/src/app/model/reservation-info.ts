import {AdditionalField, Ticket} from './ticket';
import {PaymentMethod, PaymentProxy, PaymentProxyWithParameters} from './event';
import {TicketAccessType} from './ticket-category';

export class ReservationInfo {
    id: string;
    shortId: string;
    firstName: string;
    lastName: string;
    email: string;
    validity: number;
    ticketsByCategory: TicketsByTicketCategory[];
    orderSummary: OrderSummary;

    //
    status: ReservationStatus;
    validatedBookingInformation: boolean;
    //
    formattedExpirationDate: {[key: string]: string};
    //

    //
    invoiceNumber: string;
    invoiceRequested: boolean;
    invoiceOrReceiptDocumentPresent: boolean;
    paid: boolean;
    //
    tokenAcquired: boolean;
    paymentProxy: PaymentProxy;
    //
    addCompanyBillingDetails: boolean;
    customerReference: string;
    skipVatNr: boolean;

    billingAddress: string;
    //
    billingDetails: BillingDetails;

    // group related info
    containsCategoriesLinkedToGroups: boolean;
    //

    activePaymentMethods: {[key in PaymentMethod]?: PaymentProxyWithParameters};
    subscriptionInfos?: Array<ReservationSubscriptionInfo>;
    metadata: ReservationMetadata;
    additionalServiceWithData?: Array<AdditionalServiceWithData>;
}

export interface AdditionalServiceWithData {
  title: {[lang: string]: string};
  itemId: number;
  serviceId: number;
  ticketUUID: string | null;
  ticketFieldConfiguration: Array<AdditionalField>;
  type: 'DONATION' | 'SUPPLEMENT';
}

export interface ReservationMetadata {
  hideContactData: boolean;
  lockEmailEdit: boolean;
  hideConfirmationButtons: boolean;
  readyForConfirmation: boolean;
  finalized: boolean;
}

export class ReservationSubscriptionInfo {
  id?: string;
  pin?: string;
  usageDetails?: SubscriptionUsageDetails;
  owner?: SubscriptionOwner;
  configuration?: SubscriptionConfiguration;
  fieldConfigurationBeforeStandard: AdditionalField[];
  fieldConfigurationAfterStandard: AdditionalField[];
  additionalFields: AdditionalField[];
}

export class SubscriptionOwner {
  firstName: string;
  lastName: string;
  email: string;
}

export interface SubscriptionConfiguration {
  displayPin: boolean;
}

export interface SubscriptionUsageDetails {
  total: number | null;
  used: number;
  available: number | null;
}

export class ReservationStatusInfo {
    status: ReservationStatus;
    validatedBookingInformation: boolean;
}

export class TicketsByTicketCategory {
    name: string;
    ticketAccessType: TicketAccessType;
    tickets: Ticket[];
}

export type SummaryType = 'TICKET' | 'PROMOTION_CODE' | 'DYNAMIC_DISCOUNT' | 'ADDITIONAL_SERVICE' | 'APPLIED_SUBSCRIPTION' | 'TAX_DETAIL';

export class OrderSummary {
    summary: SummaryRow[];
    totalPrice: string;
    free: boolean;
    displayVat: boolean;
    priceInCents: number;
    descriptionForPayment: string;
    totalVAT: string;
    vatPercentage: string;
}

export class SummaryRow {
    name: string;
    amount: number;
    price: string;
    subTotal: string;
    type: SummaryType;
    taxPercentage: string;
}

export type ReservationStatus = 'PENDING' | 'IN_PAYMENT' | 'EXTERNAL_PROCESSING_PAYMENT' |
                                'WAITING_EXTERNAL_CONFIRMATION' | 'OFFLINE_PAYMENT' | 'DEFERRED_OFFLINE_PAYMENT' |
                                'OFFLINE_FINALIZING' | 'FINALIZING' | 'COMPLETE' | 'STUCK' | 'CANCELLED' |
                                'CREDIT_NOTE_ISSUED' | 'NOT_FOUND';

export type ItalianEInvoicingReferenceType = 'ADDRESSEE_CODE' | 'PEC' | 'NONE';

export interface BillingDetails {
  companyName: string;
  addressLine1: string;
  addressLine2: string;
  zip: string;
  city: string;
  state: string;
  country: string;
  taxId: string;
  invoicingAdditionalInfo: TicketReservationInvoicingAdditionalInfo;
}

export interface TicketReservationInvoicingAdditionalInfo {
  italianEInvoicing?: ItalianEInvoicing;
}

export interface ItalianEInvoicing {
  referenceType: ItalianEInvoicingReferenceType;
  fiscalCode: string;
  addresseeCode: string;
  pec: string;
  /**
   * either addressee code, pec, or null
   */
  reference: string;
  splitPayment: boolean;
}
