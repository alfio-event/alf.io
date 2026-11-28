import {TemplateResult} from 'lit';
import {ReservationStatus} from '../model/reservation.ts';
import {statusBadge, StatusBadgeConfig} from '../components/status-badge.ts';
import { t } from '../service/i18n';

const STATUSES: Record<ReservationStatus, StatusBadgeConfig> = {
    PENDING: { label: t('admin.reservation-status.pending'), variant: 'neutral', icon: 'cart' },
    IN_PAYMENT: { label: t('admin.reservation-status.in-payment'), variant: 'primary', icon: 'hourglass-split' },
    EXTERNAL_PROCESSING_PAYMENT: { label: t('admin.reservation-status.processing-payment'), variant: 'primary', icon: 'hourglass-split' },
    WAITING_EXTERNAL_CONFIRMATION: { label: t('admin.reservation-status.waiting-confirmation'), variant: 'primary', icon: 'hourglass-split' },
    OFFLINE_PAYMENT: { label: t('admin.reservation-status.payment-pending'), variant: 'warning', icon: 'cash-coin' },
    CUSTOM_OFFLINE_PAYMENT: { label: t('admin.reservation-status.payment-pending'), variant: 'warning', icon: 'cash-coin' },
    DEFERRED_OFFLINE_PAYMENT: { label: t('admin.reservation-status.deferred-payment'), variant: 'warning', icon: 'cash-coin' },
    COMPLETE: { label: t('admin.reservation-status.complete'), variant: 'success', icon: 'check2' },
    STUCK: { label: t('admin.reservation-status.stuck'), variant: 'danger', icon: 'exclamation-triangle' },
    CREDIT_NOTE_ISSUED: { label: t('admin.reservation-status.credit-note-issued'), variant: 'neutral', icon: 'arrow-counterclockwise' },
    CANCELLED: { label: t('admin.ticket-status.cancelled'), variant: 'neutral', icon: 'x-circle' },
};

/**
 * Status of a reservation. Use with the "badges" styles.
 */
export function reservationStatusBadge(status: ReservationStatus): TemplateResult {
    return statusBadge(status, STATUSES[status], 'reservation-status');
}

/**
 * Statuses of reservations waiting for an offline payment (bank transfer or custom payment method)
 */
export function isOfflinePayment(status: ReservationStatus): boolean {
    return status === 'OFFLINE_PAYMENT' || status === 'CUSTOM_OFFLINE_PAYMENT';
}

/**
 * Statuses of reservations that haven't been paid yet
 */
export function isNotPaid(status: ReservationStatus): boolean {
    return status === 'PENDING' || isOfflinePayment(status);
}
