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

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

@Getter
public class Configuration implements Comparable<Configuration> {

    private final int id;
    private final String key;
    private final String value;
    private final String description;
    private final ConfigurationKeys configurationKey;
    private final ConfigurationPathLevel configurationPathLevel;
    private final boolean basic;


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
        this.basic = this.configurationKey.isBasic();
    }

    public ComponentType getComponentType() {
        return configurationKey.getComponentType();
    }

    @Override
    public int compareTo(Configuration o) {
        return new CompareToBuilder().append(configurationKey.ordinal(), o.configurationKey.ordinal()).toComparison();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        Configuration o = (Configuration) obj;
        return new EqualsBuilder().append(configurationKey, o.configurationKey).append(configurationPathLevel, configurationPathLevel).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(configurationKey).append(configurationPathLevel).toHashCode();
    }

    public interface ConfigurationPath {
        ConfigurationPathLevel pathLevel();
    }

    @EqualsAndHashCode
    public static class SystemConfigurationPath implements  ConfigurationPath {
        @Override
        public ConfigurationPathLevel pathLevel() {
            return ConfigurationPathLevel.SYSTEM;
        }

    }

    @EqualsAndHashCode
    @Getter
    public static class OrganizationConfigurationPath implements ConfigurationPath {

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
    @Getter
    public static class EventConfigurationPath implements ConfigurationPath {

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
    @Getter
    public static class TicketCategoryConfigurationPath implements ConfigurationPath {

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

    private static ConfigurationPath organization(int id) {
        return new OrganizationConfigurationPath(id);
    }

    private static ConfigurationPath ticketCategory(int organizationId, int eventId, int id) {
        return new TicketCategoryConfigurationPath(organizationId, eventId, id);
    }


    //
    @Getter
    @EqualsAndHashCode
    public static class ConfigurationPathKey {
        private final ConfigurationPath path;
        private final ConfigurationKeys key;

        private ConfigurationPathKey(ConfigurationPath path, ConfigurationKeys key) {
            this.path = path;
            this.key = key;
        }
    }
    //

    public static ConfigurationPathKey getSystemConfiguration(ConfigurationKeys configurationKey) {
        return new ConfigurationPathKey(system(), configurationKey);
    }

    private static ConfigurationPathKey getOrganizationConfiguration(int organizationId, ConfigurationKeys configurationKey) {
        return new ConfigurationPathKey(organization(organizationId), configurationKey);
    }

    private static ConfigurationPathKey getEventConfiguration(int organizationId, int eventId, ConfigurationKeys configurationKey) {
        return new ConfigurationPathKey(new EventConfigurationPath(organizationId, eventId), configurationKey);
    }

    private static ConfigurationPathKey getTicketCategoryConfiguration(int organizationId, int eventId, int ticketCategoryId, ConfigurationKeys configurationKey) {
        return new ConfigurationPathKey(ticketCategory(organizationId, eventId, ticketCategoryId), configurationKey);
    }

    public static ConfigurationPathKey from(int organizationId, ConfigurationKeys key) {
        return from(Optional.of(organizationId), Optional.empty(), Optional.empty(), key);
    }

    public static ConfigurationPathKey from(int organizationId, int eventId, ConfigurationKeys key) {
        return from(Optional.of(organizationId), Optional.of(eventId), Optional.empty(), key);
    }

    public static ConfigurationPathKey from(int organizationId, int eventId, int ticketCategoryId, ConfigurationKeys key) {
        return from(Optional.of(organizationId), Optional.of(eventId), Optional.of(ticketCategoryId), key);
    }

    private static ConfigurationPathKey from(Optional<Integer> organizationId, Optional<Integer> eventId, Optional<Integer> ticketCategoryId, ConfigurationKeys key) {
        boolean organizationAvailable = organizationId.isPresent();
        boolean eventAvailable = eventId.isPresent();
        boolean categoryAvailable = ticketCategoryId.isPresent();
        ConfigurationPathLevel mostSensible = Arrays.stream(ConfigurationPathLevel.values())
            .sorted(Comparator.<ConfigurationPathLevel>naturalOrder().reversed())
            .filter(path -> path == ConfigurationPathLevel.ORGANIZATION && organizationAvailable
                || path == ConfigurationPathLevel.EVENT && organizationAvailable && eventAvailable
                || path == ConfigurationPathLevel.TICKET_CATEGORY && organizationAvailable && eventAvailable && categoryAvailable)
            .findFirst().orElseGet(() -> ConfigurationPathLevel.SYSTEM);
        switch(mostSensible) {
            case ORGANIZATION:
                return getOrganizationConfiguration(organizationId.get(), key);
            case EVENT:
                return getEventConfiguration(organizationId.get(), eventId.get(), key);
            case TICKET_CATEGORY:
                return getTicketCategoryConfiguration(organizationId.get(), eventId.get(), ticketCategoryId.get(), key);
        }
        return getSystemConfiguration(key);
    }

}
