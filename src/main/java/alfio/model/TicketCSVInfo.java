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
package alfio.model;

import alfio.model.transaction.PaymentProxy;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.time.ZonedDateTime;
import java.util.Date;

@Getter
public class TicketCSVInfo {

    @Delegate
    private final Ticket ticket;

    private final TicketReservation ticketReservation;

    public TicketCSVInfo(@ConstructorAnnotationRowMapper.Column("t_id") int id,
                         @ConstructorAnnotationRowMapper.Column("t_uuid") String uuid,
                         @ConstructorAnnotationRowMapper.Column("t_creation") ZonedDateTime creation,
                         @ConstructorAnnotationRowMapper.Column("t_category_id") int categoryId,
                         @ConstructorAnnotationRowMapper.Column("t_status") String status,
                         @ConstructorAnnotationRowMapper.Column("t_event_id") int eventId,
                         @ConstructorAnnotationRowMapper.Column("t_src_price_cts") int ticketSrcPriceCts,
                         @ConstructorAnnotationRowMapper.Column("t_final_price_cts") int ticketFinalPriceCts,
                         @ConstructorAnnotationRowMapper.Column("t_vat_cts") int ticketVatCts,
                         @ConstructorAnnotationRowMapper.Column("t_discount_cts") int ticketDiscountCts,
                         @ConstructorAnnotationRowMapper.Column("t_tickets_reservation_id") String ticketsReservationId,
                         @ConstructorAnnotationRowMapper.Column("t_full_name") String fullName,
                         @ConstructorAnnotationRowMapper.Column("t_first_name") String firstName,
                         @ConstructorAnnotationRowMapper.Column("t_last_name") String lastName,
                         @ConstructorAnnotationRowMapper.Column("t_email_address") String email,
                         @ConstructorAnnotationRowMapper.Column("t_locked_assignment") boolean lockedAssignment,
                         @ConstructorAnnotationRowMapper.Column("t_user_language") String userLanguage,
                         //
                         @ConstructorAnnotationRowMapper.Column("tr_id") String trId,
                         @ConstructorAnnotationRowMapper.Column("tr_validity") Date trValidity,
                         @ConstructorAnnotationRowMapper.Column("tr_status") TicketReservation.TicketReservationStatus trStatus,
                         @ConstructorAnnotationRowMapper.Column("tr_full_name") String trFullName,
                         @ConstructorAnnotationRowMapper.Column("tr_first_name") String trFirstName,
                         @ConstructorAnnotationRowMapper.Column("tr_last_name") String trLastName,
                         @ConstructorAnnotationRowMapper.Column("tr_email_address") String trEmail,
                         @ConstructorAnnotationRowMapper.Column("tr_billing_address") String trBillingAddress,
                         @ConstructorAnnotationRowMapper.Column("tr_confirmation_ts") ZonedDateTime trConfirmationTimestamp,
                         @ConstructorAnnotationRowMapper.Column("tr_latest_reminder_ts") ZonedDateTime trLatestReminder,
                         @ConstructorAnnotationRowMapper.Column("tr_payment_method") PaymentProxy trPaymentMethod,
                         @ConstructorAnnotationRowMapper.Column("tr_offline_payment_reminder_sent") Boolean trReminderSent,
                         @ConstructorAnnotationRowMapper.Column("tr_promo_code_id_fk") Integer trPromoCodeDiscountId,
                         @ConstructorAnnotationRowMapper.Column("tr_automatic") boolean trAutomatic,
                         @ConstructorAnnotationRowMapper.Column("tr_user_language") String resUserLanguage,
                         @ConstructorAnnotationRowMapper.Column("tr_direct_assignment") boolean directAssignment) {

        this.ticket = new Ticket(id, uuid, creation, categoryId, status, eventId, ticketsReservationId, fullName, firstName, lastName, email,
            lockedAssignment, userLanguage, ticketSrcPriceCts, ticketFinalPriceCts, ticketVatCts, ticketDiscountCts);
        this.ticketReservation = new TicketReservation(trId, trValidity, trStatus, trFullName, trFirstName, trLastName, trEmail, trBillingAddress,
            trConfirmationTimestamp, trLatestReminder, trPaymentMethod, trReminderSent, trPromoCodeDiscountId, trAutomatic, resUserLanguage, directAssignment);
    }
}
