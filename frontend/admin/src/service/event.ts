import {EventWithOrganization} from "../model/event.ts";
import {fetchJson} from "./helpers.ts";

export class EventService {
    static load(publicIdentifier: string): Promise<EventWithOrganization> {
        return fetchJson(`/admin/api/events/${publicIdentifier}`);
    }
}
