import {DateTimeModification, EventStatistic} from "../model/event.ts";
import {html, nothing, TemplateResult} from "lit";
import {when} from "lit/directives/when.js";
import {ContentLanguage} from "../model/purchase-context.ts";

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

export function renderEventActions(ev: EventStatistic, hideTicketWhenExpired: boolean = false): TemplateResult {
    const total = ev.soldTickets + ev.checkedInTickets;
    return html`
        ${when((!hideTicketWhenExpired || !ev.expired) && ev.visibleForCurrentUser,
            () => html`<a class="btn btn-primary btn-xs" href="#/events/${ev.shortName}/detail"><i class="fa fa-ticket"></i> ${total} / ${ev.availableSeats}</a>`,
            () => nothing)}
        <a class="btn btn-primary btn-xs hidden-xs hidden-sm" href="#/events/${ev.shortName}/detail"><i class="fa fa-bar-chart"></i> Detail</a>
        ${when(ev.visibleForCurrentUser,
            () => html`<a class="btn btn-primary btn-xs hidden-xs hidden-sm" href="#/events/${ev.shortName}/configuration"><i class="fa fa-wrench"></i> Settings</a>`,
            () => nothing)}
    `;
}

export function supportsOfflinePayments(allowedPaymentProxies: string[]): boolean {
    return allowedPaymentProxies.includes('OFFLINE');
}

export function formatDate(dateString: string): string {
    if (!dateString) return '';
    const cleaned = dateString.replace(/\[[A-Za-z0-9\-/]+]/, '');
    const m = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})$/.exec(cleaned);
    if (!m) return dateString;
    const [, year, month, day, hours, minutes] = m;
    return `${day}.${month}.${year} ${hours}:${minutes}`;
}

export function injectFontAwesome(root: ShadowRoot): void {
    if (root.querySelector('link[href*="font-awesome"]')) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = `${(window.ALFIO_CONTEXT_PATH || '').replace(/\/+$/, '')}/resources/bower_components/components-font-awesome/css/font-awesome.min.css`;
    root.appendChild(link);
}

declare global {
    interface Window {
        SUPPORTED_LANGUAGES: string | null;
        USER_IS_OWNER: boolean;
        ALFIO_CONTEXT_PATH: string;
        BASE_URL: string;
    }
}
