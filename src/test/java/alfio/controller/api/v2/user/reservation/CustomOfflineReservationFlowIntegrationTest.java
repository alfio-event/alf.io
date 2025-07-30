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
import alfio.controller.form.ReservationForm;
import alfio.controller.payment.api.stripe.StripePaymentWebhookController;
import alfio.extension.ExtensionService;
import alfio.manager.*;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodAlreadyExistsException;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodDoesNotExistException;
import alfio.manager.support.extension.ExtensionEvent;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.repository.*;
import alfio.repository.audit.ScanAuditRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.context.request.ServletWebRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class CustomOfflineReservationFlowIntegrationTest extends BaseReservationFlowTest {
    @Autowired
    protected EventManager eventManager;
    @Autowired
    protected EventRepository eventRepository;
    @Autowired
    protected UserManager userManager;
    @Autowired
    protected ClockProvider clockProvider;
    @Autowired
    protected ConfigurationRepository configurationRepository;
    @Autowired
    protected EventStatisticsManager eventStatisticsManager;
    @Autowired
    protected TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    protected TicketReservationRepository ticketReservationRepository;
    @Autowired
    protected EventApiController eventApiController;
    @Autowired
    protected TicketRepository ticketRepository;
    @Autowired
    protected PurchaseContextFieldRepository purchaseContextFieldRepository;
    @Autowired
    protected AdditionalServiceApiController additionalServiceApiController;
    @Autowired
    protected SpecialPriceTokenGenerator specialPriceTokenGenerator;
    @Autowired
    protected SpecialPriceRepository specialPriceRepository;
    @Autowired
    protected CheckInApiController checkInApiController;
    @Autowired
    protected AttendeeApiController attendeeApiController;
    @Autowired
    protected UsersApiController usersApiController;
    @Autowired
    protected ScanAuditRepository scanAuditRepository;
    @Autowired
    protected AuditingRepository auditingRepository;
    @Autowired
    protected AdminReservationManager adminReservationManager;
    @Autowired
    protected TicketReservationManager ticketReservationManager;
    @Autowired
    protected InfoApiController infoApiController;
    @Autowired
    protected TranslationsApiController translationsApiController;
    @Autowired
    protected EventApiV2Controller eventApiV2Controller;
    @Autowired
    protected ReservationApiV2Controller reservationApiV2Controller;
    @Autowired
    protected TicketApiV2Controller ticketApiV2Controller;
    @Autowired
    protected IndexController indexController;
    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    protected ExtensionLogRepository extensionLogRepository;
    @Autowired
    protected ExtensionService extensionService;
    @Autowired
    protected PollRepository pollRepository;
    @Autowired
    protected NotificationManager notificationManager;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected OrganizationDeleter organizationDeleter;
    @Autowired
    protected PromoCodeDiscountRepository promoCodeDiscountRepository;
    @Autowired
    protected PromoCodeRequestManager promoCodeRequestManager;
    @Autowired
    protected StripePaymentWebhookController stripePaymentWebhookController;
    @Autowired
    protected ExportManager exportManager;
    @Autowired
    protected PurchaseContextFieldManager purchaseContextFieldManager;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected OrganizationRepository organizationRepository;
    @Autowired
    protected CustomOfflineConfigurationManager customOfflineConfigurationManager;

    private List<UserDefinedOfflinePaymentMethod> paymentMethods = List.of(
        new UserDefinedOfflinePaymentMethod(
            "b059f733-bae7-4ee4-a95a-07941ecffe48",
            Map.of(
                "en",
                new UserDefinedOfflinePaymentMethod.Localization(
                    "Interac E-Transfer",
                    "Payments to and from any Canadian bank _account_",
                    "## Send payments to `payments@org1.org`."
                )
            )
        ),
        new UserDefinedOfflinePaymentMethod(
            "8c8027d1-8c67-4443-8dc3-660ac4eb4cbc",
            Map.of(
                "en",
                new UserDefinedOfflinePaymentMethod.Localization(
                    "Cash App",
                    "Send instant payments through the Cash App app for Android and IOS",
                    "Send the full invoiced amount to `org1payments` in the app."
                )
            )
        )
    );
    private PaymentMethod testingPaymentMethod;

    private ReservationFlowContext createContext() throws CustomOfflinePaymentMethodAlreadyExistsException, CustomOfflinePaymentMethodDoesNotExistException {
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
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, null, Event.EventFormat.ONLINE);

        var event = eventAndUser.getLeft();
        var orgId = event.getOrganizationId();

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(orgId, pm);
        }

        customOfflineConfigurationManager.setAllowedCustomOfflinePaymentMethodsForEvent(
            event,
            List.of(paymentMethods.get(1).getPaymentMethodId())
        );

        return new ReservationFlowContext(eventAndUser.getLeft(), owner(eventAndUser.getRight()), null, null, null, null, true, false, Map.of(), true);
    }

    @Test
    void payWithCustomPaymentMethod() throws Exception {
        testingPaymentMethod = paymentMethods.get(1);
        super.testBasicFlow(() -> {
            try {
                return createContext();
            } catch (CustomOfflinePaymentMethodAlreadyExistsException |
                     CustomOfflinePaymentMethodDoesNotExistException e) {
                return null;
            }
        });
    }

    @Test
    void attemptPayWithUnavailablePaymentMethod() throws Exception {
        testingPaymentMethod = paymentMethods.get(0);

        var context = this.createContext();
        var form = new ReservationForm();
        var ticketReservation = new TicketReservationModification();
        ticketReservation.setQuantity(1);
        var ticketCategoriesResponse = eventApiV2Controller.getTicketCategories(context.event.getShortName(), null);
        ticketReservation.setTicketCategoryId(ticketCategoriesResponse.getBody().ticketCategories().get(0).getId());
        form.setReservation(Collections.singletonList(ticketReservation));
        if (context.applyDiscount) {
            form.setPromoCode(PROMO_CODE);
        }
        var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
        assertEquals(HttpStatus.OK, res.getStatusCode());
        var resBody = res.getBody();
        assertNotNull(resBody);
        assertTrue(resBody.isSuccess());
        assertEquals(0, resBody.getErrorCount());
        var reservationId = resBody.getValue();

        var paymentForm = new PaymentForm();
        paymentForm.setPrivacyPolicyAccepted(true);
        paymentForm.setTermAndConditionsAccepted(true);
        paymentForm.setPaymentProxy(PaymentProxy.CUSTOM_OFFLINE);
        paymentForm.setSelectedPaymentMethod(testingPaymentMethod);

        var handleRes = reservationApiV2Controller.confirmOverview(
            reservationId,
            "en",
            paymentForm,
            new BeanPropertyBindingResult(paymentForm, "paymentForm"),
            new MockHttpServletRequest(), context.getPublicUser()
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, handleRes.getStatusCode());
        assertTrue(handleRes.getBody().getValue().isFailure());
    }

    @Override
    protected void performAndValidatePayment(ReservationFlowContext context,
                                             String reservationId,
                                             int promoCodeId,
                                             Runnable cleanupExtensionLog) {
        ReservationInfo reservation;
        reservation = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser()).getBody();
        var paymentForm = new PaymentForm();
        var handleResError = reservationApiV2Controller.confirmOverview(
            reservationId,
            "en",
            paymentForm,
            new BeanPropertyBindingResult(paymentForm, "paymentForm"),
            new MockHttpServletRequest(),
            context.getPublicUser()
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, handleResError.getStatusCode());


        paymentForm.setPrivacyPolicyAccepted(true);
        paymentForm.setTermAndConditionsAccepted(true);
        paymentForm.setPaymentProxy(PaymentProxy.CUSTOM_OFFLINE);
        paymentForm.setSelectedPaymentMethod(testingPaymentMethod);

        // Custom offline does not have a transaction, it's created on confirmOverview call
        var tStatus = reservationApiV2Controller.getTransactionStatus(reservationId, testingPaymentMethod);
        assertEquals(HttpStatus.NOT_FOUND, tStatus.getStatusCode());
        //
        var promoCodeUsage = promoCodeRequestManager.retrieveDetailedUsage(promoCodeId, context.event.getId());
        assertTrue(promoCodeUsage.isEmpty());


        var handleRes = reservationApiV2Controller.confirmOverview(
            reservationId,
            "en",
            paymentForm,
            new BeanPropertyBindingResult(paymentForm, "paymentForm"),
            new MockHttpServletRequest(),
            context.getPublicUser()
        );

        assertEquals(HttpStatus.OK, handleRes.getStatusCode());

        checkStatus(reservationId,
            HttpStatus.OK,
            true,
            TicketReservation.TicketReservationStatus.CUSTOM_OFFLINE_PAYMENT,
            context
        );

        tStatus = reservationApiV2Controller.getTransactionStatus(reservationId, testingPaymentMethod);
        assertEquals(HttpStatus.OK, tStatus.getStatusCode());
        assertNotNull(tStatus.getBody());
        assertFalse(tStatus.getBody().isSuccess());

        reservation = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser()).getBody();
        assertEquals(PaymentProxy.CUSTOM_OFFLINE, reservation.getPaymentProxy());
        assertNotNull(reservation);
        checkOrderSummary(reservation, context);
        cleanupExtensionLog.run();
        validatePayment(context.event.getShortName(), reservationId, context);

        var extLogs = extensionLogRepository.getPage(null, null, null, 100, 0);

        boolean online = containsOnlineTickets(context, reservationId);
        assertEventLogged(extLogs, ExtensionEvent.RESERVATION_CONFIRMED, online ? 12 : 10);
        assertEventLogged(extLogs, ExtensionEvent.CONFIRMATION_MAIL_CUSTOM_TEXT, online ? 12 : 10);
        assertEventLogged(extLogs, ExtensionEvent.TICKET_ASSIGNED, online ? 12 : 10);
        if(online) {
            assertEventLogged(extLogs, ExtensionEvent.CUSTOM_ONLINE_JOIN_URL, 12);
        }
        assertEventLogged(extLogs, ExtensionEvent.TICKET_ASSIGNED_GENERATE_METADATA, online ? 12 : 10);
        assertEventLogged(extLogs, ExtensionEvent.TICKET_MAIL_CUSTOM_TEXT, online ? 12 : 10);

        tStatus = reservationApiV2Controller.getTransactionStatus(reservationId, testingPaymentMethod);
        assertEquals(HttpStatus.OK, tStatus.getStatusCode());
        assertNotNull(tStatus.getBody());
        assertTrue(tStatus.getBody().isSuccess());
    }
}
