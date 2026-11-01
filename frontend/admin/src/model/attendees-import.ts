import {AdminReservationRequest} from './admin-reservation.ts';

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

export type AttendeesImportRequest = AdminReservationRequest<ImportedAttendee>;

export interface AttendeesImportStats {
    requestId: string;
    countSuccess: number;
    countPending: number;
    countError: number;
}
