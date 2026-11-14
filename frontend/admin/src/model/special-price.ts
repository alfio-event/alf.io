/**
 * One row of the CSV file with the invitations to send: the code can be blank, the backend assigns one.
 */
export interface SendCodeModification {
    code: string | null;
    assignee: string;
    email: string;
    language: string;
}

export type SpecialPriceStatus = 'WAITING' | 'FREE' | 'PENDING' | 'TAKEN' | 'CANCELLED';

/**
 * A code of a restricted ticket category. sentTimestamp is an ISO-8601 instant (ZonedDateTime).
 */
export interface SpecialPrice {
    id: number;
    code: string;
    priceInCents: number;
    ticketCategoryId: number;
    status: SpecialPriceStatus;
    sentTimestamp: string | null;
    recipientName: string;
    recipientEmail: string;
    accessCodeId: number | null;
}
