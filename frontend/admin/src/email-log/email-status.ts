import {css, html, TemplateResult} from 'lit';
import {EmailMessageStatus} from '../model/email-message.ts';

const STATUSES: Record<EmailMessageStatus, { label: string, variant: string, icon: string }> = {
    WAITING: { label: 'Waiting', variant: 'neutral', icon: 'hourglass-split' },
    IN_PROCESS: { label: 'Sending', variant: 'primary', icon: 'send' },
    RETRY: { label: 'Retrying', variant: 'warning', icon: 'arrow-repeat' },
    SENT: { label: 'Sent', variant: 'success', icon: 'check2' },
    ERROR: { label: 'Error', variant: 'danger', icon: 'exclamation-triangle' },
};

/* styles of emailStatusBadge(). Use with the "badges" styles */
export const emailStatus = css`
    .email-status sl-icon {
        margin-inline-end: var(--sl-spacing-3x-small);
    }
`;

/**
 * Delivery status of an e-mail. Use with the "badges" and "emailStatus" styles.
 */
export function emailStatusBadge(status: EmailMessageStatus): TemplateResult {
    const config = STATUSES[status];
    return html`
        <sl-badge class="email-status" variant=${config.variant} data-status=${status}>
            <sl-icon name=${config.icon} aria-hidden="true"></sl-icon>
            ${config.label}
        </sl-badge>
    `;
}
