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
import alfio.model.transaction.capabilities.ClientServerTokenRequest;
import alfio.model.transaction.capabilities.PaymentInfo;
import alfio.model.transaction.capabilities.RefundRequest;
import alfio.model.transaction.capabilities.SignedWebhookHandler;
import alfio.model.transaction.token.StripeSCACreditCardToken;
import alfio.model.transaction.webhook.StripeChargeTransactionWebhookPayload;
import alfio.model.transaction.webhook.StripePaymentIntentWebhookPayload;
import alfio.repository.TicketRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.system.ConfigurationRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.STRIPE_ENABLE_SCA;
import static alfio.model.system.ConfigurationKeys.STRIPE_WEBHOOK_KEY;

@Log4j2
@Component
@Transactional
public class StripeWebhookPaymentManager implements PaymentProvider, RefundRequest, PaymentInfo, SignedWebhookHandler, ClientServerTokenRequest {


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
    public PaymentToken initTransaction(PaymentSpecification paymentSpecification) {
        var reservationId = paymentSpecification.getReservationId();
        return transactionRepository.loadOptionalByReservationId(reservationId)
            .map(this::buildTokenFromTransaction)
            .orElseGet(() -> createNewToken(paymentSpecification));
    }

    private StripeSCACreditCardToken buildTokenFromTransaction(Transaction transaction) {
        return new StripeSCACreditCardToken(transaction.getPaymentId(), transaction.getTransactionId(), null);
    }

    private StripeSCACreditCardToken createNewToken(PaymentSpecification paymentSpecification) {
        var paymentIntentParams = baseStripeManager.createParams(paymentSpecification);
        paymentIntentParams.put("allowed_source_types", List.of("card"));
        try {
            var intent = PaymentIntent.create(paymentIntentParams, baseStripeManager.options(paymentSpecification.getEvent()).orElseThrow());
            var clientSecret = intent.getClientSecret();
            long platformFee = paymentIntentParams.containsKey("application_fee") ? (long) paymentIntentParams.get("application_fee") : 0L;
            transactionRepository.insert(null, intent.getId(),
                paymentSpecification.getReservationId(), ZonedDateTime.now(paymentSpecification.getEvent().getZoneId()),
                paymentSpecification.getPriceWithVAT(), paymentSpecification.getEvent().getCurrency(), "Payment Intent",
                PaymentProxy.STRIPE.name(), platformFee,0L, Transaction.Status.PENDING);
            return new StripeSCACreditCardToken(intent.getId(), null, clientSecret);

        } catch (StripeException e) {
            throw new IllegalStateException(e);
        }
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
                return Optional.of(new StripeChargeTransactionWebhookPayload(eventType, (Charge) stripeEvent.getData().getObject()));
            } else if(eventType.startsWith("payment_intent.")) {
                return Optional.of(new StripePaymentIntentWebhookPayload(eventType, (PaymentIntent) stripeEvent.getData().getObject()));
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
            && configurationManager.getBooleanConfigValue(context.narrow(STRIPE_ENABLE_SCA), false)
            && isWebhookKeyDefined(context);
    }

    private boolean isWebhookKeyDefined(PaymentContext context) {
        return !configurationManager.getStringConfigValue(context.narrow(STRIPE_WEBHOOK_KEY))
            .map(String::strip)
            .orElse("")
            .isEmpty();
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

    @Override
    public PaymentToken buildPaymentToken(String clientToken, PaymentContext paymentContext) {
        var reservationId = paymentContext.getReservationId().orElseThrow();
        var optionalTransaction = transactionRepository.loadOptionalByReservationId(reservationId);
        return new StripeSCACreditCardToken(optionalTransaction.map(Transaction::getPaymentId).orElse(null), clientToken, null);
    }
}
