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
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Audit;
import alfio.model.PaymentInformation;
import alfio.model.PurchaseContext;
import alfio.model.TicketReservation;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.*;
import alfio.model.transaction.token.StripeSCACreditCardToken;
import alfio.model.transaction.webhook.StripeChargeTransactionWebhookPayload;
import alfio.model.transaction.webhook.StripePaymentIntentWebhookPayload;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.ClockProvider;
import com.google.gson.JsonParser;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.HttpHeaders;
import com.stripe.net.RequestOptions;
import com.stripe.net.StripeResponse;
import com.stripe.net.Webhook;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.time.ZonedDateTime;
import java.util.*;

import static alfio.manager.payment.BaseStripeManager.STRIPE_MANAGER_TYPE_KEY;
import static alfio.model.TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT;
import static alfio.model.TicketReservation.TicketReservationStatus.WAITING_EXTERNAL_CONFIRMATION;
import static alfio.model.system.ConfigurationKeys.*;
import static java.util.Objects.requireNonNull;

@Log4j2
@Component
@Transactional
public class StripeWebhookPaymentManager implements PaymentProvider, RefundRequest, PaymentInfo, WebhookHandler, ClientServerTokenRequest, ServerInitiatedTransaction {

    private static final String STRIPE_MANAGER = StripeWebhookPaymentManager.class.getName();
    static final String CLIENT_SECRET_METADATA = "clientSecret";
    private static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String PAYMENT_INTENT_PAYMENT_FAILED = "payment_intent.payment_failed";
    private static final String PAYMENT_INTENT_CREATED = "payment_intent.created";
    private static final EnumSet<ConfigurationKeys> OPTIONS_TO_LOAD = EnumSet.of(STRIPE_ENABLE_SCA, BASE_URL, STRIPE_WEBHOOK_PAYMENT_KEY);
    private static final String REQUIRES_PAYMENT_METHOD = "requires_payment_method";
    private final ConfigurationManager configurationManager;
    private final BaseStripeManager baseStripeManager;
    private final TransactionRepository transactionRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;
    private final AuditingRepository auditingRepository;
    private final ClockProvider clockProvider;
    private final List<String> interestingEventTypes = List.of(PAYMENT_INTENT_SUCCEEDED, PAYMENT_INTENT_PAYMENT_FAILED, PAYMENT_INTENT_CREATED);
    private final Set<String> cancellableStatuses = Set.of(REQUIRES_PAYMENT_METHOD, "requires_confirmation", "requires_action");

    @Autowired
    public StripeWebhookPaymentManager(ConfigurationManager configurationManager,
                                       TicketRepository ticketRepository,
                                       TransactionRepository transactionRepository,
                                       ConfigurationRepository configurationRepository,
                                       TicketReservationRepository ticketReservationRepository,
                                       EventRepository eventRepository,
                                       AuditingRepository auditingRepository,
                                       Environment environment,
                                       ClockProvider clockProvider) {
        this(configurationManager,
            transactionRepository,
            ticketReservationRepository,
            eventRepository,
            auditingRepository,
            clockProvider,
            new BaseStripeManager(configurationManager, configurationRepository, ticketRepository, environment));
    }

    StripeWebhookPaymentManager(ConfigurationManager configurationManager,
                                TransactionRepository transactionRepository,
                                TicketReservationRepository ticketReservationRepository,
                                EventRepository eventRepository,
                                AuditingRepository auditingRepository,
                                ClockProvider clockProvider,
                                BaseStripeManager baseStripeManager) {
        this.configurationManager = configurationManager;
        this.transactionRepository = transactionRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.eventRepository = eventRepository;
        this.auditingRepository = auditingRepository;
        this.baseStripeManager = baseStripeManager;
        this.clockProvider = clockProvider;
    }

    @Override
    public TransactionInitializationToken initTransaction(PaymentSpecification paymentSpecification, Map<String, List<String>> params) {
        var reservationId = paymentSpecification.getReservationId();
        return transactionRepository.loadOptionalByReservationId(reservationId)
            .map(transaction -> {
                if(transaction.getStatus() == Transaction.Status.PENDING) {
                    return buildTokenFromTransaction(transaction, paymentSpecification.getPurchaseContext(), true);
                } else {
                    return errorToken("Reload reservation", true);
                }
            })
            .orElseGet(() -> createNewToken(paymentSpecification));
    }

