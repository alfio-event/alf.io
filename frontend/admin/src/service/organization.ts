import {Organization, OrganizationModification} from "../model/organization.ts";
import {fetchJson, postJson} from "./helpers.ts";
import {ValidatedResponse} from "../model/validation.ts";

export interface ValidationResult extends Omit<ValidatedResponse<OrganizationModification>, "value"> {
}

export class OrganizationService {

    static loadAll(): Promise<ReadonlyArray<Organization>> {
        return fetchJson("/admin/api/organizations");
    }

    static load(id: number): Promise<Organization> {
        return fetchJson(`/admin/api/organizations/${id}`);
    }

    static create(org: OrganizationModification): Promise<Response> {
        return postJson("/admin/api/organizations/new", org);
    }

    static update(org: OrganizationModification): Promise<Response> {
        return postJson("/admin/api/organizations/update", org);
    }

    static async check(org: OrganizationModification): Promise<ValidationResult> {
        const response = await postJson("/admin/api/organizations/check", org);
        return response.json();
    }

    static async checkSlug(org: OrganizationModification): Promise<ValidationResult> {
        const response = await postJson("/admin/api/organizations/validate-slug", org);
        return response.json();
    }
}
