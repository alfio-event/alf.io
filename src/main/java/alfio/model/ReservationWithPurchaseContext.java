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

import alfio.model.support.JSONData;
import alfio.model.transaction.PaymentProxy;
import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static alfio.util.LocaleUtil.atZone;
import static alfio.util.MonetaryUtil.centsToUnit;

@Getter
public class ReservationWithPurchaseContext implements PriceContainer {
    private final String id;
    private final ZonedDateTime validity;
    private final TicketReservation.TicketReservationStatus status;
    private final ZonedDateTime purchaseContextStartDate;
    private final ZonedDateTime purchaseContextEndDate;
    private final ZonedDateTime confirmationTs;
    private final ZonedDateTime registrationTs;
    private final PaymentProxy paymentMethod;
    private final String invoiceNumber;
    private final PriceContainer.VatStatus vatStatus;
    private final BigDecimal vatPercentage;
    private final int srcPriceCts;
    private final int finalPriceCts;
    private final int vatCts;
    private final int discountCts;
    private final String currencyCode;

    private final PurchaseContext.PurchaseContextType purchaseContextType;
    private final String purchaseContextPublicIdentifier;
    private final Map<String, String> purchaseContextTitle;
    private final List<PurchaseContextItem> items;


    public ReservationWithPurchaseContext(@Column("tr_id") String id,
                                          @Column("tr_validity") ZonedDateTime validity,
                                          @Column("tr_status") TicketReservation.TicketReservationStatus status,
                                          @Column("tr_confirmation_ts") ZonedDateTime confirmationTs,
                                          @Column("tr_registration_ts") ZonedDateTime registrationTs,
                                          @Column("tr_payment_method") PaymentProxy paymentMethod,
                                          @Column("tr_invoice_number") String invoiceNumber,
                                          @Column("tr_vat_status") PriceContainer.VatStatus vatStatus,
                                          @Column("tr_used_vat_percent") BigDecimal vatPercentage,
                                          @Column("tr_src_price_cts") int srcPriceCts,
                                          @Column("tr_final_price_cts") int finalPriceCts,
                                          @Column("tr_vat_cts") int vatCts,
                                          @Column("tr_discount_cts") int discountCts,
                                          @Column("tr_currency_code") String currencyCode,
                                          @Column("pc_type") PurchaseContext.PurchaseContextType purchaseContextType,
                                          @Column("pc_public_identifier") String purchaseContextPublicIdentifier,
                                          @Column("pc_title") @JSONData Map<String, String> purchaseContextTitle,
                                          @Column("pc_time_zone") String purchaseContextTimezone,
                                          @Column("pc_start_date") ZonedDateTime purchaseContextStartDate,
                                          @Column("pc_end_date") ZonedDateTime purchaseContextEndDate,
                                          @Column("pc_items") String itemsJson) {
        var zoneId = ZoneId.of(purchaseContextTimezone);
        this.id = id;
        this.validity = atZone(validity, zoneId);
        this.status = status;
        this.confirmationTs = atZone(confirmationTs, zoneId);
        this.registrationTs = atZone(registrationTs, zoneId);
        this.paymentMethod = paymentMethod;
        this.invoiceNumber = invoiceNumber;
        this.vatStatus = vatStatus;
        this.vatPercentage = vatPercentage;
        this.srcPriceCts = srcPriceCts;
        this.finalPriceCts = finalPriceCts;
        this.vatCts = vatCts;
        this.discountCts = discountCts;
        this.currencyCode = currencyCode;
        this.purchaseContextType = purchaseContextType;
        this.purchaseContextPublicIdentifier = purchaseContextPublicIdentifier;
        this.purchaseContextTitle = purchaseContextTitle;
        this.purchaseContextStartDate = atZone(purchaseContextStartDate, zoneId);
        this.purchaseContextEndDate = atZone(purchaseContextEndDate, zoneId);
        this.items = Json.fromJson(itemsJson, new TypeReference<>() {});
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(vatPercentage);
    }

    @Override
    public BigDecimal getFinalPrice() {
        return centsToUnit(finalPriceCts, currencyCode);
    }

    @Override
    public BigDecimal getVAT() {
        return centsToUnit(vatCts, currencyCode);
    }

    @Override
    public BigDecimal getAppliedDiscount() {
        return centsToUnit(discountCts, currencyCode);
    }

    @AllArgsConstructor
    @Getter
    public static class PurchaseContextItem {
        private final String id;
        private final String firstName;
        private final String lastName;
        private final Map<String, String> type;
    }
}
