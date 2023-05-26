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

import alfio.manager.system.ConfigurationLevel;
import alfio.model.PurchaseContext;
import alfio.model.TicketReservation;
import alfio.repository.EventRepository;
import alfio.repository.SubscriptionRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
@Component
public class PurchaseContextManager {

    private static final Logger log = LoggerFactory.getLogger(PurchaseContextManager.class);

    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final OrganizationRepository organizationRepository;

    public PurchaseContextManager(EventRepository eventRepository,
                                  SubscriptionRepository subscriptionRepository,
                                  TicketReservationRepository ticketReservationRepository,
                                  OrganizationRepository organizationRepository) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.organizationRepository = organizationRepository;
    }

    public Optional<? extends PurchaseContext> findBy(PurchaseContext.PurchaseContextType purchaseContextType, String publicIdentifier) {
        return switch (purchaseContextType) {
            case event -> eventRepository.findOptionalByShortName(publicIdentifier);
            case subscription -> subscriptionRepository.findOne(UUID.fromString(publicIdentifier));
            default -> throw new IllegalStateException("not a covered type " + purchaseContextType);
        };
    }

    // temporary until we have a proper ownership check service (WIP)
    public boolean validateAccess(PurchaseContext purchaseContext, Principal principal) {
        var username = Objects.requireNonNull(principal).getName();
        return organizationRepository.findOrganizationForUser(username, purchaseContext.getOrganizationId()).isPresent();
    }

    Optional<? extends PurchaseContext> findById(PurchaseContext.PurchaseContextType purchaseContextType, String idAsString) {
        return switch (purchaseContextType) {
            case event -> eventRepository.findOptionalById(Integer.parseInt(idAsString));
            case subscription -> subscriptionRepository.findOne(UUID.fromString(idAsString));
            default -> throw new IllegalStateException("not a covered type " + purchaseContextType);
        };
    }

    public Optional<PurchaseContext> findByReservationId(String reservationId) {
        return ticketReservationRepository.findEventIdFor(reservationId).map(eventRepository::findById)
            .map(PurchaseContext.class::cast)
            .or(() -> subscriptionRepository.findDescriptorByReservationId(reservationId));
    }

    public Optional<ConfigurationLevel> detectConfigurationLevel(String eventShortName, String subscriptionId) {
        if (StringUtils.isAllEmpty(eventShortName, subscriptionId)) {
            return Optional.empty();
        }

        try {
            if (StringUtils.isNotEmpty(eventShortName)) {
                return eventRepository.findOptionalEventAndOrganizationIdByShortName(eventShortName)
                    .map(ConfigurationLevel::event);
            }

            return subscriptionRepository.findOrganizationIdForSubscription(UUID.fromString(subscriptionId))
                .map(ConfigurationLevel::organization);
        } catch (Exception ex) {
            log.warn("error while loading ConfigurationLevel", ex);
            return Optional.empty();
        }
    }

    public Optional<Pair<PurchaseContext, TicketReservation>> getReservationWithPurchaseContext(String reservationId) {
        return findByReservationId(reservationId)
            .map(event -> Pair.of(event, ticketReservationRepository.findReservationById(reservationId)));
    }
}
