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

import alfio.datamapper.ConstructorAnnotationRowMapper.Column;
import alfio.model.transaction.PaymentProxy;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.time.ZonedDateTime;
import java.util.Date;

@Getter
public class FullTicketInfo {

    @Delegate
    private final Ticket ticket;

    private final TicketReservation ticketReservation;

    private final TicketCategory ticketCategory;


    public FullTicketInfo(@Column("ticket.id") int id,
                          @Column("ticket.uuid") String uuid,
                          @Column("ticket.creation") ZonedDateTime creation,
                          @Column("ticket.category_id") int categoryId,
                          @Column("ticket.status") String status,
                          @Column("ticket.event_id") int eventId,
                          @Column("ticket.original_price_cts") int originalPriceInCents,
                          @Column("ticket.paid_price_cts") int paidPriceInCents,
                          @Column("ticket.tickets_reservation_id") String ticketsReservationId,
                          @Column("ticket.full_name") String fullName,
                          @Column("ticket.email_address") String email,
                          @Column("ticket.locked_assignment") boolean lockedAssignment,
                          //
                          @Column("ticket.job_title") String jobTitle,
                          @Column("ticket.company") String company,
                          @Column("ticket.phone_number") String phoneNumber,
                          @Column("ticket.address") String address,
                          @Column("ticket.country") String country,
                          @Column("ticket.tshirt_size") String tshirtSize,
                          @Column("ticket.notes") String notes,
                          @Column("ticket.user_language") String userLanguage,
                          //
                          //
                          @Column("tickets_reservation.id") String trId,
                          @Column("tickets_reservation.validity") Date trValidity,
                          @Column("tickets_reservation.status") TicketReservation.TicketReservationStatus trStatus,
                          @Column("tickets_reservation.full_name") String trFullName,
                          @Column("tickets_reservation.email_address") String trEmail,
                          @Column("tickets_reservation.billing_address") String trBillingAddress,
                          @Column("tickets_reservation.confirmation_ts") ZonedDateTime trConfirmationTimestamp,
                          @Column("tickets_reservation.latest_reminder_ts") ZonedDateTime trLatestReminder,
                          @Column("tickets_reservation.payment_method") PaymentProxy trPaymentMethod,
                          @Column("tickets_reservation.offline_payment_reminder_sent") Boolean trReminderSent,
                          @Column("tickets_reservation.promo_code_id_fk") Integer trPromoCodeDiscountId,
                          //
                          //
                          @Column("ticket_category.id") int tcId,
                          @Column("ticket_category.inception") ZonedDateTime tcUtcInception,
                          @Column("ticket_category.expiration") ZonedDateTime tcUtcExpiration,
                          @Column("ticket_category.max_tickets") int tcMaxTickets,
                          @Column("ticket_category.name") String tcName,
                          @Column("ticket_category.description") String tcDescription,
                          @Column("ticket_category.price_cts") int tcPriceInCents,
                          @Column("ticket_category.access_restricted") boolean tcAccessRestricted,
                          @Column("ticket_category.tc_status") TicketCategory.Status tcStatus,
                          @Column("ticket_category.event_id") int tcEventId) {

        this.ticket = new Ticket(id, uuid, creation, categoryId, status, eventId, originalPriceInCents, paidPriceInCents,
                ticketsReservationId, fullName, email, lockedAssignment, jobTitle, company, phoneNumber, address, country,
                tshirtSize, notes, userLanguage);
        this.ticketReservation = new TicketReservation(trId, trValidity, trStatus, trFullName, trEmail, trBillingAddress,
                trConfirmationTimestamp, trLatestReminder, trPaymentMethod, trReminderSent, trPromoCodeDiscountId);
        this.ticketCategory = new TicketCategory(tcId, tcUtcInception, tcUtcExpiration, tcMaxTickets, tcName, tcDescription,
                tcPriceInCents, tcAccessRestricted, tcStatus, tcEventId);

    }
}
