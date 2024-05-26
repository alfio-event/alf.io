import {AdditionalItem} from "../model/additional-item.ts";

export type UsageCount = { [id: number]: { [ status: string ]: number } };

export class AdditionalItemService {
    static loadAll(eventId: number): Promise<Array<AdditionalItem>> {
        return fetch(`/admin/api/event/${eventId}/additional-services`)
            .then(response => response.json());
    }

    static useCount(eventId: number): Promise<UsageCount> {
        return fetch(`/admin/api/event/${eventId}/additional-services/count`)
            .then(response => response.json());
    }
}

