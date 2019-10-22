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
import alfio.model.TicketCategory;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@QueryRepository
public interface SpecialPriceRepository {

    String IS_FREE = " status = 'FREE' and recipient_name is null and recipient_email is null and access_code_id_fk is null";
    String SELECT_FREE = "select * from special_price where ticket_category_id = :ticketCategoryId and " + IS_FREE;

    @Query("select * from special_price where ticket_category_id = :ticketCategoryId")
    List<SpecialPrice> findAllByCategoryId(@Bind("ticketCategoryId") int ticketCategoryId);

    @Query("select * from special_price where ticket_category_id in (:ticketCategoryIds)")
    List<SpecialPrice> findAllByCategoriesIds(@Bind("ticketCategoryIds") Collection<Integer> ticketCategoryIds);

    @Query("select * from special_price where ticket_category_id = :ticketCategoryId and "+IS_FREE+" for update skip locked limit :limitTo")
    List<SpecialPrice> findActiveByCategoryIdForUpdate(@Bind("ticketCategoryId") int ticketCategoryId, @Bind("limitTo") int limitTo);

    @Query(SELECT_FREE + " limit :limitTo for update skip locked")
    List<SpecialPrice> findActiveNotAssignedByCategoryId(@Bind("ticketCategoryId") int ticketCategoryId, @Bind("limitTo") int limitTo);

    @Query("select count(*) from special_price where ticket_category_id = :ticketCategoryId and " + IS_FREE)
    Integer countFreeTokens(@Bind("ticketCategoryId") int ticketCategoryId);

    @Query(type= QueryType.MODIFYING_WITH_RETURN, value = "update special_price set access_code_id_fk = :accessCodeId where id in (" +
        "select id from special_price where ticket_category_id = :ticketCategoryId and " +IS_FREE+ " and access_code_id_fk is null limit :limitTo" +
        ") returning *")
    List<SpecialPrice> bindToAccessCode(@Bind("ticketCategoryId") int ticketCategoryId, @Bind("accessCodeId") int accessCodeId, @Bind("limitTo") int limitTo);

    @Query("update special_price set sent_ts = :timestamp, recipient_name = :recipientName, recipient_email = :recipientAddress where code = :code")
    int markAsSent(@Bind("timestamp") ZonedDateTime timestamp, @Bind("recipientName") String recipientName, @Bind("recipientAddress") String recipientAddress, @Bind("code") String code);

    @Query("update special_price set sent_ts = null, recipient_name = null, recipient_email = null where id = :id and ticket_category_id = :ticketCategoryId")
    int clearRecipientData(@Bind("id") int id, @Bind("ticketCategoryId") int ticketCategoryId);

    @Query("select * from special_price where code = :code")
    Optional<SpecialPrice> getByCode(@Bind("code") String code);

    @Query("select * from special_price where code = :code for update skip locked")
    Optional<SpecialPrice> getForUpdateByCode(@Bind("code") String code);

    @Query("select count(*) from special_price where code = :code")
    Integer countByCode(@Bind("code") String code);

    @Query("update special_price set status = :status, session_id = null, access_code_id_fk = :accessCodeId where id in (:ids)")
    int batchUpdateStatus(@Bind("ids") List<Integer> ids, @Bind("status") SpecialPrice.Status status, @Bind("accessCodeId") Integer accessCodeId);

    @Query("update special_price set status = :status, session_id = :sessionId, access_code_id_fk = :accessCodeId where id = :id")
    int updateStatus(@Bind("id") int id, @Bind("status") String status, @Bind("sessionId") String sessionIdentifier, @Bind("accessCodeId") Integer accessCodeId);

    @Query("update special_price set status = :status where id in (select special_price_id_fk from ticket where tickets_reservation_id in (:reservationIds) and special_price_id_fk is not null)")
    int updateStatusForReservation(@Bind("reservationIds") List<String> reservationIds, @Bind("status") String status);

    @Query("update special_price set status = 'FREE', session_id = null, sent_ts = null, recipient_name = null, recipient_email = null, access_code_id_fk = null " +
        "where id in (select special_price_id_fk from ticket where tickets_reservation_id in (:reservationIds) and special_price_id_fk is not null)")
    int resetToFreeAndCleanupForReservation(@Bind("reservationIds") List<String> reservationIds);


    @Query("update special_price set status = 'FREE', session_id = null, sent_ts = null, recipient_name = null, recipient_email = null, access_code_id_fk = null " +
        " where id in (select special_price_id_fk from ticket where ticket.id in (:ticketIds) and special_price_id_fk is not null) ")
    int resetToFreeAndCleanupForTickets(@Bind("ticketIds") List<Integer> ticketIds);

    @Query("update special_price set code = :code, status = 'FREE', sent_ts = null where id = :id")
    int updateCode(@Bind("code") String code, @Bind("id") int id);

    NamedParameterJdbcTemplate getNamedParameterJdbcTemplate();

    default void bulkInsert(TicketCategory ticketCategory, int requiredTokens) {

        MapSqlParameterSource[] params = Stream.generate(MapSqlParameterSource::new)
            .limit(requiredTokens)
            .peek(ps -> {
                ps.addValue("code", UUID.randomUUID().toString());
                ps.addValue("priceInCents", ticketCategory.getSrcPriceCts());
                ps.addValue("ticketCategoryId", ticketCategory.getId());
                ps.addValue("status", SpecialPrice.Status.WAITING.name());
            }).toArray(MapSqlParameterSource[]::new);

        getNamedParameterJdbcTemplate()
            .batchUpdate("insert into special_price (code, price_cts, ticket_category_id, status, sent_ts) values(:code, :priceInCents, :ticketCategoryId, :status, null)", params);
    }


    @Query("update special_price set status = 'CANCELLED' where ticket_category_id = :categoryId and status in ('FREE', 'WAITING')")
    int cancelExpiredTokens(@Bind("categoryId") int categoryId);

    @Query("select id from special_price where ticket_category_id = :categoryId and status in ('FREE', 'WAITING') and sent_ts is null limit :limit for update")
    List<Integer> lockNotSentTokens(@Bind("categoryId") int categoryId, @Bind("limit") int limit);

    @Query("select count(id) from special_price where ticket_category_id = :categoryId and status in ('FREE', 'WAITING') and sent_ts is null")
    Integer countNotSentToken(@Bind("categoryId") int categoryId);

    @Query("update special_price set status = 'CANCELLED' where id in (:ids)")
    int cancelTokens(@Bind("ids") List<Integer> ids);

    @Query("select id, ticket_category_id from special_price where status = 'WAITING' for update skip locked")
    List<SpecialPrice.SpecialPriceTicketCategoryId> findWaitingElements();

    @Query("select id, ticket_category_id from special_price where status = 'WAITING' and ticket_category_id = :categoryId for update skip locked")
    List<SpecialPrice.SpecialPriceTicketCategoryId> findWaitingElementsForCategory(@Bind("categoryId") int categoryId);


    default Map<Integer,List<SpecialPrice>> findAllByCategoriesIdsMapped(Collection<Integer> ticketCategoriesIds) {
        if(ticketCategoriesIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return findAllByCategoriesIds(ticketCategoriesIds).stream().collect(Collectors.groupingBy(SpecialPrice::getTicketCategoryId));
    }
}
