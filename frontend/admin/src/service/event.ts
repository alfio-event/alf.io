import {EventWithOrganization, GroupLink, TicketsStatistics} from "../model/event.ts";
import {EventModificationPayload, TicketCategoryModificationPayload} from "../model/event-modification.ts";
import {ValidatedResponse} from "../model/validation.ts";
import {DateTimeModification} from "../model/purchase-context.ts";
import {WaitingQueueSubscription} from "../model/waiting-queue.ts";
import {callDelete, fetchJson, postJson, putJson, responseErrorMessage} from "./helpers.ts";

export interface EventValidationResult extends Omit<ValidatedResponse<unknown>, "value"> {
}

/**
 * The online settings of an event or of a category (MetadataModification)
 */
export interface OnlineSettingsPayload {
    callLinks: { link: string; validFrom: DateTimeModification; validTo: DateTimeModification }[];
    // locale -> markdown text
    requirementDescriptions: Record<string, string>;
}

export class EventService {
    static load(publicIdentifier: string): Promise<EventWithOrganization> {
        return fetchJson(`/admin/api/events/${publicIdentifier}`);
    }

    /**
     * Validates an event payload without saving it (POST /events/check)
     */
    static async check(payload: EventModificationPayload): Promise<EventValidationResult> {
        const response = await postJson('/admin/api/events/check', payload);
        return await response.json();
    }

    /**
     * Creates the event (POST /events/new). Resolves when the backend accepted it,
     * rejects with the error message otherwise
     */
    static async create(payload: EventModificationPayload): Promise<void> {
        const response = await postJson('/admin/api/events/new', payload);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, `Unexpected status ${response.status}`));
        }
    }

    /**
     * Updates the header of an existing event (POST /events/{id}/header/update).
     * Resolves with the validation result of the backend
     */
    static async updateHeader(eventId: number, payload: EventModificationPayload): Promise<EventValidationResult> {
        const response = await postJson(`/admin/api/events/${eventId}/header/update`, payload);
        return await response.json();
    }

    /**
     * Updates seats and prices of an existing event (POST /events/{id}/prices/update).
     * Resolves with the validation result of the backend
     */
    static async updatePrices(eventId: number, payload: EventModificationPayload): Promise<EventValidationResult> {
        const response = await postJson(`/admin/api/events/${eventId}/prices/update`, payload);
        return await response.json();
    }

    /**
     * Publishes or hides an event (PUT /events/{id}/status?active=…)
     */
    static async updateStatus(eventId: number, active: boolean): Promise<void> {
        const response = await putJson(`/admin/api/events/${eventId}/status?active=${active}`, null);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, `Unexpected status ${response.status}`));
        }
    }

    static async createCategory(eventId: number, category: TicketCategoryModificationPayload): Promise<EventValidationResult> {
        const response = await postJson(`/admin/api/events/${eventId}/categories/new`, category);
        return validationResult(await response.json());
    }

    static async updateCategory(eventId: number, categoryId: number, category: TicketCategoryModificationPayload): Promise<EventValidationResult> {
        const response = await postJson(`/admin/api/events/${eventId}/categories/${categoryId}/update`, category);
        return validationResult(await response.json());
    }

    static async deleteCategory(eventName: string, categoryId: number): Promise<void> {
        const response = await callDelete(`/admin/api/events/${eventName}/category/${categoryId}`);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, `Unexpected status ${response.status}`));
        }
    }

    /**
     * Moves the orphan tickets of a category to another one (PUT /events/reallocate)
     */
    static async reallocateOrphans(srcCategoryId: number, targetCategoryId: number, eventId: number): Promise<void> {
        const response = await putJson('/admin/api/events/reallocate', {srcCategoryId, targetCategoryId, eventId});
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, `Unexpected status ${response.status}`));
        }
    }

    /**
     * Assigns the tickets of a category to the dynamic categories (PUT /events/{eventName}/category/{categoryId}/unbind-tickets)
     */
    static async unbindTickets(eventName: string, categoryId: number): Promise<void> {
        const response = await putJson(`/admin/api/events/${eventName}/category/${categoryId}/unbind-tickets`, null);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, `Unexpected status ${response.status}`));
        }
    }

    /**
     * Saves the new order of the categories (PUT /events/{eventName}/rearrange-categories)
     */
    static async rearrangeCategories(eventName: string, categories: { id: number; name: string; ordinal: number }[]): Promise<void> {
        const response = await putJson(`/admin/api/events/${eventName}/rearrange-categories`, categories);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, `Unexpected status ${response.status}`));
        }
    }

    static loadTicketsStatistics(eventName: string, from?: string, to?: string): Promise<TicketsStatistics> {
        const params = new URLSearchParams();
        if (from != null) {
            params.set('from', from);
        }
        if (to != null) {
            params.set('to', to);
        }
        let query = '';
        if (params.size > 0) {
            query = `?${params}`;
        }
        return fetchJson(`/admin/api/events/${eventName}/ticket-sold-statistics${query}`);
    }

    /**
     * Saves the online settings (meeting links, prerequisites) of the event, or of one of its categories
     * when categoryId is set (PUT /events/{eventName}[/category/{categoryId}]/metadata)
     */
    static async updateOnlineSettings(eventName: string, categoryId: number | null, settings: OnlineSettingsPayload): Promise<void> {
        let url = `/admin/api/events/${eventName}/metadata`;
        if (categoryId != null) {
            url = `/admin/api/events/${eventName}/category/${categoryId}/metadata`;
        }
        const response = await putJson(url, settings);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'The online settings could not be saved. Please check the meeting links and their dates.'));
        }
    }

    /**
     * Runs a capability of an extension for the event (POST /events/{eventName}/capability/{capability}).
     * Resolves with the text returned by the extension, e.g. a link
     */
    static async executeCapability(eventName: string, capability: string, params: Record<string, string>): Promise<string> {
        const response = await postJson(`/admin/api/events/${eventName}/capability/${capability}`, params);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'The extension could not complete the action'));
        }
        return await response.text();
    }

    /**
     * The groups linked to the event and to its categories (GET /admin/api/group/for/event/{eventName}/all)
     */
    static loadGroupLinks(eventName: string): Promise<GroupLink[]> {
        return fetchJson(`/admin/api/group/for/event/${eventName}/all`);
    }

    /**
     * Deletes the event and all its data (DELETE /events/{id})
     */
    static async deleteEvent(eventId: number): Promise<void> {
        const response = await callDelete(`/admin/api/events/${eventId}`);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'The event could not be deleted'));
        }
    }

    /**
     * The attendee fields that can be exported (GET /events/{eventName}/fields): key = request parameter, value = label
     */
    static loadExportFields(eventName: string): Promise<ExportField[]> {
        return fetchJson(`/admin/api/events/${encodeURIComponent(eventName)}/fields`);
    }

    /**
     * The number of billing documents (invoices, receipts) of the event
     */
    static countBillingDocuments(eventName: string): Promise<number> {
        return fetchJson(`/admin/api/events/${encodeURIComponent(eventName)}/invoices/count`);
    }

    static loadWaitingQueueCount(eventName: string): Promise<number> {
        return fetchJson(`/admin/api/event/${eventName}/waiting-queue/count`);
    }

    static loadWaitingQueue(eventName: string): Promise<WaitingQueueSubscription[]> {
        return fetchJson(`/admin/api/event/${encodeURIComponent(eventName)}/waiting-queue/load`);
    }

    /**
     * Removes a person from the waiting list. The backend refuses it unless the person is still waiting
     */
    static removeFromWaitingQueue(eventName: string, subscriptionId: number): Promise<WaitingQueueSubscription[]> {
        return waitingQueueModification(callDelete(`/admin/api/event/${encodeURIComponent(eventName)}/waiting-queue/subscriber/${subscriptionId}`));
    }

    static restoreInWaitingQueue(eventName: string, subscriptionId: number): Promise<WaitingQueueSubscription[]> {
        return waitingQueueModification(putJson(`/admin/api/event/${encodeURIComponent(eventName)}/waiting-queue/subscriber/${subscriptionId}/restore`, null));
    }

    static loadWaitingQueueStatus(eventName: string): Promise<{ active: boolean; paused: boolean }> {
        return fetchJson(`/admin/api/event/${eventName}/waiting-queue/status`);
    }

    static async setWaitingQueuePaused(eventName: string, paused: boolean): Promise<void> {
        const response = await putJson(`/admin/api/event/${eventName}/waiting-queue/status`, {status: paused});
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, `Unexpected status ${response.status}`));
        }
    }
}

