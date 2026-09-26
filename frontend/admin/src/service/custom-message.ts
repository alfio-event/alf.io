import {fetchJson, postJson} from "./helpers.ts";
import {ContentLanguage} from "../model/purchase-context.ts";
import {MessageModification} from "../model/custom-message.ts";

export class CustomMessageService {

    static loadLanguages(eventName: string): Promise<ContentLanguage[]> {
        return fetchJson(`/admin/api/events/${encodeURIComponent(eventName)}/languages`);
    }

    /**
     * @param categoryIds the categories whose attendees will receive the message. Empty means all the attendees.
     */
    static preview(eventName: string, categoryIds: number[], messages: MessageModification[]): Promise<Response> {
        return postJson(CustomMessageService.url(eventName, 'preview', categoryIds), messages);
    }

    /**
     * @param categoryIds the categories whose attendees will receive the message. Empty means all the attendees.
     */
    static send(eventName: string, categoryIds: number[], messages: MessageModification[]): Promise<Response> {
        return postJson(CustomMessageService.url(eventName, 'send', categoryIds), messages);
    }

    private static url(eventName: string, action: 'preview' | 'send', categoryIds: number[]): string {
        const url = `/admin/api/events/${encodeURIComponent(eventName)}/messages/${action}`;
        if (categoryIds.length === 0) {
            return url;
        }
        const params = new URLSearchParams();
        categoryIds.forEach(id => params.append('categoryIds', String(id)));
        return `${url}?${params}`;
    }
}
