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
package alfio.manager.system;

import alfio.model.EventAndOrganizationId;
import alfio.model.PurchaseContext;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.system.ConfigurationPathLevel;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public interface ConfigurationLevel {


    ConfigurationPathLevel getPathLevel(); //waiting for pattern matching...
    default OptionalInt getOrganizationId() {
        return OptionalInt.empty();
    }

    default OptionalInt getEventId() {
        return OptionalInt.empty();
    }

    default Optional<UUID> getSubscriptionDescriptorId() {
        return Optional.empty();
    }

    default OptionalInt getTicketCategoryId() {
        return OptionalInt.empty();
    }

    default boolean isSystem() {
        return getPathLevel() == ConfigurationPathLevel.SYSTEM;
    }

    static ConfigurationLevel external() {
        return new ConfigurationLevels.ExternalLevel();
    }

    static ConfigurationLevel system() {
        return new ConfigurationLevels.SystemLevel();
    }

    static ConfigurationLevel event(EventAndOrganizationId eventAndOrganizationId) {
        return new ConfigurationLevels.EventLevel(eventAndOrganizationId.getOrganizationId(), eventAndOrganizationId.getId());
    }

    static ConfigurationLevel subscriptionDescriptor(SubscriptionDescriptor subscriptionDescriptor) {
        return new ConfigurationLevels.SubscriptionDescriptorLevel(subscriptionDescriptor.getOrganizationId(), subscriptionDescriptor.getId());
    }

    static ConfigurationLevel purchaseContext(PurchaseContext purchaseContext) {
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            return event(purchaseContext.event().orElseThrow());
        }
        return subscriptionDescriptor((SubscriptionDescriptor) purchaseContext);
    }

    static ConfigurationLevel organization(int organizationId) {
        return new ConfigurationLevels.OrganizationLevel(organizationId);
    }

    static ConfigurationLevel ticketCategory(EventAndOrganizationId eventAndOrganizationId, int ticketCategoryId) {
        return new ConfigurationLevels.CategoryLevel(eventAndOrganizationId.getOrganizationId(), eventAndOrganizationId.getId(), ticketCategoryId);
    }

}
