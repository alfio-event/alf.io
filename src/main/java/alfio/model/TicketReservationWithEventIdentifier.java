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
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Date;

public class TicketReservationWithEventIdentifier {
    @Delegate
    private final TicketReservation reservation;
    private final String eventPublicIdentifier;

    public TicketReservationWithEventIdentifier(
        @Column("id") String id,
        @Column("validity") Date validity,
        @Column("status") TicketReservation.TicketReservationStatus status,
        @Column("full_name") String fullName,
        @Column("first_name") String firstName,
        @Column("last_name") String lastName,
        @Column("email_address") String email,
        @Column("billing_address") String billingAddress,
        @Column("confirmation_ts") ZonedDateTime confirmationTimestamp,
        @Column("latest_reminder_ts") ZonedDateTime latestReminder,
        @Column("payment_method") PaymentProxy paymentMethod,
        @Column("offline_payment_reminder_sent") Boolean reminderSent,
        @Column("promo_code_id_fk") Integer promoCodeDiscountId,
        @Column("automatic") boolean automatic,
        @Column("user_language") String userLanguage,
        @Column("direct_assignment") boolean directAssignmentRequested,
        @Column("invoice_number") String invoiceNumber,
        @Column("invoice_model") String invoiceModel,
        @Column("vat_status") PriceContainer.VatStatus vatStatus,
        @Column("vat_nr") String vatNr,
        @Column("vat_country") String vatCountryCode,
        @Column("invoice_requested") boolean invoiceRequested,
        @Column("used_vat_percent") BigDecimal usedVatPercent,
        @Column("vat_included") Boolean vatIncluded,
        @Column("creation_ts") ZonedDateTime creationTimestamp,
        @Column("customer_reference") String customerReference,
        @Column("registration_ts") ZonedDateTime registrationTimestamp,
        @Column("src_price_cts") Integer srcPriceCts,
        @Column("final_price_cts") Integer finalPriceCts,
        @Column("vat_cts") Integer vatCts,
        @Column("discount_cts") Integer discountCts,
        @Column("currency_code") String currencyCode,
        @Column("short_name") String eventPublicIdentifier) {

        this.eventPublicIdentifier = eventPublicIdentifier;
        this.reservation = new TicketReservation(id,
            validity,
            status,
            fullName,
            firstName,
            lastName,
            email,
            billingAddress,
            confirmationTimestamp,
            latestReminder,
            paymentMethod,
            reminderSent,
            promoCodeDiscountId,
            automatic,
            userLanguage,
            directAssignmentRequested,
            invoiceNumber,
            invoiceModel,
            vatStatus,
            vatNr,
            vatCountryCode,
            invoiceRequested,
            usedVatPercent,
            vatIncluded,
            creationTimestamp,
            customerReference,
            registrationTimestamp,
            srcPriceCts,
            finalPriceCts,
            vatCts,
            discountCts,
            currencyCode);
    }

    public String getEventPublicIdentifier() {
        return eventPublicIdentifier;
    }
}
