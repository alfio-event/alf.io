import { fetchJson, postJson } from "./helpers";

export class ConfigurationService {

    static update(kv: { key: string, value: string }): Promise<Response> {
        return postJson('/admin/api/configuration/update', kv);
    }

}

export class OrganizationConfigurationService {
    organizationId: number;

    constructor(organizationId: number) {
        this.organizationId = organizationId;
    }

    async getConfigurationEntry<T>(key: string): Promise<T> {
        const result: T = await fetchJson(`/admin/api/configuration/organizations/${this.organizationId}/single/${key}`);
        return result;
    }

    async updateConfigurationEntries(kv: {[key: string]: {id: number, key: string, value: string}[]}) {
        const result = await postJson(`/admin/api/configuration/organizations/${this.organizationId}/update`, kv);
        return result;
    }

    getConfiguration() {
        fetchJson(`/admin/api/configuration/update`)
    }
}
