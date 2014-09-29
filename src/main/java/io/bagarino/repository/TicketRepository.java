/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.repository;

import io.bagarino.datamapper.Bind;
import io.bagarino.datamapper.Query;
import io.bagarino.datamapper.QueryRepository;
import io.bagarino.datamapper.QueryType;
import io.bagarino.model.Ticket;

import java.util.List;

@QueryRepository
public interface TicketRepository {

	@Query(type = QueryType.TEMPLATE, value = "insert into ticket (uuid, creation, category_id, event_id, status, original_price, paid_price)"
			+ "values(:uuid, :creation, :categoryId, :eventId, :status, :originalPrice, :paidPrice)")
	String bulkTicketInitialization();

	@Query("select id from ticket where status = 'FREE' and category_id = :categoryId and event_id = :eventId and tickets_reservation_id is null limit :amount for update")
	List<Integer> selectTicketInCategoryForUpdate(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId,
			@Bind("amount") int amount);

	@Query("update ticket set tickets_reservation_id = :reservationId, status = 'PENDING' where id in (:reservedForUpdate)")
	int reserveTickets(@Bind("reservationId") String reservationId,
			@Bind("reservedForUpdate") List<Integer> reservedForUpdate);

	@Query("update ticket set status = :status where tickets_reservation_id = :reservationId")
	int updateTicketStatus(@Bind("reservationId") String reservationId, @Bind("status") String status);

	@Query("update ticket set status = 'FREE', tickets_reservation_id = null where status = 'PENDING' "
			+ " and tickets_reservation_id in (:reservationIds)")
	int freeFromReservation(@Bind("reservationIds") List<String> reservationIds);

	@Query("select * from ticket where tickets_reservation_id = :reservationId order by category_id asc")
	List<Ticket> findTicketsInReservation(@Bind("reservationId") String reservationId);
	
	@Query("select * from ticket where uuid = :uuid")
	Ticket findByUUID(@Bind("uuid") String uuid);
}
