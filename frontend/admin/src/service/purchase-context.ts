import {SubscriptionDescriptor} from "../model/subscription-descriptor.ts";
import {PurchaseContextType} from "../model/purchase-context.ts";
import {EventWithOrganization} from "../model/event.ts";
import {EventService} from "./event.ts";
import {SubscriptionDescriptorService} from "./subscription-descriptor.ts";

export class PurchaseContextService {
    static async load(publicIdentifier: string, type: PurchaseContextType, organizationId: number): Promise<{eventWithOrganization?: EventWithOrganization, subscriptionDescriptor?: SubscriptionDescriptor}> {
        if (type === 'subscription') {
            return {subscriptionDescriptor: await SubscriptionDescriptorService.load(publicIdentifier, organizationId)};
        }
        return {eventWithOrganization: await EventService.load(publicIdentifier)};
    }
}
