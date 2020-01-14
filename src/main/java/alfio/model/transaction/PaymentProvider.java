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

import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.PaymentResult;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface PaymentProvider {

    Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest);

    PaymentProxy getPaymentProxy();

    boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest);

    boolean accept(Transaction transaction);

    PaymentMethod getPaymentMethodForTransaction(Transaction transaction);

    boolean isActive(PaymentContext paymentContext);

    default PaymentResult getToken(PaymentSpecification spec) {
        return PaymentResult.initialized(UUID.randomUUID().toString());
    }

    PaymentResult doPayment(PaymentSpecification spec);

    default PaymentResult getTokenAndPay(PaymentSpecification spec) {
        PaymentResult tokenResult = getToken(spec);
        if(tokenResult.isInitialized()) {
            return doPayment(spec);
        }
        return tokenResult;
    }

    default Map<String, ?> getModelOptions(PaymentContext context) {
        return Collections.emptyMap();
    }
}
