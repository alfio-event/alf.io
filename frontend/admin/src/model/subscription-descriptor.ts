import {ContentLanguage, DateTimeModification, LocalizedContent, PaymentProxy, PurchaseContext, VatStatus} from "./purchase-context.ts";

export interface SubscriptionDescriptor extends PurchaseContext {
    title: LocalizedContent;
    organizationId: number;
    currency: string;
    timeZone: string;
}

export type SubscriptionValidityType = 'STANDARD' | 'CUSTOM' | 'NOT_SET';
export type SubscriptionTimeUnit = 'DAYS' | 'MONTHS' | 'YEARS';
export type SubscriptionUsageType = 'ONCE_PER_EVENT' | 'UNLIMITED';

/**
 * A subscription descriptor as returned by the admin list and detail endpoints.
 * Dates are ISO-8601 instants (ZonedDateTime), price is in cents.
 */
export interface SubscriptionDescriptorFull {
    id: string;
    title: LocalizedContent;
    // title with the commonmark already rendered to plain text
    titleAsText: LocalizedContent;
    description: LocalizedContent;
    descriptionAsText: LocalizedContent;
    maxAvailable: number; // -1 = unlimited
    creation: string;
    onSaleFrom: string | null;
    onSaleTo: string | null;
    price: number;
    vat: string | null;
    vatStatus: VatStatus;
    currency: string;
    isPublic: boolean;
    organizationId: number;
    maxEntries: number | null;
    validityType: SubscriptionValidityType;
    validityTimeUnit: SubscriptionTimeUnit | null;
    validityUnits: number | null;
    validityFrom: string | null;
    validityTo: string | null;
    usageType: SubscriptionUsageType;
    termsAndConditionsUrl: string | null;
    privacyPolicyUrl: string | null;
    fileBlobId: string | null;
    paymentProxies: string[];
    timeZone: string;
    supportsTicketsGeneration: boolean | null;
}

export interface SubscriptionWithStatistics {
    soldCount: number;
    pendingCount: number;
    linkedEventsCount: number;
    reservationsCount: number;
    availableCount: number;
    unitPrice: number;
    descriptor: SubscriptionDescriptorFull;
}

/**
 * The editable descriptor returned by GET /subscription/{id}. The *Model properties are the
 * wall-clock date/time of the corresponding instant.
 */
export interface SubscriptionDescriptorModification {
    id: string;
    publicIdentifier: string;
    title: LocalizedContent;
    description: LocalizedContent;
    // languages of the purchase context, serialized by the backend
    contentLanguages: ContentLanguage[];
    maxAvailable: number | null;
    onSaleFrom: string | null;
    onSaleTo: string | null;
    onSaleFromModel: DateTimeModification | null;
    onSaleToModel: DateTimeModification | null;
    price: number | null;
    vat: number | null;
    vatStatus: VatStatus | null;
    currency: string;
    isPublic: boolean | null;
    organizationId: number;
    maxEntries: number | null;
    validityType: SubscriptionValidityType | null;
    validityTimeUnit: SubscriptionTimeUnit | null;
    validityUnits: number | null;
    validityFrom: string | null;
    validityTo: string | null;
    validityFromModel: DateTimeModification | null;
    validityToModel: DateTimeModification | null;
    usageType: SubscriptionUsageType | null;
    termsAndConditionsUrl: string | null;
    privacyPolicyUrl: string | null;
    fileBlobId: string | null;
    paymentProxies: PaymentProxy[];
    timeZone: string | null;
    supportsTicketsGeneration: boolean | null;
}

/**
 * The payload sent to POST /subscription and /subscription/{id}.
 * Dates are ISO-8601 with the offset of the subscription time zone, prices in currency units.
 */
export interface SubscriptionPayload {
    id: string | null;
    title: LocalizedContent;
    description: LocalizedContent;
    maxAvailable: number | null;
    onSaleFrom: string | null;
    onSaleTo: string | null;
    price: number | null;
    vat: number | null;
    vatStatus: VatStatus | null;
    currency: string;
    isPublic: boolean | null;
    organizationId: number;
    maxEntries: number | null;
    validityType: SubscriptionValidityType | null;
    validityTimeUnit: SubscriptionTimeUnit | null;
    validityUnits: number | null;
    validityFrom: string | null;
    validityTo: string | null;
    usageType: SubscriptionUsageType | null;
    termsAndConditionsUrl: string | null;
    privacyPolicyUrl: string | null;
    fileBlobId: string | null;
    paymentProxies: PaymentProxy[];
    timeZone: string | null;
    supportsTicketsGeneration: boolean | null;
}

export interface EventSubscriptionLink {
    subscriptionDescriptorId: string;
    subscriptionDescriptorTitle: LocalizedContent;
    eventId: number;
    eventShortName: string;
    eventDisplayName: string;
    pricePerTicket: number;
    formattedPricePerTicket: number;
    eventCurrency: string;
    compatibleCategories: number[];
}
