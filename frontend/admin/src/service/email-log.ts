import {PurchaseContextType} from "../model/purchase-context.ts";
import {EmailMessage} from "../model/email-message.ts";
import {PageAndContent} from "../model/reservation.ts";
import {fetchJson} from "./helpers.ts";

export class EmailLogService {

    /**
     * @param page 1-based page number
     */
    static list(type: PurchaseContextType, publicIdentifier: string, page: number, search: string): Promise<PageAndContent<EmailMessage[]>> {
        const params = new URLSearchParams({ page: String(page - 1), search });
        return fetchJson(`${EmailLogService.baseUrl(type, publicIdentifier)}?${params}`);
    }

    static async load(type: PurchaseContextType, publicIdentifier: string, messageId: number): Promise<EmailMessage> {
        const response = await fetch(`${EmailLogService.baseUrl(type, publicIdentifier)}/${messageId}`, { credentials: 'include' });
        if (!response.ok) {
            throw new Error(`Failed to load message ${messageId}`);
        }
        return response.json();
    }

    private static baseUrl(type: PurchaseContextType, publicIdentifier: string): string {
        return `/admin/api/${type}/${encodeURIComponent(publicIdentifier)}/email`;
    }
}
