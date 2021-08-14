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
package alfio.controller.api.v2.model;

import alfio.model.PurchaseContext;
import alfio.model.ReservationWithPurchaseContext;
import alfio.util.LocaleUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public class PurchaseContextWithReservations {
    private final Map<String, String> title;
    private final String publicIdentifier;
    private final PurchaseContext.PurchaseContextType type;
    private final Map<String, String> formattedStartDate;
    private final Map<String, String> formattedEndDate;
    private final boolean sameDay;
    private final List<ReservationHeader> reservations;

    public static PurchaseContextWithReservations from(List<ReservationWithPurchaseContext> reservations,
                                                       Map<Locale, String> datePatternsMap) {
        Validate.isTrue(!reservations.isEmpty(), "Cannot build PurchaseContextWithReservation out of an empty list");
        var first = reservations.get(0);
        return new PurchaseContextWithReservations(first.getPurchaseContextTitle(),
            first.getPurchaseContextPublicIdentifier(),
            first.getPurchaseContextType(),
            LocaleUtil.formatDate(first.getPurchaseContextStartDate(), datePatternsMap),
            LocaleUtil.formatDate(first.getPurchaseContextEndDate(), datePatternsMap),
            isSameDay(first.getPurchaseContextStartDate(), first.getPurchaseContextEndDate()),
            reservations.stream().map(r -> ReservationHeader.from(r, datePatternsMap)).collect(Collectors.toList())
        );
    }

    private static boolean isSameDay(ZonedDateTime startDate, ZonedDateTime endDate) {
        return startDate != null
            && endDate != null
            && startDate.truncatedTo(ChronoUnit.DAYS).equals(endDate.truncatedTo(ChronoUnit.DAYS));
    }

}
