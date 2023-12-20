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
import alfio.model.support.JSONData;
import alfio.model.support.UserIdAndOrganizationId;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;

@QueryRepository
public interface TicketReservationRepository {

    @Query("insert into tickets_reservation(id, creation_ts, validity, promo_code_id_fk, status, user_language, event_id_fk, used_vat_percent, vat_included, currency_code, organization_id_fk, user_id_fk)" +
        " values (:id, :creationTimestamp, :validity, :promotionCodeDiscountId, 'PENDING', :userLanguage, :eventId, :vatPercentage, :vatIncluded, :currencyCode, :organizationId, :userId)")
    int createNewReservation(@Bind("id") String id,
                             @Bind("creationTimestamp") ZonedDateTime creationTimestamp,
                             @Bind("validity") Date validity,
                             @Bind("promotionCodeDiscountId") Integer promotionCodeDiscountId, @Bind("userLanguage") String userLanguage,
                             @Bind("eventId") Integer eventId, // <- optional
                             @Bind("vatPercentage") BigDecimal vatPercentage,
                             @Bind("vatIncluded") Boolean vatIncluded,
                             @Bind("currencyCode") String currencyCode,
                             @Bind("organizationId") int organizationId,
                             @Bind("userId") Integer userId);

    @Query("update tickets_reservation set user_id_fk = :userId where id = :reservationId")
    int setReservationOwner(@Bind("reservationId") String reservationId, @Bind("userId") Integer userId);

    @Query("select user_id_fk user_id, organization_id_fk organization_id from tickets_reservation where id = :reservationId and user_id_fk is not null")
    Optional<UserIdAndOrganizationId> getReservationOwnerAndOrganizationId(@Bind("reservationId") String reservationId);

    @Query("select * from tickets_reservation tr join ba_user u on u.id = tr.user_id_fk where u.username = :username")
    List<TicketReservation> loadByOwner(@Bind("username") String username);

    @Query("update tickets_reservation set status = :status, full_name = :fullName, first_name = :firstName, last_name = :lastName, email_address = :email," +
        " user_language = :userLanguage, billing_address = :billingAddress, confirmation_ts = :timestamp, payment_method = :paymentMethod, customer_reference = :customerReference where id = :reservationId")
    int updateTicketReservation(@Bind("reservationId") String reservationId,
                                @Bind("status") String status,
                                @Bind("email") String email,
                                @Bind("fullName") String fullName,
                                @Bind("firstName") String firstName,
                                @Bind("lastName") String lastName,
                                @Bind("userLanguage") String userLanguage,
                                @Bind("billingAddress") String billingAddress,
                                @Bind("timestamp") ZonedDateTime confirmationTimestamp,
                                @Bind("paymentMethod") String paymentMethod,
                                @Bind("customerReference") String customerReference);

    @Query("update tickets_reservation set validity = :validity, status = :status, payment_method = 'OFFLINE', full_name = :fullName, first_name = :firstName," +
        " last_name = :lastName, email_address = :email, billing_address = :billingAddress, customer_reference = :customerReference where id = :reservationId")
    int postponePayment(@Bind("reservationId") String reservationId, @Bind("status") TicketReservation.TicketReservationStatus status, @Bind("validity") Date validity, @Bind("email") String email,
                        @Bind("fullName") String fullName, @Bind("firstName") String firstName, @Bind("lastName") String lastName,
                        @Bind("billingAddress") String billingAddress,
                        @Bind("customerReference") String customerReference);

    @Query("update tickets_reservation set status = :status, confirmation_ts = :timestamp where id = :reservationId")
    int confirmOfflinePayment(@Bind("reservationId") String reservationId, @Bind("status") String status, @Bind("timestamp") ZonedDateTime timestamp);

    @Query("update tickets_reservation set full_name = :fullName where id = :reservationId")
    int updateAssignee(@Bind("reservationId") String reservationId, @Bind("fullName") String fullName);

