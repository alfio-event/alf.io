export interface EventInfo {
    id: number;
    displayName: string;
    status: string; // TODO: enum
    shortName: string;
    organizationId: number;
    allowedPaymentProxies: string[]; // TODO: enum
    expired: boolean;
    notAllocatedTickets: number;
    fileBlobId: string;
    availableSeats: number;
    checkedInTickets: number;
    notSoldTickets: number;
    soldTickets: number;
    pendingTickets: number;
    dynamicAllocation: number;
    releasedTickets: number;
    formattedEnd: string;
    visibleForCurrentUser: boolean;
    warningNeeded: boolean;
    formattedBegin: string;
    displayStatistics: boolean;
}
