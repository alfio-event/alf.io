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

/**
 * Events that can trigger an extension script.
 * <p>
 * Each event declares how it is dispatched: scripts run only for the events matching their "async" metadata flag
 * (see {@code ExtensionRepository.findActive}). The admin script editor reads this declaration at build time
 * to suggest the events a script can handle, so keep it in sync with the calls in {@code ExtensionManager},
 * which verify it at runtime.
 */
public enum ExtensionEvent {
    RESERVATION_CONFIRMED(Dispatch.ASYNC),
    RESERVATION_CANCELLED(Dispatch.SYNC),
    RESERVATION_CREDIT_NOTE_ISSUED(Dispatch.SYNC),
    TICKET_CANCELLED(Dispatch.SYNC),
    RESERVATION_EXPIRED(Dispatch.SYNC),
    TICKET_ASSIGNED(Dispatch.ASYNC),
    TICKET_ASSIGNED_GENERATE_METADATA(Dispatch.SYNC),
    WAITING_QUEUE_SUBSCRIBED(Dispatch.ASYNC),
    INVOICE_GENERATION(Dispatch.SYNC),
    CREDIT_NOTE_GENERATION(Dispatch.SYNC),
    CREDIT_NOTE_GENERATED(Dispatch.ASYNC),
    TAX_ID_NUMBER_VALIDATION(Dispatch.SYNC),
    CUSTOM_TAX_POLICY_APPLICATION(Dispatch.SYNC),
    RESERVATION_VALIDATION(Dispatch.SYNC),
    TICKET_UPDATE_VALIDATION(Dispatch.SYNC),
    EVENT_METADATA_UPDATE(Dispatch.SYNC),
    STUCK_RESERVATIONS(Dispatch.ASYNC),
    OFFLINE_RESERVATIONS_WILL_EXPIRE(Dispatch.ASYNC),
    EVENT_VALIDATE_CREATION(Dispatch.SYNC),
    EVENT_CREATED(Dispatch.ALL),
    EVENT_HEADER_UPDATED(Dispatch.ASYNC),
    EVENT_STATUS_CHANGE(Dispatch.ALL),
    EVENT_VALIDATE_SEATS_PRICES_UPDATE(Dispatch.SYNC),
    TICKET_CHECKED_IN(Dispatch.ASYNC),
    TICKET_REVERT_CHECKED_IN(Dispatch.ASYNC),
    PDF_GENERATION(Dispatch.SYNC),
    OAUTH2_STATE_GENERATION(Dispatch.SYNC),
    USER_ADDITIONAL_INFO_FILTER(Dispatch.SYNC),
    CONFIRMATION_MAIL_CUSTOM_TEXT(Dispatch.SYNC),
    TICKET_MAIL_CUSTOM_TEXT(Dispatch.SYNC),
    REFUND_ISSUED(Dispatch.ASYNC),
    DYNAMIC_DISCOUNT_APPLICATION(Dispatch.SYNC),
    SUBSCRIPTION_ASSIGNED_GENERATE_METADATA(Dispatch.SYNC),
    ONLINE_CHECK_IN_REDIRECT(Dispatch.SYNC),
    CUSTOM_ONLINE_JOIN_URL(Dispatch.SYNC),
    PUBLIC_USER_SIGN_UP(Dispatch.ASYNC),
    PUBLIC_USER_DELETE(Dispatch.SYNC),
    PUBLIC_USER_PROFILE_VALIDATION(Dispatch.SYNC);

    private final Dispatch dispatch;

    ExtensionEvent(Dispatch dispatch) {
        this.dispatch = dispatch;
    }

    public boolean isDispatchedSynchronously() {
        return dispatch != Dispatch.ASYNC;
    }

    public boolean isDispatchedAsynchronously() {
        return dispatch != Dispatch.SYNC;
    }

    public enum Dispatch {
        // scripts with "async: false" handle the event, and can return a result
        SYNC,
        // scripts with "async: true" handle the event, without blocking the caller
        ASYNC,
        // fired twice: first to the synchronous scripts, then to the asynchronous ones
        ALL
    }
}
