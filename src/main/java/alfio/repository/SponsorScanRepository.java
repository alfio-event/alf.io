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
package alfio.repository;

import alfio.model.DetailedScanData;
import alfio.model.SponsorScan;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@QueryRepository
public interface SponsorScanRepository {

    ZonedDateTime DEFAULT_TIMESTAMP = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);

    @Query("select creation from sponsor_scan where user_id = :userId and event_id = :eventId and ticket_id = :ticketId and operator = :operator")
    Optional<ZonedDateTime> getRegistrationTimestamp(@Bind("userId") int userId, @Bind("eventId") int eventId, @Bind("ticketId") int ticketId, @Bind("operator") String operator);

    @Query("insert into sponsor_scan (user_id, creation, event_id, ticket_id, notes, lead_status, operator) values(:userId, :creation, :eventId, :ticketId, :notes, :leadStatus, :operator)")
    int insert(@Bind("userId") int userId,
               @Bind("creation") ZonedDateTime creation,
               @Bind("eventId") int eventId,
               @Bind("ticketId") int ticketId,
               @Bind("notes") String notes,
               @Bind("leadStatus") SponsorScan.LeadStatus leadStatus,
               @Bind("operator") String operator);

    @Query("update sponsor_scan set notes = :notes, lead_status = :leadStatus where user_id = :userId and event_id = :eventId and ticket_id = :ticketId and operator = :operator")
    int updateNotesAndLeadStatus(@Bind("userId") int userId,
                                 @Bind("eventId") int eventId,
                                 @Bind("ticketId") int ticketId,
                                 @Bind("notes") String notes,
                                 @Bind("leadStatus") SponsorScan.LeadStatus leadStatus,
                                 @Bind("operator") String operator);

    @Query("select t.id t_id, t.uuid t_uuid, t.creation t_creation, t.category_id t_category_id, t.status t_status, t.event_id t_event_id," +
        " t.src_price_cts t_src_price_cts, t.final_price_cts t_final_price_cts, t.vat_cts t_vat_cts, t.discount_cts t_discount_cts, t.tickets_reservation_id t_tickets_reservation_id," +
        " t.full_name t_full_name, t.first_name t_first_name, t.last_name t_last_name, t.email_address t_email_address, t.locked_assignment t_locked_assignment," +
        " t.user_language t_user_language, t.ext_reference t_ext_reference, t.currency_code t_currency_code, t.tags t_tags, t.subscription_id_fk t_subscription_id, t.vat_status t_vat_status," +
        " s.user_id s_user_id, s.creation s_creation, s.event_id s_event_id, s.ticket_id s_ticket_id, s.notes s_notes, s.lead_status s_lead_status, s.operator s_operator, " +
        " (case when s.lead_status = 'HOT' then 2 when s.lead_status = 'WARM' then 1 else 0 end) as priority"+
        " from sponsor_scan s, ticket t where s.event_id = :eventId and s.user_id = :userId and s.creation > :start and s.ticket_id = t.id order by priority desc, s.creation")
    List<DetailedScanData> loadSponsorData(@Bind("eventId") int eventId,
                                           @Bind("userId") int userId,
                                           @Bind("start") ZonedDateTime start);

}
