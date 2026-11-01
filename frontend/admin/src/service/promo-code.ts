import {postJson, callDelete, fetchJson} from "./helpers.ts";
import {AlfioEvent} from "../model/event.ts";
import {
    PromoCodeDiscount,
    PromoCodeFormData,
    PromoCodeSettingsData,
    UsageDetailEvent
} from "../model/promo-code.ts";

export class PromoCodeService {
    static add(promoCode: PromoCodeFormData): Promise<Response> {
        const payload = { ...promoCode };
        if (promoCode.eventId == null) {
            payload.utcOffset = -new Date().getTimezoneOffset() * 60;
        }
        return postJson('/admin/api/promo-code', payload);
    }

    static update(promoCodeId: number, data: PromoCodeSettingsData): Promise<Response> {
        const payload = { ...data };
        if (data.eventId == null) {
            payload.utcOffset = -new Date().getTimezoneOffset() * 60;
        }
        return postJson(`/admin/api/promo-code/${promoCodeId}`, payload);
    }

    static remove(promoCodeId: number): Promise<Response> {
        return callDelete(`/admin/api/promo-code/${promoCodeId}`);
    }

    static disable(promoCodeId: number): Promise<Response> {
        return postJson(`/admin/api/promo-code/${promoCodeId}/disable`, null);
    }

    static listByEvent(eventId: number): Promise<PromoCodeDiscount[]> {
        return fetchJson(`/admin/api/events/${eventId}/promo-code`);
    }

    static listByOrganization(organizationId: number): Promise<PromoCodeDiscount[]> {
        return fetchJson(`/admin/api/organization/${organizationId}/promo-code`);
    }

    /**
     * The discount codes that can be applied to a new reservation of the event: the ones of the event and of its
     * organization, valid now and in the currency of the event. A code of the event hides the organization's one
     * with the same name, as when the code is applied.
     */
    static async listApplicableDiscounts(event: AlfioEvent): Promise<PromoCodeDiscount[]> {
        const [eventCodes, organizationCodes] = await Promise.all([
            PromoCodeService.listByEvent(event.id),
            PromoCodeService.listByOrganization(event.organizationId),
        ]);
        const eventCodeNames = new Set(eventCodes.map(code => code.promoCode));
        return [...eventCodes, ...organizationCodes.filter(code => !eventCodeNames.has(code.promoCode))]
            .filter(code => code.codeType === 'DISCOUNT' && code.currentlyValid)
            .filter(code => !code.currencyCode || code.currencyCode === event.currency)
            .sort((a, b) => a.promoCode.localeCompare(b.promoCode));
    }

    static countUse(promoCodeId: number): Promise<number> {
        return fetchJson(`/admin/api/promo-code/${promoCodeId}/count-use`);
    }

    static getUsageDetails(promoCodeId: number, eventShortName?: string): Promise<UsageDetailEvent[]> {
        const params = eventShortName ? `?eventShortName=${encodeURIComponent(eventShortName)}` : '';
        return fetchJson(`/admin/api/promo-code/${promoCodeId}/detailed-usage${params}`);
    }

    static loadSingleConfig(publicIdentifier: string, key: string): Promise<string> {
        return fetchJson(`/admin/api/configuration/events/${publicIdentifier}/single/${key}`);
    }
}
/**
 * The amount of the discount, e.g. "-10%" or "-5.00 CHF per ticket"
 */
export function discountDescription(code: PromoCodeDiscount, currency: string): string {
    if (code.discountType === 'PERCENTAGE') {
        return `-${code.discountAmount}%`;
    }
    const amount = `-${code.formattedDiscountAmount ?? code.discountAmount} ${code.currencyCode || currency}`;
    if (code.discountType === 'FIXED_AMOUNT') {
        return `${amount} per ticket`;
    }
    return `${amount} per reservation`;
}
