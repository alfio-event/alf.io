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
import alfio.model.transaction.token.StripeCreditCardToken;
import alfio.repository.TicketRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.system.ConfigurationRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.STRIPE_ENABLE_SCA;

@Component
@Log4j2
public class StripeCreditCardManager implements PaymentProvider, ClientServerTokenRequest, RefundRequest, PaymentInfo {

    public static final String STRIPE_UNEXPECTED = "error.STEP2_STRIPE_unexpected";

    private final ConfigurationManager configurationManager;
    private final TransactionRepository transactionRepository;
    private final BaseStripeManager baseStripeManager;

    @Autowired
    public StripeCreditCardManager(ConfigurationManager configurationManager,
                                   TicketRepository ticketRepository,
                                   TransactionRepository transactionRepository,
                                   ConfigurationRepository configurationRepository,
                                   Environment environment) {
        this(configurationManager,
            transactionRepository,
            new BaseStripeManager(configurationManager, configurationRepository, ticketRepository, environment));
    }

    StripeCreditCardManager( ConfigurationManager configurationManager,
                             TransactionRepository transactionRepository,
                             BaseStripeManager baseStripeManager) {
        this.configurationManager = configurationManager;
        this.transactionRepository = transactionRepository;
        this.baseStripeManager = baseStripeManager;
    }

    public Optional<Boolean> processWebhookEvent(String body, String signature) {
        return baseStripeManager.processWebhookEvent(body, signature);
    }

    @Override
    public PaymentResult getToken(PaymentSpecification spec) {
        return baseStripeManager.getToken(spec);
    }

    @Override
    public Optional<PaymentInformation> getInfo(Transaction transaction, Event event) {
        return baseStripeManager.getInfo(transaction, event);
    }

    // https://stripe.com/docs/api#create_refund
    @Override
    public boolean refund(Transaction transaction, Event event, Integer amountToRefund) {
        return baseStripeManager.refund(transaction, event, amountToRefund);
    }

    @Override
    public Map<String, ?> getModelOptions(PaymentContext context) {
        return baseStripeManager.getModelOptions(context);
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context) {
        return baseStripeManager.accept(paymentMethod, context)
            && !configurationManager.getBooleanConfigValue(context.narrow(STRIPE_ENABLE_SCA), false);
    }

    @Override
    public PaymentResult doPayment( PaymentSpecification spec ) {
        try {
            final Optional<Charge> optionalCharge = baseStripeManager.chargeCreditCard(spec);
            return optionalCharge.map(charge -> {
                log.info("transaction {} paid: {}", spec.getReservationId(), charge.getPaid());
                Pair<Long, Long> fees = Optional.ofNullable(charge.getBalanceTransactionObject()).map( bt -> {
                    List<BalanceTransaction.Fee> feeDetails = bt.getFeeDetails();
                    return Pair.of(Optional.ofNullable( BaseStripeManager.getFeeAmount(feeDetails, "application_fee")).map(Long::parseLong).orElse(0L),
                        Optional.ofNullable( BaseStripeManager.getFeeAmount(feeDetails, "stripe_fee")).map(Long::parseLong).orElse(0L));
                }).orElse(null);

                transactionRepository.insert(charge.getId(), null, spec.getReservationId(),
                    ZonedDateTime.now(), spec.getPriceWithVAT(), spec.getEvent().getCurrency(), charge.getDescription(), PaymentProxy.STRIPE.name(),
                    fees != null ? fees.getLeft() : 0L, fees != null ? fees.getRight() : 0L, Transaction.Status.COMPLETE, Map.of());
                return PaymentResult.successful(charge.getId());
            }).orElseGet(() -> PaymentResult.failed("error.STEP2_UNABLE_TO_TRANSITION"));
        } catch (Exception e) {
            if(e instanceof StripeException) {
                return PaymentResult.failed( baseStripeManager.handleException((StripeException)e));
            }
            throw new IllegalStateException(e);
        }
    }

    @Override
    public PaymentToken buildPaymentToken(String clientToken, PaymentContext context) {
        return new StripeCreditCardToken(clientToken);
    }

}
