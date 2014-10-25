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
package io.bagarino.repository.join;

import io.bagarino.datamapper.Bind;
import io.bagarino.datamapper.Query;
import io.bagarino.datamapper.QueryRepository;
import io.bagarino.model.join.EventTicketCategory;

import java.util.List;

@QueryRepository
public interface EventTicketCategoryRepository {

    @Query("insert into j_event_ticket_category(event_id, ticket_category_id) values(:event_id, :ticket_category_id)")
    int insert(@Bind("event_id") int eventId, @Bind("ticket_category_id") int ticketCategoryId);

    @Query("select * from j_event_ticket_category where event_id = :event_id")
    List<EventTicketCategory> findByEventId(@Bind("event_id") int eventId);

    @Query("select * from j_event_ticket_category where ticket_category_id = :ticket_category_id")
    EventTicketCategory findByTicketCategoryId(@Bind("ticket_category_id") int ticketCategoryId);

}
