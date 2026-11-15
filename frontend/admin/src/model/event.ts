import {Organization} from "./organization.ts";
import {LocalizedContent, PaymentProxy, PurchaseContext} from "./purchase-context.ts";
import {SpecialPrice} from "./special-price.ts";
import {SubscriptionDescriptorFull} from "./subscription-descriptor.ts";

/**
 * The online-event metadata of a purchase context (AlfioMetadata), as returned by
 * GET /admin/api/events/{name}
 */
export interface AlfioMetadata {
    onlineConfiguration?: {
        callLinks?: { link: string; validFrom: string; validTo: string }[];
    } | null;
    requirementsDescriptions?: Record<string, string>;
    conditionsToBeAccepted?: unknown[];
    applicationData?: Record<string, string>;
}

/**
 * An action that an extension offers for the event (ExtensionCapabilitySummary): a capability,
 * with one entry per variant when the extension describes them
 */
export interface ExtensionCapabilitySummary {
    capability: string;
    details?: { label: string; description?: string | null; selector?: string | null }[] | null;
}

/**
 * A group linked to an event or to one of its categories
 * (GET /admin/api/group/for/event/{eventName}/all)
 */
export interface GroupLink {
    id: number;
    groupId: number;
    ticketCategoryId: number | null;
    groupName?: string;
}

/**
 * Ticket sold/reserved counts per date (GET /admin/api/events/{eventName}/ticket-sold-statistics)
 */
export interface TicketsStatistics {
    granularity: string;
    sold: { date: string; count: number }[];
    reserved: { date: string; count: number }[];
}

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
    // present on the full event (GET /admin/api/events/{name}), shown on the event detail page
    imageUrl?:                         string | null;
    externalUrl?:                      string | null;
    shortDescription?:                 string;
    fileBlobIdIsPresent?:              boolean;
    grossIncome?:                      number;
    soldTickets?:                      number;
    checkedInTickets?:                 number;
    pendingTickets?:                   number;
    releasedTickets?:                  number;
    notSoldTickets?:                   number;
    notAllocatedTickets?:              number;
    dynamicAllocation?:                number;
    warningNeeded?:                    boolean;
    addCategoryEnabled?:               boolean;
    containingUnboundedCategories?:    boolean;
    metadata?:                         AlfioMetadata;
    // the actions that the active extensions offer for an online event (e.g. generate the meeting link)
    supportedCapabilities?:            ExtensionCapabilitySummary[];
    linkedSubscriptions?:              string[];
    // set on the event by the detail page, once the group links have been loaded
    attendeesList?:                    GroupLink | null;
    subscriptionDescriptors?:          SubscriptionDescriptorFull[];
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
    // present on the full event (GET /admin/api/events/{name}), shown on the event detail page
    soldTickets?:                 number;
    checkedInTickets?:            number;
    pendingTickets?:              number;
    releasedTickets?:             number;
    notSoldTickets?:              number;
    notAllocatedTickets?:         number;
    dynamicAllocation?:           number;
    containingOrphans?:           boolean;
    containingStuckTickets?:      boolean;
    ticketCheckInStrategy?:       string | null;
    metadata?:                    AlfioMetadata;
    // set on the category by the detail page, once the group links have been loaded
    attendeesList?:               GroupLink | null;
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

