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

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.math.BigDecimal;
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

    private final Integer srcPriceCts;

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
                             @Column("src_price_cts") Integer srcPriceCts) {
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
    }

    public ZonedDateTime getInception(ZoneId zoneId) {
        return Optional.ofNullable(utcInception).map(i -> i.withZoneSameInstant(zoneId)).orElseGet(() -> ZonedDateTime.now(zoneId).minus(1L, ChronoUnit.HOURS));
    }

    public ZonedDateTime getExpiration(ZoneId zoneId) {
        return Optional.ofNullable(utcExpiration).map(i -> i.withZoneSameInstant(zoneId)).orElseGet(() -> ZonedDateTime.now(zoneId).plus(1L, ChronoUnit.HOURS));
    }
}
