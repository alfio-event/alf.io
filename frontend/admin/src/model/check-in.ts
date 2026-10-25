export type TicketStatus = 'FREE' | 'PENDING' | 'TO_BE_PAID' | 'ACQUIRED' | 'CANCELLED' | 'CHECKED_IN' | 'EXPIRED' | 'INVALIDATED' | 'RELEASED' | 'PRE_RESERVED';

export type CheckInStatus =
    'EVENT_NOT_FOUND'
    | 'TICKET_NOT_FOUND'
    | 'EMPTY_TICKET_CODE'
    | 'INVALID_TICKET_CODE'
    | 'INVALID_TICKET_STATE'
    | 'ALREADY_CHECK_IN'
    | 'MUST_PAY'
    | 'OK_READY_TO_BE_CHECKED_IN'
    | 'SUCCESS'
    | 'INVALID_TICKET_CATEGORY_CHECK_IN_DATE'
    | 'BADGE_SCAN_ALREADY_DONE'
    | 'OK_READY_FOR_BADGE_SCAN'
    | 'BADGE_SCAN_SUCCESS'
    | 'ERROR';

/**
 * Attendee, as returned by the admin check-in API (FullTicketInfo)
 */
export interface CheckInTicket {
    id: number;
    uuid: string;
    publicUuid: string;
    status: TicketStatus;
    eventId: number;
    ticketsReservationId: string;
    fullName: string;
    firstName?: string | null;
    lastName?: string | null;
    email: string;
    extReference?: string | null;
    ticketCategory: {
        name: string;
    };
    ticketReservation: {
        fullName?: string | null;
        firstName?: string | null;
        lastName?: string | null;
        email?: string | null;
        paymentMethod?: string | null;
    };
}

/**
 * Ticket and evaluation of its status, returned when a QR code is scanned
 */
export interface TicketAndCheckInResult {
    ticket: {
        fullName?: string | null;
        email?: string | null;
        categoryName?: string | null;
        userLanguage?: string | null;
    } | null;
    result: {
        status: CheckInStatus;
        message?: string | null;
        // only for MUST_PAY
        dueAmount?: number | null;
        currency?: string | null;
    };
}