    @Query("select count(id) from tickets_reservation where status in('OFFLINE_PAYMENT', 'DEFERRED_OFFLINE_PAYMENT') and event_id_fk = :eventId")
    Integer findAllReservationsWaitingForPaymentCountInEventId(@Bind("eventId") int eventId);

    @Query("select * from tickets_reservation where status = 'OFFLINE_PAYMENT' and date_trunc('day', validity) <= :expiration and offline_payment_reminder_sent = false for update skip locked")
    List<TicketReservation> findAllOfflinePaymentReservationForNotificationForUpdate(@Bind("expiration") Date expiration);

    @Query("select id, full_name, first_name, last_name, email_address, final_price_cts, currency_code, event_id_fk, validity from tickets_reservation where status = 'OFFLINE_PAYMENT' and date_trunc('day', validity) <= :expiration and event_id_fk = :eventId for update skip locked")
    List<TicketReservationInfo> findAllOfflinePaymentReservationWithExpirationBeforeForUpdate(@Bind("expiration") ZonedDateTime expiration, @Bind("eventId") int eventId);

    @Query("update tickets_reservation set offline_payment_reminder_sent = true where id = :reservationId")
    int flagAsOfflinePaymentReminderSent(@Bind("reservationId") String reservationId);

    @Query("update tickets_reservation set latest_reminder_ts = :latestReminderTimestamp where id = :reservationId")
    int updateLatestReminderTimestamp(@Bind("reservationId") String reservationId, @Bind("latestReminderTimestamp") ZonedDateTime latestReminderTimestamp);

    @Query("update tickets_reservation set validity = :validity where id = :reservationId")
    int updateValidity(@Bind("reservationId") String reservationId, @Bind("validity") Date validity);

    @Query("select id from tickets_reservation where id = :reservationId for update")
    String lockReservationForUpdate(@Bind("reservationId") String reservationId);

    @Query("update tickets_reservation set status = :status where id = :reservationId")
    int updateReservationStatus(@Bind("reservationId") String reservationId, @Bind("status") String status);

    @Query("update tickets_reservation set status = :status where id in (:reservationIds)")
    int updateReservationsStatus(@Bind("reservationIds") Collection<String> ids, @Bind("status") String status);

    @Query("update tickets_reservation set registration_ts = :registrationTimestamp where id = :reservationId")
    int updateRegistrationTimestamp(@Bind("reservationId") String id, @Bind("registrationTimestamp") ZonedDateTime registrationTimestamp);

    @Query("select * from tickets_reservation where id = :id")
    TicketReservation findReservationById(@Bind("id") String id);

    @Query("select * from tickets_reservation where id = :id for update")
    TicketReservation findReservationByIdForUpdate(@Bind("id") String id);

    @Query("select * from tickets_reservation where id = :id and event_id_fk = :eventId")
    Optional<TicketReservation> findOptionalReservationByIdAndEventId(@Bind("id") String reservationId, @Bind("eventId") int eventId);

    @Query("select * from tickets_reservation where id = :id")
    Optional<TicketReservation> findOptionalReservationById(@Bind("id") String id);

    @Query("select status, validated_for_overview from tickets_reservation where id = :id")
    Optional<TicketReservationStatusAndValidation> findOptionalStatusAndValidationById(@Bind("id") String id);

    @Query("select id from tickets_reservation where validity < :date and status = 'PENDING' for update skip locked")
    List<String> findExpiredReservationForUpdate(@Bind("date") Date date);

    @Query("select distinct tr.* from tickets_reservation tr join b_transaction tx on tx.reservation_id = tr.id where tr.id in (:reservationIds) and tr.status = 'PENDING'")
    List<TicketReservation> findReservationsWithPendingTransaction(@Bind("reservationIds") Collection<String> reservationIds);

    @Query("select id from tickets_reservation where validity < :date and status = 'OFFLINE_PAYMENT' for update skip locked")
    List<String> findExpiredOfflineReservationsForUpdate(@Bind("date") Date date);

