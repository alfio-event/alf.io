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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public class PurchaseContextWithReservations {
    private final Map<String, String> title;
    private final String publicIdentifier;
    private final PurchaseContext.PurchaseContextType type;
    private final List<ReservationHeader> reservations;

    public static PurchaseContextWithReservations from(List<ReservationWithPurchaseContext> reservations) {
        Validate.isTrue(!reservations.isEmpty(), "Cannot build PurchaseContextWithReservation out of an empty list");
        var first = reservations.get(0);
        return new PurchaseContextWithReservations(first.getPurchaseContextTitle(),
            first.getPurchaseContextPublicIdentifier(),
            first.getPurchaseContextType(),
            reservations.stream().map(ReservationHeader::from).collect(Collectors.toList())
        );
    }
}
