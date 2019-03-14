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
package alfio.model.transaction.token;

import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.TransactionInitializationToken;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class StripeSCACreditCardToken implements TransactionInitializationToken {

    @JsonIgnore
    private final String paymentIntentId;
    @JsonIgnore
    private final String chargeId;
    private final String clientSecret;

    @Override
    public String getToken() {
        return chargeId;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    @Override
    public String getClientSecret() {
        return clientSecret;
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public PaymentProxy getPaymentProvider() {
        return PaymentProxy.STRIPE;
    }
}
