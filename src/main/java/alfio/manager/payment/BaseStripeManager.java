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

import alfio.manager.support.FeeCalculator;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Configurable;
import alfio.model.PaymentInformation;
import alfio.model.PurchaseContext;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.ErrorsCode;
import alfio.util.MonetaryUtil;
import com.stripe.Stripe;
import com.stripe.exception.*;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static alfio.model.system.ConfigurationKeys.*;

@Log4j2
@AllArgsConstructor
class BaseStripeManager {

    static final String STRIPE_MANAGER_TYPE_KEY = "stripeManagerType";
    static final String SUCCEEDED = "succeeded";
    static final String PENDING = "pending";
    private final ConfigurationManager configurationManager;
    private final ConfigurationRepository configurationRepository;
    private final TicketRepository ticketRepository;
    private final Environment environment;

    private final Map<Class<? extends StripeException>, StripeExceptionHandler> handlers = Map.of(
        CardException.class, this::handleCardException,
        InvalidRequestException.class, this::handleInvalidRequestException,
        AuthenticationException.class, this::handleAuthenticationException,
        ApiConnectionException.class, this::handleApiConnectionException,
        StripeException.class, this::handleGenericException
    );

    static {
        Stripe.setAppInfo("Alf.io", "2.x", "https://alf.io");
    }

    String getSecretKey(Configurable configurable) {
        return configurationManager.getFor(STRIPE_SECRET_KEY, configurable.getConfigurationLevel()).getRequiredValue();
    }

    String getWebhookSignatureKey() {
        return configurationManager.getForSystem(STRIPE_WEBHOOK_KEY).getRequiredValue();
    }

    String getPublicKey(PaymentContext context) {
        if(isConnectEnabled(context)) {
            return configurationManager.getForSystem(STRIPE_PUBLIC_KEY).getRequiredValue();
        }
        return configurationManager.getFor(STRIPE_PUBLIC_KEY, context.getConfigurationLevel()).getRequiredValue();
    }

    Map<String, ?> getModelOptions(PaymentContext context) {
        Map<String, Object> options = new HashMap<>();
        options.put("enableSCA", configurationManager.getFor(STRIPE_ENABLE_SCA, context.getConfigurationLevel()).getValueAsBooleanOrDefault());
        options.put("stripe_p_key", getPublicKey(context));
        return options;
    }

    private boolean isConnectEnabled(PaymentContext context) {
        return configurationManager.getFor(PLATFORM_MODE_ENABLED, context.getConfigurationLevel()).getValueAsBooleanOrDefault();
    }

    String getSystemSecretKey() {
        return configurationManager.getForSystem(STRIPE_SECRET_KEY).getRequiredValue();
    }

    Optional<Boolean> processWebhookEvent(String body, String signature) {
        try {
            com.stripe.model.Event event = Webhook.constructEvent(body, signature, getWebhookSignatureKey());
            if("account.application.deauthorized".equals(event.getType())
                && Boolean.TRUE.equals(event.getLivemode()) == environment.acceptsProfiles(Profiles.of("dev", "test", "demo"))) {
                return Optional.of(revokeToken(event.getAccount()));
            }
            return Optional.of(true);
        } catch (Exception e) {
            log.error("got exception while handling stripe webhook", e);
            return Optional.empty();
        }
    }

    private boolean revokeToken(String accountId) {
        String key = ConfigurationKeys.STRIPE_CONNECTED_ID.getValue();
        Optional<Integer> optional = configurationRepository.findOrganizationIdByKeyAndValue(key, accountId);
        if(optional.isPresent()) {
            Integer organizationId = optional.get();
            log.warn("revoking access token {} for organization {}", accountId, organizationId);
            configurationManager.deleteOrganizationLevelByKey(key, organizationId, UserManager.ADMIN_USERNAME);
            return true;
        }
        return false;
    }

    /**
     * After client side integration with stripe widget, our server receives the stripeToken
     * StripeToken is a single-use token that allows our server to actually charge the credit card and
     * get money on our account.
     * <p>
     * as documented in https://stripe.com/docs/tutorials/charges
     *
     * @return
     * @throws StripeException
     */
    Optional<Charge> chargeCreditCard(PaymentSpecification spec) throws StripeException {
        var chargeParams = createParams(spec, Map.of());
        chargeParams.put("card", spec.getGatewayToken().getToken());
        return charge(spec, chargeParams );
    }

