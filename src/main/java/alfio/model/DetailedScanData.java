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

import java.time.ZonedDateTime;

@Getter
public class DetailedScanData {

    private final Ticket ticket;
    private final SponsorScan sponsorScan;

    public DetailedScanData(@Column("t_id") int ticketId,
                            @Column("t_uuid") String ticketUuid,
                            @Column("t_creation") ZonedDateTime ticketCreation,
                            @Column("t_category_id") int ticketCategoryId,
                            @Column("t_status") String ticketStatus,
                            @Column("t_event_id") int ticketEventId,

                            @Column("t_src_price_cts") int ticketSrcPriceCts,
                            @Column("t_final_price_cts") int ticketFinalPriceCts,
                            @Column("t_vat_cts") int ticketVatCts,
                            @Column("t_discount_cts") int ticketDiscountCts,

                            @Column("t_tickets_reservation_id") String ticketsReservationId,
                            @Column("t_full_name") String ticketFullName,
                            @Column("t_email_address") String ticketEmail,
                            @Column("t_locked_assignment") boolean ticketLockedAssignment,
                            @Column("t_user_language") String ticketUserLanguage,
                            //
                            @Column("s_user_id") int scanUserId,
                            @Column("s_creation") ZonedDateTime scanTimestamp,
                            @Column("s_event_id") int scanEventId,
                            @Column("s_ticket_id") int scanTicketId) {
        this.ticket = new Ticket(ticketId, ticketUuid, ticketCreation, ticketCategoryId, ticketStatus, ticketEventId, ticketsReservationId, ticketFullName, ticketEmail, ticketLockedAssignment, ticketUserLanguage, ticketSrcPriceCts, ticketFinalPriceCts, ticketVatCts, ticketDiscountCts);
        this.sponsorScan = new SponsorScan(scanUserId, scanTimestamp, scanEventId, scanTicketId);
    }
}
