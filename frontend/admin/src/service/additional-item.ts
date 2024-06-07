import {AdditionalItem} from "../model/additional-item.ts";
import {ValidatedResponse} from "../model/validation.ts";
import {fetchJson, postJson} from "./helpers.ts";

export type UsageCount = { [id: number]: { [ status: string ]: number } };

export class AdditionalItemService {
    static async loadAll({eventId}: { eventId: number }): Promise<Array<AdditionalItem>> {
        return await fetchJson(`/admin/api/event/${eventId}/additional-services`);
    }

    static async useCount(eventId: number): Promise<UsageCount> {
        return await fetchJson(`/admin/api/event/${eventId}/additional-services/count`);
    }

    static async validateAdditionalItem(additionalItem: Partial<AdditionalItem>): Promise<ValidatedResponse<AdditionalItem>> {
        const response = await postJson('/admin/api/additional-services/validate', additionalItem);
        return response.json();
    }

    static async updateAdditionalItem(additionalItem: Partial<AdditionalItem>, eventId: number): Promise<Response> {
        return await postJson(`/admin/api/event/${eventId}/additional-services`, additionalItem);
    }

}

