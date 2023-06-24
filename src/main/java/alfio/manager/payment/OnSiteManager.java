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
package alfio.manager.payment;

import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationPathLevel;
import alfio.model.transaction.*;
import alfio.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static alfio.manager.TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID;
import static alfio.model.system.ConfigurationKeys.ON_SITE_ENABLED;
import static alfio.model.system.ConfigurationKeys.RECAPTCHA_API_KEY;

@Component
@Log4j2
@AllArgsConstructor
@Transactional
public class OnSiteManager implements PaymentProvider {

    private final ConfigurationManager configurationManager;
    private final TransactionRepository transactionRepository;

    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest) {
        return EnumSet.of(PaymentMethod.ON_SITE);
    }

    @Override
    public PaymentProxy getPaymentProxy() {
        return PaymentProxy.ON_SITE;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        return paymentMethod == PaymentMethod.ON_SITE && isActive(context);
    }

    @Override
    public boolean accept(Transaction transaction) {
        return PaymentProxy.ON_SITE == transaction.getPaymentProxy();
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        return PaymentMethod.ON_SITE;
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        return configurationManager.getFor(ON_SITE_ENABLED, paymentContext.getConfigurationLevel()).getValueAsBooleanOrDefault()
            && (paymentContext.getConfigurationLevel().getPathLevel() != ConfigurationPathLevel.PURCHASE_CONTEXT || !paymentContext.isOnline());
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        PaymentManagerUtils.invalidateExistingTransactions(spec.getReservationId(), transactionRepository);
        return PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
    }

    @Override
    public Map<String, ?> getModelOptions(PaymentContext context) {
        Map<String, Object> model = new HashMap<>();
        boolean recaptchaEnabled = configurationManager.isRecaptchaForOfflinePaymentAndFreeEnabled(context.getConfigurationLevel());
        model.put("captchaRequestedForOffline", recaptchaEnabled);
        if(recaptchaEnabled) {
            model.put("recaptchaApiKey", configurationManager.getForSystem(RECAPTCHA_API_KEY).getValue().orElse(null));
        }
        return model;
    }
}
