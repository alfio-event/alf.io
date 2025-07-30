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
package alfio.model.transaction;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;

/**
 * Represents a payment method defined by an organization which
 * allows a user to make payment through a sidechannel outside of Alf.io.
 */
public class UserDefinedOfflinePaymentMethod implements UserDefinedPaymentMethod, PaymentMethod, Serializable {
    @Getter
    private String paymentMethodId;

    @Getter
    private Map<String, Localization> localizations;

    /**
     * Used for soft-deleting custom payment methods for the sake of
     * historical record presevation.
     */
    private boolean deleted;

    @JsonCreator
    public UserDefinedOfflinePaymentMethod(
        @JsonProperty("paymentMethodId") String paymentMethodId,
        @JsonProperty("localizations") Map<String, Localization> localizations
    ) {
        this.paymentMethodId = paymentMethodId == null ? UUID.randomUUID().toString() : paymentMethodId;
        this.localizations = localizations;
    }

    @Override
    public String name() {
        if(this.localizations.containsKey("en")) {
            return this.localizations.get("en").paymentName();
        }

        return this.localizations
            .values()
            .stream()
            .map(locale -> locale.paymentName())
            .sorted()
            .findFirst()
            .orElseThrow();
    }

    public Localization getLocaleByKey(String key) {
        return localizations.get(key);
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted() {
        deleted = true;
    }

    public record Localization(String paymentName, String paymentDescription, String paymentInstructions) {
        @JsonCreator
        public Localization(
            @JsonProperty("paymentName") String paymentName,
            @JsonProperty("paymentDescription") String paymentDescription,
            @JsonProperty("paymentInstructions") String paymentInstructions
        ) {
            this.paymentName = paymentName;
            this.paymentDescription = paymentDescription;
            this.paymentInstructions = paymentInstructions;
        }
    }
}
