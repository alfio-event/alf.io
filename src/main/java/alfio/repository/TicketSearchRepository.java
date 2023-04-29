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

import alfio.model.ReservationPaymentDetail;
import alfio.model.TicketReservation;
import alfio.model.TicketReservationWithTransaction;
import alfio.model.TicketWithReservationAndTransaction;
import alfio.model.poll.PollParticipant;
import alfio.model.support.Array;
import alfio.model.transaction.Transaction;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@QueryRepository
public interface TicketSearchRepository {

    String BASE_FILTER = ":search is null or (lower(tr_id) like lower(:search) or lower(t_uuid) like lower(:search) or lower(t_full_name) like lower(:search) or lower(t_first_name) like lower(:search) or lower(t_last_name) like lower(:search) or lower(t_email_address) like lower(:search) or " +
        "  lower(tr_full_name) like lower(:search) or lower(tr_first_name) like lower(:search) or lower(tr_last_name) like lower(:search) or lower(tr_email_address) like lower(:search) or lower(tr_customer_reference) like lower(:search) " +
        "  or (tr_invoice_number is not null and lower(tr_invoice_number) like lower(:search)) )";

    String APPLY_FILTER = " (" + BASE_FILTER + " or lower(promo_code) like lower(:search) or lower(special_price_token) like lower(:search)) ";

    String APPLY_FILTER_SUBSCRIPTION = " (:search is null or (lower(tr_id) like lower(:search) or lower(s_id::text) like lower(:search) or lower(s_first_name) like lower(:search) or lower(s_last_name) like lower(:search) or lower(s_email_address) like lower(:search) " +
        "  or lower(tr_first_name) like lower(:search) or lower(tr_last_name) like lower(:search) or lower(tr_email_address) like lower(:search) or lower(tr_customer_reference) like lower(:search) or lower(promo_code) like lower(:search) )) ";

    String FIND_ALL_MODIFIED_TICKETS_WITH_RESERVATION_AND_TRANSACTION = "select * from reservation_and_ticket_and_tx where t_id is not null and t_status in ('PENDING', 'ACQUIRED', 'TO_BE_PAID', 'CANCELLED', 'CHECKED_IN') and t_category_id = :categoryId and t_event_id = :eventId and " + APPLY_FILTER;

    String FIND_ALL_CONFIRMED_TICKETS_FOR_EVENT = "select * from reservation_and_ticket_and_tx where t_id is not null and t_status in ('ACQUIRED', 'TO_BE_PAID', 'CHECKED_IN') and t_event_id = :eventId and " + APPLY_FILTER;

    String FIND_ALL_PAYMENTS_FOR_EVENT = "select * from reservation_and_ticket_and_tx where tr_status in (:reservationStatus) and tr_payment_method in (:paymentMethods) and t_id is not null and t_status in ('ACQUIRED', 'TO_BE_PAID', 'CHECKED_IN') and t_event_id = :eventId and " + APPLY_FILTER;

    String FIND_ALL_TICKETS_INCLUDING_NEW = "select * from reservation_and_ticket_and_tx where tr_event_id = :eventId and tr_id is not null and tr_status in (:status) and " + APPLY_FILTER;

    String FIND_ALL_SUBSCRIPTION_INCLUDING_NEW = "select * from reservation_and_subscription_and_tx where s_descriptor_id = :subscriptionDescriptorId::uuid and tr_id is not null and tr_status in (:status) and " + APPLY_FILTER_SUBSCRIPTION;

    String RESERVATION_FIELDS = "tr_id id, tr_validity validity, tr_status status, tr_full_name full_name, tr_first_name first_name, tr_last_name last_name, tr_email_address email_address," +
        "tr_billing_address billing_address, tr_confirmation_ts confirmation_ts, tr_latest_reminder_ts latest_reminder_ts, tr_payment_method payment_method," +
        "tr_offline_payment_reminder_sent offline_payment_reminder_sent, tr_promo_code_id_fk promo_code_id_fk, tr_automatic automatic," +
        "tr_user_language user_language, tr_direct_assignment direct_assignment, tr_invoice_number invoice_number, tr_invoice_model invoice_model," +
        "tr_vat_status vat_status, tr_vat_nr vat_nr, tr_vat_country vat_country, tr_invoice_requested invoice_requested, tr_used_vat_percent used_vat_percent," +
        "tr_vat_included vat_included, tr_creation_ts creation_ts, tr_registration_ts registration_ts, tr_customer_reference customer_reference, tr_billing_address_company billing_address_company, tr_invoicing_additional_information invoicing_additional_information," +
        "tr_src_price_cts src_price_cts, tr_final_price_cts final_price_cts, tr_vat_cts vat_cts, tr_discount_cts discount_cts, tr_currency_code currency_code ";