    @Query("select id, event_id_fk from tickets_reservation where validity < :date and status in ('IN_PAYMENT', 'EXTERNAL_PROCESSING_PAYMENT') and event_id_fk is not null for update skip locked")
    List<ReservationIdAndEventId> findStuckReservationsForUpdate(@Bind("date") Date date);

    @Query("delete from tickets_reservation where id in (:ids)")
    int remove(@Bind("ids") List<String> ids);

    @Query("select * from tickets_reservation where id like :partialID")
    List<TicketReservation> findByPartialID(@Bind("partialID") String partialID);

    @Query("update tickets_reservation set invoice_model = :invoiceModel where id = :reservationId")
    int addReservationInvoiceOrReceiptModel(@Bind("reservationId") String reservationId, @Bind("invoiceModel") String invoiceModel);

    @Query("update tickets_reservation set invoice_number = :invoiceNumber where id = :reservationId and invoice_number is null")
    int setInvoiceNumber(@Bind("reservationId") String reservationId, @Bind("invoiceNumber") String invoiceNumber);


    @Query("update tickets_reservation set vat_status = :vatStatus, src_price_cts = :srcPriceCts, " +
        " final_price_cts = :finalPriceCts, vat_cts = :vatCts, discount_cts = :discountCts, currency_code = :currencyCode, " +
        " vat_nr = :vatNr, vat_country = :vatCountry, invoice_requested = :invoiceRequested where id = :reservationId")
    int updateBillingData(@Bind("vatStatus") PriceContainer.VatStatus vatStatus,
                          @Bind("srcPriceCts") int srcPriceCts,
                          @Bind("finalPriceCts") int finalPriceCts,
                          @Bind("vatCts") int vatCts,
                          @Bind("discountCts") int discountCts,
                          @Bind("currencyCode") String currencyCode,
                          @Bind("vatNr") String vatNr,
                          @Bind("vatCountry") String country,
                          @Bind("invoiceRequested") boolean invoiceRequested,
                          @Bind("reservationId") String reservationId);
    

    @Query("select min(confirmation_ts) from tickets_reservation where event_id_fk = :eventId and confirmation_ts is not null")
    Optional<ZonedDateTime> getFirstConfirmationTimestampForEvent(@Bind("eventId") int eventId);

    @Query("select min(creation_ts) from tickets_reservation where event_id_fk = :eventId")
    Optional<ZonedDateTime> getFirstReservationCreatedTimestampForEvent(@Bind("eventId") int eventId);

    @Query("select to_char(date_trunc(:granularity, d.day), 'YYYY-MM-DD') as day, count(ticket.id) ticket_count " +
        " from (select generate_series(lower(r), upper(r), '1 day')::date as day, generate_series(lower(r), upper(r), '1 day')::timestamp as ts from tsrange(:fromDate::timestamp, :toDate::timestamp) r) as d " +
        " left join (select id, confirmation_ts from tickets_reservation where confirmation_ts is not null and event_id_fk = :eventId) res on date_trunc('day', res.confirmation_ts) = d.day" +
        " left join ticket on event_id = :eventId and tickets_reservation_id = res.id"+
        " group by 1 order by 1")
    List<TicketsByDateStatistic> getSoldStatistic(@Bind("eventId") int eventId, @Bind("fromDate") ZonedDateTime from, @Bind("toDate") ZonedDateTime to, @Bind("granularity") String granularity);

    @Query("select to_char(date_trunc(:granularity, d.day), 'YYYY-MM-DD') as day, count(ticket.id) ticket_count " +
        " from (select generate_series(lower(r), upper(r), '1 day')::date as day, generate_series(lower(r), upper(r), '1 day')::timestamp as ts from tsrange(:fromDate::timestamp, :toDate::timestamp) r) as d " +
        " left join (select id, creation_ts from tickets_reservation where event_id_fk = :eventId and status in ('IN_PAYMENT', 'EXTERNAL_PROCESSING_PAYMENT', 'OFFLINE_PAYMENT', 'DEFERRED_OFFLINE_PAYMENT', 'COMPLETE', 'STUCK')) res on date_trunc('day', res.creation_ts) = d.day " +
        " left join ticket on event_id = :eventId and tickets_reservation_id = res.id"+
        " group by 1 order by 1")
    List<TicketsByDateStatistic> getReservedStatistic(@Bind("eventId") int eventId, @Bind("fromDate") ZonedDateTime from, @Bind("toDate") ZonedDateTime to, @Bind("granularity") String granularity);



