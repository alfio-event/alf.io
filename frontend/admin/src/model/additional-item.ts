import {DateTimeModification} from "./purchase-context.ts";
import { t } from '../service/i18n';

export type AdditionalItemType = 'DONATION' | 'SUPPLEMENT';
export type AdditionalItemTaxType = 'INHERITED' | 'NONE' | 'CUSTOM_INCLUDED' | 'CUSTOM_EXCLUDED';
export type SupplementPolicy =
    'MANDATORY_ONE_FOR_TICKET' |
    'MANDATORY_PERCENTAGE_RESERVATION' |
    'MANDATORY_PERCENTAGE_FOR_TICKET' |
    'OPTIONAL_UNLIMITED_AMOUNT' |
    'OPTIONAL_MAX_AMOUNT_PER_TICKET' |
    'OPTIONAL_MAX_AMOUNT_PER_RESERVATION';

export const supplementPolicyDescriptions: {[key: string]: string} = {
    'MANDATORY_ONE_FOR_TICKET': t('admin.additional-item.policy.mandatory-one-for-ticket'),
    'MANDATORY_PERCENTAGE_RESERVATION': t('admin.additional-item.policy.mandatory-percentage-reservation'),
    'MANDATORY_PERCENTAGE_FOR_TICKET': t('admin.additional-item.policy.mandatory-percentage-for-ticket'),
    'OPTIONAL_UNLIMITED_AMOUNT': t('admin.additional-item.policy.optional-unlimited'),
    'OPTIONAL_MAX_AMOUNT_PER_TICKET': t('admin.additional-item.policy.optional-max-per-ticket'),
    'OPTIONAL_MAX_AMOUNT_PER_RESERVATION': t('admin.additional-item.policy.optional-max-per-reservation'),
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
    'INHERITED': t('admin.additional-item.tax.inherited'),
    'NONE': t('admin.additional-item.tax.none')
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
