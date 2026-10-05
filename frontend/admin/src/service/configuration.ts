import { fetchJson, postJson } from "./helpers";

export class ConfigurationService {

    static update(kv: { key: string, value: string }): Promise<Response> {
        return postJson('/admin/api/configuration/update', kv);
    }

    /**
     * Whether reservations should be identified by their invoice number. Falls back to false if the setting cannot be loaded
     */
    static async useInvoiceNumberAsId(eventName: string): Promise<boolean> {
        try {
            return (await ConfigurationService.loadSingleConfig(eventName, 'USE_INVOICE_NUMBER_AS_ID')) === 'true';
        } catch {
            return false;
        }
    }

    static loadSingleConfig(publicIdentifier: string, key: string): Promise<string> {
        return fetchJson(`/admin/api/configuration/events/${publicIdentifier}/single/${key}`);
    }
}
