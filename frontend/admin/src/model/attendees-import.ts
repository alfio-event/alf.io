import {DateTimeModification} from './event.ts';

export interface ImportedAttendee {
    firstName: string;
    lastName: string;
    emailAddress: string;
    language: string;
    // attendee ID in the organizer's system
    reference?: string;
    additionalInfo: Record<string, string[]>;
    forbidReassignment?: boolean;
}

export interface ParsedAttendees {
    attendees: ImportedAttendee[];
    // the additional data columns of the file
    additionalFields: string[];
    // rows without enough columns
    skippedRows: number;
}

export interface ImportCategory {
    existingCategoryId?: number;
    name?: string;
    price?: number;
}

/**
 * see AdminReservationModification
 */
export interface AttendeesImportRequest {
    expiration: DateTimeModification;
    customerData: {
        firstName: string;
        lastName: string;
        emailAddress: string;
    };
    language?: string;
    ticketsInfo: {
        category: ImportCategory;
        attendees: ImportedAttendee[];
        addSeatsIfNotAvailable: boolean;
    }[];
    notification: {
        customer: boolean;
        attendees: boolean;
    };
}

export interface AttendeesImportStats {
    requestId: string;
    countSuccess: number;
    countPending: number;
    countError: number;
}
