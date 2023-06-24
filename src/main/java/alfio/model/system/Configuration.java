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

import alfio.model.EventAndOrganizationId;
import alfio.model.subscription.SubscriptionDescriptor;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.UUID;
import java.util.function.Function;

@Getter
public class Configuration implements Comparable<Configuration> {

    private final int id;
    private final String key;
    private final String value;
    private final String description;
    private final ConfigurationKeys configurationKey;
    private final ConfigurationPathLevel configurationPathLevel;
    private final boolean basic;
    private final boolean internal;


    @JsonCreator
    public Configuration(@JsonProperty("id") @Column("id") int id,
                         @JsonProperty("key") @Column("c_key") String key,
                         @JsonProperty("value") @Column("c_value") String value,
                         @JsonProperty("configurationPathLevel") @Column("configuration_path_level") ConfigurationPathLevel configurationPathLevel) {
        this.id = id;
        this.key = key;
        this.value = value;
        this.configurationKey = ConfigurationKeys.safeValueOf(key);
        this.description = configurationKey.getDescription();
        this.configurationPathLevel = configurationPathLevel;
        this.basic = this.configurationKey.isBasic();
        this.internal = this.configurationKey.isInternal();
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
        if (!(obj instanceof  Configuration)) {
            return false;
        }
        Configuration o = (Configuration) obj;
        return new EqualsBuilder().append(configurationKey, o.configurationKey).append(configurationPathLevel, configurationPathLevel).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(configurationKey).append(configurationPathLevel).toHashCode();
    }

    @FunctionalInterface
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
            return ConfigurationPathLevel.PURCHASE_CONTEXT;
        }

    }

    @EqualsAndHashCode
    @Getter
    public static class SubscriptionDescriptorConfigurationPath implements ConfigurationPath {

        private final int organizationId;
        private final UUID id;

        private SubscriptionDescriptorConfigurationPath(int organizationId, UUID id) {
            this.organizationId = organizationId;
            this.id = id;
        }


        @Override
        public ConfigurationPathLevel pathLevel() {
            return ConfigurationPathLevel.PURCHASE_CONTEXT;
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
        return getOrganizationConfiguration(organizationId, key);
    }

    public static ConfigurationPathKey from(EventAndOrganizationId eventAndOrganizationId, ConfigurationKeys key) {
        return getEventConfiguration(eventAndOrganizationId.getOrganizationId(), eventAndOrganizationId.getId(), key);
    }

    public static ConfigurationPathKey from(SubscriptionDescriptor subscriptionDescriptor, ConfigurationKeys key) {
        return new ConfigurationPathKey(new SubscriptionDescriptorConfigurationPath(subscriptionDescriptor.getOrganizationId(), subscriptionDescriptor.getId()), key);
    }

    public static Function<ConfigurationKeys, ConfigurationPathKey> from(EventAndOrganizationId e) {
        return p -> from(e, p);
    }

    public static Function<ConfigurationKeys, ConfigurationPathKey> from(int organizationId) {
        return p -> from(organizationId, p);
    }

    public static ConfigurationPathKey from(int organizationId, int eventId, int ticketCategoryId, ConfigurationKeys key) {
        return getTicketCategoryConfiguration(organizationId, eventId, ticketCategoryId, key);
    }
}
