import {AlfioEvent} from "../model/event.ts";
import {ReservationSummary} from "../model/reservation.ts";
import { formatNumber } from './i18n.ts';

/**
 * Full name of a person (customer, attendee…)
 */
export function formatFullName(person: { firstName?: string | null, lastName?: string | null, fullName?: string | null }): string {
    if (person.firstName && person.lastName) {
        return `${person.firstName} ${person.lastName}`;
    }
    return person.fullName ?? '';
}

export function reservationIdentifier(reservation: ReservationSummary, useInvoiceNumberAsId: boolean): string {
    if (useInvoiceNumberAsId) {
        return reservation.invoiceNumber?.trim() || 'N/A';
    }
    return shortReservationId(reservation.id);
}

export function shortReservationId(id: string): string {
    return id.substring(0, 8).toUpperCase();
}

export function paymentMethodIcon(paymentMethod: string): string | undefined {
    switch (paymentMethod.toUpperCase()) {
        case 'STRIPE':
        case 'MOLLIE':
        case 'SAFERPAY':
            return 'credit-card';
        case 'OFFLINE':
            return 'cash-stack';
        default:
            return undefined;
    }
}

export function formatAmount(amount: number | string | null | undefined, currencyCode: string | null | undefined): string {
    if (amount == null || currencyCode == null) {
        return '';
    }
    if (typeof amount === 'string') {
        // already formatted by the backend
        return `${currencyCode} ${amount}`;
    }
    return `${currencyCode} ${formatNumber(amount, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function localizedTitle(event: AlfioEvent): string {
    return Object.values(event.title)[0] ?? event.shortName;
}
