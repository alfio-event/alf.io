import {html, TemplateResult} from 'lit';
import {repeat} from 'lit/directives/repeat.js';

export interface DetailEntry {
    label: string;
    value: unknown;
    monospace?: boolean;
}

/**
 * Read-only "label: value" pairs. Entries that are null or false are skipped, so that optional values can be
 * written inline (e.g. `invoiceNumber != null && { label: 'Invoice', value: invoiceNumber }`).
 * Use with the "detailList" styles.
 */
export function detailList(entries: (DetailEntry | null | false | undefined)[]): TemplateResult {
    const visible = entries.filter((entry): entry is DetailEntry => !!entry);
    return html`
        <dl class="detail-list">
            ${repeat(visible, (entry) => entry.label, (entry) => html`
                <dt>${entry.label}</dt>
                <dd class=${entry.monospace ? 'monospace' : ''}>${entry.value}</dd>
            `)}
        </dl>
    `;
}
