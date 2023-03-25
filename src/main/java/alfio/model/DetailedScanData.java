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

import alfio.model.support.Array;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

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
                            @Column("t_vat_status") PriceContainer.VatStatus ticketVatStatus,

                            @Column("t_tickets_reservation_id") String ticketsReservationId,
                            @Column("t_full_name") String ticketFullName,
                            @Column("t_first_name") String ticketFirstName,
                            @Column("t_last_name") String ticketLastName,
                            @Column("t_email_address") String ticketEmail,
                            @Column("t_locked_assignment") boolean ticketLockedAssignment,
                            @Column("t_user_language") String ticketUserLanguage,
                            @Column("t_ext_reference") String extReference,
                            @Column("t_currency_code") String currencyCode,
                            @Column("t_tags") @Array List<String> ticketTags,
                            @Column("t_subscription_id") UUID ticketSubscriptionId,
                            //
                            @Column("s_user_id") int scanUserId,
                            @Column("s_creation") ZonedDateTime scanTimestamp,
                            @Column("s_event_id") int scanEventId,
                            @Column("s_ticket_id") int scanTicketId,
                            @Column("s_notes") String notes,
                            @Column("s_lead_status") SponsorScan.LeadStatus leadStatus,
                            @Column("s_operator") String operator) {
        this.ticket = new Ticket(ticketId, ticketUuid, ticketCreation, ticketCategoryId,
            ticketStatus, ticketEventId, ticketsReservationId, ticketFullName, ticketFirstName,
            ticketLastName, ticketEmail, ticketLockedAssignment, ticketUserLanguage, ticketSrcPriceCts,
            ticketFinalPriceCts, ticketVatCts, ticketDiscountCts, extReference, currencyCode, ticketTags,
            ticketSubscriptionId, ticketVatStatus);
        this.sponsorScan = new SponsorScan(scanUserId, scanTimestamp, scanEventId, scanTicketId, notes, leadStatus, operator);
    }
}
