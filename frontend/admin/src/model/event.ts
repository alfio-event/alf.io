import {Organization} from "./organization.ts";

export interface EventWithOrganization {
    event: AlfioEvent;
    organization: Organization;
}

export interface DateTimeModification {
    date: string;
    time: string;
}

export interface AlfioEvent {
    id:                                number;
    shortName:                         string;
    displayName:                       string;
    publicIdentifier:                  string;
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
    contentLanguages:                  ContentLanguage[];
    termsAndConditionsUrl:             string;
    privacyPolicyUrl:                  string;
    vatIncluded:                       boolean;
    vatPercentage:                     number;
    beginTimeZoneOffset:               number;
    endTimeZoneOffset:                 number;
    isOnline:                          boolean;
    firstContentLanguage:              ContentLanguage;
    supportsAdditionalItemsQuantity:   boolean;
    supportsAdditionalServicesOrdinal: boolean;
    finalPrice:                        number;
    netPrice:                          number;
    taxablePrice:                      number;
}

export interface ContentLanguage {
    locale:          string;
    value:           number;
    language:        string;
    displayLanguage: string;
}

export interface LocalizedContent {
    [key: string]: string;
}

export interface TicketCategory {
    description:                  LocalizedContent;
    name:                         string;
    id:                           number;
}

