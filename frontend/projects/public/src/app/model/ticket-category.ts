export type TicketAccessType = 'INHERIT' | 'IN_PERSON' | 'ONLINE';

export interface TicketCategory {
    id: number;
    name: string;
    ticketAccessType: TicketAccessType;
    bounded: boolean;
    maximumSaleableTickets: number;
    description: {[key: string]: string};
    free: boolean;
    formattedFinalPrice: string;
    hasDiscount: boolean;
    formattedDiscountedPrice: string;


    //
    expired: boolean;
    saleInFuture: boolean;
    formattedInception: {[key: string]: string};
    formattedExpiration: {[key: string]: string};
    //

    saleableAndLimitNotReached: boolean;
    accessRestricted: boolean;
    soldOutOrLimitReached: boolean;

    availableTickets: number | null;

    displayTaxInformation: boolean;
}

