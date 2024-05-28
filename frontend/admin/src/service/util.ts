import {fetchJson} from "./helpers.ts";

export class UtilService {
    static renderMarkdown(text: string): Promise<string> {
        return fetchJson(`/admin/api/utils/render-commonmark?text=${encodeURIComponent(text)}`)
    }
}
