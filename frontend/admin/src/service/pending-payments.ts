/**
 * Centralized store for the "pending payments" count of each event.
 *
 * The admin bundle is loaded once, so this module-level state survives AngularJS route changes:
 * - there is at most one poller per event, regardless of how many components display its count
 * - polling stops when the last subscriber goes away, the cached count is reused when a new one comes back
 * - polling is paused while the page is hidden
 * - errors are never notified to the user: the last known value is kept, and the next attempt is delayed (backoff)
 */

import {BulkConfirmationResult, PendingPayment} from '../model/reservation.ts';
import {callDelete, fetchJson, postJson} from './helpers.ts';

export type PendingPaymentsListener = (count: number | null) => void;

const POLL_INTERVAL_MS = 30_000;
const MAX_BACKOFF_MS = 5 * 60_000;

export const PENDING_PAYMENTS_CHANGED_EVENT = 'alfio-pending-payments-changed';

interface Entry {
    count: number | null;
    lastFetch: number;
    failures: number;
    disabled: boolean;
    inFlight: boolean;
    timer: ReturnType<typeof setTimeout> | null;
    listeners: Set<PendingPaymentsListener>;
}

const entries = new Map<string, Entry>();

function getEntry(eventName: string): Entry {
    let entry = entries.get(eventName);
    if (entry == null) {
        entry = { count: null, lastFetch: 0, failures: 0, disabled: false, inFlight: false, timer: null, listeners: new Set() };
        entries.set(eventName, entry);
    }
    return entry;
}

function nextDelay(entry: Entry): number {
    if (entry.failures === 0) {
        return POLL_INTERVAL_MS;
    }
    return Math.min(POLL_INTERVAL_MS * Math.pow(2, entry.failures), MAX_BACKOFF_MS);
}

function clearTimer(entry: Entry): void {
    if (entry.timer != null) {
        clearTimeout(entry.timer);
        entry.timer = null;
    }
}

function isActive(entry: Entry): boolean {
    return entry.listeners.size > 0 && !entry.disabled && document.visibilityState !== 'hidden';
}

function schedule(eventName: string, entry: Entry): void {
    clearTimer(entry);
    if (!isActive(entry) || entry.inFlight) {
        return;
    }
    const elapsed = Date.now() - entry.lastFetch;
    const delay = Math.max(0, nextDelay(entry) - elapsed);
    entry.timer = setTimeout(() => {
        entry.timer = null;
        void load(eventName, entry);
    }, delay);
}

function notify(entry: Entry): void {
    entry.listeners.forEach(listener => listener(entry.count));
}

async function load(eventName: string, entry: Entry): Promise<void> {
    if (entry.inFlight) {
        return;
    }
    entry.inFlight = true;
    try {
        const response = await fetch(`/admin/api/events/${encodeURIComponent(eventName)}/pending-payments-count`, {
            method: 'GET',
            credentials: 'include',
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        });
        if (response.status === 403 || response.status === 404) {
            // the current user cannot see this information. No point in retrying.
            entry.disabled = true;
            entry.count = null;
        } else if (!response.ok) {
            throw new Error(`unexpected status ${response.status}`);
        } else {
            const count = Number(await response.json());
            entry.count = Number.isFinite(count) ? count : 0;
            entry.failures = 0;
        }
    } catch (e) {
        console.warn(`Cannot load pending payments count for ${eventName}`, e);
        entry.failures++;
    } finally {
        entry.lastFetch = Date.now();
        entry.inFlight = false;
    }
    notify(entry);
    schedule(eventName, entry);
}

/**
 * Subscribes to the pending payments count of the given event. The listener is invoked immediately if a value is available.
 * @returns a function to unsubscribe
 */
export function subscribePendingPayments(eventName: string, listener: PendingPaymentsListener): () => void {
    const entry = getEntry(eventName);
    entry.listeners.add(listener);
    if (entry.lastFetch > 0) {
        listener(entry.count);
    }
    if (entry.listeners.size === 1) {
        schedule(eventName, entry);
    }
    return () => {
        entry.listeners.delete(listener);
        if (entry.listeners.size === 0) {
            clearTimer(entry);
        }
    };
}

/**
 * Forces a reload of the count for the given event, e.g. after a payment has been confirmed
 */
export function refreshPendingPayments(eventName: string): void {
    const entry = entries.get(eventName);
    if (entry != null && entry.listeners.size > 0) {
        clearTimer(entry);
        entry.failures = 0;
        void load(eventName, entry);
    } else if (entry != null) {
        // nobody is listening right now: make sure that the next subscriber gets a fresh value
        entry.lastFetch = 0;
    }
}

document.addEventListener('visibilitychange', () => {
    entries.forEach((entry, eventName) => schedule(eventName, entry));
});

// allows the legacy (AngularJS) application to signal a change
window.addEventListener(PENDING_PAYMENTS_CHANGED_EVENT, (e) => {
    const eventName = (e as CustomEvent<{ eventName?: string }>).detail?.eventName;
    if (eventName != null) {
        refreshPendingPayments(eventName);
    }
});

/**
 * Operations on the pending payments of an event. Successful changes refresh the pending payments count.
 */
export class PendingPaymentsService {

    static load(eventName: string): Promise<PendingPayment[]> {
        return fetchJson(baseUrl(eventName));
    }

    /**
     * Deletes the reservation, or issues a credit note if "credit" is true
     */
    static cancel(eventName: string, reservationId: string, credit: boolean, notify: boolean): Promise<void> {
        const params = new URLSearchParams({ credit: String(credit), notify: String(notify) });
        return changed(eventName, callDelete(`${baseUrl(eventName)}/${reservationId}?${params}`));
    }

    /**
     * Flags the transaction matched by the payment provider as not valid
     */
    static discardMatchingTransaction(eventName: string, reservationId: string, transactionId: number): Promise<void> {
        const url = `/admin/api/events/${encodeURIComponent(eventName)}/reservation/${reservationId}/transaction/${transactionId}/discard`;
        return changed(eventName, callDelete(url));
    }

    static async bulkConfirm(eventName: string, file: File): Promise<BulkConfirmationResult[]> {
        const response = await postJson(`${baseUrl(eventName)}/bulk-confirmation`, {
            file: await readAsBase64(file),
            type: file.type,
            name: file.name,
        });
        await ensureSuccess(response);
        refreshPendingPayments(eventName);
        return response.json();
    }
}

function baseUrl(eventName: string): string {
    return `/admin/api/events/${encodeURIComponent(eventName)}/pending-payments`;
}

async function changed(eventName: string, request: Promise<Response>): Promise<void> {
    await ensureSuccess(await request);
    refreshPendingPayments(eventName);
}

async function ensureSuccess(response: Response): Promise<void> {
    if (!response.ok) {
        throw new Error((await response.text()) || `unexpected status ${response.status}`);
    }
}

function readAsBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
            const dataUrl = reader.result as string;
            resolve(dataUrl.substring(dataUrl.indexOf('base64,') + 'base64,'.length));
        };
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(file);
    });
}