    String RESERVATION_SEARCH_FIELD = "tr_id, tr_validity, tr_status, tr_full_name, tr_first_name, tr_last_name, tr_email_address, tr_billing_address, tr_confirmation_ts, tr_latest_reminder_ts, tr_payment_method, tr_offline_payment_reminder_sent, tr_promo_code_id_fk," +
        " tr_automatic, tr_user_language, tr_direct_assignment, tr_invoice_number, tr_invoice_model, tr_vat_status, tr_vat_nr, tr_vat_country, tr_invoice_requested, tr_used_vat_percent, tr_vat_included, tr_creation_ts, tr_registration_ts, tr_customer_reference," +
        " tr_billing_address_company, tr_invoicing_additional_information, tr_billing_address_line1, tr_billing_address_line2, tr_billing_address_city, tr_billing_address_state, tr_billing_address_zip, tickets_count," +
        " tr_src_price_cts, tr_final_price_cts, tr_vat_cts, tr_discount_cts, tr_currency_code ";

    String TRANSACTION_FIELDS = "bt_id, bt_gtw_tx_id, bt_gtw_payment_id, bt_reservation_id, bt_t_timestamp, bt_price_cts, bt_currency, bt_description, bt_payment_proxy, bt_gtw_fee, bt_plat_fee, bt_status, bt_metadata";

    String PROMO_CODE_FIELDS = "promo_code, special_price_token";


    @Query("select * from (" + FIND_ALL_MODIFIED_TICKETS_WITH_RESERVATION_AND_TRANSACTION + " limit :pageSize offset :page) as d_tbl order by tr_confirmation_ts asc, tr_id, t_uuid")
    List<TicketWithReservationAndTransaction> findAllModifiedTicketsWithReservationAndTransaction(@Bind("eventId") int eventId,
                                                                                                  @Bind("categoryId") int categoryId,
                                                                                                  @Bind("page") int page,
                                                                                                  @Bind("pageSize") int pageSize,
                                                                                                  @Bind("search") String search);

    @Query("select d_tbl.t_id as t_id, d_tbl.t_first_name as t_first_name, d_tbl.t_last_name as t_last_name, d_tbl.t_email_address as t_email_address, tc.name as tc_name from (" + FIND_ALL_CONFIRMED_TICKETS_FOR_EVENT + " and not (:tags::text[] && t_tags)  limit :maxResults) as d_tbl " +
        " join ticket_category tc on d_tbl.t_category_id = tc.id order by tr_confirmation_ts asc, tr_id, t_uuid")
    List<PollParticipant> filterConfirmedTicketsInEventForPoll(@Bind("eventId") int eventId,
                                                               @Bind("maxResults") int maxResults,
                                                               @Bind("search") String search,
                                                               @Bind("tags") @Array List<String> tagsToFilter);

    @Query("select count(*) from (" + FIND_ALL_MODIFIED_TICKETS_WITH_RESERVATION_AND_TRANSACTION +" ) as d_tbl")
    Integer countAllModifiedTicketsWithReservationAndTransaction(@Bind("eventId") int eventId,
                                                                 @Bind("categoryId") int categoryId,
                                                                 @Bind("search") String search);

    @Query("select distinct "+RESERVATION_FIELDS+" from (" + FIND_ALL_TICKETS_INCLUDING_NEW + ") as d_tbl order by tr_confirmation_ts desc nulls last, tr_validity limit :pageSize offset :page")
    List<TicketReservation> findReservationsForEvent(@Bind("eventId") int eventId,
                                                     @Bind("page") int page,
                                                     @Bind("pageSize") int pageSize,
                                                     @Bind("search") String search,
                                                     @Bind("status") List<String> toFilter);

    @Query("select distinct tr_id, tr_first_name, tr_last_name, tr_email_address, tr_payment_method, bt_price_cts, bt_currency, bt_t_timestamp, bt_metadata ->> '"+ Transaction.NOTES_KEY + "'" +
        " as bt_notes, tr_invoice_number from (" + FIND_ALL_PAYMENTS_FOR_EVENT + ") as d_tbl order by bt_t_timestamp desc nulls last limit :pageSize offset :page")
    List<ReservationPaymentDetail> findAllPaymentsForEvent(@Bind("eventId") int eventId,
                                                           @Bind("page") int page,
                                                           @Bind("pageSize") int pageSize,
                                                           @Bind("search") String search,
                                                           @Bind("reservationStatus") List<String> toFilter,
                                                           @Bind("paymentMethods") List<String> paymentMethods);

