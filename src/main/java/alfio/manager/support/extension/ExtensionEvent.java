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
    TICKET_ASSIGNED_GENERATE_METADATA,
    WAITING_QUEUE_SUBSCRIBED,
    INVOICE_GENERATION,
    CREDIT_NOTE_GENERATION,
    CREDIT_NOTE_GENERATED,
    TAX_ID_NUMBER_VALIDATION,
    CUSTOM_TAX_POLICY_APPLICATION,
    RESERVATION_VALIDATION,
    TICKET_UPDATE_VALIDATION,
    EVENT_METADATA_UPDATE,
    //
    STUCK_RESERVATIONS,
    OFFLINE_RESERVATIONS_WILL_EXPIRE,
    EVENT_VALIDATE_CREATION,
    EVENT_CREATED,
    EVENT_HEADER_UPDATED,
    EVENT_STATUS_CHANGE,
    EVENT_VALIDATE_SEATS_PRICES_UPDATE,
    TICKET_CHECKED_IN,
    TICKET_REVERT_CHECKED_IN,
    PDF_GENERATION,
    OAUTH2_STATE_GENERATION,
    USER_ADDITIONAL_INFO_FILTER,

    CONFIRMATION_MAIL_CUSTOM_TEXT,
    TICKET_MAIL_CUSTOM_TEXT,
    REFUND_ISSUED,

    DYNAMIC_DISCOUNT_APPLICATION,

    SUBSCRIPTION_ASSIGNED_GENERATE_METADATA,

    ONLINE_CHECK_IN_REDIRECT,
    CUSTOM_ONLINE_JOIN_URL,

    PUBLIC_USER_SIGN_UP,
    PUBLIC_USER_DELETE,
    PUBLIC_USER_PROFILE_VALIDATION
}