/**
 * The waiting list after a change of one of its subscriptions
 */
async function waitingQueueModification(request: Promise<Response>): Promise<WaitingQueueSubscription[]> {
    const response = await request;
    if (!response.ok) {
        throw new Error(await responseErrorMessage(response, `Unexpected status ${response.status}`));
    }
    const body: { list: WaitingQueueSubscription[] } = await response.json();
    return body.list;
}

/**
 * The validation result of a category request. The server answers an unexpected error with a plain message
 * (and status 200): it becomes a rejection, so that it isn't mistaken for a validation result
 */
function validationResult(body: EventValidationResult | string): EventValidationResult {
    if (typeof body === 'string') {
        throw new Error(body);
    }
    return body;
}

/**
 * An attendee field of the export
 */
export interface ExportField {
    key: string;
    value: string;
}

/**
 * Fired on window when a component has changed an event (e.g. its status), so that the other components showing
 * it (the event menu, the detail page) reload it. The source ignores its own notification
 */
export const EVENT_CHANGED_EVENT = 'alfio-event-changed';

export interface EventChangedDetail {
    eventName: string;
    source: EventTarget;
}

export function dispatchEventChanged(eventName: string, source: EventTarget): void {
    window.dispatchEvent(new CustomEvent<EventChangedDetail>(EVENT_CHANGED_EVENT, { detail: { eventName, source } }));
}

/**
 * Whether a notification of EVENT_CHANGED_EVENT concerns the given event and comes from another component
 */
export function isEventChangedElsewhere(e: Event, eventName: string, self: EventTarget): boolean {
    const detail = (e as CustomEvent<EventChangedDetail>).detail;
    return detail != null && detail.eventName === eventName && detail.source !== self;
}
