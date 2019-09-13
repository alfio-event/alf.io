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

import static alfio.model.system.ConfigurationPathLevel.*;
import static alfio.model.system.ConfigurationPathLevel.TICKET_CATEGORY;

class ConfigurationLevels {
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
    }

    @AllArgsConstructor
    static class EventLevel implements ConfigurationLevel {
        final int organizationId;
        final int eventId;

        @Override
        public ConfigurationPathLevel getPathLevel() {
            return EVENT;
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
    }
}
