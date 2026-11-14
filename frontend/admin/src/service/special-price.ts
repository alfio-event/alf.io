import Papa from 'papaparse';
import {SendCodeModification, SpecialPrice} from "../model/special-price.ts";
import {callDelete, ensureOk, postJson, responseErrorMessage} from "./helpers.ts";

// the header of the template, in this order: the columns of a row, without header, are code, assignee, email, language
export const CSV_HEADER = ['code', 'assignee', 'email', 'language'];

/**
 * Invitations for the reserved codes of a restricted ticket category (SpecialPriceApiController).
 */
export class SpecialPriceService {

    private static baseUrl(eventName: string, categoryId: number): string {
        return `/admin/api/events/${encodeURIComponent(eventName)}/categories/${categoryId}`;
    }

    /**
     * An empty file with the header, like the template of the attendees import: the example row is only
     * in the file specifications, so that it is never sent by mistake.
     */
    static template(): string {
        return Papa.unparse([CSV_HEADER], { quotes: true }) + '\r\n';
    }

    /**
     * Uploads the CSV file with the attendees to invite, telling the backend whether its first line is the
     * header. The backend parses the file, assigns the missing codes and resolves with the rows to review
     * before sending.
     */
    static async linkCodes(eventName: string, categoryId: number, file: File): Promise<SendCodeModification[]> {
        // strip a leading byte order mark, which the backend would read as part of the first column
        const content = (await file.text()).replace(/^\uFEFF/, '');
        return SpecialPriceService.postLinkCodes(eventName, categoryId, content, detectHeader(content), file.type, file.name);
    }

    /**
     * Prepares the invitations from the CSV content built by the manual form: it always has the header of
     * the template, so the backend skips its first line.
     */
    static async linkCodesFromCsv(eventName: string, categoryId: number, csv: string): Promise<SendCodeModification[]> {
        return SpecialPriceService.postLinkCodes(eventName, categoryId, csv, true, 'text/csv', 'invitations.csv');
    }

    private static async postLinkCodes(eventName: string, categoryId: number, content: string, withHeader: boolean,
                                       type: string, name: string): Promise<SendCodeModification[]> {
        const response = await postJson(`${SpecialPriceService.baseUrl(eventName, categoryId)}/link-codes?withHeader=${withHeader}`, {
            fileAsString: content,
            type,
            name,
        });
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'The invitations cannot be read.'));
        }
        return await response.json();
    }

    /**
     * Sends the invitation email to each assignee of the given codes.
     */
    static async sendCodes(eventName: string, categoryId: number, codes: SendCodeModification[]): Promise<void> {
        const response = await postJson(`${SpecialPriceService.baseUrl(eventName, categoryId)}/send-codes`, codes);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'The invitations have not been sent.'));
        }
    }

    /**
     * The codes of the category for which an invitation has already been sent.
     */
    static async loadSentCodes(eventName: string, categoryId: number): Promise<SpecialPrice[]> {
        const response = await fetch(`${SpecialPriceService.baseUrl(eventName, categoryId)}/sent-codes`, { credentials: 'include' });
        return await ensureOk<SpecialPrice[]>(response);
    }

    /**
     * Clears the recipient of a code, which becomes free again.
     */
    static async clearRecipient(eventName: string, categoryId: number, codeId: number): Promise<void> {
        const response = await callDelete(`${SpecialPriceService.baseUrl(eventName, categoryId)}/codes/${codeId}/recipient`);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'The recipient has not been cleared.'));
        }
    }
}

/**
 * The CSV content of the invitees added manually in the form: the header of the template, then one line
 * per invitee, in the same format as the uploaded file.
 */
export function invitationsCsv(invitees: SendCodeModification[]): string {
    return Papa.unparse([CSV_HEADER, ...invitees.map(i => [i.code ?? '', i.assignee, i.email, i.language])], { quotes: true }) + '\r\n';
}

/**
 * Whether the first line of the file is the header of the template. The file is uploaded as it is: with the
 * header, the backend skips the first line; without it, every line is data — so files generated before the
 * template existed keep working.
 */
function detectHeader(content: string): boolean {
    const endOfFirstLine = content.indexOf('\n');
    const firstLine = endOfFirstLine === -1 ? content : content.substring(0, endOfFirstLine);
    const firstRow = Papa.parse<string[]>(firstLine, { skipEmptyLines: true }).data[0] ?? [];
    const cells = firstRow.map(cell => String(cell).trim().toLowerCase());
    if (cells.length === CSV_HEADER.length && CSV_HEADER.every((name, index) => cells[index] === name)) {
        return true;
    }
    // a header with wrong names or order would be read as a data row by the backend: at least two cells match
    // the column names (a data row can have one, e.g. a code literally named "code", but not two)
    if (cells.filter(cell => CSV_HEADER.includes(cell)).length >= 2) {
        throw new Error(`The first line must be the header: ${CSV_HEADER.join(', ')}. Download the template and fill it in.`);
    }
    return false;
}
