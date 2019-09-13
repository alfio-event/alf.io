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

import alfio.model.*;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.ZonedDateTime;
import java.util.*;

@QueryRepository
public interface TicketRepository {

    String CONFIRMED = "'ACQUIRED', 'CHECKED_IN', 'TO_BE_PAID'";
    String FREE = "FREE";
    String RELEASED = "RELEASED";
    String REVERT_TO_FREE = "update ticket set status = 'FREE' where status = 'RELEASED' and event_id = :eventId";


    //TODO: refactor, try to move the MapSqlParameterSource inside the default method!
    default void bulkTicketInitialization(MapSqlParameterSource[] args) {
        getNamedParameterJdbcTemplate().batchUpdate("insert into ticket (uuid, creation, category_id, event_id, status, original_price_cts, paid_price_cts, src_price_cts)"
            + "values(:uuid, :creation, :categoryId, :eventId, :status, 0, 0, :srcPriceCts)", args);
    }

    default void bulkTicketUpdate(List<Integer> ids, TicketCategory ticketCategory) {
        MapSqlParameterSource[] params = ids.stream().map(id -> new MapSqlParameterSource("id", id)
            .addValue("categoryId", ticketCategory.getId())
            .addValue("srcPriceCts", ticketCategory.getSrcPriceCts()))
            .toArray(MapSqlParameterSource[]::new);
        getNamedParameterJdbcTemplate().batchUpdate("update ticket set category_id = :categoryId, src_price_cts = :srcPriceCts where id = :id", params);
    }

    @Query("select id from ticket where status in (:requiredStatuses) and category_id = :categoryId and event_id = :eventId and tickets_reservation_id is null order by id limit :amount for update")
    List<Integer> selectTicketInCategoryForUpdate(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId, @Bind("amount") int amount, @Bind("requiredStatuses") List<String> requiredStatus);

    @Query("select id from ticket where status in (:requiredStatuses) and category_id = :categoryId and event_id = :eventId and tickets_reservation_id is null order by id limit :amount for update skip locked")
    List<Integer> selectTicketInCategoryForUpdateSkipLocked(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId, @Bind("amount") int amount, @Bind("requiredStatuses") List<String> requiredStatus);

    @Query("select id from ticket where status in(:requiredStatuses) and category_id is null and event_id = :eventId and tickets_reservation_id is null order by id limit :amount for update")
    List<Integer> selectNotAllocatedTicketsForUpdate(@Bind("eventId") int eventId, @Bind("amount") int amount, @Bind("requiredStatuses") List<String> requiredStatuses);

    @Query("select id from ticket where status in(:requiredStatuses) and category_id is null and event_id = :eventId and tickets_reservation_id is null order by id limit :amount for update skip locked")
    List<Integer> selectNotAllocatedTicketsForUpdateSkipLocked(@Bind("eventId") int eventId, @Bind("amount") int amount, @Bind("requiredStatuses") List<String> requiredStatuses);

    @Query("select id from ticket where status = 'FREE' and category_id = :categoryId and event_id = :eventId and tickets_reservation_id is null order by id desc limit :amount for update")
    List<Integer> lockTicketsToInvalidate(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId, @Bind("amount") int amount);

    @Query("select count(*) from ticket where status in ("+CONFIRMED+") and category_id = :categoryId and event_id = :eventId and full_name is not null and email_address is not null")
    Integer countAssignedTickets(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);


