import { callDelete, fetchJson, postJson, putJson } from "./helpers";

export class CustomPaymentMethodsService {
    async getPaymentMethodsForOrganization(organizationId: number) {
        const result = await fetchJson<CustomOfflinePayment[]>(
            `/admin/api/configuration/organizations/${organizationId}/payment-method`
        );
        return result;
    }

    async createPaymentMethod(organizationId: number, paymentMethod: CustomOfflinePayment) {
        const result = await postJson(`/admin/api/configuration/organizations/${organizationId}/payment-method`, paymentMethod);
        return result;
    }

    async updatePaymentMethod(organizationId: number, existingMethodId: string, paymentMethod: CustomOfflinePayment) {
        const result = await putJson(
            `/admin/api/configuration/organizations/${organizationId}/payment-method/${existingMethodId}`,
            paymentMethod
        );
        return result;
    }

    async deletePaymentMethod(organizationId: number, existingMethodId: string) {
        const result = await callDelete(
            `/admin/api/configuration/organizations/${organizationId}/payment-method/${existingMethodId}`
        );
        return result;
    }

    async setPaymentMethodsForEvent(eventId: number, paymentMethodIds: string[]) {
        const result = await postJson(
            `/admin/api/configuration/event/${eventId}/payment-method`,
            paymentMethodIds
        );
        return result;
    }

    async getAllowedPaymentMethodsForEvent(eventId: number) {
        const result = await fetchJson<CustomOfflinePayment[]>(
            `/admin/api/configuration/event/${eventId}/payment-method`
        );
        return result;
    }

    async getDeniedPaymentMethodsForCategory(eventId: number, categoryId: number) {
        const result = await fetchJson<string[]>(
            `/admin/api/events/${eventId}/categories/${categoryId}/denied-custom-payment-methods`
        );
        return result;
    }

    async setDeniedPaymentMethodsForCategory(eventId: number, categoryId: number, paymentMethodIds: string[]) {
        const result = await postJson(
            `/admin/api/events/${eventId}/categories/${categoryId}/denied-custom-payment-methods`,
            paymentMethodIds
        );
        return result;
    }
}
