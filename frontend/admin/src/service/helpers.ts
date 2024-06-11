import {ContentLanguage, DateTimeModification} from "../model/event.ts";
import {html, nothing, TemplateResult} from "lit";
import {when} from "lit/directives/when.js";
import {FieldApi} from "@tanstack/lit-form";

export function postJson(url: string, payload: any): Promise<Response> {
    return performRequest(url, 'POST', payload);
}

export function putJson(url: string, payload: any): Promise<Response> {
    return performRequest(url, 'PUT', payload);
}

export function callDelete(url: string): Promise<Response> {
    return performRequest(url, 'DELETE', null);
}

function performRequest(url: string, method: 'PUT' | 'POST' | 'DELETE', payload: any): Promise<Response> {
    const xsrfName = document.querySelector('meta[name=_csrf_header]')?.getAttribute('content') as string;
    const xsrfValue = document.querySelector('meta[name=_csrf]')?.getAttribute('content') as string;
    return fetch(url, {
        method, credentials: 'include', headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest',
            [xsrfName]: xsrfValue
        },
        body: payload != null ? JSON.stringify(payload) : null
    })
}

export function fetchJson<T>(url: string) : Promise<T> {
    return fetch(url, {
        method: 'GET',
        credentials: 'include'
    }).then(r => r.json());
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

export function toDateTimeModification(isoString: string): DateTimeModification {
    return {
        date: isoString.substring(0, 10),
        time: isoString.substring(11, 16),
    };
}

export function extractDateTime(isoString?: string): string {
    if (isoString != null) {
        return isoString.substring(0, 16);
    }
    return "";
}

export function notifyChange(event: InputEvent, field: FieldApi<any, any>): void {
    const target = event.currentTarget as HTMLInputElement | null;
    if (target != null) {
        field.handleChange(target.value);
    }
}

declare global {
    interface Window {
        SUPPORTED_LANGUAGES: string | null;
    }
}
