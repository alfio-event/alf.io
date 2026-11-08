import Papa from 'papaparse';
import {AlfioEvent} from '../model/event.ts';
import {DateTimeModification} from '../model/purchase-context.ts';
import {
    AdminReservationRequest,
    CategoryType,
    ReservationAttendee,
    ReservationCategory,
    TicketAccessType,
} from '../model/admin-reservation.ts';
import {ChoiceCardOption} from '../components/choice-cards.ts';
import type {AttendeeFieldValues} from '../components/attendee-fields.ts';
import {AdditionalField, allowedValues} from '../model/additional-field.ts';
import {postJson, result} from './helpers.ts';
import {formatAmount} from './reservation-format.ts';

export interface ParsedReservationAttendees {
    attendees: ReservationAttendee[];
    // the names of the additional fields in the file
    additionalFields: string[];
    // rows without first name, last name and e-mail
    skippedRows: number;
}

// the first columns of the CSV file of the attendees, see parseAttendees()
export const RESERVATION_ATTENDEE_COLUMNS = ['firstName', 'lastName', 'email'];
// separates the options of a checkbox field in a CSV cell
export const CHECKBOX_OPTIONS_SEPARATOR = ';';
const MAX_REPORTED_ERRORS = 5;

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
     * An empty file with the columns of the attendees: first name, last name, e-mail and the given additional fields
     */
    static attendeesTemplate(fields: readonly AdditionalField[]): string {
        return Papa.unparse([[...RESERVATION_ATTENDEE_COLUMNS, ...fields.map(field => field.name)]], { quotes: true }) + '\r\n';
    }

    /**
     * Parses a CSV file with header, as the template: the first three columns are first name, last name and e-mail,
     * whatever their name; the other ones are additional fields, by name.
     * Separator: comma, quote character: double quote, escaped by doubling it.
     *
     * @param fields the additional fields that can be imported
     * @throws Error if the file has no header, a column that is not an additional field, or invalid values
     */
    static parseAttendees(content: string, fields: readonly AdditionalField[]): ParsedReservationAttendees {
        const parsed = Papa.parse<string[]>(content.replace(/^\uFEFF/, '').trim(), {
            header: false,
            skipEmptyLines: true,
            delimiter: ',',
            quoteChar: '"',
        });
        const [header = [], ...rows] = parsed.data;
        const fixedColumns = RESERVATION_ATTENDEE_COLUMNS.length;
        if (header.length < fixedColumns || header[2].includes('@')) {
            throw new Error(`The first row must contain the names of the columns: ${RESERVATION_ATTENDEE_COLUMNS.join(', ')} and the additional fields. Please use the template.`);
        }
        const fieldsByName = new Map(fields.map(field => [field.name.toLowerCase(), field]));
        const columns = header.slice(fixedColumns).map(name => fieldsByName.get(name.trim().toLowerCase()));
        const unknownColumns = header.slice(fixedColumns).filter((_, i) => columns[i] == null);
        if (unknownColumns.length > 0) {
            throw new Error(`These columns are not attendees' data to collect of this event: ${unknownColumns.join(', ')}. Please use the template.`);
        }
        const attendees: ReservationAttendee[] = [];
        const invalidValues: string[] = [];
        let skippedRows = 0;
        rows.forEach((row, rowIndex) => {
            if (row.length < fixedColumns || row.slice(0, fixedColumns).some(value => value.trim() === '')) {
                skippedRows++;
                return;
            }
            const additionalInfo: AttendeeFieldValues = {};
            columns.forEach((field, i) => {
                const value = (row[fixedColumns + i] ?? '').trim();
                if (field == null || value === '') {
                    return;
                }
                const values = fieldValues(field, value);
                if (values == null) {
                    // the header is the first row of the file
                    invalidValues.push(`row ${rowIndex + 2}, ${field.name}: "${value}"`);
                    return;
                }
                additionalInfo[field.name] = values;
            });
            const attendee: ReservationAttendee = {
                firstName: row[0].trim(),
                lastName: row[1].trim(),
                emailAddress: row[2].trim(),
            };
            if (Object.keys(additionalInfo).length > 0) {
                attendee.additionalInfo = additionalInfo;
            }
            attendees.push(attendee);
        });
        if (invalidValues.length > 0) {
            const shown = invalidValues.slice(0, MAX_REPORTED_ERRORS).join('; ');
            let more = '';
            if (invalidValues.length > MAX_REPORTED_ERRORS) {
                more = ` and ${invalidValues.length - MAX_REPORTED_ERRORS} more`;
            }
            throw new Error(`These values are not options of their field: ${shown}${more}. See the file specifications.`);
        }
        return {
            attendees,
            additionalFields: columns.filter(field => field != null).map(field => field.name),
            skippedRows,
        };
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

/**
 * The values of an additional field, as stored by attendeeFields(), from the content of a CSV cell
 *
 * @return null if the value is not one of the options of the field
 */
function fieldValues(field: AdditionalField, value: string): (string | null)[] | null {
    const options = allowedValues(field);
    const findOption = (candidate: string) => options.find(option => option.toLowerCase() === candidate.toLowerCase());
    switch (field.type) {
        case 'select':
        case 'radio': {
            const option = findOption(value);
            if (option == null) {
                return null;
            }
            return [option];
        }
        case 'checkbox': {
            // a value can contain the separator, if it is an option
            let selected = [value];
            if (findOption(value) == null) {
                selected = value.split(CHECKBOX_OPTIONS_SEPARATOR).map(v => v.trim()).filter(v => v !== '');
            }
            const chosen = selected.map(findOption);
            if (chosen.some(option => option == null)) {
                return null;
            }
            // one value for each option, by position
            return (field.restrictedValues ?? []).map(option => {
                if (chosen.includes(option)) {
                    return option;
                }
                return null;
            });
        }
        case 'country':
            return [value.toUpperCase()];
    }
    return [value];
}
