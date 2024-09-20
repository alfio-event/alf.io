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

import alfio.model.DescriptorIdAndReservationId;
import alfio.model.modification.SubscriptionDescriptorModification;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.system.command.CleanupReservations;
import alfio.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional
public class SubscriptionReservationManager {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionReservationManager.class);

    private final SubscriptionRepository subscriptionRepository;
    private final ExtensionManager extensionManager;

    public SubscriptionReservationManager(SubscriptionRepository subscriptionRepository,
                                          ExtensionManager extensionManager) {
        this.subscriptionRepository = subscriptionRepository;
        this.extensionManager = extensionManager;
    }

    @EventListener(CleanupReservations.class)
    public void cleanupReservations(CleanupReservations cleanupReservations) {
        if (cleanupReservations.purchaseContext() != null && cleanupReservations.purchaseContext() instanceof SubscriptionDescriptor sd) {
            deleteReservationsForDescriptor(cleanupReservations.reservationIds(), sd, cleanupReservations.expired());
        } else if (cleanupReservations.purchaseContext() == null) {
            var involvedDescriptors = subscriptionRepository.findDescriptorsByReservationIds(cleanupReservations.reservationIds());

            var descriptorsIdsNeedingUpdate = involvedDescriptors.stream()
                .filter(d -> d.maxAvailable() > 0)
                .map(DescriptorIdAndReservationId::descriptorId)
                .collect(Collectors.toSet());

            if (descriptorsIdsNeedingUpdate.isEmpty()) {
                deleteReservationsForDescriptor(cleanupReservations.reservationIds(), null, cleanupReservations.expired());
            } else {
                Map<UUID, SubscriptionDescriptor> descriptorsById = subscriptionRepository.findByIds(descriptorsIdsNeedingUpdate).stream()
                    .collect(Collectors.toMap(SubscriptionDescriptor::getId, Function.identity()));
                var reservationByDescriptorId = involvedDescriptors.stream()
                    .collect(Collectors.groupingBy(DescriptorIdAndReservationId::descriptorId));
                for (var entry : reservationByDescriptorId.entrySet()) {
                    var reservations = entry.getValue().stream().map(DescriptorIdAndReservationId::reservationId).toList();
                    deleteReservationsForDescriptor(reservations, descriptorsById.get(entry.getKey()), cleanupReservations.expired());
                }
            }
        }
    }

    private void deleteReservationsForDescriptor(List<String> reservationIds, SubscriptionDescriptor sd, boolean expired) {
        int deleted = subscriptionRepository.deleteSubscriptionWithReservationId(reservationIds);
        log.trace("deleted {} subscriptions", deleted);
        if (sd != null && sd.getMaxAvailable() > -1) {
            // restore deleted subscriptions
            subscriptionRepository.preGenerateSubscriptions(SubscriptionDescriptorModification.fromModel(sd), sd.getId(), deleted);
            log.trace("created {} subscriptions to replace deleted", deleted);
        }
        if(expired) {
            extensionManager.handleReservationsExpired(sd, reservationIds);
        } else {
            extensionManager.handleReservationsCancelled(sd, reservationIds);
        }
    }
}
