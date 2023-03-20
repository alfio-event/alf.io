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
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.With;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Getter
public class TicketReservation implements PriceContainer {

    public enum TicketReservationStatus {
        PENDING,
        IN_PAYMENT,
        EXTERNAL_PROCESSING_PAYMENT,
        WAITING_EXTERNAL_CONFIRMATION,
        OFFLINE_PAYMENT,
        DEFERRED_OFFLINE_PAYMENT,
        /**
         * Reservation is in the process of being finalized
         */
        FINALIZING,
        /**
         * Special finalization status for OFFLINE payment
         */
        OFFLINE_FINALIZING,
        COMPLETE,
        STUCK,
        CANCELLED,
        CREDIT_NOTE_ISSUED
    }

    private final String id;
    private final Date validity;
    private final TicketReservationStatus status;
    private final String fullName;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String billingAddress;
    private final ZonedDateTime confirmationTimestamp;
    private final ZonedDateTime latestReminder;
    private final PaymentProxy paymentMethod;
    private final Boolean reminderSent;
    private final Integer promoCodeDiscountId;
    private final boolean automatic;
    private final String userLanguage;
    private final boolean directAssignmentRequested;
    private final String invoiceNumber;
    @JsonIgnore
    private final String invoiceModel;
    @With
    private final PriceContainer.VatStatus vatStatus;
    private final String vatNr;
    private final String vatCountryCode;
    private final boolean invoiceRequested;
    private final BigDecimal usedVatPercent;
    private final Boolean vatIncluded;
    private final ZonedDateTime creationTimestamp;
    private final String customerReference;
    private final ZonedDateTime registrationTimestamp;


    private final int srcPriceCts;
    private final int finalPriceCts;
    private final int vatCts;
    private final int discountCts;
    private final String currencyCode;


    public TicketReservation(@Column("id") String id,
                             @Column("validity") Date validity,
                             @Column("status") TicketReservationStatus status,
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
                             @Column("currency_code") String currencyCode) {
        this.id = id;
        this.validity = validity;
        this.status = status;
        this.fullName = fullName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.billingAddress = billingAddress;
        this.confirmationTimestamp = confirmationTimestamp;
        this.latestReminder = latestReminder;
        this.paymentMethod = paymentMethod;
        this.reminderSent = reminderSent;
        this.promoCodeDiscountId = promoCodeDiscountId;
        this.automatic = automatic;
        this.userLanguage = userLanguage;
        this.directAssignmentRequested = directAssignmentRequested;
        this.invoiceNumber = invoiceNumber;
        this.invoiceModel = invoiceModel;
        this.vatStatus = vatStatus;
        this.vatNr = vatNr;
        this.vatCountryCode = vatCountryCode;
        this.invoiceRequested = invoiceRequested;
        this.usedVatPercent = usedVatPercent;
        this.vatIncluded = vatIncluded;
        this.creationTimestamp = creationTimestamp;
        this.registrationTimestamp = registrationTimestamp;
        this.customerReference = customerReference;

        this.srcPriceCts = Optional.ofNullable(srcPriceCts).orElse(0);
        this.finalPriceCts = Optional.ofNullable(finalPriceCts).orElse(0);
        this.vatCts = Optional.ofNullable(vatCts).orElse(0);
        this.discountCts = Optional.ofNullable(discountCts).orElse(0);
        this.currencyCode = currencyCode;
    }

    public boolean isStuck() {
        return status == TicketReservationStatus.STUCK;
    }

    public boolean isReminderSent() {
        return Optional.ofNullable(reminderSent).orElse(false);
    }

    public boolean getHasBillingAddress() {
        return StringUtils.isNotBlank(billingAddress);
    }

    public Optional<ZonedDateTime> latestNotificationTimestamp(ZoneId zoneId) {
        return Optional.ofNullable(latestReminder).map(d -> d.withZoneSameInstant(zoneId));
    }

    public String getFullName() {
        return (firstName != null && lastName != null) ? (firstName + " " + lastName) : fullName;
    }

    public boolean getHasInvoiceNumber() {
        return invoiceNumber != null;
    }

    public boolean getHasInvoiceOrReceiptDocument() {
        return invoiceModel != null;
    }

    public boolean getHasBeenPaid() {
        return status == TicketReservationStatus.COMPLETE && !EnumSet.of(PaymentProxy.NONE, PaymentProxy.ADMIN).contains(paymentMethod);
    }

    public boolean getHasVatNumber() {
        return StringUtils.isNotEmpty(vatNr);
    }

    public List<String> getLineSplittedBillingAddress() {
        if(billingAddress == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(StringUtils.split(billingAddress, '\n'));
    }

    public boolean isCancelled() {
        return status == TicketReservationStatus.CANCELLED;
    }

    public String getPaidAmount() {
        if(finalPriceCts > 0) {
            return MonetaryUtil.formatCents(finalPriceCts, currencyCode);
        }
        return null;
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(usedVatPercent);
    }

    public boolean isPendingOfflinePayment() {
        return status == TicketReservationStatus.OFFLINE_PAYMENT
            || status == TicketReservationStatus.DEFERRED_OFFLINE_PAYMENT;
    }
}
