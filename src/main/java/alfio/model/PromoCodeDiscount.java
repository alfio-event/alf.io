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

import alfio.util.Json;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Getter
public class PromoCodeDiscount {

    public enum CodeType {
        DISCOUNT,
        ACCESS,
        DYNAMIC
    }
    
    public enum DiscountType {
        FIXED_AMOUNT, PERCENTAGE, FIXED_AMOUNT_RESERVATION, NONE;

        public static boolean isFixedAmount(DiscountType discountType) {
            return discountType == FIXED_AMOUNT || discountType == FIXED_AMOUNT_RESERVATION;
        }
    }

    private final int id;
    private final String promoCode;
    private final Integer eventId;
    private final Integer organizationId;
    private final ZonedDateTime utcStart;
    private final ZonedDateTime utcEnd;
    private final int discountAmount;
    private final DiscountType discountType;
    private final Set<Integer> categories;
    private final Integer maxUsage;
    private final String description;
    private final String emailReference;
    private final CodeType codeType;
    private final Integer hiddenCategoryId;
    private final String currencyCode;
    
    public PromoCodeDiscount(@Column("id")int id, 
                             @Column("promo_code") String promoCode,
                             @Column("event_id_fk") Integer eventId,
                             @Column("organization_id_fk") Integer organizationId,
                             @Column("valid_from") ZonedDateTime utcStart,
                             @Column("valid_to") ZonedDateTime utcEnd,
                             @Column("discount_amount") int discountAmount,
                             @Column("discount_type") DiscountType discountType,
                             @Column("categories") String categories,
                             @Column("max_usage") Integer maxUsage,
                             @Column("description") String description,
                             @Column("email_reference") String emailReference,
                             @Column("code_type") CodeType codeType,
                             @Column("hidden_category_id") Integer hiddenCategoryId,
                             @Column("currency_code") String currencyCode) {

        this.id = id;
        this.promoCode = promoCode;
        this.eventId = eventId;
        this.organizationId = organizationId;
        this.utcStart = utcStart;
        this.utcEnd = utcEnd;
        this.discountAmount = discountAmount;
        this.discountType = discountType;
        this.maxUsage = maxUsage;
        if(categories != null) {
            List<Integer> categoriesId = Json.GSON.fromJson(categories, new TypeToken<List<Integer>>(){}.getType());
            this.categories = categoriesId == null ? Collections.emptySet() : new TreeSet<>(categoriesId);
        } else {
            this.categories = Collections.emptySet();
        }
        this.description = description;
        this.emailReference = emailReference;
        this.codeType = codeType;
        this.hiddenCategoryId = hiddenCategoryId;
        this.currencyCode = currencyCode;
    }
    
    public boolean isCurrentlyValid(ZoneId eventZoneId, ZonedDateTime now) {
        return utcStart.withZoneSameInstant(eventZoneId).isBefore(now) && utcEnd.withZoneSameInstant(eventZoneId).isAfter(now);
    }
    
    public boolean isExpired(ZoneId eventZoneId, ZonedDateTime now) {
        return now.isAfter(utcEnd.withZoneSameInstant(eventZoneId));
    }
    
    public boolean getFixedAmount() {
        return DiscountType.FIXED_AMOUNT == discountType || DiscountType.FIXED_AMOUNT_RESERVATION == discountType;
    }

    public static Set<Integer> categoriesOrNull(PromoCodeDiscount code) {
        if(code.codeType == CodeType.DYNAMIC) {
            return null;
        }
        if(code.codeType == CodeType.DISCOUNT) {
            Set<Integer> categories = code.getCategories();
            return CollectionUtils.isEmpty(categories) ? null : categories;
        }
        return Set.of(code.hiddenCategoryId);
    }

    public boolean isDynamic() {
        return codeType == CodeType.DYNAMIC;
    }

    public boolean hasCurrencyCode() {
        return StringUtils.length(currencyCode) == 3;
    }

    public static String format(PromoCodeDiscount discount, String eventCurrencyCode) {
        if(discount.getDiscountType() == DiscountType.PERCENTAGE) {
            return Integer.toString(discount.getDiscountAmount());
        }
        return MonetaryUtil.formatCents(discount.getDiscountAmount(), eventCurrencyCode);
    }

    public static boolean supportsCurrencyCode(CodeType codeType, DiscountType discountType) {
        return codeType == CodeType.DISCOUNT && discountType != DiscountType.PERCENTAGE;
    }
}