    protected Map<String, Object> createParams(PaymentSpecification spec, Map<String, String> baseMetadata) {
        final int items;
        if(spec.getPurchaseContext().getType() == PurchaseContextType.event) {
            items = ticketRepository.countTicketsInReservation(spec.getReservationId());
        } else {
            items = 1;
        }
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", spec.getPriceWithVAT());
        var purchaseContext = spec.getPurchaseContext();
        FeeCalculator.getCalculator(purchaseContext, configurationManager, spec.getCurrencyCode())
            .apply(items, (long) spec.getPriceWithVAT())
            .filter(l -> l > 0)
            .ifPresent(fee -> chargeParams.put("application_fee_amount", fee));
        chargeParams.put("currency", purchaseContext.getCurrency());
        var description = purchaseContext.ofType(PurchaseContextType.event) ? "ticket(s) for event" : "x subscription";
        chargeParams.put("description", String.format("%d %s %s", items, description, purchaseContext.getDisplayName()));
        chargeParams.put("metadata", MetadataBuilder.buildMetadata(spec, baseMetadata));
        return chargeParams;
    }

    protected Optional<Charge> charge(PaymentSpecification spec, Map<String, Object> chargeParams ) throws StripeException {
        Optional<RequestOptions> opt = options(spec.getPurchaseContext(), builder -> builder.setIdempotencyKey(spec.getReservationId()));
        if(opt.isEmpty()) {
            return Optional.empty();
        }
        RequestOptions options = opt.get();
        Charge charge = Charge.create(chargeParams, options);
        if(charge.getBalanceTransactionObject() == null) {
            try {
                charge.setBalanceTransactionObject(retrieveBalanceTransaction(charge.getBalanceTransaction(), options));
            } catch(Exception e) {
                log.warn("can't retrieve balance transaction", e);
            }
        }
        return Optional.of(charge);
    }

    PaymentResult getToken(PaymentSpecification spec) {
        if(spec.getGatewayToken() != null && spec.getGatewayToken().getPaymentProvider() == PaymentProxy.STRIPE) {
            return PaymentResult.initialized(spec.getGatewayToken().getToken());
        }
        return PaymentResult.failed(ErrorsCode.STEP_2_MISSING_STRIPE_TOKEN);
    }

    BalanceTransaction retrieveBalanceTransaction(String balanceTransaction, RequestOptions options) throws StripeException {
        return BalanceTransaction.retrieve(balanceTransaction, options);
    }

    Charge retrieveCharge(String chargeId, RequestOptions requestOptions) throws StripeException {
        return Charge.retrieve(chargeId, requestOptions);
    }

    Optional<RequestOptions> options(PurchaseContext purchaseContext) {
        return options(purchaseContext, UnaryOperator.identity());
    }

    Optional<RequestOptions> options(PurchaseContext purchaseContext, UnaryOperator<RequestOptions.RequestOptionsBuilder> optionsBuilderConfigurer) {
        RequestOptions.RequestOptionsBuilder builder = optionsBuilderConfigurer.apply(RequestOptions.builder());
        if(isConnectEnabled(new PaymentContext(purchaseContext))) {
            return configurationManager.getFor(STRIPE_CONNECTED_ID, purchaseContext.getConfigurationLevel()).getValue()
                .map(connectedId -> {
                    //connected stripe account
                    builder.setStripeAccount(connectedId);
                    return builder.setApiKey(getSystemSecretKey()).build();
                });
        }
        return Optional.of(builder.setApiKey(getSecretKey(purchaseContext)).build());
    }

    Optional<String> getConnectedAccount(PaymentContext paymentContext) {
        if(isConnectEnabled(paymentContext)) {
            return configurationManager.getFor(STRIPE_CONNECTED_ID, paymentContext.getConfigurationLevel()).getValue();
        }
        return Optional.empty();
    }

    Optional<PaymentInformation> getInfo(Transaction transaction, PurchaseContext purchaseContext) {
        try {
            Optional<RequestOptions> requestOptionsOptional = options(purchaseContext);
            if(requestOptionsOptional.isPresent()) {
                RequestOptions options = requestOptionsOptional.get();
                Charge charge = retrieveCharge(transaction.getTransactionId(), options);
                String paidAmount = MonetaryUtil.formatCents(charge.getAmount(), charge.getCurrency());
                String refundedAmount = MonetaryUtil.formatCents(charge.getAmountRefunded(), charge.getCurrency());
                List<BalanceTransaction.FeeDetail> fees = retrieveBalanceTransaction(charge.getBalanceTransaction(), options).getFeeDetails();
                return Optional.of(new PaymentInformation(paidAmount, refundedAmount, getFeeAmount(fees, "stripe_fee"), getFeeAmount(fees, "application_fee")));
            }
            return Optional.empty();
        } catch (StripeException e) {
            return Optional.empty();
        }
    }

    static String getFeeAmount(List<BalanceTransaction.FeeDetail> fees, String feeType) {
        return fees.stream()
            .filter(f -> f.getType().equals(feeType))
            .findFirst()
            .map(BalanceTransaction.FeeDetail::getAmount)
            .map(String::valueOf)
            .orElse(null);
    }

