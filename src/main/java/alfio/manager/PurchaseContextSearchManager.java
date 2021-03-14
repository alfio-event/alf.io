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
package alfio.manager;

import alfio.model.Event;
import alfio.model.PurchaseContext;
import alfio.model.TicketReservation;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.repository.TicketSearchRepository;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Component
@Transactional(readOnly = true)
@AllArgsConstructor
public class PurchaseContextSearchManager {

    private final TicketSearchRepository ticketSearchRepository;

    public Pair<List<TicketReservation>, Integer> findAllReservationsFor(PurchaseContext purchaseContext, Integer page, String search, List<TicketReservation.TicketReservationStatus> status) {
        final int pageSize = 50;
        int offset = page == null ? 0 : page * pageSize;
        String toSearch = StringUtils.trimToNull(search);
        toSearch = toSearch == null ? null : ("%" + toSearch + "%");
        List<String> toFilter = (status == null || status.isEmpty() ? Arrays.asList(TicketReservation.TicketReservationStatus.values()) : status).stream().map(TicketReservation.TicketReservationStatus::toString).collect(toList());
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            var event = (Event)purchaseContext;
            List<TicketReservation> reservationsForEvent = ticketSearchRepository.findReservationsForEvent(event.getId(), offset, pageSize, toSearch, toFilter);
            return Pair.of(reservationsForEvent, ticketSearchRepository.countReservationsForEvent(event.getId(), toSearch, toFilter));
        } else {
            var subscription = (SubscriptionDescriptor) purchaseContext;
            List<TicketReservation> reservationsForSubscription = ticketSearchRepository.findReservationsForSubscription(subscription.getId(), offset, pageSize, toSearch, toFilter);
            return Pair.of(reservationsForSubscription, ticketSearchRepository.countReservationsForSubscription(subscription.getId(), toSearch, toFilter));
        }
    }
}
