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

import java.util.Date;
import java.util.List;

@QueryRepository
public interface TicketRepository {

    @Query(type = QueryType.TEMPLATE, value = "insert into ticket (uuid, creation, category_id, event_id, status, original_price, paid_price)" +
            "values(:uuid, :creation, :categoryId, :eventId, :status, :originalPrice, :paidPrice)")
    String bulkTicketInitialization();
    
    @Query("select id from ticket where status = 'FREE' and category_id = :categoryId and event_id = :eventId and transaction_id is null limit :amount for update")
    List<Integer> selectTicketInCategoryForUpdate(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId, @Bind("amount") int amount);
    
    @Query("update ticket set transaction_id = :transactionId, status = 'PENDING' where id in (:reservedForUpdate)")
    int reserveTickets(@Bind("transactionId") String transactionId, @Bind("reservedForUpdate") List<Integer> reservedForUpdate);
    
    @Query("insert into tickets_transaction(id, validity) values (:id, :validity)")
	int createNewTransaction(@Bind("id") String id, @Bind("validity") Date validity);
}
