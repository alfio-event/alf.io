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

import alfio.manager.system.ConfigurationLevel;
import alfio.model.Event;
import alfio.model.Purchasable;

import java.util.Optional;

public class PaymentContext {

    private final Purchasable purchasable;
    private final String reservationId;
    private final ConfigurationLevel configurationLevel;

    public PaymentContext() {
        this(null, ConfigurationLevel.system());
    }

    public PaymentContext(Purchasable purchasable) {
        this(purchasable, purchasable.getConfigurationLevel());
    }

    public PaymentContext(Purchasable purchasable, String reservationId) {
        this(purchasable, purchasable.getConfigurationLevel(), reservationId);
    }

    public PaymentContext(Purchasable purchasable, ConfigurationLevel configurationLevel) {
        this(purchasable, configurationLevel, null);
    }

    public PaymentContext(Purchasable purchasable, ConfigurationLevel configurationLevel, String reservationId) {
        this.purchasable = purchasable;
        this.configurationLevel = configurationLevel;
        this.reservationId = reservationId;
    }

    /**
     * The {@link Purchasable} on which this configuration refers to
     * @return Purchasable, or null
     */
    public Purchasable getPurchasable() {
        return purchasable;
    }

    public Optional<String> getReservationId() {
        return Optional.ofNullable(reservationId);
    }

    public ConfigurationLevel getConfigurationLevel() {
        return configurationLevel;
    }

    public boolean isOnline() {
        return (purchasable instanceof Event) ? ((Event) purchasable).isOnline() : true;
    }
}