    @Query("select id, event_id_fk from tickets_reservation where id in (:ids) and event_id_fk is not null")
    List<ReservationIdAndEventId> getReservationIdAndEventId(@Bind("ids") Collection<String> ids);

    @Query("select * from tickets_reservation where id in (:ids)")
    List<TicketReservation> findByIds(@Bind("ids") Collection<String> ids);

    @Query("update tickets_reservation set full_name = :fullName, first_name = :firstName, last_name = :lastName, email_address = :email, " +
        " billing_address = :completeBillingAddress, vat_country = :vatCountry, vat_nr = :vatNr, " +
        " invoice_requested = :invoiceRequested, " +
        " billing_address_company = :billingAddressCompany, " +
        " billing_address_line1 = :billingAddressLine1, " +
        " billing_address_line2 = :billingAddressLine2, " +
        " billing_address_zip = :billingAddressZip, " +
        " billing_address_city = :billingAddressCity, " +
        " billing_address_state = :billingAddressState, " +
        " add_company_billing_details = :addCompanyBillingDetails, " +
        " skip_vat_nr = :skipVatNr, " +
        " customer_reference = :customerReference, "+
        " validated_for_overview = :validated " +
        " where id = :reservationId")
    int updateTicketReservationWithValidation(@Bind("reservationId") String reservationId,
                                              @Bind("fullName") String fullName,
                                              @Bind("firstName") String firstName,
                                              @Bind("lastName") String lastName,
                                              @Bind("email") String email,
                                              @Bind("billingAddressCompany") String billingAddressCompany,
                                              @Bind("billingAddressLine1") String billingAddressLine1,
                                              @Bind("billingAddressLine2") String billingAddressLine2,
                                              @Bind("billingAddressZip") String billingAddressZip,
                                              @Bind("billingAddressCity") String billingAddressCity,
                                              @Bind("billingAddressState") String billingAddressState,
                                              @Bind("completeBillingAddress") String completeBillingAddress,
                                              @Bind("vatCountry") String vatCountry,
                                              @Bind("vatNr") String vatNr,
                                              @Bind("invoiceRequested") boolean invoiceRequested,
                                              @Bind("addCompanyBillingDetails") boolean addCompanyBillingDetails,
                                              @Bind("skipVatNr") boolean skipVatNr,
                                              @Bind("customerReference") String customerReference,
                                              @Bind("validated") boolean validated);


    @Query("select billing_address_company, billing_address_line1, billing_address_line2, " +
        " billing_address_zip, billing_address_city, billing_address_state, validated_for_overview, skip_vat_nr, " +
        " add_company_billing_details, invoicing_additional_information, vat_country, vat_nr " +
        " from tickets_reservation where id = :id")
    TicketReservationAdditionalInfo getAdditionalInfo(@Bind("id") String reservationId);

    @Query("select metadata from tickets_reservation where id = :id")
    @JSONData
    ReservationMetadata getMetadata(@Bind("id") String reservationId);

    @Query("select metadata->'finalized' = 'true' as finalized from tickets_reservation where id = :id")
    Boolean checkIfFinalized(@Bind("id") String reservationId);

    @Query("update tickets_reservation set metadata = :metadata::jsonb where id = :id")
    int setMetadata(@Bind("id") String reservationId, @Bind("metadata") @JSONData ReservationMetadata metadata);

    @Query("update tickets_reservation set validated_for_overview = :validated where id = :reservationId")
    int updateValidationStatus(@Bind("reservationId") String reservationId, @Bind("validated") boolean validated);

