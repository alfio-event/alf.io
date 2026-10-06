import {TemplateResult} from 'lit';
import {ReservationStatus} from '../model/reservation.ts';
import {statusBadge, StatusBadgeConfig} from '../components/status-badge.ts';

const STATUSES: Record<ReservationStatus, StatusBadgeConfig> = {
    PENDING: { label: 'In process', variant: 'neutral', icon: 'cart' },
    IN_PAYMENT: { label: 'In payment', variant: 'primary', icon: 'hourglass-split' },
    EXTERNAL_PROCESSING_PAYMENT: { label: 'Processing payment', variant: 'primary', icon: 'hourglass-split' },
    WAITING_EXTERNAL_CONFIRMATION: { label: 'Waiting for confirmation', variant: 'primary', icon: 'hourglass-split' },
    OFFLINE_PAYMENT: { label: 'Payment pending', variant: 'warning', icon: 'cash-coin' },
    CUSTOM_OFFLINE_PAYMENT: { label: 'Payment pending', variant: 'warning', icon: 'cash-coin' },
    DEFERRED_OFFLINE_PAYMENT: { label: 'Deferred payment', variant: 'warning', icon: 'cash-coin' },
    COMPLETE: { label: 'Complete', variant: 'success', icon: 'check2' },
    STUCK: { label: 'Stuck', variant: 'danger', icon: 'exclamation-triangle' },
    CREDIT_NOTE_ISSUED: { label: 'Credit note issued', variant: 'neutral', icon: 'arrow-counterclockwise' },
    CANCELLED: { label: 'Cancelled', variant: 'neutral', icon: 'x-circle' },
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
