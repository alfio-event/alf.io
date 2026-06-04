import type { SubscriptionDescriptor } from '../model/subscription-descriptor.ts';
import { fetchJson } from './helpers.ts';

export class SubscriptionDescriptorService {
    static load(
        publicIdentifier: string,
        organizationId: number,
    ): Promise<SubscriptionDescriptor> {
        return fetchJson(
            `/admin/api/organization/${organizationId}/subscription/${publicIdentifier}`,
        );
    }
}
