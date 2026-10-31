import {html, TemplateResult} from 'lit';
import {when} from 'lit/directives/when.js';
import {ExtensionEvent} from '../model/extension.ts';
import type {Organization} from '../model/organization.ts';

export interface ExtensionScopeIds {
    organizationId: number | null;
    eventId: number | null;
}

/**
 * Reads the scope stored by the backend: "-" (system), "-<organizationId>" or "-<organizationId>-<eventId>"
 */
export function parseScope(path: string): ExtensionScopeIds {
    const segments = path.split('-').filter((s) => s.length > 0);
    return {
        organizationId: parseSegment(segments[0]),
        eventId: parseSegment(segments[1]),
    };
}

/**
 * The scope displayed as "System > organization > event". Use with the "textColors" styles.
 */
export function renderScope(path: string,
                            organizations: ReadonlyArray<Organization>,
                            events: ReadonlyArray<ExtensionEvent>): TemplateResult {
    const ids = parseScope(path);
    const organization = organizations.find((o) => o.id === ids.organizationId);
    const event = events.find((e) => e.id === ids.eventId);
    return html`
        <span class="text-muted">System</span>
        ${when(organization !== undefined, () => html`<span class="text-muted"> &gt; ${organization!.name}</span>`)}
        ${when(event !== undefined, () => html`<span class="text-muted"> &gt; ${event!.displayName}</span>`)}
    `;
}

function parseSegment(segment: string | undefined): number | null {
    if (segment !== undefined) {
        return Number.parseInt(segment, 10);
    }
    return null;
}
