import {DateTimeModification} from "./event.ts";

export type AdditionalItemType = 'DONATION' | 'SUPPLEMENT';
export type AdditionalItemTaxType = 'INHERITED' | 'NONE' | 'CUSTOM_INCLUDED' | 'CUSTOM_EXCLUDED';
export type SupplementPolicy =
    'MANDATORY_ONE_FOR_TICKET' |
    'MANDATORY_PERCENTAGE_RESERVATION' |
    'MANDATORY_PERCENTAGE_FOR_TICKET' |
    'OPTIONAL_UNLIMITED_AMOUNT' |
    'OPTIONAL_MAX_AMOUNT_PER_TICKET' |
    'OPTIONAL_MAX_AMOUNT_PER_RESERVATION';

// temporary until we get the i18n translations
export const supplementPolicyDescriptions: {[key: string]: string} = {
    'MANDATORY_ONE_FOR_TICKET': 'Mandatory fixed fee, 1 per ticket',
    'MANDATORY_PERCENTAGE_RESERVATION': 'Mandatory percentage fee, entire reservation (including user-selected Additional Items, if any)',
    'MANDATORY_PERCENTAGE_FOR_TICKET': 'Mandatory percentage fee for tickets',
    'OPTIONAL_UNLIMITED_AMOUNT': 'User-selected item',
    'OPTIONAL_MAX_AMOUNT_PER_TICKET': 'User-selected item, limited quantity per ticket',
    'OPTIONAL_MAX_AMOUNT_PER_RESERVATION': 'User-selected item, limited quantity per reservation',
}

export function isMandatory(supplementPolicy: SupplementPolicy): boolean {
    return supplementPolicy === "MANDATORY_ONE_FOR_TICKET"
        || supplementPolicy === 'MANDATORY_PERCENTAGE_FOR_TICKET'
        || supplementPolicy === 'MANDATORY_PERCENTAGE_RESERVATION';
}

export function isMandatoryPercentage(supplementPolicy: SupplementPolicy): boolean {
    return supplementPolicy === 'MANDATORY_PERCENTAGE_FOR_TICKET'
        || supplementPolicy === 'MANDATORY_PERCENTAGE_RESERVATION';
}

export const taxTypeDescriptions: {[key: string]: string} = {
    'INHERITED': 'Use Event settings',
    'NONE': 'Do not apply taxes on this items'
}

// export type AdditionalItemStatus = 'FREE' | 'PENDING' | 'TO_BE_PAID' | 'ACQUIRED' | 'CANCELLED' | 'CHECKED_IN' | 'EXPIRED' | 'INVALIDATED' | 'RELEASED';

export interface AdditionalItemLocalizedContent {
    id: number | null,
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
