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

import alfio.model.SpecialPrice;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;

import java.util.List;

@QueryRepository
public interface SpecialPriceRepository {

    @Query("select * from special_price where ticket_category_id = :ticketCategoryId")
    List<SpecialPrice> findAllByCategoryId(@Bind("ticketCategoryId") int ticketCategoryId);

    @Query("select * from special_price where ticket_category_id = :ticketCategoryId and status = 'FREE'")
    List<SpecialPrice> findActiveByCategoryId(@Bind("ticketCategoryId") int ticketCategoryId);

    @Query("select * from special_price where code = :code")
    SpecialPrice getByCode(@Bind("code") String code);

    @Query("select count(*) from special_price where code = :code")
    Integer countByCode(@Bind("code") String code);
    
    @Query("update special_price set status = :status, session_id = :sessionId where id = :id")
    int updateStatus(@Bind("id") int id, @Bind("status") String status, @Bind("sessionId") String sessionIdentifier);

    @Query("update special_price set session_id = :sessionId where id = :id")
    int bindToSession(@Bind("id") int id, @Bind("sessionId") String sessionIdentifier);

    @Query("update special_price set session_id = null, status = 'FREE' where session_id = :sessionId and status in ('FREE', 'PENDING')")
    int unbindFromSession(@Bind("sessionId") String sessionIdentifier);

    @Query("update special_price set status = :status where id in (select special_price_id_fk from ticket where tickets_reservation_id in (:reservationIds) and special_price_id_fk is not null)")
    int updateStatusForReservation(@Bind("reservationIds") List<String> reservationIds, @Bind("status") String status);

    @Query("update special_price set code = :code, status = 'FREE' where id = :id")
    int updateCode(@Bind("code") String code, @Bind("id") int id);

    @Query(type = QueryType.TEMPLATE, value = "insert into special_price (code, price_cts, ticket_category_id, status) " +
            "values(:code, :priceInCents, :ticketCategoryId, :status)")
    String bulkInsert();


    @Query("update special_price set status = 'CANCELLED' where ticket_category_id = :categoryId and status in ('FREE', 'WAITING')")
    int cancelExpiredTokens(@Bind("categoryId") int categoryId);

    @Query("select id from special_price where ticket_category_id = :categoryId and status in ('FREE', 'WAITING') limit :limit for update")
    List<Integer> lockTokens(@Bind("categoryId") int categoryId, @Bind("limit") int limit);

    @Query("update special_price set status = 'CANCELLED' where id in (:ids)")
    int cancelTokens(@Bind("ids") List<Integer> ids);

    @Query("select * from special_price where status = 'WAITING' for update")
    List<SpecialPrice> findWaitingElements();
}
