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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PurchaseContext extends Configurable, TimeZoneInfo, LocalizedContent {

    Map<String, String> getTitle();

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
    PurchaseContextType getType();
    String getPublicIdentifier();

    String getFileBlobId();

    boolean isFreeOfCharge();


    String getDisplayName();

    //FIXME: check every USE
    Optional<Event> event();

    ZonedDateTime getBegin();


    enum PurchaseContextType {
        subscription("subscription"), event("event");

        private final String urlComponent;

        PurchaseContextType(String urlComponent) {
            this.urlComponent = urlComponent;
        }

        public static PurchaseContextType from(String purchaseContextType) {
            switch (purchaseContextType) {
                case "subscription": return subscription;
                case "event": return event;
                default: throw new IllegalStateException("Purchase type not supported:" + purchaseContextType);
            }
        }

        public String getUrlComponent() {
            return urlComponent;
        }
    }

    String getPrivateKey();

    default boolean mustUseFirstAndLastName() {
        return true;
    }

    default boolean getFileBlobIdIsPresent() {
        return true;
    }

    default boolean ofType(PurchaseContextType purchaseContextType) {
        return getType() == purchaseContextType;
    }
}
