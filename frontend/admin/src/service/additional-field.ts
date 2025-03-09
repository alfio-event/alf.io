import {PurchaseContext} from "../model/purchase-context.ts";
import {AdditionalField} from "../model/additional-field.ts";
import {callDelete, fetchJson, postJson} from "./helpers.ts";

export class AdditionalFieldService {
    /*
            getRestrictedValuesStats: function(purchaseContextType, publicIdentifier, id) {
                return $http.get('/admin/api/'+purchaseContextType+'/'+publicIdentifier+'/additional-field/'+id+'/stats').error(HttpErrorHandler.handle);
            }
     */
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

}
