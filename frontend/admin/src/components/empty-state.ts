import {html, TemplateResult} from 'lit';

/**
 * Placeholder for lists without items. Use with the "modernLayout" styles.
 */
export function emptyState(message: string | TemplateResult): TemplateResult {
    return html`
        <div class="empty-state">
            <sl-icon name="inbox"></sl-icon>
            <span>${message}</span>
        </div>
    `;
}
