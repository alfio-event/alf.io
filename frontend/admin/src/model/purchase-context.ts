
export interface PurchaseContext {
    type: PurchaseContextType;
    publicIdentifier: string;
    contentLanguages: ContentLanguage[];
    firstContentLanguage: ContentLanguage;
}

export type PurchaseContextType = 'event' | 'subscription';

export interface ContentLanguage {
    locale:          string;
    value:           number;
    language:        string;
    displayLanguage: string;
}

export interface LocalizedContent {
    [key: string]: string;
}

/**
 * A payment provider that can be attached to a purchase context (event or subscription)
 */
export type PaymentProxy = 'STRIPE' | 'ON_SITE' | 'OFFLINE' | 'NONE' | 'ADMIN' | 'PAYPAL' | 'MOLLIE' | 'SAFERPAY' | 'CUSTOM_OFFLINE';

/**
 * Tax policy of a purchase context, see PriceContainer.VatStatus
 */
export type VatStatus = 'NONE' | 'INCLUDED' | 'NOT_INCLUDED' | 'INCLUDED_EXEMPT' | 'NOT_INCLUDED_EXEMPT';

/**
 * Wall-clock date/time pair ({date: yyyy-MM-dd, time: HH:mm:ss}), as used by the backend
 * for the editable date fields of events, subscriptions and reservations
 */
export interface DateTimeModification {
    date: string;
    time: string;
}
