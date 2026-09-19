import {fetchJson} from "./helpers.ts";
import {dispatchFeedback} from "../model/dom-events.ts";
import {LitElement} from "lit";

export class UtilService {
    static renderMarkdown(text: string): Promise<string> {
        return fetchJson(`/admin/api/utils/render-commonmark?text=${encodeURIComponent(text)}`)
    }

    static async copyValueToClipboard(supplier: () => string, what: string, src: LitElement): Promise<void> {
        if (await UtilService.copyToClipboard(supplier())) {
            dispatchFeedback({type: 'success', message: `${what} copied to Clipboard!`}, src);
        } else {
            dispatchFeedback({type: 'danger', message: 'Unable to copy ${what} to Clipboard'}, src);
        }
    }

    private static async copyToClipboard(text: string): Promise<boolean> {
        if (navigator.clipboard?.writeText) {
            try {
                await navigator.clipboard.writeText(text);
                return true;
            } catch {
            }
        }

        const listener = (clipboardEvent: ClipboardEvent) => {
            const clipboard = clipboardEvent.clipboardData || (window as any).clipboardData;
            clipboard.setData('text', text);
            clipboardEvent.preventDefault();
        };
        document.addEventListener('copy', listener, false);
        let succeeded = false;
        try {
            // noinspection JSDeprecatedSymbols
            succeeded = document.execCommand('copy');
        } catch {
        } finally {
            document.removeEventListener('copy', listener, false);
        }
        return succeeded;
    }
}
