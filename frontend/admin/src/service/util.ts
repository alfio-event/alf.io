import {fetchJson, postJson, responseErrorMessage} from "./helpers.ts";
import {dispatchFeedback} from "../model/dom-events.ts";
import {LitElement} from "lit";
import {Country} from "../components/attendee-fields.ts";

export interface AvailableCurrency {
    code: string;
    name: string;
    symbol: string;
    decimalPlaces: number;
}

export class UtilService {
    static renderMarkdown(text: string): Promise<string> {
        return fetchJson(`/admin/api/utils/render-commonmark?text=${encodeURIComponent(text)}`)
    }

    /**
     * Countries that can be selected for VAT purposes, as ISO code -> name
     */
    static countriesForVat(): Promise<Record<string, string>> {
        return fetchJson('/admin/api/utils/countriesForVat');
    }

    static availableCurrencies(): Promise<AvailableCurrency[]> {
        return fetchJson('/admin/api/utils/currencies');
    }

    /**
     * Generates a candidate event short name (slug) from the display name
     */
    static generateShortName(displayName: string): Promise<string> {
        return fetchJson(`/admin/api/utils/short-name/generate?displayName=${encodeURIComponent(displayName)}`);
    }

    /**
     * Whether the short name is still available. Rejects when it is taken (409)
     */
    static async validateShortName(shortName: string): Promise<boolean> {
        const response = await postJson(`/admin/api/utils/short-name/validate?shortName=${encodeURIComponent(shortName)}`, null);
        if (response.status === 409) {
            return false;
        }
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, `Unexpected status ${response.status}`));
        }
        return await response.json();
    }

    /**
     * All the countries, sorted by their name in the given language
     */
    static countries(language: string): Promise<Country[]> {
        return fetchJson(`/api/v2/public/i18n/countries/${encodeURIComponent(language)}`);
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
