import {DateTimeModification, LocalizedContent, PaymentProxy} from "./purchase-context.ts";

export type EventFormat = 'IN_PERSON' | 'ONLINE' | 'HYBRID';

export const EVENT_FORMATS: { value: EventFormat; label: string }[] = [
    { value: 'IN_PERSON', label: 'In person' },
    { value: 'ONLINE', label: 'Online' },
    { value: 'HYBRID', label: 'Hybrid (in person + online)' },
];

/**
 * The geolocation of an in-person event, as sent to the backend (LocationDescriptor)
 */
export interface GeolocationDescriptor {
    timeZone: string;
    latitude: string | null;
    longitude: string | null;
    mapUrl?: string | null;
}

/**
 * A ticket category of the event being created (TicketCategoryModification)
 */
export interface TicketCategoryModificationPayload {
    name: string;
    ticketAccessType: string | null;
    maxTickets: number;
    inception: DateTimeModification;
    expiration: DateTimeModification;
    description: LocalizedContent | null;
    price: number | null;
    tokenGenerationRequested: boolean;
    dateString?: string;
    bounded: boolean;
    code: string | null;
    ordinal: number;
    validCheckInFrom: DateTimeModification | null;
    validCheckInTo: DateTimeModification | null;
    ticketValidityStart: DateTimeModification | null;
    ticketValidityEnd: DateTimeModification | null;
    ticketCheckInStrategy: string | null;
    badgeColor?: string | null;
    metadata: { copiedFrom?: number } | null;
}

export interface EventMetadataPayload {
    copiedFrom?: string;
}

/**
 * The payload of POST /admin/api/events/new and /admin/api/events/check (EventModification)
 */
export interface EventModificationPayload {
    id?: number | null;
    format: EventFormat;
    websiteUrl: string;
    externalUrl?: string | null;
    termsAndConditionsUrl: string;
    privacyPolicyUrl: string | null;
    imageUrl?: string | null;
    fileBlobId: string | null;
    shortName: string;
    displayName: string;
    organizationId: number;
    location: string | null;
    latitude: string | null;
    longitude: string | null;
    zoneId: string;
    description: LocalizedContent;
    begin: DateTimeModification;
    end: DateTimeModification;
    regularPrice: number | null;
    currency: string | null;
    availableSeats: number;
    vatPercentage: number | null;
    vatIncluded: boolean;
    allowedPaymentProxies: PaymentProxy[];
    ticketCategories: TicketCategoryModificationPayload[];
    freeOfCharge: boolean;
    geolocation: GeolocationDescriptor | null;
    locales: number;
    metadata: EventMetadataPayload | null;
    linkedSubscriptions: string[];
    ticketFields?: unknown[];
    additionalServices: unknown[];
}
