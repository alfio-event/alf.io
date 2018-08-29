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

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.time.ZonedDateTime;

@Getter
public class TicketInfo {

    @Delegate
    private final Ticket ticket;

    private final TicketCategory ticketCategory;


    public TicketInfo(@Column("t_id") int id,
                      @Column("t_uuid") String uuid,
                      @Column("t_creation") ZonedDateTime creation,
                      @Column("t_category_id") int categoryId,
                      @Column("t_status") String status,
                      @Column("t_event_id") int eventId,
                      @Column("t_src_price_cts") int ticketSrcPriceCts,
                      @Column("t_final_price_cts") int ticketFinalPriceCts,
                      @Column("t_vat_cts") int ticketVatCts,
                      @Column("t_discount_cts") int ticketDiscountCts,
                      @Column("t_tickets_reservation_id") String ticketsReservationId,
                      @Column("t_full_name") String fullName,
                      @Column("t_first_name") String firstName,
                      @Column("t_last_name") String lastName,
                      @Column("t_email_address") String email,
                      @Column("t_locked_assignment") boolean lockedAssignment,
                      @Column("t_user_language") String userLanguage,
                      @Column("t_ext_reference") String extReference,
                      //
                      @Column("tc_id") int tcId,
                      @Column("tc_inception") ZonedDateTime tcUtcInception,
                      @Column("tc_expiration") ZonedDateTime tcUtcExpiration,
                      @Column("tc_max_tickets") int tcMaxTickets,
                      @Column("tc_name") String tcName,
                      @Column("tc_src_price_cts") int tcSrcPriceCts,
                      @Column("tc_access_restricted") boolean tcAccessRestricted,
                      @Column("tc_tc_status") TicketCategory.Status tcStatus,
                      @Column("tc_event_id") int tcEventId,
                      @Column("tc_bounded") boolean bounded,
                      @Column("tc_category_code") String code,
                      @Column("tc_valid_checkin_from") ZonedDateTime validCheckInFrom,
                      @Column("tc_valid_checkin_to") ZonedDateTime validCheckInTo,
                      @Column("tc_ticket_validity_start") ZonedDateTime ticketValidityStart,
                      @Column("tc_ticket_validity_end") ZonedDateTime ticketValidityEnd
                          ) {

        this.ticket = new Ticket(id, uuid, creation, categoryId, status, eventId, ticketsReservationId, fullName, firstName, lastName, email,
            lockedAssignment, userLanguage, ticketSrcPriceCts, ticketFinalPriceCts, ticketVatCts, ticketDiscountCts, extReference);
        this.ticketCategory = new TicketCategory(tcId, tcUtcInception, tcUtcExpiration, tcMaxTickets, tcName,
                tcAccessRestricted, tcStatus, tcEventId, bounded, tcSrcPriceCts, code, validCheckInFrom, validCheckInTo,
                ticketValidityStart, ticketValidityEnd);

    }
}
