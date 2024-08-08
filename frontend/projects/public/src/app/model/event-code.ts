export type EventCode = {
    code: string;
    type: EventCodeType;
    discountType: DiscountType;
    discountAmount: string;
}

export type EventCodeType = 'SPECIAL_PRICE' | 'DISCOUNT' | 'ACCESS';
export type DiscountType = 'FIXED_AMOUNT' | 'FIXED_AMOUNT_RESERVATION' | 'PERCENTAGE' | 'NONE';

export type DynamicDiscount = {
    discount: string;
    discountType: DiscountType;
    formattedMessage: {[key: string]: string};
}
