import {ContentLanguage} from "../model/event.ts";
import {html, nothing, TemplateResult} from "lit";
import {when} from "lit/directives/when.js";

export function postJson(url: string, payload: any): Promise<Response> {
    const xsrfName = document.querySelector('meta[name=_csrf_header]')?.getAttribute('content') as string;
    const xsrfValue = document.querySelector('meta[name=_csrf]')?.getAttribute('content') as string;
    return fetch(url, {
        method: 'POST', credentials: 'include', headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest',
            [xsrfName]: xsrfValue
        },
        body: JSON.stringify(payload)
    })
}

export function renderIf(predicate: () => boolean, template: () => TemplateResult): TemplateResult {
    return html`${when(predicate(), template, () => nothing)}`;
}

export function supportedLanguages(): ContentLanguage[] {
    if (window.SUPPORTED_LANGUAGES != null) {
        return JSON.parse(window.SUPPORTED_LANGUAGES);
    }
    return [];
}

declare global {
    interface Window {
        SUPPORTED_LANGUAGES: string | null;
    }
}
