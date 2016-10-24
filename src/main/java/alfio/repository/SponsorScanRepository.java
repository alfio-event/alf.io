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

import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.Optional;

@QueryRepository
public interface SponsorScanRepository {

    @Query("select creation from sponsor_scan where user_id = :userId and event_id = :eventId and ticket_id = :ticketId")
    Optional<ZonedDateTime> getRegistrationTimestamp(@Bind("userId") int userId, @Bind("eventId") int eventId, @Bind("ticketId") int ticketId);

    @Query("insert into sponsor_scan (user_id, creation, event_id, ticket_id) values(:userId, :creation, :eventId, :ticketId)")
    int insert(@Bind("userId") int userId, @Bind("creation") ZonedDateTime creation, @Bind("eventId") int eventId, @Bind("ticketId") int ticketId);
}
