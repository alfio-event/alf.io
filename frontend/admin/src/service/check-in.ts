/**
 * Admin check-in.
 *
 * Attendees are downloaded in chunks and kept in memory: the admin bundle is loaded once, so the cache survives
 * AngularJS route changes, and subsequent loads fetch only the tickets modified in the meantime.
 * Filtering and pagination happen on the client, like they did on the legacy (IndexedDB-backed) page.
 */

import {CheckInTicket, TicketAndCheckInResult} from '../model/check-in.ts';
import {postJson} from './helpers.ts';

const CHUNK_SIZE = 200;
// set by the server on the "ticket-identifiers" response. Used as "changedSince" for the next sync
const SERVER_TIME_HEADER = 'Alfio-TIME';

/**
 * Invoked after each downloaded chunk, with all the attendees known so far, so that the list can be displayed
 * (and searched) while the download is still in progress
 */
export type LoadProgressListener = (completedChunks: number, totalChunks: number, tickets: CheckInTicket[]) => void;

interface EventCache {
    since: string | null;
    tickets: Map<number, CheckInTicket>;
}

const caches = new Map<number, EventCache>();

export class CheckInService {

    /**
     * Downloads the attendees modified since the last call (all of them on first call)
     * @returns all the known attendees of the event, sorted by id
     */
    static async loadTickets(eventId: number, onProgress: LoadProgressListener = () => {}): Promise<CheckInTicket[]> {
        let cache = caches.get(eventId);
        if (cache == null) {
            cache = { since: null, tickets: new Map() };
            caches.set(eventId, cache);
        }
        const params = cache.since != null ? `?${new URLSearchParams({ changedSince: cache.since })}` : '';
        const response = await fetch(`${baseUrl(eventId)}/ticket-identifiers${params}`, { credentials: 'include' });
        await ensureSuccess(response);
        const since = response.headers.get(SERVER_TIME_HEADER);
        const ids: number[] = await response.json();
        const chunks = toChunks(ids, CHUNK_SIZE);
        for (let i = 0; i < chunks.length; i++) {
            const chunkResponse = await postJson(`${baseUrl(eventId)}/tickets`, chunks[i]);
            await ensureSuccess(chunkResponse);
            const tickets: CheckInTicket[] = await chunkResponse.json();
            tickets.forEach(ticket => cache.tickets.set(ticket.id, trim(ticket)));
            if (i < chunks.length - 1) {
                onProgress(i + 1, chunks.length, sortedTickets(cache));
            }
        }
        // update the timestamp only after all the chunks have been downloaded, so that a failure triggers a full retry
        cache.since = since;
        return sortedTickets(cache);
    }

    /**
     * @returns true if the ticket has been checked in
     */
    static manualCheckIn(ticket: CheckInTicket): Promise<boolean> {
        return postForBoolean(`${baseUrl(ticket.eventId)}/ticket/${ticket.uuid}/manual-check-in`);
    }

    /**
     * @returns true if the check-in has been reverted
     */
    static revertCheckIn(ticket: CheckInTicket): Promise<boolean> {
        return postForBoolean(`${baseUrl(ticket.eventId)}/ticket/${ticket.uuid}/revert-check-in`);
    }

    /**
     * Evaluates the scanned QR code, without checking in the ticket
     */
    static async getTicket(eventId: number, code: string): Promise<TicketAndCheckInResult> {
        const params = new URLSearchParams({ qrCode: code });
        const response = await fetch(`${baseUrl(eventId)}/ticket/${ticketIdentifier(code)}?${params}`, { credentials: 'include' });
        await ensureSuccess(response);
        return response.json();
    }

    static async checkIn(eventId: number, code: string): Promise<TicketAndCheckInResult> {
        const response = await postJson(`${baseUrl(eventId)}/ticket/${ticketIdentifier(code)}`, { code });
        await ensureSuccess(response);
        return response.json();
    }

    /**
     * Confirms the payment of a "MUST_PAY" ticket. The ticket must be checked in afterwards.
     * @returns the updated status of the ticket
     */
    static async confirmPayment(eventId: number, code: string): Promise<TicketAndCheckInResult> {
        const response = await postJson(`${baseUrl(eventId)}/ticket/${ticketIdentifier(code)}/confirm-on-site-payment`, null);
        await ensureSuccess(response);
        return CheckInService.getTicket(eventId, code);
    }
}

/**
 * "Pending" attendees are all the attendees not yet checked in
 */
export function isCheckedIn(ticket: CheckInTicket): boolean {
    return ticket.status === 'CHECKED_IN';
}

/**
 * Case-insensitive match on name, e-mail, category, ticket / reservation identifiers
 */
export function matchesSearch(ticket: CheckInTicket, search: string): boolean {
    if (search.trim() === '') {
        return true;
    }
    return [ticket.fullName, ticket.email, ticket.ticketCategory.name, ticket.uuid, ticket.publicUuid, ticket.ticketsReservationId, ticket.extReference]
        .join('/')
        .toLowerCase()
        .includes(search.trim().toLowerCase());
}

/**
 * The API returns the full ticket info (about 3KB per ticket). Big events can have tens of thousands of attendees:
 * keep in memory only what the page uses
 */
function trim(ticket: CheckInTicket): CheckInTicket {
    const reservation = ticket.ticketReservation;
    return {
        id: ticket.id,
        uuid: ticket.uuid,
        publicUuid: ticket.publicUuid,
        status: ticket.status,
        eventId: ticket.eventId,
        ticketsReservationId: ticket.ticketsReservationId,
        fullName: ticket.fullName,
        firstName: ticket.firstName,
        lastName: ticket.lastName,
        email: ticket.email,
        extReference: ticket.extReference,
        ticketCategory: { name: ticket.ticketCategory.name },
        ticketReservation: {
            fullName: reservation.fullName,
            firstName: reservation.firstName,
            lastName: reservation.lastName,
            email: reservation.email,
            paymentMethod: reservation.paymentMethod,
        },
    };
}

function sortedTickets(cache: EventCache): CheckInTicket[] {
    return [...cache.tickets.values()].sort((a, b) => a.id - b.id);
}

function baseUrl(eventId: number): string {
    return `/admin/api/check-in/${eventId}`;
}

// QR codes are in the format "<ticket uuid>/<hmac>"
function ticketIdentifier(code: string): string {
    return encodeURIComponent(code.split('/')[0]);
}

async function postForBoolean(url: string): Promise<boolean> {
    const response = await postJson(url, null);
    await ensureSuccess(response);
    return (await response.json()) === true;
}

async function ensureSuccess(response: Response): Promise<void> {
    if (!response.ok) {
        throw new Error((await response.text()) || `unexpected status ${response.status}`);
    }
}

function toChunks<T>(items: T[], size: number): T[][] {
    const chunks: T[][] = [];
    for (let i = 0; i < items.length; i += size) {
        chunks.push(items.slice(i, i + size));
    }
    return chunks;
}
