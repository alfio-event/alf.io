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

import alfio.model.EventStatistic;
import alfio.model.PromoCodeDiscount;
import alfio.util.ClockProvider;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class PromoCodeDiscountWithFormattedTimeAndAmount {

    @JsonIgnore
    @Delegate
    private final PromoCodeDiscount promo;
    @JsonIgnore
    private final ZoneId eventZoneId;
    private final String eventCurrency;

    public PromoCodeDiscountWithFormattedTimeAndAmount(PromoCodeDiscount promo, ZoneId eventZoneId, String eventCurrency) {
        this.promo = promo;
        this.eventZoneId = eventZoneId;
        this.eventCurrency = eventCurrency;
    }

    public boolean isCurrentlyValid() {
        return isCurrentlyValid(eventZoneId, ZonedDateTime.now(ClockProvider.clock().withZone(eventZoneId)));
    }
    
    public boolean isExpired() {
        return isExpired(eventZoneId, ZonedDateTime.now(ClockProvider.clock().withZone(eventZoneId)));
    }
    
    public String getFormattedStart() {
        return getUtcStart().withZoneSameInstant(eventZoneId).format(EventStatistic.JSON_DATE_FORMATTER);
    }

    public String getFormattedEnd() {
        return getUtcEnd().withZoneSameInstant(eventZoneId).format(EventStatistic.JSON_DATE_FORMATTER);
    }

    public String getFormattedDiscountAmount() {
        var currency = StringUtils.firstNonEmpty(eventCurrency, promo.getCurrencyCode());
        if(promo.getFixedAmount() && StringUtils.isNotBlank(currency)) {
            return MonetaryUtil.formatCents(promo.getDiscountAmount(), currency);
        }
        return null;
    }
}
