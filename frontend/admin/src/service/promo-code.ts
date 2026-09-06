import {postJson, callDelete, fetchJson} from "./helpers.ts";
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