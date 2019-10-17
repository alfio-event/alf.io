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
import alfio.model.Event;
import alfio.model.EventAndOrganizationId;
import alfio.model.PaymentInformation;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
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

import static alfio.model.system.ConfigurationKeys.*;

@Log4j2
@AllArgsConstructor
class BaseStripeManager {

    static final String SUCCEEDED = "succeeded";
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

    String getSecretKey(EventAndOrganizationId event) {
        return configurationManager.getRequiredValue(Configuration.from(event, STRIPE_SECRET_KEY));
    }

    String getWebhookSignatureKey() {
        return configurationManager.getRequiredValue(Configuration.getSystemConfiguration(STRIPE_WEBHOOK_KEY));
    }

    String getPublicKey(PaymentContext context) {
        if(isConnectEnabled(context)) {
            return configurationManager.getRequiredValue(Configuration.getSystemConfiguration(STRIPE_PUBLIC_KEY));
        }
        return configurationManager.getRequiredValue(context.narrow(STRIPE_PUBLIC_KEY));
    }

    Map<String, ?> getModelOptions(PaymentContext context) {
        return Collections.singletonMap("stripe_p_key", getPublicKey(context));
    }

    private boolean isConnectEnabled(PaymentContext context) {
        return configurationManager.getBooleanConfigValue(context.narrow(PLATFORM_MODE_ENABLED), false);
    }

    String getSystemApiKey() {
        return configurationManager.getRequiredValue(Configuration.getSystemConfiguration(STRIPE_SECRET_KEY));
    }

    Optional<Boolean> processWebhookEvent(String body, String signature) {
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
    Optional<Charge> chargeCreditCard(PaymentSpecification spec) throws StripeException {
        var chargeParams = createParams(spec, Map.of());
        chargeParams.put("card", spec.getGatewayToken().getToken());
        return charge( spec.getEvent(), chargeParams );
    }

    protected Map<String, Object> createParams(PaymentSpecification spec, Map<String, String> baseMetadata) {
        int tickets = ticketRepository.countTicketsInReservation(spec.getReservationId());
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", spec.getPriceWithVAT());
        FeeCalculator.getCalculator(spec.getEvent(), configurationManager)
            .apply(tickets, (long) spec.getPriceWithVAT())
            .filter(l -> l > 0)
            .ifPresent(fee -> chargeParams.put("application_fee_amount", fee));
        chargeParams.put("currency", spec.getEvent().getCurrency());

        chargeParams.put("description", String.format("%d ticket(s) for event %s", tickets, spec.getEvent().getDisplayName()));

        chargeParams.put("metadata", MetadataBuilder.buildMetadata(spec, baseMetadata));
        return chargeParams;
    }

    protected Optional<Charge> charge( Event event, Map<String, Object> chargeParams ) throws StripeException {
        Optional<RequestOptions> opt = options(event);
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

    private BalanceTransaction retrieveBalanceTransaction(String balanceTransaction, RequestOptions options) throws StripeException {
        return BalanceTransaction.retrieve(balanceTransaction, options);
    }


    Optional<RequestOptions> options(Event event) {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder();
        if(isConnectEnabled(new PaymentContext(event))) {
            return configurationManager.getStringConfigValue(Configuration.from(event, STRIPE_CONNECTED_ID))
                .map(connectedId -> {
                    //connected stripe account
                    builder.setStripeAccount(connectedId);
                    return builder.setApiKey(getSystemApiKey()).build();
                });
        }
        return Optional.of(builder.setApiKey(getSecretKey(event)).build());
    }

    Optional<String> getConnectedAccount(PaymentContext paymentContext) {
        if(isConnectEnabled(paymentContext)) {
            return configurationManager.getStringConfigValue(paymentContext.narrow(STRIPE_CONNECTED_ID));
        }
        return Optional.empty();
    }

    Optional<PaymentInformation> getInfo(Transaction transaction, Event event) {
        try {
            Optional<RequestOptions> requestOptionsOptional = options(event);
            if(requestOptionsOptional.isPresent()) {
                RequestOptions options = requestOptionsOptional.get();
                Charge charge = Charge.retrieve(transaction.getTransactionId(), options);
                String paidAmount = MonetaryUtil.formatCents(charge.getAmount());
                String refundedAmount = MonetaryUtil.formatCents(charge.getAmountRefunded());
                List<BalanceTransaction.Fee> fees = retrieveBalanceTransaction(charge.getBalanceTransaction(), options).getFeeDetails();
                return Optional.of(new PaymentInformation(paidAmount, refundedAmount, getFeeAmount(fees, "stripe_fee"), getFeeAmount(fees, "application_fee")));
            }
            return Optional.empty();
        } catch (StripeException e) {
            return Optional.empty();
        }
    }

    static String getFeeAmount(List<BalanceTransaction.Fee> fees, String feeType) {
        return fees.stream()
            .filter(f -> f.getType().equals(feeType))
            .findFirst()
            .map(BalanceTransaction.Fee::getAmount)
            .map(String::valueOf)
            .orElse(null);
    }

    // https://stripe.com/docs/api#create_refund
    boolean refund(Transaction transaction, Event event, Integer amountToRefund) {
        Optional<Integer> amount = Optional.ofNullable(amountToRefund);
        String chargeId = transaction.getTransactionId();
        try {
            String amountOrFull = amount.map(MonetaryUtil::formatCents).orElse("full");
            log.info("Stripe: trying to do a refund for payment {} with amount: {}", chargeId, amountOrFull);
            Map<String, Object> params = new HashMap<>();
            params.put("charge", chargeId);
            amount.ifPresent(a -> params.put("amount", a));
            if(transaction.getPlatformFee() > 0 && isConnectEnabled(new PaymentContext(event))) {
                params.put("refund_application_fee", true);
            }

            Optional<RequestOptions> requestOptionsOptional = options(event);
            if(requestOptionsOptional.isPresent()) {
                RequestOptions options = requestOptionsOptional.get();
                Refund r = Refund.create(params, options);
                if(SUCCEEDED.equals(r.getStatus())) {
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

    boolean accept(PaymentMethod paymentMethod, PaymentContext context) {
        return paymentMethod == PaymentMethod.CREDIT_CARD
            && configurationManager.getBooleanConfigValue(context.narrow(STRIPE_CC_ENABLED), false);
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
