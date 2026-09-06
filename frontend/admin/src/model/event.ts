import {Organization} from "./organization.ts";
import {LocalizedContent, PurchaseContext} from "./purchase-context.ts";

export interface EventWithOrganization {
    event: AlfioEvent;
    organization: Organization;
}

export interface DateTimeModification {
    date: string;
    time: string;
}

export interface AlfioEvent extends PurchaseContext {
    id:                                number;
    shortName:                         string;
    displayName:                       string;
    ticketCategories:                  TicketCategory[];
    description:                       LocalizedContent;
    title:                             LocalizedContent;
    begin:                             string;
    format:                            string;
    currency:                          string;
    formattedBegin:                    string;
    visibleForCurrentUser:             boolean;
    displayStatistics:                 boolean;
    status:                            string;
    expired:                           boolean;
    locales:                           number;
    freeOfCharge:                      boolean;
    sameDay:                           boolean;
    end:                               string;
    online:                            boolean;
    organizationId:                    number;
    regularPrice:                      number;
    termsAndConditionsUrl:             string;
    privacyPolicyUrl:                  string;
    vatIncluded:                       boolean;
    vatPercentage:                     number;
    beginTimeZoneOffset:               number;
    endTimeZoneOffset:                 number;
    isOnline:                          boolean;
    supportsAdditionalItemsQuantity:   boolean;
    supportsAdditionalServicesOrdinal: boolean;
    finalPrice:                        number;
    netPrice:                          number;
    taxablePrice:                      number;
    timeZone:                          string;
}

export interface TicketCategory {
    description:                  LocalizedContent;
    name:                         string;
    id:                           number;
    expired:                      boolean;
    accessRestricted:             boolean;
}

export type PaymentProxy = 'STRIPE' | 'ON_SITE' | 'OFFLINE' | 'NONE' | 'ADMIN' | 'PAYPAL' | 'MOLLIE' | 'SAFERPAY' | 'CUSTOM_OFFLINE';
export type EventStatus = 'DRAFT' | 'PUBLIC' | 'DISABLED';

export interface EventStatistic {
    id: number;
    shortName: string;
    displayName: string;
    status: EventStatus;
    expired: boolean;
    warningNeeded: boolean;
    availableSeats: number;
    soldTickets: number;
    checkedInTickets: number;
    notSoldTickets: number;
    notAllocatedTickets: number;
    pendingTickets: number;
    dynamicAllocation: number;
    releasedTickets: number;
    formattedBegin: string;
    formattedEnd: string;
    fileBlobId: string | null;
    visibleForCurrentUser: boolean;
    displayStatistics: boolean;
    organizationId: number;
    allowedPaymentProxies: PaymentProxy[];
    timeZone: string;
}

