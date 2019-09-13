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
import alfio.model.system.ConfigurationPathLevel;

public interface ConfigurationLevel {

    ConfigurationPathLevel getPathLevel(); //waiting for pattern matching...

    static ConfigurationLevel system() {
        return new ConfigurationLevels.SystemLevel();
    }

    static ConfigurationLevel event(EventAndOrganizationId eventAndOrganizationId) {
        return new ConfigurationLevels.EventLevel(eventAndOrganizationId.getOrganizationId(), eventAndOrganizationId.getId());
    }

    static ConfigurationLevel organization(int organizationId) {
        return new ConfigurationLevels.OrganizationLevel(organizationId);
    }

    static ConfigurationLevel ticketCategory(EventAndOrganizationId eventAndOrganizationId, int ticketCategoryId) {
        return new ConfigurationLevels.CategoryLevel(eventAndOrganizationId.getOrganizationId(), eventAndOrganizationId.getId(), ticketCategoryId);
    }

}
