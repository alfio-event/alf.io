import { fetchJson, postJson } from "./helpers";

export class ConfigurationService {

    static update(kv: { key: string, value: string }): Promise<Response> {
        return postJson('/admin/api/configuration/update', kv);
    }

    static loadSingleConfig(publicIdentifier: string, key: string): Promise<string> {
        return fetchJson(`/admin/api/configuration/events/${publicIdentifier}/single/${key}`);
    }
}
