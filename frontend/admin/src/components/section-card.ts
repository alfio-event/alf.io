import {html, nothing, TemplateResult} from 'lit';
import {when} from 'lit/directives/when.js';

export interface SectionCardOptions {
    icon: string;
    title: string;
    // e.g. a counter, shown next to the title
    badge?: TemplateResult;
    // buttons shown on the right side of the header
    actions?: TemplateResult;
    // additional classes of the card, e.g. to change its color
    className?: string;
}

/**
 * A titled block of a page: header (icon, title, optional badge and actions) and body. Use with the "modernLayout" styles.
 */
export function sectionCard(options: SectionCardOptions, body: TemplateResult): TemplateResult {
    return html`
        <section class="section-card ${options.className ?? ''}">
            <div class="section-header">
                <sl-icon name=${options.icon} aria-hidden="true"></sl-icon>
                <h3>${options.title}</h3>
                ${options.badge ?? nothing}
                ${when(options.actions, () => html`<div class="section-actions">${options.actions}</div>`)}
            </div>
            <div class="section-body">${body}</div>
        </section>
    `;
}
