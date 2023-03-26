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
package alfio.model.metadata;

import alfio.model.api.v1.admin.subscription.SubscriptionConfiguration;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

public class SubscriptionMetadata {

    private final Map<String, String> properties;
    private final SubscriptionConfiguration configuration;

    @JsonCreator
    public SubscriptionMetadata(@JsonProperty("properties") Map<String, String> properties,
                                @JsonProperty("configuration") SubscriptionConfiguration configuration) {
        this.properties = Objects.requireNonNullElse(properties, Map.of());
        this.configuration = Objects.requireNonNullElseGet(configuration, SubscriptionConfiguration::defaultConfiguration);
    }


    public Map<String, String> getProperties() {
        return properties;
    }

    public SubscriptionConfiguration getConfiguration() {
        return configuration;
    }

    public static SubscriptionMetadata empty() {
        return new SubscriptionMetadata(null, null);
    }
}