    @Query("select distinct tr_id, tr_first_name, tr_last_name, tr_email_address, tr_payment_method, bt_price_cts, bt_currency, bt_t_timestamp, bt_metadata ->> '"+ Transaction.NOTES_KEY + "'" +
        " as bt_notes, tr_invoice_number from (" + FIND_ALL_PAYMENTS_FOR_EVENT + ") as d_tbl order by bt_t_timestamp desc nulls last")
    List<ReservationPaymentDetail> findAllEventPaymentsForExport(@Bind("eventId") int eventId,
                                                                 @Bind("search") String search,
                                                                 @Bind("reservationStatus") List<String> toFilter,
                                                                 @Bind("paymentMethods") List<String> paymentMethods);

    @Query("select distinct "+RESERVATION_FIELDS+" from (" + FIND_ALL_SUBSCRIPTION_INCLUDING_NEW + ") as d_tbl order by tr_confirmation_ts desc nulls last, tr_validity limit :pageSize offset :page")
    List<TicketReservation> findReservationsForSubscription(@Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId,
                                                            @Bind("page") int page,
                                                            @Bind("pageSize") int pageSize,
                                                            @Bind("search") String search,
                                                            @Bind("status") List<String> toFilter);

    @Query("select distinct on(tr_id) "+RESERVATION_SEARCH_FIELD+", "+TRANSACTION_FIELDS+"," +PROMO_CODE_FIELDS+" from reservation_and_ticket_and_tx where tr_event_id = :eventId and tr_id is not null and tr_status = 'OFFLINE_PAYMENT' and bt_reservation_id is not null and bt_status = 'PENDING'")
    List<TicketReservationWithTransaction> findOfflineReservationsWithPendingTransaction(@Bind("eventId") int eventId);

    @Query("select distinct on(tr_id) "+RESERVATION_SEARCH_FIELD+", "+TRANSACTION_FIELDS+"," +PROMO_CODE_FIELDS+" from reservation_and_ticket_and_tx where tr_id in (:reservationIds) and tr_status = 'OFFLINE_PAYMENT' and bt_reservation_id is not null")
    List<TicketReservationWithTransaction> findOfflineReservationsWithTransaction(@Bind("reservationIds") List<String> reservationIds);

    @Query("select distinct on(tr_id) "+RESERVATION_SEARCH_FIELD+", "+TRANSACTION_FIELDS+"," +PROMO_CODE_FIELDS+" from reservation_and_ticket_and_tx where tr_event_id = :eventId and tr_id is not null and tr_status in('OFFLINE_PAYMENT', 'DEFERRED_OFFLINE_PAYMENT')")
    List<TicketReservationWithTransaction> findOfflineReservationsWithOptionalTransaction(@Bind("eventId") int eventId);

    @Query("select distinct on(tr_id) "+RESERVATION_SEARCH_FIELD+", "+TRANSACTION_FIELDS+"," +PROMO_CODE_FIELDS+" from reservation_and_ticket_and_tx where tr_id in (:reservationIds)")
    List<TicketReservationWithTransaction> findAllReservationsById(@Bind("reservationIds") Collection<String> reservationIds);

    @Query("select count(distinct tr_id) from (" + FIND_ALL_TICKETS_INCLUDING_NEW +" ) as d_tbl")
    Integer countReservationsForEvent(@Bind("eventId") int eventId,
                                      @Bind("search") String search,
                                      @Bind("status") List<String> toFilter);

    @Query("select count(distinct tr_id) from (" + FIND_ALL_SUBSCRIPTION_INCLUDING_NEW +" ) as d_tbl")
    Integer countReservationsForSubscription(@Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId,
                                             @Bind("search") String search,
                                             @Bind("status") List<String> toFilter);

    @Query("select count(distinct tr_id) from (" + FIND_ALL_PAYMENTS_FOR_EVENT +") as d_tbl")
    Integer countConfirmedPaymentsForEvent(@Bind("eventId") int eventId,
                                           @Bind("search") String search,
                                           @Bind("reservationStatus") List<String> toFilter,
                                           @Bind("paymentMethods") List<String> paymentMethods);

    @Query("select * from reservation_and_ticket_and_tx where tr_event_id = :eventId and tickets_count > 0 and tr_id in (:reservationIds)")
    List<TicketWithReservationAndTransaction> loadAllReservationsWithTickets(@Bind("eventId") int eventId, @Bind("reservationIds") Collection<String> reservationIds);

}
