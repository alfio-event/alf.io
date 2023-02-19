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
import alfio.model.PaymentInformation;
import alfio.model.PurchaseContext;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.ClientServerTokenRequest;
import alfio.model.transaction.capabilities.PaymentInfo;
import alfio.model.transaction.capabilities.RefundRequest;
import alfio.model.transaction.token.StripeCreditCardToken;
import alfio.repository.TicketRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.ClockProvider;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;

import static alfio.manager.payment.BaseStripeManager.STRIPE_MANAGER_TYPE_KEY;
import static alfio.model.system.ConfigurationKeys.*;

@Component
@Log4j2
public class StripeCreditCardManager implements PaymentProvider, ClientServerTokenRequest, RefundRequest, PaymentInfo {

    public static final String STRIPE_UNEXPECTED = "error.STEP2_STRIPE_unexpected";
    private static final String STRIPE_MANAGER = StripeCreditCardManager.class.getName();
    protected static final EnumSet<ConfigurationKeys> OPTIONS_TO_LOAD = EnumSet.of(STRIPE_ENABLE_SCA, STRIPE_SECRET_KEY, STRIPE_PUBLIC_KEY);

    private final TransactionRepository transactionRepository;
    private final BaseStripeManager baseStripeManager;
    private final ClockProvider clockProvider;

    @Autowired
    public StripeCreditCardManager(ConfigurationManager configurationManager,
                                   TicketRepository ticketRepository,
                                   TransactionRepository transactionRepository,
                                   ConfigurationRepository configurationRepository,
                                   Environment environment,
                                   ClockProvider clockProvider) {
        this(transactionRepository,
            new BaseStripeManager(configurationManager, configurationRepository, ticketRepository, environment),
            clockProvider);
    }

    StripeCreditCardManager(TransactionRepository transactionRepository,
                            BaseStripeManager baseStripeManager,
                            ClockProvider clockProvider) {
        this.transactionRepository = transactionRepository;
        this.baseStripeManager = baseStripeManager;
        this.clockProvider = clockProvider;
    }

    public Optional<Boolean> processWebhookEvent(String body, String signature) {
        return baseStripeManager.processWebhookEvent(body, signature);
    }

    @Override
    public PaymentResult getToken(PaymentSpecification spec) {
        return baseStripeManager.getToken(spec);
    }

    @Override
    public Optional<PaymentInformation> getInfo(Transaction transaction, PurchaseContext purchaseContext) {
        return baseStripeManager.getInfo(transaction, purchaseContext);
    }

    // https://stripe.com/docs/api#create_refund
    @Override
    public boolean refund(Transaction transaction, PurchaseContext purchaseContext, Integer amountToRefund) {
        return baseStripeManager.refund(transaction, purchaseContext, amountToRefund);
    }

    @Override
    public Map<String, ?> getModelOptions(PaymentContext context) {
        return baseStripeManager.getModelOptions(context);
    }

    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest) {
        if(!isActive(paymentContext)) {
            return EnumSet.noneOf(PaymentMethod.class);
        }
        return EnumSet.of(PaymentMethod.CREDIT_CARD);
    }

    @Override
    public PaymentProxy getPaymentProxy() {
        return PaymentProxy.STRIPE;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        return baseStripeManager.accept(paymentMethod, context, OPTIONS_TO_LOAD, this::checkConfiguration);
    }

    private boolean checkConfiguration(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> config) {
        return !config.get(STRIPE_ENABLE_SCA).getValueAsBooleanOrDefault()
            && config.get(STRIPE_SECRET_KEY).isPresent() && config.get(STRIPE_PUBLIC_KEY).isPresent();
    }

    @Override
    public boolean accept(Transaction transaction) {
        //note, only StripeWebhookPaymentManager set 'clientSecret' in the metadata, this is a fallback for old cases
        var hasMetadata = !transaction.getMetadata().isEmpty();
        var isCCManager = STRIPE_MANAGER.equals(transaction.getMetadata().get(STRIPE_MANAGER_TYPE_KEY)) || transaction.getMetadata().get("clientSecret") == null;
        return transaction.getPaymentProxy() == PaymentProxy.STRIPE && (!hasMetadata || isCCManager);
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        return baseStripeManager.isActive(paymentContext, OPTIONS_TO_LOAD, this::checkConfiguration);
    }

    @Override
    public PaymentResult doPayment( PaymentSpecification spec ) {
        try {
            final Optional<Charge> optionalCharge = baseStripeManager.chargeCreditCard(spec);
            return optionalCharge.map(charge -> {
                log.info("transaction {} paid: {}", spec.getReservationId(), charge.getPaid());
                Pair<Long, Long> fees = Optional.ofNullable(charge.getBalanceTransactionObject()).map( bt -> {
                    List<BalanceTransaction.FeeDetail> feeDetails = bt.getFeeDetails();
                    return Pair.of(Optional.ofNullable( BaseStripeManager.getFeeAmount(feeDetails, "application_fee")).map(Long::parseLong).orElse(0L),
                        Optional.ofNullable( BaseStripeManager.getFeeAmount(feeDetails, "stripe_fee")).map(Long::parseLong).orElse(0L));
                }).orElse(null);

                PaymentManagerUtils.invalidateExistingTransactions(spec.getReservationId(), transactionRepository);
                transactionRepository.insert(charge.getId(), null, spec.getReservationId(),
                    ZonedDateTime.now(clockProvider.withZone(spec.getPurchaseContext().getZoneId())), spec.getPriceWithVAT(), spec.getPurchaseContext().getCurrency(), charge.getDescription(), PaymentProxy.STRIPE.name(),
                    fees != null ? fees.getLeft() : 0L, fees != null ? fees.getRight() : 0L, Transaction.Status.COMPLETE, Map.of(STRIPE_MANAGER_TYPE_KEY, STRIPE_MANAGER));
                return PaymentResult.successful(charge.getId());
            }).orElseGet(() -> PaymentResult.failed("error.STEP2_UNABLE_TO_TRANSITION"));
        } catch (StripeException e) {
            return PaymentResult.failed(baseStripeManager.handleException(e));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public PaymentToken buildPaymentToken(String clientToken, PaymentContext context) {
        return new StripeCreditCardToken(clientToken);
    }

}
