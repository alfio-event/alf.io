export class AdditionalService {
    id: number;
    type: AdditionalServiceType;
    supplementPolicy: SupplementPolicy;
    //
    fixPrice: boolean;
    availableQuantity: number;
    maxQtyPerOrder: number;

    //
    free: boolean;
    formattedFinalPrice: string;
    hasDiscount: boolean;
    formattedDiscountedPrice: string;
    vatApplies: boolean;
    vatIncluded: boolean;
    vatPercentage: string;
    //

    //
    expired: boolean;
    saleInFuture: boolean;
    formattedInception: {[key: string]: string};
    formattedExpiration: {[key: string]: string};
    title: {[key: string]: string};
    description: {[key: string]: string};
}

export type AdditionalServiceType = 'DONATION' | 'SUPPLEMENT';
export type SupplementPolicy = 'MANDATORY_ONE_FOR_TICKET' | 'OPTIONAL_UNLIMITED_AMOUNT' | 'OPTIONAL_MAX_AMOUNT_PER_TICKET' |
                                'OPTIONAL_MAX_AMOUNT_PER_RESERVATION';
