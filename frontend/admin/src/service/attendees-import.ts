import Papa from 'papaparse';
import {AttendeesImportRequest, AttendeesImportStats, ImportedAttendee, ParsedAttendees} from '../model/attendees-import.ts';
import {postJson, result} from './helpers.ts';

export const REQUIRED_COLUMNS = ['firstName', 'lastName', 'email', 'lang'];
// the attendee ID in the organizer's system
export const REFERENCE_COLUMN = 'reference';
const FIXED_COLUMNS = REQUIRED_COLUMNS.length;

export class AttendeesImportService {

    /**
     * Schedules the creation of the reservations
     * @return the identifier of the import request
     */
    static submit(eventName: string, request: AttendeesImportRequest, oneReservationPerAttendee: boolean): Promise<string> {
        const query = oneReservationPerAttendee ? '?oneReservationPerAttendee=true' : '';
        return result(postJson(`/admin/api/event/${encodeURIComponent(eventName)}/attendees/import${query}`, request));
    }

    static loadStats(eventName: string, requestId: string): Promise<AttendeesImportStats> {
        return result(fetch(`/admin/api/event/${encodeURIComponent(eventName)}/attendees/import/${encodeURIComponent(requestId)}/status`, { credentials: 'include' }));
    }

    /**
     * An empty file with all the columns: the required ones, the reference and the given attendees' data to collect
     */
    static template(fieldNames: string[]): string {
        return Papa.unparse([[...REQUIRED_COLUMNS, REFERENCE_COLUMN, ...fieldNames]], { quotes: true }) + '\r\n';
    }

    /**
     * A column name as written in the template
     */
    static quoteColumn(name: string): string {
        return Papa.unparse([[name]], { quotes: true });
    }

    /**
     * Parses a CSV file with header. The first four columns are first name, last name, e-mail and language, whatever their name.
     * The other columns are additional data, except "reference", which is the attendee ID in the organizer's system.
     */
    static parse(content: string): ParsedAttendees {
        const parsed = Papa.parse<string[]>(content, {
            header: false,
            skipEmptyLines: true,
            delimiter: ',',
            // same as the previous import page: quotes within a quoted value are escaped by doubling them, as spreadsheets do
            quoteChar: '"',
        });
        const [header = [], ...rows] = parsed.data;
        if (header.length < FIXED_COLUMNS) {
            throw new Error(`The file must have at least ${FIXED_COLUMNS} columns: ${REQUIRED_COLUMNS.join(', ')}`);
        }
        const fieldNames = header.map(name => name.trim());
        const attendees: ImportedAttendee[] = [];
        let skippedRows = 0;
        for (const row of rows) {
            if (row.length < FIXED_COLUMNS) {
                skippedRows++;
                continue;
            }
            const attendee: ImportedAttendee = {
                firstName: row[0],
                lastName: row[1],
                emailAddress: row[2],
                language: row[3],
                additionalInfo: {},
            };
            for (let i = FIXED_COLUMNS; i < fieldNames.length; i++) {
                const value = row[i] ?? '';
                if (fieldNames[i].toLowerCase() === REFERENCE_COLUMN) {
                    attendee.reference = value;
                } else {
                    attendee.additionalInfo[fieldNames[i]] = [value];
                }
            }
            attendees.push(attendee);
        }
        return {
            attendees,
            additionalFields: fieldNames.slice(FIXED_COLUMNS).filter(name => name.toLowerCase() !== REFERENCE_COLUMN),
            skippedRows,
        };
    }
}
