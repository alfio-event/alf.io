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
package alfio.model.api.v1.admin;

import alfio.model.api.v1.admin.subscription.SubscriptionConfiguration;
import alfio.model.api.v1.admin.subscription.Owner;
import alfio.model.metadata.SubscriptionMetadata;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import static java.util.Objects.requireNonNullElseGet;

public class SubscriptionReservationCreationRequest implements ReservationAPICreationRequest {
    private final Map<String, String> metadata;
    private final ReservationUser user;
    private final String language;
    private final ReservationConfiguration reservationConfiguration;
    private final SubscriptionConfiguration subscriptionConfiguration;
    private final Owner owner;

    @JsonCreator
    public SubscriptionReservationCreationRequest(@JsonProperty("metadata") Map<String, String> metadata,
                                                  @JsonProperty("user") ReservationUser user,
                                                  @JsonProperty("language") String language,
                                                  @JsonProperty("configuration") ReservationConfiguration reservationConfiguration,
                                                  @JsonProperty("subscriptionConfiguration") SubscriptionConfiguration subscriptionConfiguration,
                                                  @JsonProperty("owner") Owner owner) {
        this.metadata = metadata;
        this.user = user;
        this.language = language;
        this.reservationConfiguration = reservationConfiguration;
        this.subscriptionConfiguration = requireNonNullElseGet(subscriptionConfiguration, SubscriptionConfiguration::defaultConfiguration);
        this.owner = requireNonNullElseGet(owner, Owner::empty);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public ReservationUser getUser() {
        return user;
    }

    @Override
    public String getLanguage() {
        return language;
    }

    @Override
    public ReservationConfiguration getReservationConfiguration() {
        return reservationConfiguration;
    }

    public SubscriptionConfiguration getSubscriptionConfiguration() {
        return subscriptionConfiguration;
    }

    public SubscriptionMetadata getMetadataOrNull() {
        if (metadata != null && !metadata.isEmpty()) {
            return new SubscriptionMetadata(metadata, subscriptionConfiguration);
        }
        return null;
    }

    public Owner getSubscriptionOwner() {
        return owner;
    }

    public boolean hasAdditionalInfo() {
        return getSubscriptionOwner().hasAdditionalInfo();
    }
}
