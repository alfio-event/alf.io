import Papa from 'papaparse';
import {AlfioEvent, DateTimeModification} from '../model/event.ts';
import {
    AdminReservationRequest,
    CategoryType,
    ReservationAttendee,
    ReservationCategory,
    TicketAccessType,
} from '../model/admin-reservation.ts';
import {ChoiceCardOption} from '../components/choice-cards.ts';
import {postJson, result} from './helpers.ts';
import {formatAmount} from './reservation-format.ts';

export interface ParsedReservationAttendees {
    attendees: ReservationAttendee[];
    // rows without first name, last name and e-mail
    skippedRows: number;
}

/**
 * Where the attendees of a new reservation get their seats, see choiceCards()
 */
export const CATEGORY_TYPE_CHOICES: ChoiceCardOption<CategoryType>[] = [
    {
        value: 'existing',
        icon: 'tags',
        title: 'Existing category',
        description: 'The attendees get seats of the category, which is extended with unallocated seats if needed.',
    },
    {
        value: 'new',
        icon: 'shield-lock',
        title: 'New restricted category',
        description: 'A category for these attendees only, with its own name and price.',
    },
];

/**
 * Creation of reservations by an administrator
 */
export class AdminReservationService {

    /**
     * @return the ID of the new reservation
     */
    static create(eventName: string, request: AdminReservationRequest): Promise<string> {
        return result(postJson(`/admin/api/reservation/event/${encodeURIComponent(eventName)}/new`, request));
    }

    /**
     * Parses a CSV file without header: first name, last name, e-mail.
     * Separator: comma, quote character: double quote, escape character: backslash
     */
    static parseAttendees(content: string): ParsedReservationAttendees {
        const parsed = Papa.parse<string[]>(content.trim(), {
            header: false,
            skipEmptyLines: true,
            delimiter: ',',
            quoteChar: '"',
            escapeChar: '\\',
        });
        const attendees: ReservationAttendee[] = [];
        let skippedRows = 0;
        for (const row of parsed.data) {
            if (row.length < 3) {
                skippedRows++;
                continue;
            }
            attendees.push({
                firstName: row[0].trim(),
                lastName: row[1].trim(),
                emailAddress: row[2].trim(),
            });
        }
        return { attendees, skippedRows };
    }
}

/**
 * The category of the tickets, as chosen in the form
 */
export interface CategoryDraft {
    categoryType: CategoryType;
    existingCategoryId: string;
    name: string;
    price: string;
    // hybrid events only
    ticketAccessType?: string;
}

export function buildCategory(event: AlfioEvent, draft: CategoryDraft): ReservationCategory {
    if (draft.categoryType === 'existing') {
        return { existingCategoryId: Number(draft.existingCategoryId) };
    }
    const category: ReservationCategory = { name: draft.name };
    if (!event.freeOfCharge) {
        category.price = Number.parseFloat(draft.price);
    }
    if (event.format === 'HYBRID' && draft.ticketAccessType) {
        category.ticketAccessType = draft.ticketAccessType as TicketAccessType;
    }
    return category;
}

/**
 * Price paid by the attendee, VAT included
 */
export function finalPrice(event: AlfioEvent, price: number): number {
    if (event.vatIncluded) {
        return price;
    }
    return price * (1 + event.vatPercentage / 100);
}

/**
 * Final price of a new category, empty until a valid price is entered
 */
export function formatFinalPrice(event: AlfioEvent, price: string): string {
    const amount = Number.parseFloat(price);
    if (Number.isNaN(amount)) {
        return '';
    }
    return formatAmount(finalPrice(event, amount), event.currency);
}

/**
 * Explains how the final price is computed
 */
export function vatDescription(event: AlfioEvent): string {
    if (event.vatIncluded) {
        return 'VAT included';
    }
    return `Including ${event.vatPercentage}% VAT`;
}

/**
 * The reservations created by an administrator expire at the end of the next day, or right before the end of the event.
 * A new category is on sale until the reservation expires, and the backend refuses a category that is on sale
 * after the end of the event: e.g. when attendees are added on site.
 */
export function reservationExpiration(event: AlfioEvent): DateTimeModification {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const endOfTomorrow = `${formatDate(tomorrow)} 23:59`;
    // both are wall-clock times of the event ("yyyy-MM-dd HH:mm"), which sort as strings
    if (endOfTomorrow < event.formattedEnd) {
        return toDateTimeModification(endOfTomorrow);
    }
    // the wall-clock time is handled as UTC, so that the subtraction is not affected by the browser time zone
    const eventEnd = new Date(`${event.formattedEnd.replace(' ', 'T')}:00Z`);
    const oneMinuteBefore = new Date(eventEnd.getTime() - 60_000);
    return toDateTimeModification(`${formatDate(oneMinuteBefore, true)} ${pad(oneMinuteBefore.getUTCHours())}:${pad(oneMinuteBefore.getUTCMinutes())}`);
}

function formatDate(date: Date, utc = false): string {
    if (utc) {
        return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
    }
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function pad(n: number): string {
    return String(n).padStart(2, '0');
}

function toDateTimeModification(wallClock: string): DateTimeModification {
    const [date, time] = wallClock.split(' ');
    return { date, time };
}
