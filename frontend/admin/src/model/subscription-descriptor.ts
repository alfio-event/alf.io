import {LocalizedContent, PurchaseContext} from "./purchase-context.ts";

export interface SubscriptionDescriptor extends PurchaseContext {
    title: LocalizedContent;
    organizationId: number;
    currency: string;
    timeZone: string;
}
