import { humanize } from '../service/helpers';
import { i18nKey, t } from '../service/i18n';
import { TicketStatus } from './event';

const TICKET_STATUS_LABELS: Record<TicketStatus, string> = {
    FREE: i18nKey('admin.ticket-status.free'),
    PENDING: i18nKey('admin.ticket-status.pending'),
    TO_BE_PAID: i18nKey('admin.ticket-status.to-be-paid'),
    ACQUIRED: i18nKey('admin.ticket-status.acquired'),
    CANCELLED: i18nKey('admin.ticket-status.cancelled'),
    CHECKED_IN: i18nKey('admin.ticket-status.checked-in'),
    EXPIRED: i18nKey('admin.ticket-status.expired'),
    INVALIDATED: i18nKey('admin.ticket-status.invalidated'),
    RELEASED: i18nKey('admin.ticket-status.released'),
    PRE_RESERVED: i18nKey('admin.ticket-status.pre-reserved'),
};

/**
 * The label of a ticket status. A status added by a newer backend falls back to its humanized name.
 */
export function ticketStatusLabel(status: string): string {
    const key = TICKET_STATUS_LABELS[status as TicketStatus];
    if (key == null) {
        return humanize(status);
    }
    return t(key);
}
