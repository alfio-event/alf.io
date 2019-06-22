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
import alfio.manager.support.PaymentWebhookResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Audit;
import alfio.model.Event;
import alfio.model.PaymentInformation;
import alfio.model.system.Configuration;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.*;
import alfio.model.transaction.token.StripeSCACreditCardToken;
import alfio.model.transaction.webhook.StripeChargeTransactionWebhookPayload;
import alfio.model.transaction.webhook.StripePaymentIntentWebhookPayload;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;

import static alfio.model.TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT;
import static alfio.model.TicketReservation.TicketReservationStatus.WAITING_EXTERNAL_CONFIRMATION;
import static alfio.model.system.ConfigurationKeys.*;

@Log4j2
@Component
@Transactional
public class StripeWebhookPaymentManager implements PaymentProvider, RefundRequest, PaymentInfo, SignedWebhookHandler, ClientServerTokenRequest, ServerInitiatedTransaction {


    private static final String CLIENT_SECRET_METADATA = "clientSecret";
    private static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String PAYMENT_INTENT_PAYMENT_FAILED = "payment_intent.payment_failed";
    private static final String PAYMENT_INTENT_CREATED = "payment_intent.created";
    private final ConfigurationManager configurationManager;
    private final BaseStripeManager baseStripeManager;
    private final TransactionRepository transactionRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;
    private final AuditingRepository auditingRepository;
    private final List<String> interestingEventTypes = List.of(PAYMENT_INTENT_SUCCEEDED, PAYMENT_INTENT_PAYMENT_FAILED, PAYMENT_INTENT_CREATED);

    public StripeWebhookPaymentManager(ConfigurationManager configurationManager,
                                       TicketRepository ticketRepository,
                                       TransactionRepository transactionRepository,
                                       ConfigurationRepository configurationRepository,
                                       TicketReservationRepository ticketReservationRepository,
                                       EventRepository eventRepository,
                                       AuditingRepository auditingRepository,
                                       Environment environment) {
        this.configurationManager = configurationManager;
        this.transactionRepository = transactionRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.eventRepository = eventRepository;
        this.auditingRepository = auditingRepository;
        this.baseStripeManager = new BaseStripeManager(configurationManager, configurationRepository, ticketRepository, environment);
    }

    @Override
    public TransactionInitializationToken initTransaction(PaymentSpecification paymentSpecification, Map<String, List<String>> params) {
        var reservationId = paymentSpecification.getReservationId();
        return transactionRepository.loadOptionalByReservationId(reservationId)
            .map(this::buildTokenFromTransaction)
            .orElseGet(() -> createNewToken(paymentSpecification));
    }

    @Override
    public TransactionInitializationToken errorToken(String errorMessage) {
        return new TransactionInitializationToken() {
            @Override
            public String getClientSecret() {
                return null;
            }

            @Override
            public String getToken() {
                return null;
            }

            @Override
            public PaymentMethod getPaymentMethod() {
                return PaymentMethod.CREDIT_CARD;
            }

            @Override
            public PaymentProxy getPaymentProvider() {
                return PaymentProxy.STRIPE;
            }

            @Override
            public String getErrorMessage() {
                return errorMessage;
            }
        };
    }

    private StripeSCACreditCardToken buildTokenFromTransaction(Transaction transaction) {
        String clientSecret = Optional.ofNullable(transaction.getMetadata()).map(m -> m.get(CLIENT_SECRET_METADATA)).orElse(null);
        String chargeId = transaction.getStatus() == Transaction.Status.COMPLETE ? transaction.getTransactionId() : null;
        return new StripeSCACreditCardToken(transaction.getPaymentId(), chargeId, clientSecret);
    }

