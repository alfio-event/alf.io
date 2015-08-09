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

import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.experimental.Delegate;
import alfio.model.PromoCodeDiscount;

public class PromoCodeDiscountWithFormattedTime {

    @JsonIgnore
    @Delegate
    private final PromoCodeDiscount promo;

    @JsonIgnore
    private final ZoneId eventZoneId;

    public PromoCodeDiscountWithFormattedTime(PromoCodeDiscount promo, ZoneId eventZoneId) {
        this.promo = promo;
        this.eventZoneId = eventZoneId;
    }

    public boolean isCurrentlyValid() {
        return isCurrentlyValid(eventZoneId, ZonedDateTime.now(eventZoneId));
    }
    
    public boolean isExpired() {
        return isExpired(eventZoneId, ZonedDateTime.now(eventZoneId));
    }
    
    public String getFormattedStart() {
        return getUtcStart().withZoneSameInstant(eventZoneId).format(EventWithStatistics.JSON_DATE_FORMATTER);
    }

    public String getFormattedEnd() {
        return getUtcEnd().withZoneSameInstant(eventZoneId).format(EventWithStatistics.JSON_DATE_FORMATTER);
    }
}
