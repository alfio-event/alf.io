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
package alfio.manager;


import alfio.manager.support.FeeCalculator;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.PaymentInformation;
import alfio.model.system.Configuration;
import alfio.model.system.Configuration.ConfigurationPathKey;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.ClientServerTokenRequest;
import alfio.model.transaction.capabilities.PaymentInfo;
import alfio.model.transaction.capabilities.RefundRequest;
import alfio.model.transaction.token.StripeCreditCardToken;
import alfio.repository.TicketRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.ErrorsCode;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.model.OAuthConfig;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.stripe.exception.*;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Fee;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;

import static alfio.model.system.ConfigurationKeys.*;

@Component
@Log4j2
public class StripeCreditCardManager implements PaymentProvider, ClientServerTokenRequest, RefundRequest, PaymentInfo {

    public static final String STRIPE_UNEXPECTED = "error.STEP2_STRIPE_unexpected";
    public static final String CONNECT_REDIRECT_PATH = "/admin/configuration/payment/stripe/authorize";
    private final Map<Class<? extends StripeException>, StripeExceptionHandler> handlers;
    private final ConfigurationManager configurationManager;
    private final TicketRepository ticketRepository;
    private final ConfigurationRepository configurationRepository;
    private final Environment environment;
    private final TransactionRepository transactionRepository;

    public StripeCreditCardManager( ConfigurationManager configurationManager,
                                    TicketRepository ticketRepository,
                                    TransactionRepository transactionRepository,
                                    ConfigurationRepository configurationRepository,
                                    Environment environment) {
        this.configurationManager = configurationManager;
        this.ticketRepository = ticketRepository;
        this.configurationRepository = configurationRepository;
        this.environment = environment;
        this.transactionRepository = transactionRepository;

        handlers = new HashMap<>();
        handlers.put(CardException.class, this::handleCardException);
        handlers.put(InvalidRequestException.class, this::handleInvalidRequestException);
        handlers.put(AuthenticationException.class, this::handleAuthenticationException);
        handlers.put(APIConnectionException.class, this::handleApiConnectionException);
        handlers.put(StripeException.class, this::handleGenericException);
    }

