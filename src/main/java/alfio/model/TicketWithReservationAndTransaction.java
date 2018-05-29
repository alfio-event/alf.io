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
package alfio.model;

import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

@Getter
public class TicketWithReservationAndTransaction {


    private final Ticket ticket;
    private final TicketReservation ticketReservation;
    private final Optional<Transaction> transaction;


    public TicketWithReservationAndTransaction(@Column("t_id") Integer id,
                                               @Column("t_uuid") String uuid,
                                               @Column("t_creation") ZonedDateTime creation,
                                               @Column("t_category_id") Integer categoryId,
                                               @Column("t_status") String status,
                                               @Column("t_tickets_reservation_id") String ticketsReservationId,
                                               @Column("t_full_name") String fullName,
                                               @Column("t_first_name") String firstName,
                                               @Column("t_last_name") String lastName,
                                               @Column("t_email_address") String email,
                                               @Column("t_locked_assignment") Boolean lockedAssignment,
                                               @Column("t_user_language") String userLanguage,
                                               @Column("t_src_price_cts") Integer srcPriceCts,
                                               @Column("t_final_price_cts") Integer finalPriceCts,
                                               @Column("t_vat_cts") Integer vatCts,
                                               @Column("t_discount_cts") Integer discountCts,
                                               @Column("t_ext_reference") String extReference,
                                               //
                                               @Column("tr_id") String trId,
                                               @Column("tr_event_id") int eventId,
                                               @Column("tr_validity") Date validity,
                                               @Column("tr_status") TicketReservation.TicketReservationStatus trStatus,
                                               @Column("tr_full_name") String trFullName,
                                               @Column("tr_first_name") String trFirstName,
                                               @Column("tr_last_name") String trLastName,
                                               @Column("tr_email_address") String trEmail,
                                               @Column("tr_billing_address") String billingAddress,
                                               @Column("tr_confirmation_ts") ZonedDateTime confirmationTimestamp,
                                               @Column("tr_latest_reminder_ts") ZonedDateTime latestReminder,
                                               @Column("tr_payment_method") PaymentProxy paymentMethod,
                                               @Column("tr_offline_payment_reminder_sent") Boolean reminderSent,
                                               @Column("tr_promo_code_id_fk") Integer promoCodeDiscountId,
                                               @Column("tr_automatic") boolean automatic,
                                               @Column("tr_user_language") String trUserLanguage,
                                               @Column("tr_direct_assignment") boolean directAssignmentRequested,
                                               @Column("tr_invoice_number") String invoiceNumber,
                                               @Column("tr_invoice_model") String invoiceModel,
                                               @Column("tr_vat_status") PriceContainer.VatStatus vatStatus,
                                               @Column("tr_vat_nr") String vatNr,
                                               @Column("tr_vat_country") String vatCountryCode,
                                               @Column("tr_invoice_requested") boolean invoiceRequested,
                                               @Column("tr_used_vat_percent") BigDecimal usedVadPercent,
                                               @Column("tr_vat_included") Boolean vatIncluded,
                                               @Column("tr_creation_ts") ZonedDateTime reservationCreationTimestamp,
                                               @Column("tr_customer_reference") String customerReference,
                                               //
                                               @Column("bt_id") Integer btId,
                                               @Column("bt_gtw_tx_id") String transactionId,
                                               @Column("bt_gtw_payment_id") String paymentId,
                                               @Column("bt_reservation_id") String reservationId,
                                               @Column("bt_t_timestamp") ZonedDateTime timestamp,
                                               @Column("bt_price_cts") Integer priceInCents,
                                               @Column("bt_currency") String currency,
                                               @Column("bt_description") String description,
                                               @Column("bt_payment_proxy") String paymentProxy,
                                               @Column("bt_plat_fee") Long platformFee,
                                               @Column("bt_gtw_fee") Long gatewayFee
                                               ) {

        this.ticket = id != null ? new Ticket(id, uuid, creation, categoryId, status, eventId, ticketsReservationId,
            fullName, firstName, lastName, email, lockedAssignment, userLanguage,
            srcPriceCts, finalPriceCts, vatCts, discountCts, extReference) : null;


        this.ticketReservation = new TicketReservation(trId, validity, trStatus,
            trFullName, trFirstName, trLastName, trEmail,
            billingAddress, confirmationTimestamp, latestReminder, paymentMethod,
            reminderSent, promoCodeDiscountId, automatic, trUserLanguage,
            directAssignmentRequested, invoiceNumber, invoiceModel, vatStatus, vatNr, vatCountryCode, invoiceRequested,
            usedVadPercent, vatIncluded, reservationCreationTimestamp, customerReference);

        if(btId != null) {
            this.transaction = Optional.of(new Transaction(btId, transactionId, paymentId, reservationId,
                timestamp, priceInCents, currency, description, paymentProxy, Optional.ofNullable(platformFee).orElse(0L), Optional.ofNullable(gatewayFee).orElse(0L)));
        } else {
            this.transaction = Optional.empty();
        }

    }
}
