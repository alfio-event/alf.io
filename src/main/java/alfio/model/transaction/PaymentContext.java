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
import alfio.model.PurchaseContext;

import java.util.Optional;

public class PaymentContext {

    private final PurchaseContext purchaseContext;
    private final String reservationId;
    private final ConfigurationLevel configurationLevel;

    public PaymentContext() {
        this(null, ConfigurationLevel.system());
    }

    public PaymentContext(PurchaseContext purchaseContext) {
        this(purchaseContext, purchaseContext.getConfigurationLevel());
    }

    public PaymentContext(PurchaseContext purchaseContext, String reservationId) {
        this(purchaseContext, purchaseContext.getConfigurationLevel(), reservationId);
    }

    public PaymentContext(PurchaseContext purchaseContext, ConfigurationLevel configurationLevel) {
        this(purchaseContext, configurationLevel, null);
    }

    public PaymentContext(PurchaseContext purchaseContext, ConfigurationLevel configurationLevel, String reservationId) {
        this.purchaseContext = purchaseContext;
        this.configurationLevel = configurationLevel;
        this.reservationId = reservationId;
    }

    /**
     * The {@link PurchaseContext} on which this configuration refers to
     * @return PurchaseContext, or null
     */
    public PurchaseContext getPurchaseContext() {
        return purchaseContext;
    }

    public Optional<String> getReservationId() {
        return Optional.ofNullable(reservationId);
    }

    public ConfigurationLevel getConfigurationLevel() {
        return configurationLevel;
    }

    public boolean isOnline() {
        return purchaseContext.event().map(Event::isOnline).orElse(true);
    }
}
