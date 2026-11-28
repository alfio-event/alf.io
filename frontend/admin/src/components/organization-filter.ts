import {html, nothing, TemplateResult} from 'lit';
import {repeat} from 'lit/directives/repeat.js';
import type {SlSelect} from '@shoelace-style/shoelace';
import type {Organization} from '../model/organization.ts';
import { t } from '../service/i18n';

/**
 * The organization whose items a list shows, as a filter of its ".filter-toolbar".
 * Renders nothing when the user belongs to a single organization. Use with the "modernLayout" styles.
 */
export function organizationFilter(organizations: readonly Organization[] | undefined,
                                   selectedId: number,
                                   onChange: (organizationId: number) => void): TemplateResult | typeof nothing {
    if (organizations == null || organizations.length < 2) {
        return nothing;
    }
    return html`
        <sl-select class="filter-select organization-filter" label=${t('admin.common.organization')} hoist
                   .value=${String(selectedId)}
                   @sl-change=${(event: Event) => onChange(Number((event.target as SlSelect).value))}>
            ${repeat(organizations, (org) => org.id, (org) => html`
                <sl-option value=${String(org.id)}>${org.name}</sl-option>
            `)}
        </sl-select>
    `;
}
