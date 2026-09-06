export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT' | 'FIXED_AMOUNT_RESERVATION' | 'NONE';
export type PromoCodeType = 'DISCOUNT' | 'ACCESS' | 'DYNAMIC';

export interface DateTimeModification {
    date: string;
    time: string;
}

export interface PromoCodeDiscount {
    id: number;
    promoCode: string;
    eventId: number | null;
    organizationId: number | null;
    utcStart: string;
    utcEnd: string;
    formattedStart: string;
    formattedEnd: string;
    discountAmount: number;
    discountType: DiscountType;
    categories: number[];
    maxUsage: number | null;
    description: string;
    emailReference: string;
    codeType: PromoCodeType;
    hiddenCategoryId: number | null;
    currencyCode: string;
    formattedDiscountAmount: string | null;
    currentlyValid: boolean;
    expired: boolean;
    fixedAmount: boolean;
}

export interface UsageDetailEvent {
    event: {
        shortName: string;
        displayName: string;
    };
    reservations: UsageReservation[];
}

export interface UsageReservation {
    id: string;
    firstName: string;
    lastName: string;
    email: string;
    paymentType: string;
    currency: string;
    finalPriceCts: number | null;
    confirmationTimestamp: string | null;
    tickets: UsageTicket[];
}

export interface UsageTicket {
    id: string;
    firstName: string;
    lastName: string;
    type: string;
}

export interface PromoCodeFormData {
    promoCode: string;
    start: DateTimeModification;
    end: DateTimeModification;
    discountAmount: number | null;
    discountType: DiscountType;
    categories: number[];
    maxUsage: number | null;
    description: string;
    emailReference: string;
    codeType: PromoCodeType;
    hiddenCategoryId: number | null;
    currencyCode: string;
    eventId: number | null;
    organizationId: number | null;
    utcOffset?: number;
}

export interface PromoCodeSettingsData {
    start: DateTimeModification;
    end: DateTimeModification;
    maxUsage: number | null;
    description: string;
    emailReference: string;
    categories: number[];
    hiddenCategoryId: number | null;
    eventId: number | null;
    organizationId: number;
    utcOffset?: number;
}
