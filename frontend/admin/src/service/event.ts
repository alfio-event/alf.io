import {EventWithOrganization} from "../model/event.ts";
import {EventModificationPayload} from "../model/event-modification.ts";
import {ValidatedResponse} from "../model/validation.ts";
import {fetchJson, postJson, responseErrorMessage} from "./helpers.ts";

export interface EventValidationResult extends Omit<ValidatedResponse<unknown>, "value"> {
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
}
