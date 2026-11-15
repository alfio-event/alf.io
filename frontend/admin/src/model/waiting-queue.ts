export type WaitingQueueStatus = 'WAITING' | 'PENDING' | 'ACQUIRED' | 'EXPIRED' | 'CANCELLED';

export type WaitingQueueSubscriptionType = 'PRE_SALES' | 'SOLD_OUT';

/**
 * A person on the waiting list of an event (WaitingQueueSubscription)
 */
export interface WaitingQueueSubscription {
    id: number;
    creation: string;
    status: WaitingQueueStatus;
    fullName: string;
    emailAddress: string;
    reservationId: string | null;
    userLanguage: string;
    selectedCategoryId: number | null;
    subscriptionType: WaitingQueueSubscriptionType | null;
}
