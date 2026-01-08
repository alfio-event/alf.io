import {PurchaseContext} from "../model/purchase-context.ts";
import {
    AdditionalField,
    AdditionalFieldCreateRequest,
    AdditionalFieldStats,
    AdditionalFieldTemplate
} from "../model/additional-field.ts";
import {callDelete, fetchJson, postJson} from "./helpers.ts";
import {ValidatedResponse} from "../model/validation.ts";

export class AdditionalFieldService {

    static loadRestrictedValuesStats(purchaseContext: PurchaseContext, id: number): Promise<ReadonlyArray<AdditionalFieldStats>> {
        return fetchJson(`/admin/api/${purchaseContext.type}/${purchaseContext.publicIdentifier}/additional-field/${id}/stats`);
    }

    static loadAllByPurchaseContext(purchaseContext: PurchaseContext): Promise<ReadonlyArray<AdditionalField>> {
        return fetchJson(`/admin/api/${purchaseContext.type}/${purchaseContext.publicIdentifier}/additional-field`);
    }

    static deleteField(purchaseContext: PurchaseContext, id: number): Promise<Response> {
        return callDelete(`/admin/api/${purchaseContext.type}/${purchaseContext.publicIdentifier}/additional-field/${id}`);
    }

    static swapFieldPosition(purchaseContext: PurchaseContext, id1: number, id2: number): Promise<Response> {
        return postJson(`/admin/api/${purchaseContext.type}/${purchaseContext.publicIdentifier}/additional-field/swap-position/${id1}/${id2}`, null);
    }

    static moveField(purchaseContext: PurchaseContext, id: number, position: number): Promise<Response> {
        const body = new URLSearchParams();
        body.append("newPosition", String(position));
        return postJson(`/admin/api/${purchaseContext.type}/${purchaseContext.publicIdentifier}/additional-field/set-position/${id}`, body);
    }

    static loadTemplates(purchaseContext: PurchaseContext): Promise<ReadonlyArray<AdditionalFieldTemplate>> {
        return fetchJson(`/admin/api/${purchaseContext.type}/${purchaseContext.publicIdentifier}/additional-field/templates`);
    }

    static async createNewField(purchaseContext: PurchaseContext, field: AdditionalFieldCreateRequest): Promise<ValidatedResponse<AdditionalField>> {
        const response = await postJson(`/admin/api/${purchaseContext.type}/${purchaseContext.publicIdentifier}/additional-field/new`, field);
        return response.json();
    }

    static async saveField(purchaseContext: PurchaseContext, field: AdditionalField): Promise<Response> {
        const url = `/admin/api/${purchaseContext.type}/${purchaseContext.publicIdentifier}/additional-field/${field.id}`;
        return await postJson(url, field);
    }
}
