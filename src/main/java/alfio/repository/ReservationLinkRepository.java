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

import alfio.model.ReservationLink;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@QueryRepository
public interface ReservationLinkRepository {

    @Query("select * from reservation_link where token = :token")
    Optional<ReservationLink> findByToken(@Bind("token") String token);

    @Query("select * from reservation_link where category_id = :categoryId order by created_at desc")
    List<ReservationLink> findByCategoryId(@Bind("categoryId") int categoryId);

    @Query("insert into reservation_link (token, event_id, category_id, quantity, expires_at) values (:token, :eventId, :categoryId, :quantity, :expiresAt)")
    void create(@Bind("token") String token,
                @Bind("eventId") int eventId,
                @Bind("categoryId") int categoryId,
                @Bind("quantity") int quantity,
                @Bind("expiresAt") ZonedDateTime expiresAt);

    @Query("update reservation_link set status = 'USED' where id = :id and status = 'ACTIVE' and expires_at > :now")
    int markAsUsed(@Bind("id") int id, @Bind("now") ZonedDateTime now);

    @Query("update reservation_link set status = 'REVOKED' where id = :id and status = 'ACTIVE'")
    int markAsRevoked(@Bind("id") int id);
}
