/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager.support.extension;

public enum ExtensionEvent {
    RESERVATION_CONFIRMED,
    RESERVATION_CANCELLED,
    RESERVATION_CREDIT_NOTE_ISSUED,
    TICKET_CANCELLED,
    RESERVATION_EXPIRED,
    TICKET_ASSIGNED,
    WAITING_QUEUE_SUBSCRIBED,
    INVOICE_GENERATION,
    CREDIT_NOTE_GENERATION,
    CREDIT_NOTE_GENERATED,
    TAX_ID_NUMBER_VALIDATION,
    RESERVATION_VALIDATION,
    EVENT_METADATA_UPDATE,
    //
    STUCK_RESERVATIONS,
    OFFLINE_RESERVATIONS_WILL_EXPIRE,
    EVENT_CREATED,
    EVENT_STATUS_CHANGE,
    TICKET_CHECKED_IN,
    TICKET_REVERT_CHECKED_IN,
    PDF_GENERATION,
    OAUTH2_STATE_GENERATION,

    CONFIRMATION_MAIL_CUSTOM_TEXT,
    TICKET_MAIL_CUSTOM_TEXT,
    REFUND_ISSUED,

    DYNAMIC_DISCOUNT_APPLICATION,

    ONLINE_CHECK_IN_REDIRECT
}
