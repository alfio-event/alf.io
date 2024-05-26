import {DateTimeModification} from "./event.ts";

export type AdditionalItemType = 'DONATION' | 'SUPPLEMENT';
export type AdditionalItemTaxType = 'INHERITED' | 'NONE' | 'CUSTOM_INCLUDED' | 'CUSTOM_EXCLUDED';
export type SupplementPolicy = 'MANDATORY_ONE_FOR_TICKET' | 'OPTIONAL_UNLIMITED_AMOUNT' | 'OPTIONAL_MAX_AMOUNT_PER_TICKET' |
    'OPTIONAL_MAX_AMOUNT_PER_RESERVATION';
// export type AdditionalItemStatus = 'FREE' | 'PENDING' | 'TO_BE_PAID' | 'ACQUIRED' | 'CANCELLED' | 'CHECKED_IN' | 'EXPIRED' | 'INVALIDATED' | 'RELEASED';

export interface AdditionalItemLocalizedContent {
    id: number,
    locale: string,
    value: string,
    type: 'DESCRIPTION' | 'TITLE'
}

export interface AdditionalItem {
    id: number;
    price: number,
    fixPrice: boolean,
    ordinal: number,
    availableQuantity?: number,
    maxQtyPerOrder?: number,
    inception: DateTimeModification,
    expiration: DateTimeModification,
    vat: number | null,
    vatType: AdditionalItemTaxType,
    title: AdditionalItemLocalizedContent[],
    description: AdditionalItemLocalizedContent[],
    type: AdditionalItemType,
    supplementPolicy: SupplementPolicy,
    currencyCode: string,
    availableItems: number | null,
    minPrice: number | null,
    maxPrice: number | null,
    finalPrice: number,
    currency: string
}
