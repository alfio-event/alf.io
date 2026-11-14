import {Organization} from "./organization.ts";
import {LocalizedContent, PaymentProxy, PurchaseContext} from "./purchase-context.ts";
import {SpecialPrice} from "./special-price.ts";

export interface EventWithOrganization {
    event: AlfioEvent;
    organization: Organization;
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
    // wall-clock time in the event time zone (yyyy-MM-dd HH:mm)
    formattedEnd:                      string;
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
    // present on the full event (GET /admin/api/events/{name}), used by the "copy from previous event" flow
    location?:                         string | null;
    latitude?:                         string | null;
    longitude?:                        string | null;
    availableSeats?:                   number;
    allowedPaymentProxies?:            PaymentProxy[];
    websiteUrl?:                       string;
    fileBlobId?:                       string | null;
}

export interface TicketCategory {
    description:                  LocalizedContent;
    name:                         string;
    id:                           number;
    expired:                      boolean;
    accessRestricted:             boolean;
    // present on the full event (GET /admin/api/events/{name}): all the codes of a restricted category
    tokenStatus?:                 SpecialPrice[];
    // present on the full event (GET /admin/api/events/{name}), used by the "copy from previous event" flow
    bounded?:                     boolean;
    maxTickets?:                  number;
    price?:                       number;
    ordinal?:                     number;
    code?:                        string | null;
    ticketAccessType?:            string | null;
    // wall-clock "yyyy-MM-dd HH:mm" in the event time zone
    formattedInception?:          string;
    formattedExpiration?:         string;
    formattedValidCheckInFrom?:   string | null;
    formattedValidCheckInTo?:     string | null;
    formattedTicketValidityStart?: string | null;
    formattedTicketValidityEnd?:  string | null;
}

export type EventStatus = 'DRAFT' | 'PUBLIC' | 'DISABLED';

/**
 * Status of a ticket
 */
export type TicketStatus = 'FREE' | 'PENDING' | 'TO_BE_PAID' | 'ACQUIRED' | 'CANCELLED' | 'CHECKED_IN' | 'EXPIRED' | 'INVALIDATED' | 'RELEASED' | 'PRE_RESERVED';

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

