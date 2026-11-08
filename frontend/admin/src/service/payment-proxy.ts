import {fetchJson} from "./helpers.ts";
import {PaymentProxy} from "../model/purchase-context.ts";

export interface PaymentMethodDTO {
    paymentProxy: PaymentProxy;
    paymentMethodId: string;
    status: 'ACTIVE' | 'ERROR';
    onlyForCurrency?: string[] | null;
}

export const PAYMENT_PROXY_DESCRIPTIONS: Record<string, string> = {
    'STRIPE': 'Stripe: Credit cards',
    'ON_SITE': 'On site (cash) payment',
    'OFFLINE': 'Offline payment (bank transfer, invoice, etc.)',
    'PAYPAL': 'PayPal',
    'MOLLIE': 'Mollie: Credit cards, iDEAL, Bancontact, ING Home Pay, Belfius, KBC/CBC, Przelewy24',
    'SAFERPAY': 'Saferpay By SIX Payments',
    'CUSTOM_OFFLINE': 'Custom Offline Payment Methods'
};

/**
 * Payment methods that can be attached to a purchase context: the active ones, except the
 * on-site/offline ones, which don't apply to online sales
 */
export const SELECTABLE_PAYMENT_PROXIES: PaymentProxy[] = ['ON_SITE', 'OFFLINE', 'CUSTOM_OFFLINE'];

export class PaymentProxyService {
    static allProxies(organizationId: number): Promise<PaymentMethodDTO[]> {
        return fetchJson(`/admin/api/paymentProxies/${organizationId}`);
    }

    static selectableProxies(organizationId: number): Promise<PaymentMethodDTO[]> {
        return PaymentProxyService.allProxies(organizationId).then(proxies =>
            proxies.filter(p => p.status === 'ACTIVE' && !SELECTABLE_PAYMENT_PROXIES.includes(p.paymentProxy)));
    }
}
