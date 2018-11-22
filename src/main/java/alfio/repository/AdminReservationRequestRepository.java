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

import alfio.model.AdminReservationRequest;
import alfio.model.AdminReservationRequestStats;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;

import java.util.List;
import java.util.Optional;

@QueryRepository
public interface AdminReservationRequestRepository {

    @Query(value = "insert into admin_reservation_request(user_id, request_id, event_id, request_type, status, body) values(:userId, :requestId, :eventId, :requestType, :status, :body)",
        type = QueryType.TEMPLATE)
    String insertRequest();

    @Query("select id from admin_reservation_request where status = 'PENDING' order by request_id limit :limit for update skip locked")
    List<Long> findPendingForUpdate(@Bind("limit") int limit);

    @Query("select * from admin_reservation_request where id = :id")
    AdminReservationRequest fetchCompleteById(@Bind("id") long id);

    @Query(value = "update admin_reservation_request set status = :status, reservation_id = :reservationId, failure_code = :failureCode where id = :id", type = QueryType.TEMPLATE)
    String updateStatus();


    @Query("select * from admin_reservation_request_stats where request_id = :requestId and event_id = :eventId")
    Optional<AdminReservationRequestStats> findStatsByRequestIdAndEventId(@Bind("requestId") String requestId, @Bind("eventId") long eventId);

}
