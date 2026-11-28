import {TemplateResult} from 'lit';
import {EmailMessageStatus} from '../model/email-message.ts';
import {statusBadge, StatusBadgeConfig} from '../components/status-badge.ts';
import { t } from '../service/i18n';

const STATUSES: Record<EmailMessageStatus, StatusBadgeConfig> = {
    WAITING: { label: t('admin.email-log.status.waiting'), variant: 'neutral', icon: 'hourglass-split' },
    IN_PROCESS: { label: t('admin.email-log.status.sending'), variant: 'primary', icon: 'send' },
    RETRY: { label: t('admin.email-log.status.retrying'), variant: 'warning', icon: 'arrow-repeat' },
    SENT: { label: t('admin.email-log.sent'), variant: 'success', icon: 'check2' },
    ERROR: { label: t('admin.check-in-scan.status.error'), variant: 'danger', icon: 'exclamation-triangle' },
};

/**
 * Delivery status of an e-mail. Use with the "badges" styles.
 */
export function emailStatusBadge(status: EmailMessageStatus): TemplateResult {
    return statusBadge(status, STATUSES[status], 'email-status');
}
