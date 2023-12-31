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
package alfio.controller.api.v2.user.reservation;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.IndexController;
import alfio.controller.api.ControllerConfiguration;
import alfio.controller.api.admin.AdditionalServiceApiController;
import alfio.controller.api.admin.CheckInApiController;
import alfio.controller.api.admin.EventApiController;
import alfio.controller.api.admin.UsersApiController;
import alfio.controller.api.v1.AttendeeApiController;
import alfio.controller.api.v2.InfoApiController;
import alfio.controller.api.v2.TranslationsApiController;
import alfio.controller.api.v2.model.ReservationInfo;
import alfio.controller.api.v2.user.EventApiV2Controller;
import alfio.controller.api.v2.user.ReservationApiV2Controller;
import alfio.controller.api.v2.user.TicketApiV2Controller;
import alfio.controller.form.PaymentForm;
import alfio.controller.payment.api.stripe.StripePaymentWebhookController;
import alfio.extension.ExtensionService;
import alfio.manager.*;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.repository.audit.ScanAuditRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.util.ClockProvider;
import com.stripe.net.Webhook;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.validation.BeanPropertyBindingResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static alfio.manager.support.extension.ExtensionEvent.*;
import static alfio.test.util.IntegrationTestUtil.*;
import static alfio.util.HttpUtils.APPLICATION_JSON;
import static alfio.util.HttpUtils.APPLICATION_JSON_UTF8;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class StripeReservationFlowIntegrationTest extends BaseReservationFlowTest {

    public static final String WEBHOOK_SECRET = "WEBHOOK_SECRET";
    private static final String PAYLOAD_FILENAME = "payloadFilename";
    private final OrganizationRepository organizationRepository;
    private final UserManager userManager;
    private final StripePaymentWebhookController stripePaymentWebhookController;

    @Autowired
    public StripeReservationFlowIntegrationTest(OrganizationRepository organizationRepository,
                                                EventManager eventManager,
                                                EventRepository eventRepository,
                                                UserManager userManager,
                                                ClockProvider clockProvider,
                                                ConfigurationRepository configurationRepository,
                                                EventStatisticsManager eventStatisticsManager,
                                                TicketCategoryRepository ticketCategoryRepository,
                                                TicketReservationRepository ticketReservationRepository,
                                                EventApiController eventApiController,
                                                TicketRepository ticketRepository,
                                                PurchaseContextFieldRepository purchaseContextFieldRepository,
                                                AdditionalServiceApiController additionalServiceApiController,
                                                SpecialPriceTokenGenerator specialPriceTokenGenerator,
                                                SpecialPriceRepository specialPriceRepository,
                                                CheckInApiController checkInApiController,
                                                AttendeeApiController attendeeApiController,
                                                UsersApiController usersApiController,
                                                ScanAuditRepository scanAuditRepository,
                                                AuditingRepository auditingRepository,
                                                AdminReservationManager adminReservationManager,
                                                TicketReservationManager ticketReservationManager,
                                                InfoApiController infoApiController,
                                                TranslationsApiController translationsApiController,
                                                EventApiV2Controller eventApiV2Controller,
                                                ReservationApiV2Controller reservationApiV2Controller,
                                                TicketApiV2Controller ticketApiV2Controller,
                                                IndexController indexController,
                                                NamedParameterJdbcTemplate jdbcTemplate,
                                                ExtensionLogRepository extensionLogRepository,
                                                ExtensionService extensionService,
                                                PollRepository pollRepository,
                                                NotificationManager notificationManager,
                                                UserRepository userRepository,
                                                OrganizationDeleter organizationDeleter,
                                                PromoCodeDiscountRepository promoCodeDiscountRepository,
                                                PromoCodeRequestManager promoCodeRequestManager,
                                                StripePaymentWebhookController stripePaymentWebhookController,
                                                ExportManager exportManager,
                                                PurchaseContextFieldManager purchaseContextFieldManager) {
        super(configurationRepository,
            eventManager,
            eventRepository,
            eventStatisticsManager,
            ticketCategoryRepository,
            ticketReservationRepository,
            eventApiController,
            ticketRepository,
            purchaseContextFieldRepository,
            additionalServiceApiController,
            specialPriceTokenGenerator,
            specialPriceRepository,
            checkInApiController,
            attendeeApiController,
            usersApiController,
            scanAuditRepository,
            auditingRepository,
            adminReservationManager,
            ticketReservationManager,
            infoApiController,
            translationsApiController,
            eventApiV2Controller,
            reservationApiV2Controller,
            ticketApiV2Controller,
            indexController,
            jdbcTemplate,
            extensionLogRepository,
            extensionService,
            pollRepository,
            clockProvider,
            notificationManager,
            userRepository,
            organizationDeleter,
            promoCodeDiscountRepository,
            promoCodeRequestManager,
            exportManager,
            purchaseContextFieldManager);
        this.organizationRepository = organizationRepository;
        this.userManager = userManager;
        this.stripePaymentWebhookController = stripePaymentWebhookController;
    }

    @BeforeEach
    void init() {
        configurationRepository.insert(ConfigurationKeys.STRIPE_ENABLE_SCA.name(), "true", "");
        configurationRepository.insert(ConfigurationKeys.STRIPE_PUBLIC_KEY.name(), "pk_test_123", "");
        configurationRepository.insert(ConfigurationKeys.STRIPE_SECRET_KEY.name(), "sk_test_123", "");
        configurationRepository.insert(ConfigurationKeys.STRIPE_WEBHOOK_PAYMENT_KEY.name(), WEBHOOK_SECRET, "");
    }

    private ReservationFlowContext createContext(String payloadFilename) {
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "hidden", TicketCategory.TicketAccessType.INHERIT, 2,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.ONE, true, "", true, URL_CODE_HIDDEN, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, null, Event.EventFormat.IN_PERSON);
        return new ReservationFlowContext(eventAndUser.getLeft(), owner(eventAndUser.getRight()), null, null, null, null, true, false, Map.of(PAYLOAD_FILENAME, payloadFilename));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/transaction-json/stripe-success-valid.json",
        "/transaction-json/stripe-success-valid-v2022-11-15.json"
    })
    void payWithStripe(String payloadFilename) throws Exception {
        super.testBasicFlow(() -> createContext(payloadFilename));
    }

    @Override
    protected void performAndValidatePayment(ReservationFlowContext context,
                                             String reservationId,
                                             int promoCodeId,
                                             Runnable cleanupExtensionLog) {
        ReservationInfo reservation;
        var paymentForm = new PaymentForm();

        paymentForm.setPrivacyPolicyAccepted(true);
        paymentForm.setTermAndConditionsAccepted(true);
        paymentForm.setPaymentProxy(PaymentProxy.STRIPE);
        paymentForm.setSelectedPaymentMethod(PaymentMethod.CREDIT_CARD);

        var tStatus = reservationApiV2Controller.getTransactionStatus(reservationId, PaymentMethod.CREDIT_CARD.name());
        assertEquals(HttpStatus.NOT_FOUND, tStatus.getStatusCode());

        // init payment
        var initPaymentRes = reservationApiV2Controller.initTransaction(reservationId, PaymentMethod.CREDIT_CARD.name(), new LinkedMultiValueMap<>());
        assertEquals(HttpStatus.OK, initPaymentRes.getStatusCode());

        tStatus = reservationApiV2Controller.getTransactionStatus(reservationId, PaymentMethod.CREDIT_CARD.name());
        assertEquals(HttpStatus.OK, tStatus.getStatusCode());

        var resInfoResponse = reservationApiV2Controller.getReservationInfo(reservationId, null);
        assertEquals(TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT, Objects.requireNonNull(resInfoResponse.getBody()).getStatus());

        //
        var promoCodeUsage = promoCodeRequestManager.retrieveDetailedUsage(promoCodeId, context.event.getId());
        assertTrue(promoCodeUsage.isEmpty());

        var handleRes = reservationApiV2Controller.confirmOverview(reservationId, "en", paymentForm, new BeanPropertyBindingResult(paymentForm, "paymentForm"),
            new MockHttpServletRequest(), context.getPublicUser());

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, handleRes.getStatusCode());

        cleanupExtensionLog.run();
        processWebHook(context.getAdditionalParams().get(PAYLOAD_FILENAME), reservationId);

        checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.COMPLETE, context);

        tStatus = reservationApiV2Controller.getTransactionStatus(reservationId, PaymentMethod.CREDIT_CARD.name());
        assertEquals(HttpStatus.OK, tStatus.getStatusCode());
        assertNotNull(tStatus.getBody());
        assertTrue(tStatus.getBody().isSuccess());

        reservation = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser()).getBody();
        assertNotNull(reservation);
        checkOrderSummary(reservation);

        var extLogs = extensionLogRepository.getPage(null, null, null, 100, 0);

        assertEventLogged(extLogs, RESERVATION_CONFIRMED);
        assertEventLogged(extLogs, CONFIRMATION_MAIL_CUSTOM_TEXT);
        assertEventLogged(extLogs, TICKET_ASSIGNED);
        assertEventLogged(extLogs, TICKET_ASSIGNED_GENERATE_METADATA);
        assertEventLogged(extLogs, TICKET_MAIL_CUSTOM_TEXT);

        tStatus = reservationApiV2Controller.getTransactionStatus(reservationId, PaymentMethod.CREDIT_CARD.name());
        assertEquals(HttpStatus.OK, tStatus.getStatusCode());
        assertNotNull(tStatus.getBody());
        assertTrue(tStatus.getBody().isSuccess());
    }

    @Override
    protected void checkOrderSummary(ReservationInfo reservation) {
        var orderSummary = reservation.getOrderSummary();
        assertFalse(orderSummary.isNotYetPaid());
        assertEquals("10.00", orderSummary.getTotalPrice());
        assertEquals("0.10", orderSummary.getTotalVAT());
        assertEquals("1.00", orderSummary.getVatPercentage());
    }

    private void processWebHook(String filename, String reservationId) {
        try {
            var resource = getClass().getResource(filename);
            assertNotNull(resource);
            var timestamp = String.valueOf(Webhook.Util.getTimeNow());
            var payload = Files.readString(Path.of(resource.toURI())).replaceAll("RESERVATION_ID", reservationId);
            var signedHeader = "t=" + timestamp + ",v1=" +Webhook.Util.computeHmacSha256(WEBHOOK_SECRET, timestamp + "." + payload);
            var httpRequest = new MockHttpServletRequest();
            httpRequest.setContent(payload.getBytes(StandardCharsets.UTF_8));
            httpRequest.setContentType(APPLICATION_JSON);
            var response = stripePaymentWebhookController.receivePaymentConfirmation(signedHeader, httpRequest);
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(APPLICATION_JSON_UTF8, Objects.requireNonNull(response.getHeaders().getContentType()).toString());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
