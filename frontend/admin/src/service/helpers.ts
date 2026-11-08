import {DateTimeModification} from "../model/purchase-context.ts";
import {html, nothing, TemplateResult} from "lit";
import {when} from "lit/directives/when.js";
import {ContentLanguage} from "../model/purchase-context.ts";
import {ApiResult} from "../model/reservation-detail.ts";

export function postJson(url: string, payload: any): Promise<Response> {
    return performRequest(url, 'POST', payload);
}

export function putJson(url: string, payload: any): Promise<Response> {
    return performRequest(url, 'PUT', payload);
}

export function patchJson(url: string, payload: any): Promise<Response> {
    return performRequest(url, 'PATCH', payload);
}

export function callDelete(url: string): Promise<Response> {
    return performRequest(url, 'DELETE', null);
}

function performRequest(url: string, method: 'PUT' | 'POST' | 'PATCH' | 'DELETE', payload: any): Promise<Response> {
    const xsrfName = document.querySelector('meta[name=_csrf_header]')?.getAttribute('content') as string;
    const xsrfValue = document.querySelector('meta[name=_csrf]')?.getAttribute('content') as string;

    let body: URLSearchParams | string | null = null;

    if (payload instanceof URLSearchParams) {
        body = payload;
    } else if (payload != null) {
        body = JSON.stringify(payload);
    }

    return fetch(url, {
        method,
        credentials: 'include',
        headers: {
            'Accept': 'application/json',
            'Content-Type': payload instanceof URLSearchParams ? 'application/x-www-form-urlencoded' : 'application/json',
            'X-Requested-With': 'XMLHttpRequest',
            [xsrfName]: xsrfValue
        },
        body
    })
}

export function fetchJson<T>(url: string) : Promise<T> {
    return fetch(url, {
        method: 'GET',
        credentials: 'include'
    }).then(r => r.json());
}

export async function ensureOk<T>(response: Response): Promise<T> {
    if (!response.ok) {
        throw new Error((await response.text()) || `unexpected status ${response.status}`);
    }
    return response.json();
}

/**
 * Unwraps the data of a backend Result, or rejects with the errors it describes
 */
export async function result<T>(request: Promise<Response>): Promise<T> {
    const body = await ensureOk<ApiResult<T>>(await request);
    if (!body.success) {
        throw new Error(body.errors?.map(e => e.description).join(', ') || 'An unexpected error has occurred');
    }
    return body.data;
}

/**
 * Message of a caught error, or the fallback if the error doesn't describe itself
 */
export function errorMessage(error: unknown, fallback: string): string {
    if (error instanceof Error) {
        return error.message;
    }
    return fallback;
}

/**
 * Message of a failed response: the backend sends either plain text or a JSON error body
 */
export async function responseErrorMessage(response: Response, fallback: string): Promise<string> {
    const text = await response.text();
    try {
        const parsed = JSON.parse(text);
        if (typeof parsed === 'string') {
            return parsed;
        }
        if (parsed != null && typeof parsed === 'object') {
            const errors = (parsed as { errors?: unknown }).errors;
            if (Array.isArray(errors) && errors.length > 0) {
                return errors.map((e) => (e as { description?: string })?.description ?? String(e)).join(', ');
            }
            const message = (parsed as { message?: unknown }).message;
            if (typeof message === 'string') {
                return message;
            }
        }
    } catch {
        // not JSON: the body itself is the message
    }
    return text || fallback;
}

/**
 * Whether a file matches an accept attribute (mime types, "type/*" or extensions like ".png")
 */
export function matchesAccept(file: File, accept: string): boolean {
    const acceptList = accept.split(',').map(s => s.trim().toLowerCase());
    const fileType = file.type.toLowerCase();
    const fileName = file.name.toLowerCase();

    for (const pattern of acceptList) {
        if (pattern === fileType) return true;
        if (pattern.endsWith('/*') && fileType.startsWith(pattern.slice(0, -1))) return true;
        if (pattern.startsWith('.') && fileName.endsWith(pattern)) return true;
    }
    return false;
}

/**
 * Human-readable size with binary units (1 MB = 1024 * 1024 bytes), unlike sl-format-bytes,
 * which is decimal and would show 1048576 bytes as "1.05 MB"
 */
export function formatBytesBinary(bytes: number): string {
    const units = ['B', 'KB', 'MB', 'GB'];
    let value = bytes;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
        value /= 1024;
        unit++;
    }
    const rounded = Math.round(value * 10) / 10;
    return `${rounded} ${units[unit]}`;
}

export function readAsDataUrl(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(file);
    });
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

export function notifyChange(event: InputEvent,
                             field: { handleChange: (m: any) => void },
                             // helps with boolean / number values
                             valueTransformer: (v: string) => any = (s) => s): void {
    const target = event.currentTarget as HTMLInputElement | null;
    if (target != null) {
        field.handleChange(valueTransformer(target.value));
    }
}

export function escapeHtml(message: string): string {
    const div = document.createElement('div');
    div.textContent = message;
    return div.innerHTML;
}

export function asString(value: any): string | null {
    if (value != null) {
        return `${value}`;
    }
    return value;
}

export function asNumber(value?: string): number | null {
    if (value != null) {
        return Number.parseInt(value, 10);
    }
    return value ?? null;
}

/**
 * Reads the query parameters of the current AngularJS route (i.e. after the "?" in the location hash)
 */
export function readRouteParams(): URLSearchParams {
    return <URLSearchParams>new URLSearchParams(window.location.hash.split('?')[1] ?? '');
}

/**
 * Replaces the query parameters of the current AngularJS route without triggering a navigation.
 * The hosting state must declare "reloadOnSearch: false"
 */
export function replaceRouteParams(params: URLSearchParams): void {
    const route = window.location.hash.split('?')[0];
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}${route}?${params}`);
}

export function toPageNumber(value: string | null): number {
    const page = Number(value);
    if (Number.isInteger(page) && page > 0) {
        return page;
    }
    return 1;
}

/**
 * "SOME_CONSTANT" -> "Some constant"
 */
export function humanize(value: string): string {
    const words = value.toLowerCase().replaceAll('_', ' ');
    return words.charAt(0).toUpperCase() + words.substring(1);
}

/**
 * Lets the browser save the CSV content as a UTF-8 file. The byte order mark makes spreadsheets (e.g. Excel) detect the encoding.
 */
export function downloadCsv(fileName: string, csv: string): void {
    const url = URL.createObjectURL(new Blob(['\uFEFF', csv], {type: 'text/csv;charset=utf-8;'}));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/**
 * Lets the browser save a binary response (e.g. a rendered PDF) as a file.
 */
export function downloadFile(fileName: string, blob: Blob): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function supportsOfflinePayments(allowedPaymentProxies: string[]): boolean {
    return allowedPaymentProxies.includes('OFFLINE');
}

declare global {
    interface Window {
        SUPPORTED_LANGUAGES: string | null;
        USER_IS_OWNER: boolean;
        isAdmin: boolean; // backwards compatibility
        IS_ADMIN: boolean;
        ALFIO_CONTEXT_PATH: string;
        BASE_URL: string;
    }
}
