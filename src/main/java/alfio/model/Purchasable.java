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
package alfio.model;

import alfio.model.transaction.PaymentProxy;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;

public interface Purchasable extends Configurable, TimeZoneInfo {
    BigDecimal getVat();
    PriceContainer.VatStatus getVatStatus();
    String getCurrency();

    List<PaymentProxy> getAllowedPaymentProxies();
    String getPrivacyPolicyLinkOrNull();
    String getPrivacyPolicyUrl();
    String getTermsAndConditionsUrl();

    //
    int getOrganizationId();

    //
    PurchasableType getType();
    String getPublicIdentifier();

    enum PurchasableType {
        SUBSCRIPTION("subscription"), EVENT("event");

        private final String urlComponent;

        PurchasableType(String urlComponent) {
            this.urlComponent = urlComponent;
        }

        public static PurchasableType from(String purchasableType) {
            switch (purchasableType) {
                case "subscription": return SUBSCRIPTION;
                case "event": return EVENT;
                default: throw new IllegalStateException("Purchase type not supported:" + purchasableType);
            }
        }

        public String getUrlComponent() {
            return urlComponent;
        }
    }
}
