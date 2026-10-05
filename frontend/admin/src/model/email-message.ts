import {PurchaseContextType} from "./purchase-context.ts";

export type EmailMessageStatus = 'WAITING' | 'RETRY' | 'IN_PROCESS' | 'SENT' | 'ERROR';

export interface EmailMessage {
    id: number;
    purchaseContextType: PurchaseContextType;
    status: EmailMessageStatus;
    recipient: string;
    cc: string[];
    subject: string;
    // abbreviated to ~128 characters in lists
    message: string;
    checksum: string;
    requestTimestamp: string;
    sentTimestamp: string | null;
    attempts: number;
}
