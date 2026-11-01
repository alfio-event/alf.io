import {DateTimeModification} from './event.ts';

export type TicketAccessType = 'IN_PERSON' | 'ONLINE';

// assign the attendees to an existing category, or to a new restricted one
export type CategoryType = 'existing' | 'new';

/**
 * Either an existing category (existingCategoryId) or a new restricted one (name, price)
 */
export interface ReservationCategory {
    existingCategoryId?: number;
    name?: string;
    price?: number;
    // hybrid events only
    ticketAccessType?: TicketAccessType;
}

export interface ReservationAttendee {
    firstName: string;
    lastName: string;
    emailAddress: string;
}

/**
 * Reservation created by an administrator, see AdminReservationModification
 */
export interface AdminReservationRequest<A extends ReservationAttendee = ReservationAttendee> {
    expiration: DateTimeModification;
    customerData: ReservationAttendee;
    language?: string;
    ticketsInfo: {
        category: ReservationCategory;
        attendees: A[];
        addSeatsIfNotAvailable: boolean;
    }[];
    notification?: {
        customer: boolean;
        attendees: boolean;
    };
}

/**
 * Detail of the "alfio-reservation-created" event
 */
export interface ReservationCreatedDetail {
    reservationId: string;
    eventName: string;
}