    private String getSecretKey(Event event) {
        return configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), STRIPE_SECRET_KEY));
    }

    private String getWebhookSignatureKey() {
        return configurationManager.getRequiredValue(Configuration.getSystemConfiguration(STRIPE_WEBHOOK_KEY));
    }

    private String getPublicKey(PaymentContext context) {
        if(isConnectEnabled(context)) {
            return configurationManager.getRequiredValue(Configuration.getSystemConfiguration(STRIPE_PUBLIC_KEY));
        }
        return configurationManager.getRequiredValue(context.narrow(STRIPE_PUBLIC_KEY));
    }

    public ConnectURL getConnectURL(Function<ConfigurationKeys, ConfigurationPathKey> keyResolver) {
        String secret = configurationManager.getRequiredValue(keyResolver.apply(STRIPE_SECRET_KEY));
        String clientId = configurationManager.getRequiredValue(keyResolver.apply(STRIPE_CONNECT_CLIENT_ID));
        String callbackURL = configurationManager.getStringConfigValue(keyResolver.apply(STRIPE_CONNECT_CALLBACK), configurationManager.getRequiredValue(keyResolver.apply(BASE_URL)) + CONNECT_REDIRECT_PATH);
        String state = UUID.randomUUID().toString();
        String code = UUID.randomUUID().toString();
        OAuthConfig config = new OAuthConfig(clientId, secret, callbackURL, "read_write", null, state, "code", null, null, null);
        return new ConnectURL(new StripeConnectApi().getAuthorizationUrl(config, Collections.emptyMap()), state, code);
    }

    private boolean isConnectEnabled(PaymentContext context) {
        return configurationManager.getBooleanConfigValue(context.narrow(PLATFORM_MODE_ENABLED), false);
    }

    public ConnectResult storeConnectedAccountId(String code, Function<ConfigurationKeys, ConfigurationPathKey> keyResolver) {
        try {
            String clientSecret = getSystemApiKey();
            OAuth20Service service = new ServiceBuilder(clientSecret).apiSecret(clientSecret).build(new StripeConnectApi());
            Map<String, String> token = Json.fromJson(service.getAccessToken(code).getRawResponse(), new TypeReference<Map<String, String>>() {});
            String accountId = token.get("stripe_user_id");
            if(accountId != null) {
                configurationManager.saveConfig(keyResolver.apply(ConfigurationKeys.STRIPE_CONNECTED_ID), accountId);
            }
            return new ConnectResult(accountId, accountId != null, token.get("error_message"));
        } catch (Exception e) {
            log.error("cannot retrieve account ID", e);
            return new ConnectResult(null, false, e.getMessage());
        }
    }

    private String getSystemApiKey() {
        return configurationManager.getRequiredValue(Configuration.getSystemConfiguration(STRIPE_SECRET_KEY));
    }

    public Optional<Boolean> processWebhookEvent(String body, String signature) {
        try {
            com.stripe.model.Event event = Webhook.constructEvent(body, signature, getWebhookSignatureKey());
            if("account.application.deauthorized".equals(event.getType())
                && event.getLivemode() != null
                && event.getLivemode() == environment.acceptsProfiles(Profiles.of("dev", "test", "demo"))) {
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
    Optional<Charge> chargeCreditCard(String stripeToken, long amountInCent, Event event,
                            String reservationId, String email, String fullName, String billingAddress) throws StripeException {

        int tickets = ticketRepository.countTicketsInReservation(reservationId);
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", amountInCent);
        FeeCalculator.getCalculator(event, configurationManager).apply(tickets, amountInCent).ifPresent(fee -> chargeParams.put("application_fee", fee));
        chargeParams.put("currency", event.getCurrency());
        chargeParams.put("card", stripeToken);

        chargeParams.put("description", String.format("%d ticket(s) for event %s", tickets, event.getDisplayName()));

        Map<String, String> initialMetadata = new HashMap<>();
        initialMetadata.put("reservationId", reservationId);
        initialMetadata.put("email", email);
        initialMetadata.put("fullName", fullName);
        if (StringUtils.isNotBlank(billingAddress)) {
            initialMetadata.put("billingAddress", billingAddress);
        }
        chargeParams.put("metadata", initialMetadata);

        return charge( event, chargeParams );
    }

    protected Optional<Charge> charge( Event event, Map<String, Object> chargeParams ) throws StripeException {
        Optional<RequestOptions> opt = options(event);
        if(!opt.isPresent()) {
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

    @Override
    public PaymentResult getToken(PaymentSpecification spec) {
        if(spec.getGatewayToken() != null && spec.getGatewayToken().getPaymentProvider() == PaymentProxy.STRIPE) {
            return PaymentResult.initialized(spec.getGatewayToken().getToken());
        }
        return PaymentResult.failed(ErrorsCode.STEP_2_MISSING_STRIPE_TOKEN);
    }

    private BalanceTransaction retrieveBalanceTransaction(String balanceTransaction, RequestOptions options) throws AuthenticationException, InvalidRequestException, APIConnectionException, CardException, APIException {
        return BalanceTransaction.retrieve(balanceTransaction, options);
    }


    Optional<RequestOptions> options(Event event) {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder();
        if(isConnectEnabled(new PaymentContext(event))) {
            return configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), STRIPE_CONNECTED_ID))
                .map(connectedId -> {
                    //connected stripe account
                    builder.setStripeAccount(connectedId);
                    return builder.setApiKey(getSystemApiKey()).build();
                });
        }
        return Optional.of(builder.setApiKey(getSecretKey(event)).build());
    }

    @Override
    public Optional<PaymentInformation> getInfo(Transaction transaction, Event event) {
        try {
            Optional<RequestOptions> requestOptionsOptional = options(event);
            if(requestOptionsOptional.isPresent()) {
                RequestOptions options = requestOptionsOptional.get();
                Charge charge = Charge.retrieve(transaction.getTransactionId(), options);
                String paidAmount = MonetaryUtil.formatCents(charge.getAmount());
                String refundedAmount = MonetaryUtil.formatCents(charge.getAmountRefunded());
                List<Fee> fees = retrieveBalanceTransaction(charge.getBalanceTransaction(), options).getFeeDetails();
                return Optional.of(new PaymentInformation(paidAmount, refundedAmount, getFeeAmount(fees, "stripe_fee"), getFeeAmount(fees, "application_fee")));
            }
            return Optional.empty();
        } catch (StripeException e) {
            return Optional.empty();
        }
    }

    private static String getFeeAmount(List<Fee> fees, String feeType) {
        return fees.stream()
            .filter(f -> f.getType().equals(feeType))
            .findFirst()
            .map(Fee::getAmount)
            .map(String::valueOf)
            .orElse(null);
    }

    // https://stripe.com/docs/api#create_refund
    @Override
    public boolean refund(Transaction transaction, Event event, Integer amountToRefund) {
        Optional<Integer> amount = Optional.ofNullable(amountToRefund);
        String chargeId = transaction.getTransactionId();
        try {
            String amountOrFull = amount.map(MonetaryUtil::formatCents).orElse("full");
            log.info("Stripe: trying to do a refund for payment {} with amount: {}", chargeId, amountOrFull);
            Map<String, Object> params = new HashMap<>();
            params.put("charge", chargeId);
            amount.ifPresent(a -> params.put("amount", a));
            if(isConnectEnabled(new PaymentContext(event))) {
                params.put("refund_application_fee", true);
            }

            Optional<RequestOptions> requestOptionsOptional = options(event);
            if(requestOptionsOptional.isPresent()) {
                RequestOptions options = requestOptionsOptional.get();
                Refund r = Refund.create(params, options);
                if("succeeded".equals(r.getStatus())) {
                    log.info("Stripe: refund for payment {} executed with success for amount: {}", chargeId, amountOrFull);
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

    @Override
    public Map<String, ?> getModelOptions(PaymentContext context) {
        return Collections.singletonMap("stripe_p_key", getPublicKey(context));
    }

    String handleException(StripeException exc) {
        return findExceptionHandler(exc).handle(exc);
    }

    private StripeExceptionHandler findExceptionHandler(StripeException exc) {
        final Optional<StripeExceptionHandler> eh = Optional.ofNullable(handlers.get(exc.getClass()));
        if(!eh.isPresent()) {
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
        return STRIPE_UNEXPECTED;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context) {
        return paymentMethod == PaymentMethod.CREDIT_CARD
            && configurationManager.getBooleanConfigValue(context.narrow(STRIPE_CC_ENABLED), false);
    }

    @Override
    public PaymentResult doPayment( PaymentSpecification spec ) {
        try {
            final Optional<Charge> optionalCharge = chargeCreditCard(spec.getGatewayToken().getToken(), spec.getPriceWithVAT(),
                spec.getEvent(), spec.getReservationId(), spec.getEmail(), spec.getCustomerName().getFullName(), spec.getBillingAddress());
            return optionalCharge.map(charge -> {
                log.info("transaction {} paid: {}", spec.getReservationId(), charge.getPaid());
                Pair<Long, Long> fees = Optional.ofNullable(charge.getBalanceTransactionObject()).map( bt -> {
                    List<Fee> feeDetails = bt.getFeeDetails();
                    return Pair.of(Optional.ofNullable( StripeCreditCardManager.getFeeAmount(feeDetails, "application_fee")).map(Long::parseLong).orElse(0L),
                        Optional.ofNullable( StripeCreditCardManager.getFeeAmount(feeDetails, "stripe_fee")).map(Long::parseLong).orElse(0L));
                }).orElse(null);

                transactionRepository.insert(charge.getId(), null, spec.getReservationId(),
                    ZonedDateTime.now(), spec.getPriceWithVAT(), spec.getEvent().getCurrency(), charge.getDescription(), PaymentProxy.STRIPE.name(),
                    fees != null ? fees.getLeft() : 0L, fees != null ? fees.getRight() : 0L);
                return PaymentResult.successful(charge.getId());
            }).orElseGet(() -> PaymentResult.failed("error.STEP2_UNABLE_TO_TRANSITION"));
        } catch (Exception e) {
            if(e instanceof StripeException) {
                return PaymentResult.failed( handleException((StripeException)e));
            }
            throw new IllegalStateException(e);
        }
    }

    @Override
    public PaymentToken buildPaymentToken(String clientToken) {
        return new StripeCreditCardToken(clientToken);
    }

    @FunctionalInterface
    private interface StripeExceptionHandler {
        String handle(StripeException exc);
    }

    private static class StripeConnectApi extends DefaultApi20 {

        @Override
        public String getAccessTokenEndpoint() {
            return "https://connect.stripe.com/oauth/token";
        }

        @Override
        protected String getAuthorizationBaseUrl() {
            return "https://connect.stripe.com/oauth/authorize";
        }
    }

    @Data
    public static class ConnectResult {
        private final String accountId;
        private final boolean success;
        private final String errorMessage;
    }

    @Data
    public static class ConnectURL {
        private final String authorizationURL;
        private final String state;
        private final String code;
    }

}
