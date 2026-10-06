import {html, TemplateResult} from 'lit';

export interface StatusBadgeConfig {
    label: string;
    variant: 'primary' | 'success' | 'neutral' | 'warning' | 'danger';
    icon: string;
}

/**
 * Status of a record: an icon and a short label. The raw status is exposed in "data-status" for tests.
 * Use with the "badges" styles.
 */
export function statusBadge(status: string, config: StatusBadgeConfig, className: string): TemplateResult {
    return html`
        <sl-badge class="status-badge ${className}" variant=${config.variant} data-status=${status}>
            <sl-icon name=${config.icon} aria-hidden="true"></sl-icon>
            ${config.label}
        </sl-badge>
    `;
}
