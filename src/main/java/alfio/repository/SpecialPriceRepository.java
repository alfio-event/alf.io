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

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@QueryRepository
public interface SpecialPriceRepository {

    @Query("select * from special_price where ticket_category_id = :ticketCategoryId")
    List<SpecialPrice> findAllByCategoryId(@Bind("ticketCategoryId") int ticketCategoryId);

    @Query("select * from special_price where ticket_category_id in (:ticketCategoryIds)")
    List<SpecialPrice> findAllByCategoriesIds(@Bind("ticketCategoryIds") Collection<Integer> ticketCategoryIds);

    @Query("select * from special_price where ticket_category_id = :ticketCategoryId and status = 'FREE'")
    List<SpecialPrice> findActiveByCategoryId(@Bind("ticketCategoryId") int ticketCategoryId);

    @Query("select * from special_price where ticket_category_id = :ticketCategoryId and status = 'FREE' and recipient_name is null and recipient_email is null")
    List<SpecialPrice> findActiveNotAssignedByCategoryId(@Bind("ticketCategoryId") int ticketCategoryId);

    @Query("update special_price set sent_ts = :timestamp, recipient_name = :recipientName, recipient_email = :recipientAddress where code = :code")
    int markAsSent(@Bind("timestamp") ZonedDateTime timestamp, @Bind("recipientName") String recipientName, @Bind("recipientAddress") String recipientAddress, @Bind("code") String code);

    @Query("update special_price set sent_ts = null, recipient_name = null, recipient_email = null where id = :id and ticket_category_id = :ticketCategoryId")
    int clearRecipientData(@Bind("id") int id, @Bind("ticketCategoryId") int ticketCategoryId);

    @Query("select * from special_price where code = :code")
    Optional<SpecialPrice> getByCode(@Bind("code") String code);

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

    @Query("update special_price set status = 'FREE', session_id = null, sent_ts = null, recipient_name = null, recipient_email = null where id in (select special_price_id_fk from ticket where tickets_reservation_id in (:reservationIds) and special_price_id_fk is not null)")
    int resetToFreeAndCleanupForReservation(@Bind("reservationIds") List<String> reservationIds);

    @Query("update special_price set code = :code, status = 'FREE', sent_ts = null where id = :id")
    int updateCode(@Bind("code") String code, @Bind("id") int id);

    @Query(type = QueryType.TEMPLATE, value = "insert into special_price (code, price_cts, ticket_category_id, status, sent_ts) " +
            "values(:code, :priceInCents, :ticketCategoryId, :status, null)")
    String bulkInsert();


    @Query("update special_price set status = 'CANCELLED' where ticket_category_id = :categoryId and status in ('FREE', 'WAITING')")
    int cancelExpiredTokens(@Bind("categoryId") int categoryId);

    @Query("select id from special_price where ticket_category_id = :categoryId and status in ('FREE', 'WAITING') and sent_ts is null limit :limit for update")
    List<Integer> lockNotSentTokens(@Bind("categoryId") int categoryId, @Bind("limit") int limit);

    @Query("select count(id) from special_price where ticket_category_id = :categoryId and status in ('FREE', 'WAITING') and sent_ts is null")
    Integer countNotSentToken(@Bind("categoryId") int categoryId);

    @Query("update special_price set status = 'CANCELLED' where id in (:ids)")
    int cancelTokens(@Bind("ids") List<Integer> ids);

    @Query("select * from special_price where status = 'WAITING' for update skip locked")
    List<SpecialPrice> findWaitingElements();

    @Query("select * from special_price where status = 'WAITING' and ticket_category_id = :categoryId for update skip locked")
    List<SpecialPrice> findWaitingElementsForCategory(@Bind("categoryId") int categoryId);


    default Map<Integer,List<SpecialPrice>> findAllByCategoriesIdsMapped(Collection<Integer> ticketCategoriesIds) {
        if(ticketCategoriesIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return findAllByCategoriesIds(ticketCategoriesIds).stream().collect(Collectors.groupingBy(SpecialPrice::getTicketCategoryId));
    }
}
