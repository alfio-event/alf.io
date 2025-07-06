import { postJson } from "./helpers";

export class ConfigurationService {

    static update(kv: { key: string, value: string }): Promise<Response> {
        return postJson('/admin/api/configuration/update', kv);
    }
}
