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

import alfio.model.support.EventBasicInfo;
import alfio.model.support.ReservationInfo;
import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PromoCodeUsageResult {

    private final String promoCode;
    private final EventBasicInfo event;
    private final List<ReservationInfo> reservations;

    public PromoCodeUsageResult(@Column("promo_code") String promoCode,
                                @Column("event_short_name") String eventShortName,
                                @Column("event_display_name") String eventDisplayName,
                                @Column("reservations") String reservationsJson) {
        this.promoCode = promoCode;
        this.event = new EventBasicInfo(eventShortName, eventDisplayName);
        List<ReservationInfo> parsed = Json.fromJson(reservationsJson, new TypeReference<>() {});
        this.reservations = parsed.stream()
            .sorted(Comparator.comparing(ReservationInfo::getConfirmationTimestamp))
            .collect(Collectors.toList());
    }

    public String getPromoCode() {
        return promoCode;
    }

    public EventBasicInfo getEvent() {
        return event;
    }

    public List<ReservationInfo> getReservations() {
        return reservations;
    }

}
