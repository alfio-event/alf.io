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
package alfio.model.modification;

import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public class PromoCodeDiscountModification {

    private final Integer organizationId;
    private final Integer eventId;
    private final String promoCode;
    private final DateTimeModification start;
    private final DateTimeModification end;
    private final BigDecimal discountAmount;
    private final DiscountType discountType;
    private final List<Integer> categories;
    private final Integer utcOffset;
    private final Integer maxUsage;
    private final String description;
    private final String emailReference;

    @JsonCreator
    public PromoCodeDiscountModification(
            @JsonProperty("organizationId") Integer organizationId,
            @JsonProperty("eventId") Integer eventId,
            @JsonProperty("promoCode") String promoCode,
            @JsonProperty("start") DateTimeModification start,
            @JsonProperty("end") DateTimeModification end,
            @JsonProperty("discountAmount") BigDecimal discountAmount,
            @JsonProperty("discountType") DiscountType discountType,
            @JsonProperty("categories") List<Integer> categories,
            @JsonProperty("utcOffset") Integer utcOffset,
            @JsonProperty("maxUsage") Integer maxUsage,
            @JsonProperty("description") String description,
            @JsonProperty("emailReference") String emailReference) {

        this.organizationId = organizationId;
        this.eventId = eventId;
        this.promoCode = promoCode;
        this.start = start;
        this.end = end;
        this.discountAmount = discountAmount;
        this.discountType = discountType;
        this.categories = Optional.ofNullable(categories).map(l -> l.stream().filter(Objects::nonNull).collect(Collectors.toList())).orElse(Collections.emptyList());
        this.utcOffset = utcOffset;
        this.maxUsage = maxUsage;
        this.description = description;
        this.emailReference = emailReference;
    }
    
    public int getDiscountAsPercent() {
        return discountAmount.intValue();
    }
    
    public int getDiscountInCents() {
        return MonetaryUtil.unitToCents(discountAmount);
    }
}