    // https://stripe.com/docs/api#create_refund
    boolean refund(Transaction transaction, PurchaseContext purchaseContext, Integer amountToRefund) {
        Optional<Integer> amount = Optional.ofNullable(amountToRefund);
        String chargeId = transaction.getTransactionId();
        try {
            String amountOrFull = amount.map(p -> MonetaryUtil.formatCents(p, transaction.getCurrency())).orElse("full");
            log.info("Stripe: trying to do a refund for payment {} with amount: {}", chargeId, amountOrFull);
            Map<String, Object> params = new HashMap<>();
            params.put("charge", chargeId);
            amount.ifPresent(a -> params.put("amount", a));
            if(transaction.getPlatformFee() > 0 && isConnectEnabled(new PaymentContext(purchaseContext))) {
                params.put("refund_application_fee", true);
            }

            Optional<RequestOptions> requestOptionsOptional = options(purchaseContext);
            if(requestOptionsOptional.isPresent()) {
                RequestOptions options = requestOptionsOptional.get();
                Refund r = Refund.create(params, options);
                boolean pending = PENDING.equals(r.getStatus());
                if(SUCCEEDED.equals(r.getStatus()) || pending) {
                    log.info("Stripe: refund for payment {} {} for amount: {}", chargeId, pending ? "registered": "executed with success", amountOrFull);
                    return true;
                } else {
                    log.warn("Stripe: was not able to refund payment with id {}, returned status is not 'succeded' but {}", chargeId, r.getStatus());
                    return false;
                }
            }
            return false;
        } catch (StripeException e) {
            log.warn("Stripe: was not able to refund payment with id " + chargeId, e);
            return false;
        }
    }

    boolean accept(PaymentMethod paymentMethod, PaymentContext context,
                   EnumSet<ConfigurationKeys> additionalKeys,
                   Predicate<Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration>> subValidator) {
        return paymentMethod == PaymentMethod.CREDIT_CARD
            && isActive(context, additionalKeys, subValidator);
    }

    boolean isActive(PaymentContext context,
                     EnumSet<ConfigurationKeys> additionalKeys,
                     Predicate<Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration>> subValidator) {
        var optionsToLoad = EnumSet.copyOf(additionalKeys);
        optionsToLoad.addAll(EnumSet.of(STRIPE_CC_ENABLED, PLATFORM_MODE_ENABLED, STRIPE_CONNECTED_ID));
        var configuration = configurationManager.getFor(optionsToLoad, context.getConfigurationLevel());
        return configuration.get(STRIPE_CC_ENABLED).getValueAsBooleanOrDefault()
            && (!configuration.get(PLATFORM_MODE_ENABLED).getValueAsBooleanOrDefault() || context.getConfigurationLevel().getPathLevel() == ConfigurationPathLevel.SYSTEM || configuration.get(STRIPE_CONNECTED_ID).isPresent())
            && subValidator.test(configuration);
    }

    String handleException(StripeException exc) {
        return findExceptionHandler(exc).handle(exc);
    }

    private StripeExceptionHandler findExceptionHandler(StripeException exc) {
        final Optional<StripeExceptionHandler> eh = Optional.ofNullable(handlers.get(exc.getClass()));
        if(eh.isEmpty()) {
            log.warn("cannot find an ExceptionHandler for {}. Falling back to the default one.", exc.getClass());
        }
        return eh.orElseGet(() -> handlers.get(StripeException.class));
    }

    /* exception handlers... */

    /**
     * This handler simply returns the message code from stripe.
     * There is no need in writing something in the log.
     * @param e the exception
     * @return the code
     */
    private String handleCardException(StripeException e) {
        CardException ce = (CardException)e;
        return "error.STEP2_STRIPE_" + ce.getCode();
    }

    /**
     * handles invalid request exception using the error.STEP2_STRIPE_invalid_ prefix for the message.
     * @param e the exception
     * @return message code
     */
    private String handleInvalidRequestException(StripeException e) {
        InvalidRequestException ire = (InvalidRequestException)e;
        return "error.STEP2_STRIPE_invalid_" + ire.getParam();
    }

    /**
     * Logs the failure and report the failure to the admin (to be done)
     * @param e the exception
     * @return error.STEP2_STRIPE_abort
     */
    private String handleAuthenticationException(StripeException e) {
        log.error("an AuthenticationException has occurred. Please fix configuration!!", e);
        return "error.STEP2_STRIPE_abort";
    }

    /**
     * Logs the failure and report the failure to the admin (to be done)
     * @param e
     * @return
     */
    private String handleApiConnectionException(StripeException e) {
        log.error("unable to connect to the Stripe API", e);
        return "error.STEP2_STRIPE_abort";
    }

    /**
     * Logs the failure and report the failure to the admin (to be done)
     * @param e
     * @return
     */
    private String handleGenericException(StripeException e) {
        log.error("unexpected error during transaction", e);
        return StripeCreditCardManager.STRIPE_UNEXPECTED;
    }

    @FunctionalInterface
    private interface StripeExceptionHandler {
        String handle(StripeException exc);
    }

}