    @Query("select count(*) from ticket where status in ("+CONFIRMED+", 'PENDING') and category_id = :categoryId and event_id = :eventId")
    Integer countConfirmedAndPendingTickets(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

    @Query("select count(*) from ticket where status in ("+CONFIRMED+") and category_id = :categoryId and event_id = :eventId")
    Integer countConfirmedForCategory(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

    @Query("select count(*) from ticket where status in ('PENDING', 'RELEASED') and category_id = :categoryId and event_id = :eventId")
    Integer countPendingOrReleasedForCategory(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);
    
    @Query("select count(*) from ticket where status = 'FREE'  and category_id = :categoryId and event_id = :eventId")
    Integer countFreeTickets(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

    @Query("select count(*) from ticket where status = 'FREE' and category_id is null and event_id = :eventId")
    Integer countFreeTicketsForUnbounded(@Bind("eventId") int eventId);

    @Query("select count(*) from ticket where status in ('FREE', 'RELEASED') and category_id is null and event_id = :eventId")
    Integer countNotAllocatedFreeAndReleasedTicket(@Bind("eventId") int eventId);

    @Query("select count(*) from ticket where status = 'RELEASED' and category_id is null and event_id = :eventId")
    Integer countReleasedUnboundedTickets(@Bind("eventId") int eventId);

    @Query("select count(*) from ticket where status = 'RELEASED' and category_id = :categoryId and event_id = :eventId")
    Integer countReleasedTicketInCategory(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

    @Query("update ticket set tickets_reservation_id = :reservationId, status = 'PENDING', category_id = :categoryId, user_language = :userLanguage, src_price_cts = :srcPriceCts, currency_code = :currencyCode where id in (:reservedForUpdate)")
    int reserveTickets(@Bind("reservationId") String reservationId, @Bind("reservedForUpdate") List<Integer> reservedForUpdate, @Bind("categoryId") int categoryId, @Bind("userLanguage") String userLanguage, @Bind("srcPriceCts") int srcPriceCts, @Bind("currencyCode") String currencyCode);

    @Query(type = QueryType.TEMPLATE, value = "update ticket set tickets_reservation_id = :reservationId, special_price_id_fk = :specialCodeId, user_language = :userLanguage, status = 'PENDING', src_price_cts = :srcPriceCts, currency_code = :currencyCode where id = :ticketId")
    String batchReserveTicket();

    @Query("update ticket set tickets_reservation_id = :reservationId, special_price_id_fk = :specialCodeId, user_language = :userLanguage, status = 'PENDING', src_price_cts = :srcPriceCts, currency_code = :currencyCode where id = :ticketId")
    void reserveTicket(@Bind("reservationId")String transactionId, @Bind("ticketId") int ticketId, @Bind("specialCodeId") int specialCodeId, @Bind("userLanguage") String userLanguage, @Bind("srcPriceCts") int srcPriceCts, @Bind("currencyCode") String currencyCode);

    @Query("update ticket set status = :status where tickets_reservation_id = :reservationId")
    int updateTicketsStatusWithReservationId(@Bind("reservationId") String reservationId, @Bind("status") String status);
    
    @Query("update ticket set status = :status where uuid = :uuid")
    int updateTicketStatusWithUUID(@Bind("uuid") String uuid, @Bind("status") String status);

    @Query("update ticket set status = 'INVALIDATED' where id in (:ids)")
    int invalidateTickets(@Bind("ids") List<Integer> ids);

    @Query("update ticket set src_price_cts = :srcPriceCts, final_price_cts = :finalPriceCts, vat_cts = :vatCts, discount_cts = :discountCts, currency_code = :currencyCode where event_id = :eventId and category_id = :categoryId")
    int updateTicketPrice(@Bind("categoryId") int categoryId, @Bind("eventId") int eventId, @Bind("srcPriceCts") int srcPriceCts, @Bind("finalPriceCts") int finalPriceCts, @Bind("vatCts") int vatCts, @Bind("discountCts") int discountCts, @Bind("currencyCode") String currencyCode);

    @Query("update ticket set src_price_cts = :srcPriceCts, final_price_cts = :finalPriceCts, vat_cts = :vatCts, discount_cts = :discountCts, currency_code = :currencyCode where event_id = :eventId and category_id = :categoryId and id in(:ids)")
    int updateTicketPrice(@Bind("ids") List<Integer> ids, @Bind("categoryId") int categoryId, @Bind("eventId") int eventId, @Bind("srcPriceCts") int srcPriceCts, @Bind("finalPriceCts") int finalPriceCts, @Bind("vatCts") int vatCts, @Bind("discountCts") int discountCts, @Bind("currencyCode") String currencyCode);

    @Query("update ticket set status = 'RELEASED', tickets_reservation_id = null, special_price_id_fk = null, first_name = null, last_name = null, full_name = null, email_address = null where status in ('PENDING', 'OFFLINE_PAYMENT') "
            + " and tickets_reservation_id in (:reservationIds)")
    int freeFromReservation(@Bind("reservationIds") List<String> reservationIds);

    @Query("update ticket set category_id = null where tickets_reservation_id in (:reservationIds) and status in ('PENDING', 'OFFLINE_PAYMENT') and category_id in (select tc.id from ticket_category tc, ticket t where t.tickets_reservation_id in (:reservationIds) and t.category_id = tc.id and tc.bounded = false)")
    int resetCategoryIdForUnboundedCategories(@Bind("reservationIds") List<String> reservationIds);

    @Query("update ticket set category_id = null where id in (:ticketIds) and category_id in (select tc.id from ticket_category tc, ticket t where t.id in (:ticketIds) and t.category_id = tc.id and tc.bounded = false)")
    int resetCategoryIdForUnboundedCategoriesWithTicketIds(@Bind("ticketIds") List<Integer> ticketIds);

    @Query("update ticket set category_id = null where event_id = :eventId and category_id = :categoryId and id in (:ticketIds)")
    int unbindTicketsFromCategory(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId, @Bind("ticketIds") List<Integer> ids);

    @Query("select * from ticket where tickets_reservation_id = :reservationId order by category_id asc, uuid asc")
    List<Ticket> findTicketsInReservation(@Bind("reservationId") String reservationId);

    @Query("select id from ticket where tickets_reservation_id = :reservationId order by category_id asc, uuid asc")
    List<Integer> findTicketIdsInReservation(@Bind("reservationId") String reservationId);

    @Query("select * from ticket where tickets_reservation_id = :reservationId order by category_id asc, uuid asc LIMIT 1 OFFSET 0")
    Optional<Ticket> findFirstTicketInReservation(@Bind("reservationId") String reservationId);

    @Query("select count(*) from ticket where tickets_reservation_id = :reservationId ")
    Integer countTicketsInReservation(@Bind("reservationId") String reservationId);
    
    @Query("select * from ticket where uuid = :uuid")
    Ticket findByUUID(@Bind("uuid") String uuid);

    @Query("select category_id from ticket where uuid = :uuid")
    Integer getTicketCategoryByUIID(@Bind("uuid") String uuid);

    @Query("select * from ticket where uuid = :uuid")
    Optional<Ticket> findOptionalByUUID(@Bind("uuid") String uuid);

    @Query("select * from ticket where uuid = :uuid for update")
    Optional<Ticket> findByUUIDForUpdate(@Bind("uuid") String uuid);

    @Query("update ticket set email_address = :email, full_name = :fullName, first_name = :firstName, last_name = :lastName where uuid = :ticketIdentifier")
    int updateTicketOwner(@Bind("ticketIdentifier") String ticketIdentifier, @Bind("email") String email, @Bind("fullName") String fullName, @Bind("firstName") String firstName, @Bind("lastName") String lastName);

    @Query("update ticket set email_address = :email, full_name = :fullName, first_name = :firstName, last_name = :lastName where id = :id")
    int updateTicketOwnerById(@Bind("id") int id, @Bind("email") String email, @Bind("fullName") String fullName, @Bind("firstName") String firstName, @Bind("lastName") String lastName);

    @Query("update ticket set locked_assignment = :lockedAssignment where id = :id and category_id = :categoryId")
    int toggleTicketLocking(@Bind("id") int ticketId, @Bind("categoryId") int categoryId, @Bind("lockedAssignment") boolean locked);

    @Query("update ticket set locked_assignment = true where id in (:ids)")
    int forbidReassignment(@Bind("ids") Collection<Integer> ticketIds);

    @Query("update ticket set ext_reference = :extReference, locked_assignment = :lockedAssignment where id = :id and category_id = :categoryId")
    int updateExternalReferenceAndLocking(@Bind("id") int ticketId, @Bind("categoryId") int categoryId, @Bind("extReference") String extReference, @Bind("lockedAssignment") boolean locked);
    
    @Query("update ticket set user_language = :userLanguage where uuid = :ticketIdentifier")
    int updateOptionalTicketInfo(@Bind("ticketIdentifier") String ticketIdentifier, @Bind("userLanguage") String userLanguage);

    @Query("select * from ticket where id = :id and category_id = :categoryId")
    Ticket findById(@Bind("id") int ticketId, @Bind("categoryId") int categoryId);

    @Query("select * from ticket where id in (:ids)")
    List<Ticket> findByIds(@Bind("ids") List<Integer> ticketIds);

    @Query("select uuid from ticket where id in (:ids)")
    List<String> findUUIDs(@Bind("ids") List<Integer> ticketIds);

    @Query("select distinct tickets_reservation_id from ticket where id in (:ids)")
    List<String> findReservationIds(@Bind("ids") List<Integer> ticketIds);

    @Query("select * from ticket where special_price_id_fk = :specialPriceId")
    Optional<Ticket> findBySpecialPriceId(@Bind("specialPriceId") int specialPriceId);

    @Query("update ticket set category_id = :targetCategoryId, src_price_cts = :srcPriceCts where id in (:ticketIds)")
    int moveToAnotherCategory(@Bind("ticketIds") List<Integer> ticketIds, @Bind("targetCategoryId") int targetCategoryId, @Bind("srcPriceCts") int srcPriceCts);

    @Query("select * from ticket where category_id in (:categories) and status = 'PENDING'")
    List<Ticket> findPendingTicketsInCategories(@Bind("categories") List<Integer> categories);

    String FIND_FULL_TICKET_INFO = "select " +
        " t.id t_id, t.uuid t_uuid, t.creation t_creation, t.category_id t_category_id, t.status t_status, t.event_id t_event_id," +
        " t.src_price_cts t_src_price_cts, t.final_price_cts t_final_price_cts, t.vat_cts t_vat_cts, t.discount_cts t_discount_cts, t.tickets_reservation_id t_tickets_reservation_id," +
        " t.full_name t_full_name, t.first_name t_first_name, t.last_name t_last_name, t.email_address t_email_address, t.locked_assignment t_locked_assignment," +
        " t.user_language t_user_language, t.ext_reference t_ext_reference, t.currency_code t_currency_code, " +
        " tr.id tr_id, tr.validity tr_validity, tr.status tr_status, tr.full_name tr_full_name, tr.first_name tr_first_name, tr.last_name tr_last_name, tr.email_address tr_email_address, tr.billing_address tr_billing_address," +
        " tr.confirmation_ts tr_confirmation_ts, tr.latest_reminder_ts tr_latest_reminder_ts, tr.payment_method tr_payment_method, " +
        " tr.offline_payment_reminder_sent tr_offline_payment_reminder_sent, tr.promo_code_id_fk tr_promo_code_id_fk, tr.automatic tr_automatic, tr.user_language tr_user_language, tr.direct_assignment tr_direct_assignment, tr.invoice_number tr_invoice_number, tr.invoice_model tr_invoice_model, " +
        " tr.vat_status tr_vat_status, tr.vat_nr tr_vat_nr, tr.vat_country tr_vat_country, tr.invoice_requested tr_invoice_requested, tr.used_vat_percent tr_used_vat_percent, tr.vat_included tr_vat_included, tr.creation_ts tr_creation_ts, tr.registration_ts tr_registration_ts, tr.customer_reference tr_customer_reference, " +
        " tr.billing_address_company tr_billing_address_company, tr.billing_address_line1 tr_billing_address_line1, tr.billing_address_line2 tr_billing_address_line2, tr.billing_address_city tr_billing_address_city, tr.billing_address_zip tr_billing_address_zip, tr.invoicing_additional_information tr_invoicing_additional_information, " +
        " tr.src_price_cts tr_src_price_cts, tr.final_price_cts tr_final_price_cts, tr.vat_cts tr_vat_cts, tr.discount_cts tr_discount_cts, tr.currency_code tr_currency_code, " +
        " tc.id tc_id, tc.inception tc_inception, tc.expiration tc_expiration, tc.max_tickets tc_max_tickets, tc.name tc_name, tc.src_price_cts tc_src_price_cts, tc.access_restricted tc_access_restricted, tc.tc_status tc_tc_status, tc.event_id tc_event_id, tc.bounded tc_bounded, tc.category_code tc_category_code, " +
        " tc.valid_checkin_from tc_valid_checkin_from, tc.valid_checkin_to tc_valid_checkin_to, tc.ticket_validity_start tc_ticket_validity_start, tc.ticket_validity_end tc_ticket_validity_end, tc.currency_code tc_currency_code, tc.ordinal tc_ordinal" +
        " from ticket t " +
        " inner join tickets_reservation tr on t.tickets_reservation_id = tr.id " +
        " inner join ticket_category_with_currency tc on t.category_id = tc.id ";

    @Query(FIND_FULL_TICKET_INFO +
            " where t.event_id = :eventId and t.full_name is not null and t.email_address is not null and t.id in (:ids) order by t.id asc")
    List<FullTicketInfo> findAllFullTicketInfoAssignedByEventId(@Bind("eventId") int eventId, @Bind("ids") List<Integer> ids);


    @Query("select t.id " +
            " from ticket t " +
            " left outer join latest_ticket_update ltu on t.id = ltu.ticket_id and ltu.event_id = :eventId " +
            " where t.event_id = :eventId and t.full_name is not null and t.email_address is not null and (coalesce(ltu.last_update, t.creation) > :changedSince)  order by t.id asc")
    List<Integer> findAllAssignedByEventId(@Bind("eventId") int eventId, @Bind("changedSince") Date changedSince);

    @Query("select * from reservation_and_ticket_and_tx where t_id is not null and t_status in (" + CONFIRMED + ") and t_event_id = :eventId order by tr_confirmation_ts, t_id")
    List<TicketWithReservationAndTransaction> findAllConfirmedForCSV(@Bind("eventId") int eventId);

    @Query("select a.*, b.confirmation_ts from ticket a, tickets_reservation b where a.event_id = :eventId and a.status in(" + CONFIRMED + ") and a.tickets_reservation_id = b.id order by b.confirmation_ts")
    List<Ticket> findAllConfirmed(@Bind("eventId") int eventId);

    @Query("select * from ticket where event_id = :eventId and status in(" + CONFIRMED + ") and category_id = :categoryId")
    List<Ticket> findConfirmedByCategoryId(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

    @Query("select count(*) from ticket where event_id = :eventId and status in(" + CONFIRMED + ") and full_name is not null and email_address is not null")
    Integer countAllAssigned(@Bind("eventId") int eventId);

    //
    @Query("select tickets_reservation_id from ticket where event_id = :eventId and status in('ACQUIRED', 'TO_BE_PAID') and (full_name is null or email_address is null) for update skip locked")
    List<String> internalFindAllReservationsConfirmedButNotAssignedForUpdate(@Bind("eventId") int eventId);

    default Set<String> findAllReservationsConfirmedButNotAssignedForUpdate(int eventId) {
        return new TreeSet<>(internalFindAllReservationsConfirmedButNotAssignedForUpdate(eventId));
    }
    //

    @Query("select * from ticket where event_id = :eventId  and status in('ACQUIRED', 'TO_BE_PAID') and full_name is not null and email_address is not null and reminder_sent = false for update skip locked")
    List<Ticket> findAllAssignedButNotYetNotifiedForUpdate(@Bind("eventId") int eventId);

    @Query("update ticket set reminder_sent = true where id = :id and reminder_sent = false")
    int flagTicketAsReminderSent(@Bind("id") int ticketId);

    String RESET_TICKET = " TICKETS_RESERVATION_ID = null, FULL_NAME = null, EMAIL_ADDRESS = null, SPECIAL_PRICE_ID_FK = null, LOCKED_ASSIGNMENT = false, USER_LANGUAGE = null, REMINDER_SENT = false, SRC_PRICE_CTS = 0, FINAL_PRICE_CTS = 0, VAT_CTS = 0, DISCOUNT_CTS = 0, FIRST_NAME = null, LAST_NAME = null, EXT_REFERENCE = null ";
    String RELEASE_TICKET_QUERY = "update ticket set status = 'RELEASED', uuid = :newUuid, " + RESET_TICKET + " where id = :ticketId and status in('ACQUIRED', 'PENDING', 'TO_BE_PAID') and tickets_reservation_id = :reservationId and event_id = :eventId";

    @Query(RELEASE_TICKET_QUERY)
    int releaseTicket(@Bind("reservationId") String reservationId, @Bind("newUuid") String newUuid, @Bind("eventId") int eventId, @Bind("ticketId") int ticketId);

    default int[] batchReleaseTickets(String reservationId, List<Integer> ticketIds, Event event) {
        MapSqlParameterSource[] args = ticketIds.stream().map(id -> new MapSqlParameterSource("ticketId", id)
            .addValue("reservationId", reservationId)
            .addValue("eventId", event.getId())
            .addValue("newUuid", UUID.randomUUID().toString())
        ).toArray(MapSqlParameterSource[]::new);
        return getNamedParameterJdbcTemplate().batchUpdate(RELEASE_TICKET_QUERY, args);
    }

    @Query("update ticket set status = 'RELEASED', uuid = :newUuid, " + RESET_TICKET + " where id = :ticketId and status = 'PENDING' and tickets_reservation_id = :reservationId and event_id = :eventId")
    int releaseExpiredTicket(@Bind("reservationId") String reservationId, @Bind("eventId") int eventId, @Bind("ticketId") int ticketId, @Bind("newUuid") String newUuid);

    NamedParameterJdbcTemplate getNamedParameterJdbcTemplate();

    default void resetTickets(List<Integer> ticketIds) {
        MapSqlParameterSource[] params = ticketIds.stream().map(ticketId -> new MapSqlParameterSource("ticketId", ticketId).addValue("newUuid", UUID.randomUUID().toString())).toArray(MapSqlParameterSource[]::new);
        getNamedParameterJdbcTemplate().batchUpdate("update ticket set status = 'RELEASED', uuid = :newUuid, " + RESET_TICKET + " where id = :ticketId", params);
    }

    @Query("select count(*) from ticket where status = 'RELEASED' and event_id = :eventId")
    Integer countWaiting(@Bind("eventId") int eventId);

    @Query("select " +
        " t.id t_id, " +
        " tc.id tc_id, tc.bounded tc_bounded " +
        " from ticket t " +
        " inner join ticket_category tc on t.category_id = tc.id "+
        " where t.event_id = :eventId and t.status = 'RELEASED' and tc.expiration <= :currentTs")
    List<TicketInfo> findReleasedBelongingToExpiredCategories(@Bind("eventId") int eventId, @Bind("currentTs") ZonedDateTime now);

    @Query(REVERT_TO_FREE)
    int revertToFree(@Bind("eventId") int eventId);

    @Query(REVERT_TO_FREE + " and category_id = :categoryId and id in (:ticketIds)")
    int revertToFree(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId, @Bind("ticketIds") List<Integer> ticketIds);

    @Query("select * from ticket where status = :status and event_id = :eventId order by id limit :amount for update")
    List<Ticket> selectWaitingTicketsForUpdate(@Bind("eventId") int eventId, @Bind("status") String status, @Bind("amount") int amount);

    @Query("select id from ticket where status = 'FREE' and event_id = :eventId and category_id = :categoryId order by id limit :amount for update skip locked")
    List<Integer> selectFreeTicketsForPreReservation(@Bind("eventId") int eventId, @Bind("amount") int amount, @Bind("categoryId") int categoryId);

    @Query("select id from ticket where status = 'FREE' and event_id = :eventId and category_id is null order by id limit :amount for update skip locked")
    List<Integer> selectNotAllocatedFreeTicketsForPreReservation(@Bind("eventId") int eventId, @Bind("amount") int amount);

    @Query("select count(*) from ticket where status = 'PRE_RESERVED'")
    Integer countPreReservedTickets(@Bind("eventId") int eventId);

    default void preReserveTicket(List<Integer> ids) {
        MapSqlParameterSource[] params = ids.stream()
            .map(id -> new MapSqlParameterSource().addValue("id", id))
            .toArray(MapSqlParameterSource[]::new);
        getNamedParameterJdbcTemplate().batchUpdate("update ticket set status = 'PRE_RESERVED' where id = :id", params);
    }

    @Query("select * from ticket where status = 'FREE' and event_id = :eventId")
    List<Ticket> findFreeByEventId(@Bind("eventId") int eventId);

    @Query("select count(*) from ticket where event_id = :eventId and category_id is not null and status <> 'INVALIDATED'")
    Integer countAllocatedTicketsForEvent(@Bind("eventId") int eventId);

    @Query("update ticket set status = 'FREE' where event_id = :eventId and category_id in(:categoryId) and status = '"+RELEASED+"'")
    int revertToFreeForRestrictedCategories(@Bind("eventId") int eventId, @Bind("categoryId") List<Integer> categoryId);
}
