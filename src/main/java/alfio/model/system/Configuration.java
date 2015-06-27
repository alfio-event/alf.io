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
package alfio.model.system;

import alfio.datamapper.ConstructorAnnotationRowMapper.Column;
import alfio.model.Event;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Configuration {

    private final int id;
    private final String key;
    private final String value;
    private final String description;
    private final ConfigurationKeys configurationKey;
    private final ConfigurationPathLevel configurationPathLevel;


    public Configuration(@Column("id") int id,
                         @Column("c_key") String key,
                         @Column("c_value") String value,
                         @Column("description") String description,
                         @Column("configuration_path_level") ConfigurationPathLevel configurationPathLevel) {
        this.id = id;
        this.key = key;
        this.value = value;
        this.description = description;
        this.configurationKey = ConfigurationKeys.valueOf(key);
        this.configurationPathLevel = configurationPathLevel;
    }

    public ConfigurationKeys.ComponentType getComponentType() {
        return configurationKey.getComponentType();
    }



    public enum ConfigurationPathLevel {
        SYSTEM, ORGANIZATION, EVENT, TICKET_CATEGORY
    }

    public interface ConfigurationPath {
        ConfigurationPathLevel pathLevel();
    }

    @EqualsAndHashCode
    private static class SystemConfigurationPath implements  ConfigurationPath {
        @Override
        public ConfigurationPathLevel pathLevel() {
            return ConfigurationPathLevel.SYSTEM;
        }

    }

    @EqualsAndHashCode
    private static class OrganizationConfigurationPath implements ConfigurationPath {

        private final int id;

        private OrganizationConfigurationPath(int id) {
            this.id = id;
        }

        @Override
        public ConfigurationPathLevel pathLevel() {
            return ConfigurationPathLevel.ORGANIZATION;
        }

    }

    @EqualsAndHashCode
    private static class EventConfigurationPath implements ConfigurationPath {

        private final int organizationId;
        private final int id;

        private EventConfigurationPath(int organizationId, int id) {
            this.organizationId = organizationId;
            this.id = id;
        }


        @Override
        public ConfigurationPathLevel pathLevel() {
            return ConfigurationPathLevel.EVENT;
        }

    }

    @EqualsAndHashCode
    private static class TicketCategoryConfigurationPath implements ConfigurationPath {

        private final int organizationId;
        private final int eventId;
        private final int id;

        private TicketCategoryConfigurationPath(int organizationId, int eventId, int id) {
            this.organizationId = organizationId;
            this.eventId = eventId;
            this.id = id;
        }

        @Override
        public ConfigurationPathLevel pathLevel() {
            return ConfigurationPathLevel.TICKET_CATEGORY;
        }

    }

    public static ConfigurationPath system() {
        return new SystemConfigurationPath();
    }

    public static ConfigurationPath organization(int id) {
        return new OrganizationConfigurationPath(id);
    }

    public static ConfigurationPath event(Event event) {
        return new EventConfigurationPath(event.getOrganizationId(), event.getId());
    }

    public static ConfigurationPath ticketCategory(int organizationId, int eventId, int id) {
        return new TicketCategoryConfigurationPath(organizationId, eventId, id);
    }
}
