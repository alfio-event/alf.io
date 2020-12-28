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

import alfio.model.SubscriptionDescriptor;
import alfio.model.modification.SubscriptionDescriptorModification;
import alfio.repository.SubscriptionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
public class SubscriptionManager {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionManager(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public List<SubscriptionDescriptor> findAll(int organizationId) {
        return subscriptionRepository.findAllByOrganizationIds(organizationId);
    }

    public Optional<UUID> createSubscriptionDescriptor(SubscriptionDescriptorModification subscriptionDescriptor) {
        var id = UUID.randomUUID();
        var result = subscriptionRepository.createSubscriptionDescriptor(
            id,
            subscriptionDescriptor.getTitle(),
            subscriptionDescriptor.getDescription(),
            Objects.requireNonNullElse(subscriptionDescriptor.getMaxAvailable(), -1),
            subscriptionDescriptor.getOnSaleFrom(),
            subscriptionDescriptor.getOnSaleTo(),
            subscriptionDescriptor.getPriceCts(),
            subscriptionDescriptor.getVat(),
            subscriptionDescriptor.getVatStatus(),
            subscriptionDescriptor.getCurrency(),
            Boolean.TRUE.equals(subscriptionDescriptor.getIsPublic()),
            subscriptionDescriptor.getOrganizationId(),

            Objects.requireNonNullElse(subscriptionDescriptor.getMaxEntries(), -1),
            subscriptionDescriptor.getValidityType(),
            subscriptionDescriptor.getValidityTimeUnit(),
            subscriptionDescriptor.getValidityUnits(),
            subscriptionDescriptor.getValidityFrom(),
            subscriptionDescriptor.getValidityTo(),
            subscriptionDescriptor.getUsageType());

        return result == 1 ? Optional.of(id) : Optional.empty();
    }

    public List<SubscriptionDescriptor> getActivePublicSubscriptionsDescriptor(ZonedDateTime from) {
        return subscriptionRepository.findAllActiveAndPublic(from);
    }
}
