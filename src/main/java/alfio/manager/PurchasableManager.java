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

import alfio.model.Purchasable;
import alfio.repository.EventRepository;
import alfio.repository.SubscriptionRepository;
import alfio.repository.TicketReservationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
@Component
public class PurchasableManager {

    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TicketReservationRepository ticketReservationRepository;

    public PurchasableManager(EventRepository eventRepository,
                              SubscriptionRepository subscriptionRepository,
                              TicketReservationRepository ticketReservationRepository) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.ticketReservationRepository = ticketReservationRepository;
    }

    public Optional<? extends Purchasable> findBy(Purchasable.PurchasableType purchasableType, String publicIdentifier) {
        switch (purchasableType) {
            case event: return eventRepository.findOptionalByShortName(publicIdentifier);
            case subscription: return subscriptionRepository.findOne(UUID.fromString(publicIdentifier));
            default: throw new IllegalStateException("not a covered type " + purchasableType);
        }
    }

    public Optional<Purchasable> findByReservationId(String reservationId) {
        return ticketReservationRepository.findEventIdFor(reservationId).map(eventRepository::findById)
            .map(Purchasable.class::cast)
            .or(() -> subscriptionRepository.findDescriptorByReservationId(reservationId));
    }
}
