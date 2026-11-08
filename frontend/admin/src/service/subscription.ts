import {fetchJson, patchJson, postJson} from "./helpers.ts";
import {
    EventSubscriptionLink,
    SubscriptionDescriptorModification,
    SubscriptionPayload,
    SubscriptionWithStatistics
} from "../model/subscription-descriptor.ts";

export class SubscriptionService {

    static loadList(organizationId: number): Promise<SubscriptionWithStatistics[]> {
        return fetchJson(`/admin/api/organization/${organizationId}/subscription/list`);
    }

    static loadDescriptor(organizationId: number, subscriptionId: string): Promise<SubscriptionDescriptorModification> {
        return fetchJson(`/admin/api/organization/${organizationId}/subscription/${subscriptionId}`);
    }

    static loadLinkedEvents(organizationId: number, subscriptionId: string): Promise<EventSubscriptionLink[]> {
        return fetchJson(`/admin/api/organization/${organizationId}/subscription/${subscriptionId}/events`);
    }

    /**
     * Creates a subscription, resolves with its id
     */
    static async create(organizationId: number, payload: SubscriptionPayload): Promise<string> {
        const response = await postJson(`/admin/api/organization/${organizationId}/subscription`, payload);
        if (!response.ok) {
            throw new Error(await response.text() || `unexpected status ${response.status}`);
        }
        // the backend returns the id as a JSON string
        return await response.json();
    }

    static async update(organizationId: number, subscriptionId: string, payload: SubscriptionPayload): Promise<void> {
        const response = await postJson(`/admin/api/organization/${organizationId}/subscription/${subscriptionId}`, payload);
        if (!response.ok) {
            throw new Error(await response.text() || `unexpected status ${response.status}`);
        }
    }

    static async setPublic(organizationId: number, subscriptionId: string, isPublic: boolean): Promise<boolean> {
        const response = await patchJson(
            `/admin/api/organization/${organizationId}/subscription/${subscriptionId}/is-public?status=${isPublic}`, null);
        if (!response.ok) {
            throw new Error(await response.text() || `unexpected status ${response.status}`);
        }
        return response.json();
    }
}
