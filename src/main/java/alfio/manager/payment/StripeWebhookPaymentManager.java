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
import alfio.model.Event;
import alfio.model.PaymentInformation;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.PaymentInfo;
import alfio.model.transaction.capabilities.RefundRequest;
import alfio.model.transaction.capabilities.SignedWebhookHandler;
import alfio.model.transaction.webhook.StripeChargeTransactionWebhookPayload;
import alfio.repository.TicketRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.system.ConfigurationRepository;
import com.stripe.model.Charge;
import com.stripe.net.Webhook;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.STRIPE_ENABLE_SCA;

@Log4j2
@Component
public class StripeWebhookPaymentManager implements PaymentProvider, RefundRequest, PaymentInfo, SignedWebhookHandler {


    private final ConfigurationManager configurationManager;
    private final BaseStripeManager baseStripeManager;
    private final TransactionRepository transactionRepository;

    public StripeWebhookPaymentManager(ConfigurationManager configurationManager,
                                       TicketRepository ticketRepository,
                                       TransactionRepository transactionRepository,
                                       ConfigurationRepository configurationRepository,
                                       Environment environment) {
        this.configurationManager = configurationManager;
        this.transactionRepository = transactionRepository;
        this.baseStripeManager = new BaseStripeManager(configurationManager, configurationRepository, ticketRepository, environment);
    }

    @Override
    public String getWebhookSignatureKey() {
        return baseStripeManager.getWebhookSignatureKey();
    }

    @Override
    public Optional<TransactionWebhookPayload> parseTransactionPayload(String body, String signature) {
        try {
            var stripeEvent = Webhook.constructEvent(body, signature, getWebhookSignatureKey());
            String eventType = stripeEvent.getType();
            if(eventType.startsWith("charge.")) {
                return Optional.of(new StripeChargeTransactionWebhookPayload(eventType, (Charge)stripeEvent.getData().getObject()));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("got exception while handling stripe webhook", e);
            return Optional.empty();
        }
    }

    @Override
    public PaymentResult confirm(TransactionWebhookPayload payload, Transaction transaction) {
        return null;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context) {
        return baseStripeManager.accept(paymentMethod, context)
            && configurationManager.getBooleanConfigValue(context.narrow(STRIPE_ENABLE_SCA), false);
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        return null;
    }

    @Override
    public Optional<PaymentInformation> getInfo(Transaction transaction, Event event) {
        return baseStripeManager.getInfo(transaction, event);
    }

    @Override
    public boolean refund(Transaction transaction, Event event, Integer amount) {
        return baseStripeManager.refund(transaction, event, amount);
    }
}
