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

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@QueryRepository
public interface TicketReservationRepository {

    @Query("insert into tickets_reservation(id, creation_ts, validity, promo_code_id_fk, status, user_language, event_id_fk, used_vat_percent, vat_included, currency_code)" +
        " values (:id, :creationTimestamp, :validity, :promotionCodeDiscountId, 'PENDING', :userLanguage, :eventId, :eventVat, :vatIncluded, :currencyCode)")
    int createNewReservation(@Bind("id") String id,
                             @Bind("creationTimestamp") ZonedDateTime creationTimestamp,
                             @Bind("validity") Date validity,
                             @Bind("promotionCodeDiscountId") Integer promotionCodeDiscountId, @Bind("userLanguage") String userLanguage,
                             @Bind("eventId") int eventId,
                             @Bind("eventVat") BigDecimal eventVat,
                             @Bind("vatIncluded") Boolean vatIncluded,
                             @Bind("currencyCode") String currencyCode);

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
                                @Bind("timestamp") ZonedDateTime timestamp,
                                @Bind("paymentMethod") String paymentMethod,
                                @Bind("customerReference") String customerReference);

    @Query("update tickets_reservation set validity = :validity, status = 'OFFLINE_PAYMENT', payment_method = 'OFFLINE', full_name = :fullName, first_name = :firstName," +
        " last_name = :lastName, email_address = :email, billing_address = :billingAddress, customer_reference = :customerReference where id = :reservationId")
    int postponePayment(@Bind("reservationId") String reservationId, @Bind("validity") Date validity, @Bind("email") String email,
                        @Bind("fullName") String fullName, @Bind("firstName") String firstName, @Bind("lastName") String lastName,
                        @Bind("billingAddress") String billingAddress,
                        @Bind("customerReference") String customerReference);

    @Query("update tickets_reservation set status = :status, confirmation_ts = :timestamp where id = :reservationId")
    int confirmOfflinePayment(@Bind("reservationId") String reservationId, @Bind("status") String status, @Bind("timestamp") ZonedDateTime timestamp);

    @Query("update tickets_reservation set full_name = :fullName where id = :reservationId")
    int updateAssignee(@Bind("reservationId") String reservationId, @Bind("fullName") String fullName);

    @Query("select count(id) from tickets_reservation where status = 'OFFLINE_PAYMENT' and event_id_fk = :eventId")
    Integer findAllReservationsWaitingForPaymentCountInEventId(@Bind("eventId") int eventId);

    @Query("select * from tickets_reservation where status = 'OFFLINE_PAYMENT' and date_trunc('day', validity) <= :expiration and offline_payment_reminder_sent = false for update skip locked")
    List<TicketReservation> findAllOfflinePaymentReservationForNotificationForUpdate(@Bind("expiration") Date expiration);

    @Query("select id, full_name, first_name, last_name, email_address, event_id_fk, validity from tickets_reservation where status = 'OFFLINE_PAYMENT' and date_trunc('day', validity) <= :expiration and event_id_fk = :eventId for update skip locked")
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

    @Query("select * from tickets_reservation where id = :id")
    Optional<TicketReservation> findOptionalReservationById(@Bind("id") String id);

    @Query("select status, validated_for_overview from tickets_reservation where id = :id")
    Optional<TicketReservationStatusAndValidation> findOptionalStatusAndValidationById(@Bind("id") String id);

    @Query("select id from tickets_reservation where validity < :date and status = 'PENDING' for update skip locked")
    List<String> findExpiredReservationForUpdate(@Bind("date") Date date);

    @Query("select id from tickets_reservation where validity < :date and status = 'OFFLINE_PAYMENT' for update skip locked")
    List<String> findExpiredOfflineReservationsForUpdate(@Bind("date") Date date);

    @Query("select id from tickets_reservation where validity < :date and status in ('IN_PAYMENT', 'EXTERNAL_PROCESSING_PAYMENT') for update skip locked")
    List<String> findStuckReservationsForUpdate(@Bind("date") Date date);

    @Query("delete from tickets_reservation where id in (:ids)")
    int remove(@Bind("ids") List<String> ids);

    @Query("select * from tickets_reservation where id like :partialID")
    List<TicketReservation> findByPartialID(@Bind("partialID") String partialID);

    @Query("update tickets_reservation set invoice_model = :invoiceModel where id = :reservationId")
    int addReservationInvoiceOrReceiptModel(@Bind("reservationId") String reservationId, @Bind("invoiceModel") String invoiceModel);

    @Query("update tickets_reservation set invoice_number = :invoiceNumber where id = :reservationId")
    int setInvoiceNumber(@Bind("reservationId") String reservationId, @Bind("invoiceNumber") String invoiceNumber);

    @Query("select count(*) from tickets_reservation where invoice_number is not null and event_id_fk = :eventId")
    Integer countInvoices(@Bind("eventId") int eventId);


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
    

    @Query("select count(ticket.id) ticket_count, to_char(date_trunc('day', confirmation_ts), 'YYYY-MM-DD') as day from ticket " +
        "inner join tickets_reservation on tickets_reservation_id = tickets_reservation.id where " +
        "ticket.event_id = :eventId and " +
        "confirmation_ts is not null and " +
        "confirmation_ts >= :from and " +
        "confirmation_ts <= :to  group by day order by day asc")
    List<TicketsByDateStatistic> getSoldStatistic(@Bind("eventId") int eventId, @Bind("from") Date from, @Bind("to") Date to);

    @Query("select count(ticket.id) ticket_count, to_char(date_trunc('day', tickets_reservation.creation_ts), 'YYYY-MM-DD') as day from ticket " +
        "inner join tickets_reservation on tickets_reservation_id = tickets_reservation.id where " +
        "ticket.event_id = :eventId and " +
        "tickets_reservation.status in ('IN_PAYMENT', 'EXTERNAL_PROCESSING_PAYMENT', 'OFFLINE_PAYMENT', 'COMPLETE', 'STUCK') and " +
        "tickets_reservation.creation_ts >= :from and " +
        "tickets_reservation.creation_ts <= :to " +
        "group by day order by day asc")
    List<TicketsByDateStatistic> getReservedStatistic(@Bind("eventId") int eventId, @Bind("from") Date from, @Bind("to") Date to);



    @Query("select id, event_id_fk from tickets_reservation where id in (:ids)")
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
                                              @Bind("completeBillingAddress") String completeBillingAddress,
                                              @Bind("vatCountry") String vatCountry,
                                              @Bind("vatNr") String vatNr,
                                              @Bind("invoiceRequested") boolean invoiceRequested,
                                              @Bind("addCompanyBillingDetails") boolean addCompanyBillingDetails,
                                              @Bind("skipVatNr") boolean skipVatNr,
                                              @Bind("customerReference") String customerReference,
                                              @Bind("validated") boolean validated);


    @Query("select billing_address_company, billing_address_line1, billing_address_line2, " +
        " billing_address_zip, billing_address_city, validated_for_overview, skip_vat_nr, " +
        " add_company_billing_details, invoicing_additional_information, vat_country, vat_nr " +
        " from tickets_reservation where id = :id")
    TicketReservationAdditionalInfo getAdditionalInfo(@Bind("id") String reservationId);

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

    @Query("select billing_address_company, billing_address_line1, billing_address_line2, billing_address_zip, billing_address_city, vat_nr, vat_country, invoicing_additional_information from tickets_reservation where id = :reservationId")
    BillingDetails getBillingDetailsForReservation(@Bind("reservationId") String reservationId);

    @Query("update tickets_reservation set invoicing_additional_information = :info::json where id = :id")
    int updateInvoicingAdditionalInformation(@Bind("id") String reservationId, @Bind("info") String info);
}