    @Query("update tickets_reservation set invoice_requested = :invoiceRequested, vat_status = :vatStatus, src_price_cts = :srcPriceCts, " +
        " final_price_cts = :finalPriceCts, vat_cts = :vatCts, discount_cts = :discountCts, currency_code = :currencyCode" +
        " where id = :reservationId")
    int resetVat(@Bind("reservationId") String reservationId,
                 @Bind("invoiceRequested") boolean invoiceRequested,
                 @Bind("vatStatus") PriceContainer.VatStatus vatStatus,
                 @Bind("srcPriceCts") int srcPriceCts,
                 @Bind("finalPriceCts") int finalPriceCts,
                 @Bind("vatCts") int vatCts,
                 @Bind("discountCts") int discountCts,
                 @Bind("currencyCode") String currencyCode);


    default Integer countTicketsInReservationForCategories(String reservationId, Collection<Integer> categories) {
        if(categories == null || categories.isEmpty()) {
            return this.countTicketsInReservationNoCategories(reservationId);
        } else {
            return this.countTicketsInReservationForExistingCategories(reservationId, categories);
        }
    }

    @Query("select count(b.id) from tickets_reservation a, ticket b where a.id = :reservationId and b.tickets_reservation_id = a.id" +
        " and (b.category_id in (:categories))")
    Integer countTicketsInReservationForExistingCategories(@Bind("reservationId") String reservationId, @Bind("categories") Collection<Integer> categories);

    @Query("select count(b.id) from tickets_reservation a, ticket b where a.id = :reservationId and b.tickets_reservation_id = a.id")
    Integer countTicketsInReservationNoCategories(@Bind("reservationId") String reservationId);

    @Query("select billing_address_company, billing_address_line1, billing_address_line2, billing_address_zip, billing_address_city, billing_address_state, vat_nr, vat_country, invoicing_additional_information from tickets_reservation where id = :reservationId")
    BillingDetails getBillingDetailsForReservation(@Bind("reservationId") String reservationId);

    @Query("update tickets_reservation set invoicing_additional_information = :info::json where id = :id")
    int updateInvoicingAdditionalInformation(@Bind("id") String reservationId, @Bind("info") String info);

    @Query("select event_id_fk from tickets_reservation where id = :id")
    Optional<Integer> findEventIdFor(@Bind("id") String reservationId);

    @Query("select exists (select * from tickets_reservation where id = :id and subscription_id_fk is not null)")
    boolean hasSubscriptionApplied(@Bind("id") String id);

    @Query("update tickets_reservation set subscription_id_fk = :subscriptionId where id = :reservationId")
    int applySubscription(@Bind("reservationId") String reservationId, @Bind("subscriptionId") UUID subscriptionId);

    @Query("select tr.*, e.short_name from tickets_reservation tr join event e on e.id = tr.event_id_fk where tr.subscription_id_fk = :subscriptionId and tr.status = 'COMPLETE'")
    List<TicketReservationWithEventIdentifier> findConfirmedReservationsBySubscriptionId(@Bind("subscriptionId") UUID subscriptionId);

    @Query("select * from reservation_with_purchase_context where tr_user_id_fk = :userId order by tr_creation_ts desc")
    List<ReservationWithPurchaseContext> findAllReservationsForUser(@Bind("userId") int userId);

    @Query("update tickets_reservation set vat_status = :vatStatus where id = :reservationId")
    int updateVatStatus(@Bind("reservationId") String reservationId,
                        @Bind("vatStatus") PriceContainer.VatStatus vatStatus);

    @Query("select count(id) from tickets_reservation where id in (:ids) and event_id_fk = :eventId")
    int countReservationsWithEventId(@Bind("ids") Set<String> reservationIds, @Bind("eventId") int eventId);

    @Query("select exists(select id from b_transaction where id = :transactionId and reservation_id = reservationId)")
    boolean hasReservationWithTransactionId(@Bind("reservationId") String reservationId, @Bind("transactionId") int transactionId);
}
