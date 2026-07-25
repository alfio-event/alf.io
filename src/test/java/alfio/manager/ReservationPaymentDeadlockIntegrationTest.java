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

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.PaymentResult;
import alfio.manager.support.PaymentWebhookResult;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.PurchaseContext;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.ServerInitiatedTransaction;
import alfio.model.transaction.capabilities.WebhookHandler;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;

import static alfio.manager.TicketReservationManagerIntegrationTest.DESCRIPTION;
import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static alfio.test.util.TestUtil.clockProvider;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces the production deadlock between the payment webhook flow, which locks the
 * b_transaction row and then updates the tickets_reservation row, and the user-driven flows
 * (init/cancel payment), which acquire the same two locks in the opposite order while performing
 * a remote call to the payment provider in between.
 */
@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ReservationPaymentDeadlockIntegrationTest.DeadlockTestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class ReservationPaymentDeadlockIntegrationTest {

    private static final String TEST_PROVIDER_MARKER = "deadlock-test";

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketReservationManager ticketReservationManager;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private PlatformTransactionManager platformTransactionManager;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    private PausingWebhookPaymentProvider pausingProvider;

    private Event event;
    private String reservationId;
    private TicketReservation reservation;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        pausingProvider.reset();
        var transactionDefinition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate = new TransactionTemplate(platformTransactionManager, transactionDefinition);

        transactionTemplate.execute(tx -> {
            List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                    new DateTimeModification(LocalDate.now(clockProvider().getClock()), LocalTime.now(clockProvider().getClock())),
                    new DateTimeModification(LocalDate.now(clockProvider().getClock()), LocalTime.now(clockProvider().getClock())),
                    DESCRIPTION, BigDecimal.TEN, false, "", false, null,
                    null, null, null, null, null, TicketCategory.TicketCheckInStrategy.ONCE_PER_EVENT, null, AlfioMetadata.empty()));
            Pair<Event, String> eventStringPair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
            event = eventStringPair.getLeft();
            return null;
        });

        transactionTemplate.execute(tx -> {
            int categoryId = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0).getId();
            TicketReservationModification tr = new TicketReservationModification();
            tr.setQuantity(1);
            tr.setTicketCategoryId(categoryId);
            var mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
            reservationId = ticketReservationManager.createTicketReservation(event, List.of(mod), List.of(), DateUtils.addHours(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
            // simulate an ongoing payment: reservation with contact data, waiting for the payment provider,
            // with a PENDING transaction handled by our pausing webhook-capable provider
            ticketReservationRepository.updateTicketReservation(reservationId,
                TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT.name(),
                "tester@example.org", "First Last", "First", "Last", "en", null, null, PaymentProxy.STRIPE.name(), null);
            transactionRepository.insert("test-tx-id", "test-payment-id", reservationId,
                ZonedDateTime.now(clockProvider().getClock()), 1000, event.getCurrency(), "deadlock test",
                PaymentProxy.STRIPE.name(), 0L, 0L, Transaction.Status.PENDING, Map.of(TEST_PROVIDER_MARKER, "true"));
            return null;
        });
        reservation = ticketReservationRepository.findReservationById(reservationId);
    }

    @Test
    void concurrentTransactionCheckAndPaymentCancellationMustNotDeadlock() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            // flow 1: webhook-style processing. Locks the transaction row, then performs a remote call
            // to the payment provider (paused via latch), then updates the reservation status
            Future<Optional<PaymentResult>> forceCheckFuture = executor.submit(() ->
                transactionTemplate.execute(tx -> ticketReservationManager.forceTransactionCheck(event, reservation)));

            assertTrue(pausingProvider.awaitRemoteCallStarted(), "the payment provider was not invoked");

            // flow 2: while flow 1 is busy with the remote call, the user cancels the pending payment.
            // This updates the reservation status, then deletes the pending transaction
            Future<Boolean> cancelFuture = executor.submit(() ->
                transactionTemplate.execute(tx -> ticketReservationManager.cancelPendingPayment(reservationId, event)));

            waitUntilASessionWaitsOnALock();

            // let the remote call complete: flow 1 will now update the reservation status
            pausingProvider.completeRemoteCall();

            var errors = new ArrayList<Throwable>();
            var forceCheckResult = await(forceCheckFuture, errors);
            var cancelResult = await(cancelFuture, errors);
            if (!errors.isEmpty()) {
                throw new AssertionError("concurrent payment operations failed: " + errors, errors.get(0));
            }

            // both flows completed: the check reported the payment as still pending,
            // then the cancellation reverted the reservation to PENDING and deleted the transaction
            assertTrue(forceCheckResult.isPresent());
            assertEquals(Boolean.TRUE, cancelResult);
            assertEquals(TicketReservation.TicketReservationStatus.PENDING, ticketReservationRepository.findReservationById(reservationId).getStatus());
            assertTrue(transactionRepository.loadOptionalByReservationId(reservationId).isEmpty());
        } finally {
            pausingProvider.completeRemoteCall();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentWebhookAndTransactionInitMustNotDeadlock() throws Exception {
        pausingProvider.setReservationIdForWebhook(reservationId);
        var executor = Executors.newFixedThreadPool(2);
        try {
            // flow 1: the user (re-)opens the payment page. This locks the reservation row, then verifies
            // the payment status remotely with the payment gateway (paused via latch) and, since the gateway
            // reports success, confirms the transaction row
            Future<Optional<TransactionInitializationToken>> initFuture = executor.submit(() ->
                transactionTemplate.execute(tx -> ticketReservationManager.initTransaction(event, reservationId, StaticPaymentMethods.CREDIT_CARD, Map.of())));

            if (!pausingProvider.awaitInitVerificationStarted()) {
                var initErrors = new ArrayList<Throwable>();
                var earlyResult = await(initFuture, initErrors);
                throw new AssertionError("the payment provider was not invoked for init. Result: " + earlyResult + ", errors: " + initErrors,
                    initErrors.isEmpty() ? null : initErrors.get(0));
            }

            // flow 2: while flow 1 is busy with the remote verification, the webhook for the same payment
            // arrives. It locks the transaction row, then processes the payload (paused via latch),
            // then updates the reservation status
            Future<PaymentWebhookResult> webhookFuture = executor.submit(() ->
                transactionTemplate.execute(tx -> ticketReservationManager.processTransactionWebhook("{}", "test-signature", PaymentProxy.STRIPE, Map.of())));

            // the webhook flow either locked the transaction row and reached the provider (vulnerable ordering),
            // or is waiting for the reservation row to be released (fixed ordering)
            waitUntil(() -> pausingProvider.isWebhookProcessingStarted() || countSessionsWaitingOnALock() > 0);

            // let both remote calls complete
            pausingProvider.completeInitVerification();
            pausingProvider.completeWebhookProcessing();

            var errors = new ArrayList<Throwable>();
            var initResult = await(initFuture, errors);
            var webhookResult = await(webhookFuture, errors);
            if (!errors.isEmpty()) {
                throw new AssertionError("concurrent payment operations failed: " + errors, errors.get(0));
            }

            // both flows completed: the init flow confirmed the transaction,
            // and the webhook has been discarded because the transaction is no longer PENDING
            assertTrue(initResult.isPresent());
            assertEquals(PaymentWebhookResult.Type.NOT_RELEVANT, webhookResult.getType());
            assertEquals(Transaction.Status.COMPLETE, transactionRepository.loadOptionalByReservationId(reservationId).orElseThrow().getStatus());
            assertEquals(TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT, ticketReservationRepository.findReservationById(reservationId).getStatus());
        } finally {
            pausingProvider.completeAllRemoteCalls();
            executor.shutdownNow();
        }
    }

    private static <T> T await(Future<T> future, List<Throwable> errors) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            errors.add(e.getCause());
            return null;
        } catch (InterruptedException | TimeoutException e) {
            Thread.currentThread().interrupt();
            errors.add(e);
            return null;
        }
    }

    private void waitUntilASessionWaitsOnALock() {
        waitUntil(() -> countSessionsWaitingOnALock() > 0);
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) {
        Awaitility.await()
            .atMost(Duration.ofSeconds(10L))
            .pollInterval(Duration.ofMillis(50L))
            .until(condition::getAsBoolean);
    }

    private int countSessionsWaitingOnALock() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'", Map.of(), Integer.class);
        return count != null ? count : 0;
    }

    @Configuration(proxyBeanMethods = false)
    static class DeadlockTestConfiguration {
        @Bean
        PausingWebhookPaymentProvider pausingWebhookPaymentProvider(TransactionRepository transactionRepository) {
            return new PausingWebhookPaymentProvider(transactionRepository);
        }
    }

    /**
     * Webhook-capable payment provider which blocks during its "remote calls" to the payment gateway
     * ({@link #forceTransactionCheck}, {@link #processWebhook} and the status verification performed
     * within {@link #initTransaction}) until the test releases it, simulating the gateway latency.
     * It only accepts transactions carrying the {@link #TEST_PROVIDER_MARKER} metadata entry.
     */
    static class PausingWebhookPaymentProvider implements PaymentProvider, WebhookHandler, ServerInitiatedTransaction {

        private final TransactionRepository transactionRepository;
        private volatile CountDownLatch remoteCallStarted;
        private volatile CountDownLatch remoteCallCompletion;
        private volatile CountDownLatch webhookProcessingStarted;
        private volatile CountDownLatch webhookProcessingCompletion;
        private volatile CountDownLatch initVerificationStarted;
        private volatile CountDownLatch initVerificationCompletion;

        PausingWebhookPaymentProvider(TransactionRepository transactionRepository) {
            this.transactionRepository = transactionRepository;
            reset();
        }

        void reset() {
            reservationIdForWebhook = null;
            remoteCallStarted = new CountDownLatch(1);
            remoteCallCompletion = new CountDownLatch(1);
            webhookProcessingStarted = new CountDownLatch(1);
            webhookProcessingCompletion = new CountDownLatch(1);
            initVerificationStarted = new CountDownLatch(1);
            initVerificationCompletion = new CountDownLatch(1);
        }

        boolean awaitRemoteCallStarted() throws InterruptedException {
            return remoteCallStarted.await(10L, TimeUnit.SECONDS);
        }

        boolean awaitInitVerificationStarted() throws InterruptedException {
            return initVerificationStarted.await(10L, TimeUnit.SECONDS);
        }

        boolean isWebhookProcessingStarted() {
            return webhookProcessingStarted.getCount() == 0L;
        }

        void completeRemoteCall() {
            remoteCallCompletion.countDown();
        }

        void completeWebhookProcessing() {
            webhookProcessingCompletion.countDown();
        }

        void completeInitVerification() {
            initVerificationCompletion.countDown();
        }

        void completeAllRemoteCalls() {
            completeRemoteCall();
            completeWebhookProcessing();
            completeInitVerification();
        }

        private static PaymentWebhookResult pauseOn(CountDownLatch started, CountDownLatch completion) {
            started.countDown();
            try {
                if (!completion.await(20L, TimeUnit.SECONDS)) {
                    return PaymentWebhookResult.error("timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return PaymentWebhookResult.error("interrupted");
            }
            return null;
        }

        @Override
        public PaymentWebhookResult forceTransactionCheck(TicketReservation reservation, Transaction transaction, PaymentContext paymentContext) {
            var interrupted = pauseOn(remoteCallStarted, remoteCallCompletion);
            return interrupted != null ? interrupted : PaymentWebhookResult.pending();
        }

        @Override
        public PaymentWebhookResult processWebhook(TransactionWebhookPayload payload, Transaction transaction, PaymentContext paymentContext) {
            var interrupted = pauseOn(webhookProcessingStarted, webhookProcessingCompletion);
            return interrupted != null ? interrupted : PaymentWebhookResult.pending();
        }

        @Override
        public TransactionInitializationToken initTransaction(PaymentSpecification paymentSpecification, Map<String, List<String>> params) {
            // replicates the vulnerable production behavior: an existing PENDING transaction is verified
            // remotely with the payment gateway, and since the gateway reports it as succeeded,
            // it gets confirmed on the spot
            var transaction = transactionRepository.loadOptionalByReservationId(paymentSpecification.getReservationId()).orElseThrow();
            var interrupted = pauseOn(initVerificationStarted, initVerificationCompletion);
            if (interrupted != null) {
                return errorToken(interrupted.getReason(), false);
            }
            transactionRepository.lockByIdForUpdate(transaction.getId());
            transactionRepository.updateIfStatus(transaction.getId(), "test-charge-id", transaction.getPaymentId(),
                ZonedDateTime.now(clockProvider().getClock()), 0L, 0L, Transaction.Status.COMPLETE,
                Map.of(TEST_PROVIDER_MARKER, "true"), Transaction.Status.PENDING);
            return errorToken("Reservation status changed", true);
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
                    return StaticPaymentMethods.CREDIT_CARD;
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
            return true;
        }

        @Override
        public boolean accept(Transaction transaction) {
            return transaction.getPaymentProxy() == PaymentProxy.STRIPE
                && "true".equals(transaction.getMetadata().get(TEST_PROVIDER_MARKER));
        }

        @Override
        public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
            return paymentMethod == StaticPaymentMethods.CREDIT_CARD;
        }

        @Override
        public Set<? extends PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest) {
            return Set.of(StaticPaymentMethods.CREDIT_CARD);
        }

        @Override
        public PaymentProxy getPaymentProxy() {
            return PaymentProxy.STRIPE;
        }

        @Override
        public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
            return StaticPaymentMethods.CREDIT_CARD;
        }

        @Override
        public boolean isActive(PaymentContext paymentContext) {
            return true;
        }

        @Override
        public PaymentResult doPayment(PaymentSpecification spec) {
            throw new UnsupportedOperationException();
        }

        private volatile String reservationIdForWebhook;

        @Override
        public Optional<TransactionWebhookPayload> parseTransactionPayload(String body, String signature, Map<String, String> additionalInfo, PaymentContext paymentContext) {
            return Optional.ofNullable(reservationIdForWebhook).map(TestWebhookPayload::new);
        }

        void setReservationIdForWebhook(String reservationId) {
            this.reservationIdForWebhook = reservationId;
        }

        private record TestWebhookPayload(String reservationId) implements TransactionWebhookPayload {
            @Override
            public Object getPayload() {
                return reservationId;
            }

            @Override
            public String getType() {
                return "test.payment_intent.succeeded";
            }

            @Override
            public String getReservationId() {
                return reservationId;
            }

            @Override
            public Status getStatus() {
                return Status.SUCCESS;
            }
        }
    }
}
