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

import alfio.datamapper.Bind;
import alfio.datamapper.Query;
import alfio.datamapper.QueryRepository;
import alfio.datamapper.QueryType;
import alfio.model.Ticket;

import java.util.List;

@QueryRepository
public interface TicketRepository {

	String CONFIRMED = "'ACQUIRED', 'CHECKED_IN', 'TO_BE_PAID'";

	@Query(type = QueryType.TEMPLATE, value = "insert into ticket (uuid, creation, category_id, event_id, status, original_price_cts, paid_price_cts)"
			+ "values(:uuid, :creation, :categoryId, :eventId, :status, :originalPrice, :paidPrice)")
	String bulkTicketInitialization();

	@Query("select id from ticket where status = 'FREE' and category_id = :categoryId and event_id = :eventId and tickets_reservation_id is null order by id limit :amount for update")
	List<Integer> selectTicketInCategoryForUpdate(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId,	@Bind("amount") int amount);

    @Query("select id from ticket where status = 'FREE' and category_id = :categoryId and event_id = :eventId and tickets_reservation_id is null order by id desc limit :amount for update")
    List<Integer> lockTicketsToInvalidate(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId,	@Bind("amount") int amount);

    @Query("select count(*) from ticket where status in ("+CONFIRMED+") and category_id = :categoryId and event_id = :eventId and full_name is not null and email_address is not null")
    Integer countAssignedTickets(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

    @Query("select * from ticket where status in ('PENDING', 'ACQUIRED', 'TO_BE_PAID', 'CANCELLED', 'CHECKED_IN') and category_id = :categoryId and event_id = :eventId")
    List<Ticket> findAllModifiedTickets(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

	@Query("select count(*) from ticket where status in ('ACQUIRED', 'CHECKED_IN', 'PENDING') and category_id = :categoryId and event_id = :eventId")
	Integer countConfirmedAndPendingTickets(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);
    
    @Query("select count(*) from ticket where status not in (" + CONFIRMED + ", 'INVALIDATED', 'CANCELLED')  and category_id = :categoryId and event_id = :eventId")
    Integer countNotSoldTickets(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

	@Query("update ticket set tickets_reservation_id = :reservationId, status = 'PENDING', user_language = :userLanguage where id in (:reservedForUpdate)")
	int reserveTickets(@Bind("reservationId") String reservationId,	@Bind("reservedForUpdate") List<Integer> reservedForUpdate, @Bind("userLanguage") String userLanguage);
	
	@Query("update ticket set tickets_reservation_id = :reservationId, special_price_id_fk = :specialCodeId, user_language = :userLanguage, status = 'PENDING' where id = :ticketId")
	void reserveTicket(@Bind("reservationId")String transactionId, @Bind("ticketId") int ticketId, @Bind("specialCodeId") int specialCodeId, @Bind("userLanguage") String userLanguage);

	@Query("update ticket set status = :status where tickets_reservation_id = :reservationId")
	int updateTicketsStatusWithReservationId(@Bind("reservationId") String reservationId, @Bind("status") String status);
	
	@Query("update ticket set status = :status where uuid = :uuid")
	int updateTicketStatusWithUUID(@Bind("uuid") String uuid, @Bind("status") String status);

	@Query("update ticket set status = 'INVALIDATED' where id in (:ids)")
	int invalidateTickets(@Bind("ids") List<Integer> ids);

	@Query("update ticket set original_price_cts = :priceInCents, paid_price_cts = :priceInCents where event_id = :eventId and category_id = :categoryId")
	int updateTicketPrice(@Bind("categoryId") int categoryId, @Bind("eventId") int eventId, @Bind("priceInCents") int priceInCents);

	@Query("update ticket set status = 'FREE', tickets_reservation_id = null, special_price_id_fk = null where status in ('PENDING', 'OFFLINE_PAYMENT') "
			+ " and tickets_reservation_id in (:reservationIds)")
	int freeFromReservation(@Bind("reservationIds") List<String> reservationIds);

	@Query("select * from ticket where tickets_reservation_id = :reservationId order by category_id asc, uuid asc")
	List<Ticket> findTicketsInReservation(@Bind("reservationId") String reservationId);

    @Query("select count(*) from ticket where tickets_reservation_id = :reservationId")
    Integer countTicketsInReservation(@Bind("reservationId") String reservationId);
	
	@Query("select * from ticket where uuid = :uuid")
	Ticket findByUUID(@Bind("uuid") String uuid);

	@Query("update ticket set email_address = :email, full_name = :fullName where uuid = :ticketIdentifier")
	int updateTicketOwner(@Bind("ticketIdentifier") String ticketIdentifier, @Bind("email") String email, @Bind("fullName") String fullName);

	@Query("update ticket set locked_assignment = :lockedAssignment where id = :id and category_id = :categoryId")
	int toggleTicketLocking(@Bind("id") int ticketId, @Bind("categoryId") int categoryId, @Bind("lockedAssignment") boolean locked);
	
	@Query("update ticket set job_title = :jobTitle, company = :company, phone_number = :phoneNumber, address = :address, country = :country, tshirt_size = :tShirtSize, notes = :notes, user_language = :userLanguage where uuid = :ticketIdentifier")
	int updateOptionalTicketInfo(@Bind("ticketIdentifier") String ticketIdentifier,
								 @Bind("jobTitle") String jobTitle,
								 @Bind("company") String company,
								 @Bind("phoneNumber") String phoneNumber,
								 @Bind("address") String address,
								 @Bind("country") String country,
								 @Bind("tShirtSize") String tShirtSize,
								 @Bind("notes") String notes,
								 @Bind("userLanguage") String userLanguage);

	@Query("select * from ticket where id = :id and category_id = :categoryId")
	Ticket findById(@Bind("id") int ticketId, @Bind("categoryId") int categoryId);

	@Query("select * from ticket where special_price_id_fk = :specialPriceId")
	Ticket findBySpecialPriceId(@Bind("specialPriceId") int specialPriceId);

	@Query("update ticket set category_id = :targetCategoryId, original_price_cts = :originalPrice, paid_price_cts = :paidPrice where id in (:ticketIds)")
	int moveToAnotherCategory(@Bind("ticketIds") List<Integer> ticketIds, @Bind("targetCategoryId") int targetCategoryId, @Bind("originalPrice") int originalPrice, @Bind("paidPrice") int paidPrice);

	@Query("select * from ticket where category_id in (:categories) and status = 'PENDING'")
	List<Ticket> findPendingTicketsInCategories(@Bind("categories") List<Integer> categories);
	
	@Query("select * from ticket where event_id = :eventId and full_name is not null and email_address is not null")
	List<Ticket> findAllAssignedByEventId(@Bind("eventId") int eventId);

    @Query("select a.*, b.id from ticket a, tickets_reservation b where a.event_id = :eventId and a.status in(" + CONFIRMED + ") and a.tickets_reservation_id = b.id order by b.id")
    List<Ticket> findAllConfirmed(@Bind("eventId") int eventId);

    @Query("select * from ticket where event_id = :eventId and status in(" + CONFIRMED + ") and category_id = :categoryId")
    List<Ticket> findConfirmedByCategoryId(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

    @Query("select count(*) from ticket where event_id = :eventId and status in(" + CONFIRMED + ") and full_name is not null and email_address is not null")
	Integer countAllAssigned(@Bind("eventId") int eventId);

    @Query("select distinct tickets_reservation_id from ticket where event_id = :eventId and status in('ACQUIRED', 'TO_BE_PAID') and (full_name is null or email_address is null)")
    List<String> findAllReservationsConfirmedButNotAssigned(@Bind("eventId") int eventId);

}
