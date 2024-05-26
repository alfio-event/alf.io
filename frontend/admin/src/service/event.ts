import {EventWithOrganization} from "../model/event.ts";

export class EventService {
    static load(publicIdentifier: string): Promise<EventWithOrganization> {
        return fetch(`/admin/api/events/${publicIdentifier}`)
            .then(response => response.json());
    }
}