    private StripeSCACreditCardToken createNewToken(PaymentSpecification paymentSpecification) {
        Map<String, String> baseMetadata = configurationManager.getStringConfigValue(Configuration.from(paymentSpecification.getEvent(), BASE_URL))
            .map(baseUrl -> Map.of("alfioBaseUrl", baseUrl))
            .orElse(Map.of());
        var paymentIntentParams = baseStripeManager.createParams(paymentSpecification, baseMetadata);
        paymentIntentParams.put("payment_method_types", List.of("card"));
        try {
            var intent = PaymentIntent.create(paymentIntentParams, baseStripeManager.options(paymentSpecification.getEvent()).orElseThrow());
            var clientSecret = intent.getClientSecret();
            long platformFee = paymentIntentParams.containsKey("application_fee") ? (long) paymentIntentParams.get("application_fee") : 0L;
            transactionRepository.insert(intent.getId(), intent.getId(),
                paymentSpecification.getReservationId(), ZonedDateTime.now(paymentSpecification.getEvent().getZoneId()),
                paymentSpecification.getPriceWithVAT(), paymentSpecification.getEvent().getCurrency(), "Payment Intent",
                PaymentProxy.STRIPE.name(), platformFee,0L, Transaction.Status.PENDING, Map.of(CLIENT_SECRET_METADATA, clientSecret));
            return new StripeSCACreditCardToken(intent.getId(), null, clientSecret);

        } catch (StripeException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getWebhookSignatureKey() {
        return configurationManager.getStringConfigValue(Configuration.getSystemConfiguration(STRIPE_WEBHOOK_PAYMENT_KEY)).orElseThrow();
    }

    @Override
    public Optional<TransactionWebhookPayload> parseTransactionPayload(String body, String signature) {
        try {
            var stripeEvent = Webhook.constructEvent(body, signature, getWebhookSignatureKey());
            String eventType = stripeEvent.getType();
            if(eventType.startsWith("charge.")) {
                return deserializeObject(stripeEvent).map(obj -> new StripeChargeTransactionWebhookPayload(eventType, (Charge)obj));
            } else if(eventType.startsWith("payment_intent.")) {
                return deserializeObject(stripeEvent).map(obj -> new StripePaymentIntentWebhookPayload(eventType, (PaymentIntent)obj));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("got exception while handling stripe webhook", e);
            return Optional.empty();
        }
    }

    private Optional<StripeObject> deserializeObject(com.stripe.model.Event stripeEvent) {
        var dataObjectDeserializer = stripeEvent.getDataObjectDeserializer();
        var cleanDeserialization = dataObjectDeserializer.getObject();
        if(cleanDeserialization.isPresent()) {
            return cleanDeserialization;
        }
        log.warn("unable to deserialize payload. Expected version {}, actual {}, falling back to unsafe deserialization", Stripe.API_VERSION, stripeEvent.getApiVersion());
        try {
            return Optional.ofNullable(dataObjectDeserializer.deserializeUnsafe());
        } catch(Exception e) {
            throw new IllegalArgumentException("Cannot deserialize webhook event.", e);
        }
    }

    @Override
    public PaymentWebhookResult processWebhook(TransactionWebhookPayload payload, Transaction transaction, PaymentContext paymentContext) {

        // first of all, we check if we're interested in the current event
        if(!interestingEventTypes.contains(payload.getType())) {
            //we're not interested to other kind of events yet...
            return PaymentWebhookResult.notRelevant(payload.getType());
        }

        boolean live = Boolean.TRUE.equals(((PaymentIntent) payload.getPayload()).getLivemode());
        if(!baseStripeManager.getSecretKey(paymentContext.getEvent()).startsWith(live ? "sk_live_" : "sk_test_")) {
            var description = live ? "live" : "test";
            log.warn("received a {} event of type {}, which is not compatible with the current configuration", description, payload.getType());
            return PaymentWebhookResult.notRelevant(description);
        }

        // since the transaction should have already been confirmed on the server, we have just to
        // check the status of the payment and, if successful, update the transaction record accordingly
        try {
            var paymentIntent = (PaymentIntent) payload.getPayload();
            //check if the reservation is still in pending state
            var optionalReservation = ticketReservationRepository.findOptionalReservationById(paymentIntent.getMetadata().get(MetadataBuilder.RESERVATION_ID))
                .filter(reservation -> (reservation.getStatus() == EXTERNAL_PROCESSING_PAYMENT || reservation.getStatus() == WAITING_EXTERNAL_CONFIRMATION));
            if(optionalReservation.isEmpty()) {
                return PaymentWebhookResult.error("reservation not found");
            }
            var reservation = optionalReservation.get();
            var event = eventRepository.findByReservationId(reservation.getId());
            switch(payload.getType()) {
                case PAYMENT_INTENT_CREATED: {
                    return PaymentWebhookResult.processStarted(buildTokenFromTransaction(transaction));
                }
                case PAYMENT_INTENT_SUCCEEDED: {
                    var charge = paymentIntent.getCharges().getData().get(0);
                    var chargeId = charge.getId();
                    long gtwFee = Optional.ofNullable(charge.getBalanceTransactionObject()).map(BalanceTransaction::getFee).orElse(0L);
                    transactionRepository.update(transaction.getId(), chargeId, transaction.getPaymentId(), ZonedDateTime.now(), transaction.getPlatformFee(), gtwFee, Transaction.Status.COMPLETE, Map.of());
                    List<Map<String, Object>> modifications = List.of(Map.of("paymentId", chargeId, "paymentMethod", "stripe"));
                    auditingRepository.insert(reservation.getId(), null, event.getId(), Audit.EventType.PAYMENT_CONFIRMED, new Date(), Audit.EntityType.RESERVATION, reservation.getId(), modifications);
                    return PaymentWebhookResult.successful(new StripeSCACreditCardToken(transaction.getPaymentId(), chargeId, null));
                }
                case PAYMENT_INTENT_PAYMENT_FAILED: {
                    // from docs:
                    //   If payment fails at any stage during the process, the PaymentIntent’s status resets to requires_payment_method.
                    //   When it returns to the initial state, you can prompt the customer to try again–potentially with a different payment method, if desired
                    // so we make sure to set the transaction as failed

                    List<Map<String, Object>> modifications = List.of(Map.of("paymentId", transaction.getPaymentId(), "paymentMethod", "stripe"));
                    auditingRepository.insert(reservation.getId(), null, event.getId(), Audit.EventType.PAYMENT_FAILED, new Date(), Audit.EntityType.RESERVATION, reservation.getId(), modifications);
                    transactionRepository.updateStatusForReservation(reservation.getId(), Transaction.Status.FAILED);
                    return PaymentWebhookResult.failed("Charge has been reset by Stripe. This is usually caused by a rejection from the customer's bank");
                }
            }

        } catch (Exception e) {
            log.error("Error while trying to confirm the reservation", e);
            return PaymentWebhookResult.error("unexpected error");
        }
        return PaymentWebhookResult.notRelevant("event is not relevant");
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context) {
        return baseStripeManager.accept(paymentMethod, context)
            && configurationManager.getBooleanConfigValue(context.narrow(STRIPE_ENABLE_SCA), false)
            && configurationManager.getStringConfigValue(context.narrow(BASE_URL)).isPresent()
            && isWebhookKeyDefined(context);
    }

    private boolean isWebhookKeyDefined(PaymentContext context) {
        return !configurationManager.getStringConfigValue(context.narrow(STRIPE_WEBHOOK_PAYMENT_KEY))
            .map(String::strip)
            .orElse("")
            .isEmpty();
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        var optionalTransaction = transactionRepository.loadOptionalByReservationId(spec.getReservationId());
        if(optionalTransaction.isEmpty()) {
            return PaymentResult.failed(StripeCreditCardManager.STRIPE_UNEXPECTED);
        }
        var transaction = optionalTransaction.get();
        return transaction.getStatus() == Transaction.Status.COMPLETE ? PaymentResult.successful(transaction.getTransactionId()) : PaymentResult.initialized(transaction.getPaymentId());
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

    @Override
    public Map<String, ?> getModelOptions(PaymentContext context) {
        var baseOptions = new HashMap<String, Object>(baseStripeManager.getModelOptions(context));
        var connectedAccountOptional = baseStripeManager.getConnectedAccount(context);
        baseOptions.put("platformMode", connectedAccountOptional.isPresent());
        connectedAccountOptional.ifPresent(account -> baseOptions.put("stripeConnectedAccount", account));
        return baseOptions;
    }
}
