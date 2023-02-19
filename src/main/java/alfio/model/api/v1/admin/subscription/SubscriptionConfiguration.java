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
package alfio.model.api.v1.admin.subscription;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class SubscriptionConfiguration {
    /**
     * Display PIN information on reservation page and PDF. Default is {@code true}
     */
    private final boolean displayPin;

    @JsonCreator
    public SubscriptionConfiguration(@JsonProperty("displayPin") Boolean displayPin) {
        this.displayPin = Objects.requireNonNullElse(displayPin, true);
    }

    public boolean isDisplayPin() {
        return displayPin;
    }

    public static SubscriptionConfiguration defaultConfiguration() {
        return new SubscriptionConfiguration(true);
    }
}
