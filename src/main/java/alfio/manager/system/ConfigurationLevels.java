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

import alfio.model.system.ConfigurationPathLevel;
import lombok.AllArgsConstructor;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static alfio.model.system.ConfigurationPathLevel.*;

class ConfigurationLevels {

    static class ExternalLevel implements ConfigurationLevel {
        @Override
        public ConfigurationPathLevel getPathLevel() {
            return EXTERNAL;
        }
    }

    static class SystemLevel implements ConfigurationLevel {
        @Override
        public ConfigurationPathLevel getPathLevel() {
            return SYSTEM;
        }
    }

    @AllArgsConstructor
    static class OrganizationLevel implements ConfigurationLevel {
        final int organizationId;

        @Override
        public ConfigurationPathLevel getPathLevel() {
            return ORGANIZATION;
        }

        @Override
        public OptionalInt getOrganizationId() {
            return OptionalInt.of(organizationId);
        }
    }

    @AllArgsConstructor
    static class EventLevel implements ConfigurationLevel {
        final int organizationId;
        final int eventId;

        @Override
        public ConfigurationPathLevel getPathLevel() {
            return PURCHASE_CONTEXT;
        }

        @Override
        public OptionalInt getOrganizationId() {
            return OptionalInt.of(organizationId);
        }

        @Override
        public OptionalInt getEventId() {
            return OptionalInt.of(eventId);
        }
    }

    static class SubscriptionDescriptorLevel implements ConfigurationLevel {
        final int organizationId;
        final UUID subscriptionDescriptorId;

        SubscriptionDescriptorLevel(int organizationId, UUID subscriptionDescriptorId) {
            this.organizationId = organizationId;
            this.subscriptionDescriptorId = subscriptionDescriptorId;
        }

        @Override
        public ConfigurationPathLevel getPathLevel() {
            return PURCHASE_CONTEXT;
        }

        @Override
        public OptionalInt getOrganizationId() {
            return OptionalInt.of(organizationId);
        }

        @Override
        public Optional<UUID> getSubscriptionDescriptorId() {
            return Optional.of(subscriptionDescriptorId);
        }
    }

    @AllArgsConstructor
    static class CategoryLevel implements ConfigurationLevel {
        final int organizationId;
        final int eventId;
        final int categoryId;

        @Override
        public ConfigurationPathLevel getPathLevel() {
            return TICKET_CATEGORY;
        }

        @Override
        public OptionalInt getOrganizationId() {
            return OptionalInt.of(organizationId);
        }

        @Override
        public OptionalInt getEventId() {
            return OptionalInt.of(eventId);
        }

        @Override
        public OptionalInt getTicketCategoryId() {
            return OptionalInt.of(categoryId);
        }
    }
}
