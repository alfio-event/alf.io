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
 * Labels of the payment methods a provider reports (StaticPaymentMethods on the backend), in display order
 */
const PAYMENT_METHOD_LABELS: Record<string, string> = {
    'CREDIT_CARD': 'Credit cards',
    'APPLE_PAY': 'Apple Pay',
    'IDEAL': 'iDEAL | WERO',
    'BANCONTACT': 'Bancontact',
    'ING_HOME_PAY': 'ING Home Pay',
    'BELFIUS': 'Belfius',
    'KBC': 'KBC/CBC',
    'PRZELEWY_24': 'Przelewy24',
};

/**
 * Providers whose payment methods come live from the provider's account (Mollie asks its API for the methods
 * enabled on the profile), so listing them describes what buyers get. The others always report the same
 * method, whatever their checkout page offers, and keep their fixed description.
 */
const PROVIDERS_WITH_LIVE_METHODS: Partial<Record<PaymentProxy, string>> = {
    'MOLLIE': 'Mollie',
};

/**
 * A payment provider, with the payment methods it reported merged into a single description
 */
export interface PaymentProviderOption {
    paymentProxy: PaymentProxy;
    description: string;
    status: 'ACTIVE' | 'ERROR';
    onlyForCurrency?: string[] | null;
}

export function describePaymentProxy(proxy: string): string {
    return PAYMENT_PROXY_DESCRIPTIONS[proxy] ?? `${proxy} (please check the configuration)`;
}

function describeProvider(proxy: PaymentProxy, methods: PaymentMethodDTO[]): string {
    const providerName = PROVIDERS_WITH_LIVE_METHODS[proxy];
    if (providerName == null) {
        return describePaymentProxy(proxy);
    }
    const methodIds = new Set(methods.map(method => method.paymentMethodId));
    const labels = Object.keys(PAYMENT_METHOD_LABELS)
        .filter(id => methodIds.has(id))
        .map(id => PAYMENT_METHOD_LABELS[id]);
    if (labels.length === 0) {
        return describePaymentProxy(proxy);
    }
    return `${providerName}: ${labels.join(', ')}`;
}

/**
 * The backend returns one entry per payment method: group them into one option per provider.
 * A provider is active when at least one of its methods is.
 */
export function groupByProvider(methods: PaymentMethodDTO[]): PaymentProviderOption[] {
    const byProxy = new Map<PaymentProxy, PaymentMethodDTO[]>();
    for (const method of methods) {
        const group = byProxy.get(method.paymentProxy);
        if (group == null) {
            byProxy.set(method.paymentProxy, [method]);
        } else {
            group.push(method);
        }
    }
    return [...byProxy.entries()].map(([proxy, group]) => {
        let status: 'ACTIVE' | 'ERROR' = 'ERROR';
        if (group.some(method => method.status === 'ACTIVE')) {
            status = 'ACTIVE';
        }
        return {
            paymentProxy: proxy,
            description: describeProvider(proxy, group),
            status,
            onlyForCurrency: group[0].onlyForCurrency,
        };
    });
}

/**
 * Payment methods that can be attached to a purchase context: the active ones, except the
 * on-site/offline ones, which don't apply to online sales
 */
export const SELECTABLE_PAYMENT_PROXIES: PaymentProxy[] = ['ON_SITE', 'OFFLINE', 'CUSTOM_OFFLINE'];

export class PaymentProxyService {
    static allProxies(organizationId: number): Promise<PaymentMethodDTO[]> {
        return fetchJson(`/admin/api/paymentProxies/${organizationId}`);
    }

    static async providers(organizationId: number): Promise<PaymentProviderOption[]> {
        const methods = await PaymentProxyService.allProxies(organizationId);
        return groupByProvider(methods);
    }

    static async selectableProviders(organizationId: number): Promise<PaymentProviderOption[]> {
        const providers = await PaymentProxyService.providers(organizationId);
        return providers.filter(p => p.status === 'ACTIVE' && !SELECTABLE_PAYMENT_PROXIES.includes(p.paymentProxy));
    }
}