    @Override
    public TransactionInitializationToken errorToken(String errorMessage, boolean reservationStatusChanged) {
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

            @Override
            public boolean isReservationStatusChanged() {
                return reservationStatusChanged;
            }
        };
    }

    @Override
    public boolean discardTransaction(Transaction transaction, PurchaseContext purchaseContext) {
        var paymentId = transaction.getPaymentId();
        try {
            var requestOptions = baseStripeManager.options(purchaseContext).orElseThrow();
            var paymentIntent = PaymentIntent.retrieve(paymentId, requestOptions);
            if(cancellableStatuses.contains(paymentIntent.getStatus())) {
                paymentIntent.cancel(requestOptions);
                return true;
            }
            log.warn("An attempt to cancel a non-cancellable Payment Intent has been detected for reservation ID {}, PaymentIntent ID {}", transaction.getReservationId(), transaction.getPaymentId());
        } catch (StripeException e) {
            log.warn("got Stripe error while trying to cancel transaction", e);
        }
        return false;
    }

    private TransactionInitializationToken buildTokenFromTransaction(Transaction transaction, PurchaseContext purchaseContext, boolean performRemoteVerification) {
        String clientSecret = Optional.ofNullable(transaction.getMetadata()).map(m -> m.get(CLIENT_SECRET_METADATA)).orElse(null);
        String chargeId = transaction.getStatus() == Transaction.Status.COMPLETE ? transaction.getTransactionId() : null;

        if(performRemoteVerification && transaction.getStatus() == Transaction.Status.PENDING) {
            // try to retrieve PaymentIntent
            try {
                var requestOptions = baseStripeManager.options(purchaseContext).orElseThrow();
                var paymentIntent = PaymentIntent.retrieve(transaction.getPaymentId(), requestOptions);
                var status = paymentIntent.getStatus();
                if(status.equals("succeeded")) {
                    // the existing PaymentIntent succeeded, so we can confirm the reservation
                    log.info("marking reservation {} as paid, because PaymentIntent reports success", transaction.getReservationId());
                    processSuccessfulPaymentIntent(transaction, paymentIntent, ticketReservationRepository.findReservationById(transaction.getReservationId()), purchaseContext, requestOptions);
                    return errorToken("Reservation status changed", true);
                } else if(!status.equals(REQUIRES_PAYMENT_METHOD)) {
                    return errorToken("Payment in process", true);
                }
            } catch (StripeException e) {
                throw new IllegalStateException(e);
            }
        }
        return new StripeSCACreditCardToken(transaction.getPaymentId(), chargeId, clientSecret);
    }

    private StripeSCACreditCardToken createNewToken(PaymentSpecification paymentSpecification) {
        Map<String, String> baseMetadata = configurationManager.getFor(BASE_URL, paymentSpecification.getPurchaseContext().getConfigurationLevel()).getValue()
            .map(baseUrl -> Map.of("alfioBaseUrl", baseUrl))
            .orElse(Map.of());
        var paymentIntentParams = baseStripeManager.createParams(paymentSpecification, baseMetadata);
        paymentIntentParams.put("payment_method_types", List.of("card"));
        try {
            var options = baseStripeManager.options(paymentSpecification.getPurchaseContext(), builder -> builder.setIdempotencyKey(paymentSpecification.getReservationId())).orElseThrow();
            var intent = PaymentIntent.create(paymentIntentParams, options);
            var clientSecret = intent.getClientSecret();
            long platformFee = paymentIntentParams.containsKey("application_fee") ? (long) paymentIntentParams.get("application_fee") : 0L;
            PaymentManagerUtils.invalidateExistingTransactions(paymentSpecification.getReservationId(), transactionRepository);
            transactionRepository.insert(intent.getId(), intent.getId(),
                paymentSpecification.getReservationId(), ZonedDateTime.now(clockProvider.withZone(paymentSpecification.getPurchaseContext().getZoneId())),
                paymentSpecification.getPriceWithVAT(), paymentSpecification.getPurchaseContext().getCurrency(), "Payment Intent",
                PaymentProxy.STRIPE.name(), platformFee,0L, Transaction.Status.PENDING, Map.of(CLIENT_SECRET_METADATA, clientSecret, STRIPE_MANAGER_TYPE_KEY, STRIPE_MANAGER));
            return new StripeSCACreditCardToken(intent.getId(), null, clientSecret);

        } catch (StripeException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getWebhookSignatureKey(ConfigurationLevel configurationLevel) {
        return configurationManager.getFor(STRIPE_WEBHOOK_PAYMENT_KEY, configurationLevel).getRequiredValue();
    }

    @Override
    public Optional<TransactionWebhookPayload> parseTransactionPayload(String body,
                                                                       String signature,
                                                                       Map<String, String> additionalInfo,
                                                                       PaymentContext paymentContext) {
        try {
            var stripeEvent = Webhook.constructEvent(body, signature, getWebhookSignatureKey(paymentContext.getConfigurationLevel()));
            String eventType = stripeEvent.getType();
            if(eventType.startsWith("charge.")) {
                return deserializeObject(stripeEvent, body)
                    .map(obj -> new StripeChargeTransactionWebhookPayload(eventType, (Charge)obj));
            } else if(eventType.startsWith("payment_intent.")) {
                return deserializeObject(stripeEvent, body)
                    .map(obj -> new StripePaymentIntentWebhookPayload(eventType, (PaymentIntent)obj));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("got exception while handling stripe webhook", e);
            return Optional.empty();
        }
    }

    private Optional<StripeObject> deserializeObject(com.stripe.model.Event stripeEvent, String rawJson) {
        var dataObjectDeserializer = stripeEvent.getDataObjectDeserializer();
        var cleanDeserialization = dataObjectDeserializer.getObject();
        if(cleanDeserialization.isPresent()) {
            return cleanDeserialization;
        }
        log.warn("unable to deserialize payload. Expected version {}, actual {}, falling back to unsafe deserialization", Stripe.API_VERSION, stripeEvent.getApiVersion());
        try {
            return Optional.ofNullable(dataObjectDeserializer.deserializeUnsafe())
                .map(stripeObject -> {
                    // if the message we received was built with an API version older than 2022-11-15
                    // we need to save the raw JSON body to ensure we have all the information to parse the message
                    // see https://stripe.com/docs/upgrades#2022-11-15 and https://github.com/alfio-event/alf.io/issues/1159
                    if (stripeObject.getLastResponse() == null && "2022-11-15".compareTo(stripeEvent.getApiVersion()) > 0) {
                        log.debug("API version requires raw JSON body. Forcing 'lastResponse' property");
                        stripeObject.setLastResponse(new StripeResponse(200, HttpHeaders.of(Map.of()), rawJson));
                    }
                    return stripeObject;
                });
        } catch(Exception e) {
            throw new IllegalArgumentException("Cannot deserialize webhook event.", e);
        }
    }

    @Override
    public PaymentWebhookResult processWebhook(TransactionWebhookPayload payload, Transaction transaction, PaymentContext paymentContext) {

        // first we check if we're interested in the current event
        if(!interestingEventTypes.contains(payload.getType())) {
            //we're not interested to other kind of events yet...
            return PaymentWebhookResult.notRelevant(payload.getType());
        }

        boolean live = Boolean.TRUE.equals(((PaymentIntent) payload.getPayload()).getLivemode());
        if(!baseStripeManager.getSecretKey(paymentContext.getPurchaseContext()).startsWith(live ? "sk_live_" : "sk_test_")) {
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
            var purchaseContext = paymentContext.getPurchaseContext();
            switch(payload.getType()) {
                case PAYMENT_INTENT_CREATED: {
                    return PaymentWebhookResult.processStarted(buildTokenFromTransaction(transaction, purchaseContext, false));
                }
                case PAYMENT_INTENT_SUCCEEDED: {
                    return processSuccessfulPaymentIntent(transaction, paymentIntent, reservation, purchaseContext, baseStripeManager.options(purchaseContext).orElseThrow());
                }
                case PAYMENT_INTENT_PAYMENT_FAILED: {
                    return processFailedPaymentIntent(transaction, reservation, purchaseContext);
                }
                default:
                    return PaymentWebhookResult.notRelevant("event is not relevant");
            }

        } catch (Exception e) {
            log.error("Error while trying to confirm the reservation", e);
            return PaymentWebhookResult.error("unexpected error");
        }
    }

    /**
     * from docs:
     * If payment fails at any stage during the process, the PaymentIntent’s status resets to requires_payment_method.
     * When it returns to the initial state, you can prompt the customer to try again–potentially with a different payment method, if desired
     * so we make sure to set the transaction as failed
     *
     * @param transaction transaction
     * @param reservation the TicketReservation this payment belongs to
     * @param purchaseContext the event
     * @return a failed {@link PaymentWebhookResult}
     */
    private PaymentWebhookResult processFailedPaymentIntent(Transaction transaction, TicketReservation reservation, PurchaseContext purchaseContext) {
        List<Map<String, Object>> modifications = List.of(Map.of("paymentId", transaction.getPaymentId(), "paymentMethod", "stripe"));
        auditingRepository.insert(reservation.getId(), null, purchaseContext, Audit.EventType.PAYMENT_FAILED, new Date(), Audit.EntityType.RESERVATION, reservation.getId(), modifications);
        return PaymentWebhookResult.failed("Charge has been reset by Stripe. This is usually caused by a rejection from the customer's bank");
    }

    private PaymentWebhookResult processSuccessfulPaymentIntent(Transaction transaction,
                                                                PaymentIntent paymentIntent,
                                                                TicketReservation reservation,
                                                                PurchaseContext purchaseContext,
                                                                RequestOptions requestOptions) throws StripeException {
        var chargeAndFees = retrieveChargeIdAndFees(paymentIntent, requestOptions);
        var chargeId = chargeAndFees.getChargeId();
        transactionRepository.lockByIdForUpdate(transaction.getId());// this serializes
        int affectedRows = transactionRepository.updateIfStatus(transaction.getId(), chargeId,
            transaction.getPaymentId(), purchaseContext.now(clockProvider), transaction.getPlatformFee(), chargeAndFees.getFeesOrZero(),
            Transaction.Status.COMPLETE, Map.of(), Transaction.Status.PENDING);
        List<Map<String, Object>> modifications = List.of(Map.of("paymentId", chargeId, "paymentMethod", "stripe"));
        if(affectedRows == 0) {
            // the transaction was already confirmed by someone else.
            // We can safely return the chargeId, but we write in the auditing that we skipped the confirmation
            auditingRepository.insert(reservation.getId(), null,
                    purchaseContext, Audit.EventType.PAYMENT_ALREADY_CONFIRMED,
                new Date(), Audit.EntityType.RESERVATION, reservation.getId(), modifications);
            return PaymentWebhookResult.successful(new StripeSCACreditCardToken(transaction.getPaymentId(), chargeId, null));
        }
        auditingRepository.insert(reservation.getId(), null,
                purchaseContext, Audit.EventType.PAYMENT_CONFIRMED,
            new Date(), Audit.EntityType.RESERVATION, reservation.getId(), modifications);
        return PaymentWebhookResult.successful(new StripeSCACreditCardToken(transaction.getPaymentId(), chargeId, null));
    }

    private ChargeIdAndFees retrieveChargeIdAndFees(PaymentIntent paymentIntent, RequestOptions requestOptions) throws StripeException {
        String chargeId = paymentIntent.getLatestCharge();
        String balanceTransactionId = null;
        long fees = 0L;
        if (chargeId == null) {
            // compatibility mode for payloads with API version up to 2022-08-01
            var jsonObject = paymentIntent.getRawJsonObject();
            // old structure is paymentIntent -> data -> object -> charges -> data[0] -> id
            var chargesContainer = requireNonNull(jsonObject.getAsJsonObject("data")
                .getAsJsonObject("object")
                .getAsJsonObject("charges"), "data -> object -> charges is null!");
            var latestCharge = requireNonNull(chargesContainer.getAsJsonArray("data").get(0), "charges is empty!")
                .getAsJsonObject();

            chargeId = requireNonNull(latestCharge.get("id"), "charges array is empty!").getAsString();
            if (latestCharge.has("balance_transaction")) {
                balanceTransactionId = latestCharge.get("balance_transaction").getAsString();
            }
        }

        if (balanceTransactionId == null) {
            var charge = baseStripeManager.retrieveCharge(chargeId, requestOptions);
            balanceTransactionId = charge.getBalanceTransaction();
        }

        if (balanceTransactionId != null) {
            fees = baseStripeManager.retrieveBalanceTransaction(balanceTransactionId, requestOptions).getFee();
        }

        return new ChargeIdAndFees(chargeId, fees);
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        return baseStripeManager.accept(paymentMethod, context, OPTIONS_TO_LOAD, this::isConfigurationValid);
    }

    private boolean isConfigurationValid(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configuration) {
        return configuration.get(BASE_URL).isPresent()
            && configuration.get(STRIPE_WEBHOOK_PAYMENT_KEY).isPresent()
            && configuration.get(STRIPE_ENABLE_SCA).getValueAsBooleanOrDefault();
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
    public boolean accept(Transaction transaction) {
        var isWebHookManager = STRIPE_MANAGER.equals(transaction.getMetadata().get(STRIPE_MANAGER_TYPE_KEY)) || transaction.getMetadata().get(CLIENT_SECRET_METADATA) != null;
        return transaction.getPaymentProxy() == PaymentProxy.STRIPE && isWebHookManager;
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        return baseStripeManager.isActive(paymentContext, OPTIONS_TO_LOAD, this::isConfigurationValid);
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
    public Optional<PaymentInformation> getInfo(Transaction transaction, PurchaseContext purchaseContext) {
        return baseStripeManager.getInfo(transaction, purchaseContext);
    }

    @Override
    public boolean refund(Transaction transaction, PurchaseContext purchaseContext, Integer amount) {
        return baseStripeManager.refund(transaction, purchaseContext, amount);
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

    @Override
    public PaymentWebhookResult forceTransactionCheck(TicketReservation reservation, Transaction transaction, PaymentContext paymentContext) {
        Validate.isTrue(transaction.getPaymentProxy() == PaymentProxy.STRIPE, "invalid transaction");
        try {
            PurchaseContext purchaseContext = paymentContext.getPurchaseContext();
            var options = baseStripeManager.options(purchaseContext, builder -> builder.setIdempotencyKey(reservation.getId())).orElseThrow();
            var intent = PaymentIntent.retrieve(transaction.getPaymentId(), options);
            switch(intent.getStatus()) {
                case "processing":
                case "requires_action":
                case "requires_confirmation":
                    return PaymentWebhookResult.pending();
                case "succeeded":
                    return processSuccessfulPaymentIntent(transaction, intent, reservation, purchaseContext, options);
                case REQUIRES_PAYMENT_METHOD:
                    //payment is failed.
                    return processFailedPaymentIntent(transaction, reservation, purchaseContext);
                default:
                    return null;
            }
        } catch(Exception ex) {
            log.error("Error trying to check PaymentIntent status", ex);
            return PaymentWebhookResult.error("failed");
        }
    }

    /**
     * Detects {@link PaymentContext} by parsing Stripe's Webhook payload.
     * If anything goes wrong, it returns an empty PaymentContext
     * @param payload Stripe Webhook payload
     * @return PaymentContext
     */
    @Override
    public Optional<PaymentContext> detectPaymentContext(String payload) {
        try (var stringReader = new StringReader(payload)) {
            var reservationId = JsonParser.parseReader(stringReader)
                .getAsJsonObject()
                .getAsJsonObject("data")
                .getAsJsonObject("object")
                .getAsJsonObject("metadata")
                .get("reservationId")
                .getAsString();

            var event = eventRepository.findByReservationId(reservationId);
            return Optional.of(new PaymentContext(event, reservationId));
        } catch(Exception ex) {
            log.warn("Cannot detect PaymentContext from the webhook body. Using a generic one", ex);
            return Optional.empty();
        }
    }

    private static class ChargeIdAndFees {
        private final String chargeId;
        private final Long fees;


        private ChargeIdAndFees(String chargeId, Long fees) {
            this.chargeId = chargeId;
            this.fees = fees;
        }

        public String getChargeId() {
            return chargeId;
        }

        public long getFeesOrZero() {
            return fees != null ? fees : 0L;
        }
    }
}
