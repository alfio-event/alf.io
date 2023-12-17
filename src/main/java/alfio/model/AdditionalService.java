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

import alfio.util.ClockProvider;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;
import org.springframework.security.crypto.codec.Hex;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Getter
public class AdditionalService {


    public enum VatType {
        INHERITED,
        NONE,
        CUSTOM_INCLUDED,
        CUSTOM_EXCLUDED
    }

    public enum AdditionalServiceType {
        DONATION, SUPPLEMENT
    }

    public enum SupplementPolicy {
        MANDATORY_ONE_FOR_TICKET,
        OPTIONAL_UNLIMITED_AMOUNT,
        OPTIONAL_MAX_AMOUNT_PER_TICKET {
            @Override
            public boolean isValid(int quantity, AdditionalService as, int ticketsCount) {
                return quantity <= ticketsCount * as.getMaxQtyPerOrder();
            }
        },
        OPTIONAL_MAX_AMOUNT_PER_RESERVATION {
            @Override
            public boolean isValid(int quantity, AdditionalService as, int selectionCount) {
                return quantity <= as.getMaxQtyPerOrder();
            }
        };

        public boolean isValid(int quantity, AdditionalService as, int selectionCount) {
            return true;
        }
    }

    private final int id;
    private final int eventId;
    private final boolean fixPrice;
    private final int ordinal;
    private final int availableQuantity;
    private final int maxQtyPerOrder;
    private final ZonedDateTime utcInception;
    private final ZonedDateTime utcExpiration;
    private final BigDecimal vat;
    private final VatType vatType;
    private final AdditionalServiceType type;
    private final SupplementPolicy supplementPolicy;

    private final Integer srcPriceCts;
    private final String currencyCode;
    private final Integer availableItems;

    public AdditionalService(@Column("id") int id,
                             @Column("event_id_fk") int eventId,
                             @Column("fix_price") boolean fixPrice,
                             @Column("ordinal") int ordinal,
                             @Column("available_qty") int availableQuantity,
                             @Column("max_qty_per_order") int maxQtyPerOrder,
                             @Column("inception_ts") ZonedDateTime utcInception,
                             @Column("expiration_ts") ZonedDateTime utcExpiration,
                             @Column("vat") BigDecimal vat,
                             @Column("vat_type") VatType vatType,
                             @Column("src_price_cts") Integer srcPriceCts,
                             @Column("service_type") AdditionalServiceType type,
                             @Column("supplement_policy") SupplementPolicy supplementPolicy,
                             @Column("currency_code") String currencyCode,
                             @Column("available_count") Integer availableItems) {
        this.id = id;
        this.eventId = eventId;
        this.fixPrice = fixPrice;
        this.ordinal = ordinal;
        this.availableQuantity = availableQuantity;
        this.maxQtyPerOrder = maxQtyPerOrder;
        this.utcInception = utcInception;
        this.utcExpiration = utcExpiration;
        this.vat = vat;
        this.vatType = vatType;
        this.srcPriceCts = srcPriceCts;
        this.type = type;
        this.supplementPolicy = supplementPolicy;
        this.currencyCode = currencyCode;
        this.availableItems = availableItems;
    }

    public ZonedDateTime getInception(ZoneId zoneId) {
        return Optional.ofNullable(utcInception).map(i -> i.withZoneSameInstant(zoneId)).orElseGet(() -> ZonedDateTime.now(ClockProvider.clock().withZone(zoneId)).minus(1L, ChronoUnit.HOURS));
    }

    public ZonedDateTime getExpiration(ZoneId zoneId) {
        return Optional.ofNullable(utcExpiration).map(i -> i.withZoneSameInstant(zoneId)).orElseGet(() -> ZonedDateTime.now(ClockProvider.clock().withZone(zoneId)).plus(1L, ChronoUnit.HOURS));
    }

    public boolean getSaleable() {
        ZonedDateTime now = ZonedDateTime.now(ClockProvider.clock());
        return getUtcInception().isBefore(now) && getUtcExpiration().isAfter(now);
    }

    public String getChecksum() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Boolean.toString(fixPrice).getBytes(StandardCharsets.UTF_8));
            digest.update(Integer.toString(ordinal).getBytes(StandardCharsets.UTF_8));
            digest.update(Integer.toString(availableQuantity).getBytes(StandardCharsets.UTF_8));
            digest.update(Integer.toString(maxQtyPerOrder).getBytes(StandardCharsets.UTF_8));
            digest.update(utcInception.toString().getBytes(StandardCharsets.UTF_8));
            digest.update(utcExpiration.toString().getBytes(StandardCharsets.UTF_8));
            digest.update(Optional.ofNullable(vat).map(BigDecimal::toString).orElse("").getBytes(StandardCharsets.UTF_8));
            digest.update(vatType.name().getBytes(StandardCharsets.UTF_8));
            digest.update(type.name().getBytes(StandardCharsets.UTF_8));
            if (supplementPolicy != null) {
                digest.update(supplementPolicy.name().getBytes(StandardCharsets.UTF_8));
            }
            return new String(Hex.encode(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static PriceContainer.VatStatus getVatStatus(VatType vatType, PriceContainer.VatStatus eventVatStatus) {
        switch (vatType) {
            case INHERITED:
                return eventVatStatus;
            case NONE:
                return PriceContainer.VatStatus.NONE;
            case CUSTOM_EXCLUDED:
                return PriceContainer.VatStatus.NOT_INCLUDED;
            case CUSTOM_INCLUDED:
                return PriceContainer.VatStatus.INCLUDED;
            default:
                return PriceContainer.VatStatus.NOT_INCLUDED;
        }
    }
}
