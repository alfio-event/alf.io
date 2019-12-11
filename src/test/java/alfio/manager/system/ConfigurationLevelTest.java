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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationLevelTest {

    @Test
    void external() {
        ConfigurationLevel external = ConfigurationLevel.external();
        assertTrue(external instanceof ConfigurationLevels.ExternalLevel);
        assertEquals(ConfigurationPathLevel.EXTERNAL, external.getPathLevel());
    }

    @Test
    void system() {
        ConfigurationLevel system = ConfigurationLevel.system();
        assertTrue(system instanceof ConfigurationLevels.SystemLevel);
        assertEquals(ConfigurationPathLevel.SYSTEM, system.getPathLevel());
    }

    @Test
    void organization() {
        ConfigurationLevel organization = ConfigurationLevel.organization(666);
        assertTrue(organization instanceof ConfigurationLevels.OrganizationLevel);
        assertEquals(ConfigurationPathLevel.ORGANIZATION, organization.getPathLevel());
        assertEquals(666, ((ConfigurationLevels.OrganizationLevel) organization).organizationId);
    }

    @Test
    void event() {
        ConfigurationLevel event = ConfigurationLevel.event(new EventAndOrganizationId(1, 666));
        assertTrue(event instanceof ConfigurationLevels.EventLevel);
        assertEquals(ConfigurationPathLevel.EVENT, event.getPathLevel());
        var eventLevel = (ConfigurationLevels.EventLevel) event;
        assertEquals(666, eventLevel.organizationId);
        assertEquals(1, eventLevel.eventId);
    }


    @Test
    void ticketCategory() {
        ConfigurationLevel ticketCategory = ConfigurationLevel.ticketCategory(new EventAndOrganizationId(1, 666), 777);
        assertTrue(ticketCategory instanceof ConfigurationLevels.CategoryLevel);
        assertEquals(ConfigurationPathLevel.TICKET_CATEGORY, ticketCategory.getPathLevel());
        var categoryLevel = (ConfigurationLevels.CategoryLevel) ticketCategory;
        assertEquals(666, categoryLevel.organizationId);
        assertEquals(1, categoryLevel.eventId);
        assertEquals(777, categoryLevel.categoryId);
    }
}