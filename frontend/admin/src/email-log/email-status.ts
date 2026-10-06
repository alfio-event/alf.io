import {TemplateResult} from 'lit';
import {EmailMessageStatus} from '../model/email-message.ts';
import {statusBadge, StatusBadgeConfig} from '../components/status-badge.ts';

const STATUSES: Record<EmailMessageStatus, StatusBadgeConfig> = {
    WAITING: { label: 'Waiting', variant: 'neutral', icon: 'hourglass-split' },
    IN_PROCESS: { label: 'Sending', variant: 'primary', icon: 'send' },
    RETRY: { label: 'Retrying', variant: 'warning', icon: 'arrow-repeat' },
    SENT: { label: 'Sent', variant: 'success', icon: 'check2' },
    ERROR: { label: 'Error', variant: 'danger', icon: 'exclamation-triangle' },
};

/**
 * Delivery status of an e-mail. Use with the "badges" styles.
 */
export function emailStatusBadge(status: EmailMessageStatus): TemplateResult {
    return statusBadge(status, STATUSES[status], 'email-status');
}
