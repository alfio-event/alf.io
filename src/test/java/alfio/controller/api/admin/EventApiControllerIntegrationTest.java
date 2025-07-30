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
package alfio.controller.api.admin;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.AdminReservationRequestManager;
import alfio.manager.EventManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodAlreadyExistsException;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodDoesNotExistException;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.AdminReservationModification;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.repository.EventDeleterRepository;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.toolkit.PromoCodeDiscountIntegrationTestingToolkit;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static alfio.controller.api.admin.EventApiController.FIXED_FIELDS;
import static alfio.test.toolkit.PromoCodeDiscountIntegrationTestingToolkit.TEST_PROMO_CODE;
import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.DESCRIPTION;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static alfio.test.util.IntegrationTestUtil.owner;
import static alfio.test.util.TestUtil.clockProvider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class EventApiControllerIntegrationTest {

    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventDeleterRepository eventDeleterRepository;
    @Autowired
    private EventApiController eventApiController;
    @Autowired
    private AttendeeBulkImportApiController attendeeBulkImportApiController;
    @Autowired
    private AdminReservationRequestManager adminReservationRequestManager;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private PromoCodeDiscountIntegrationTestingToolkit promoCodeDiscountIntegrationTestingToolkit;
    @Autowired
    private CustomOfflineConfigurationManager customOfflineConfigurationManager;

    private Event event;
    private static final String TEST_ATTENDEE_EXTERNAL_REFERENCE = "123";
    private static final String TEST_ATTENDEE_USER_LANGUAGE = "en";
    private static final String TEST_ATTENDEE_FIRST_NAME = "Attendee";
    private static final String TEST_ATTENDEE_LAST_NAME = "Test";
    private static final String TEST_ATTENDEE_EMAIL = "attendee@test.com";

    @Test
    void getAllEventsForExternalInPerson() {
        var eventAndUser = createEvent(Event.EventFormat.IN_PERSON);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Authentication.class);
        when(principal.getName()).thenReturn(eventAndUser.getValue());
        var events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), false);

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(event.getShortName(), events.get(0).getKey());
    }

    @Test
    void getAllEventsForExternalHybrid() {
        var eventAndUser = createEvent(Event.EventFormat.HYBRID);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Authentication.class);
        when(principal.getName()).thenReturn(eventAndUser.getValue());
        var events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), false);

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(event.getShortName(), events.get(0).getKey());
    }

    @Test
    void getAllEventsForExternalOnline() {
        var eventAndUser = createEvent(Event.EventFormat.ONLINE);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Authentication.class);
        when(principal.getName()).thenReturn(eventAndUser.getValue());
        var events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), false);

        assertNotNull(events);
        assertEquals(0, events.size());

        events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), true);
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(event.getShortName(), events.get(0).getKey());
    }

    @Test
    void testGivenListOfAttendeesWithFieldsUploadThenSameFieldsAvailableOnCsvDownload() throws IOException {
        // GIVEN - creation of event and registration of attendees
        var eventAndUser = createEvent(Event.EventFormat.HYBRID);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Authentication.class);
        when(principal.getName()).thenReturn(owner(eventAndUser.getValue()));
        var modification = getTestAdminReservationModification();
        var result = this.attendeeBulkImportApiController.createReservations(eventAndUser.getKey().getShortName(), modification, false, principal);
        var organizationId = organizationRepository.findAllForUser(eventAndUser.getRight()).get(0).getId();

        // GIVEN - invocation of async processing job
        var requestStatus = this.attendeeBulkImportApiController.getRequestsStatus(eventAndUser.getKey().getShortName(), result.getData(), principal);
        assertEquals(1, requestStatus.getData().getCountPending());

        // WHEN - processing of pending reservations completes
        this.adminReservationRequestManager.processPendingReservations();
        promoCodeDiscountIntegrationTestingToolkit.createPromoCodeDiscount(event.getId(), organizationId, modification.getCustomerData()
                                                                                                                      .getEmailAddress());
        // THEN - assert correctness of data persisted
        var tickets = this.ticketRepository.findAllConfirmedForCSV(event.getId());
        assertEquals(1, tickets.size());
        var foundTicket = tickets.get(0).getTicket();
        assertEquals(TEST_ATTENDEE_EXTERNAL_REFERENCE, foundTicket.getExtReference());
        assertEquals(TEST_ATTENDEE_FIRST_NAME, foundTicket.getFirstName());
        assertEquals(TEST_ATTENDEE_LAST_NAME, foundTicket.getLastName());
        assertEquals(TEST_ATTENDEE_EMAIL, foundTicket.getEmail());
        assertEquals(TEST_ATTENDEE_USER_LANGUAGE, foundTicket.getUserLanguage());

        // THEN - assert correct order of CSV fields upon download
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addParameter("fields", FIXED_FIELDS.toArray(new String[0]));
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        this.eventApiController.downloadAllTicketsCSV(event.getShortName(), "csv", mockRequest, mockResponse, principal);
        String expectedTestAttendeeCsvLine = "\""+foundTicket.getUuid()+"\""+",default,"+"\""+event.getShortName()+"\""+",ACQUIRED,0,0,0,0,"+"\""+foundTicket.getTicketsReservationId()+"\""+",\""+TEST_ATTENDEE_FIRST_NAME+" "+TEST_ATTENDEE_LAST_NAME+"\","+TEST_ATTENDEE_FIRST_NAME+","+TEST_ATTENDEE_LAST_NAME+","+TEST_ATTENDEE_EMAIL+",false,"+TEST_ATTENDEE_USER_LANGUAGE;
        String returnedCsvContent = mockResponse.getContentAsString().trim().replace("\uFEFF", ""); // remove BOM
        assertTrue(returnedCsvContent.startsWith(getExpectedHeaderCsvLine() + "\n" + expectedTestAttendeeCsvLine));
        assertTrue(returnedCsvContent.endsWith("\"Billing Address\",,"+TEST_PROMO_CODE+",,," + TEST_ATTENDEE_EXTERNAL_REFERENCE));
    }

    @Test
    void testCanGetDeniedCustomPaymentMethods() throws CustomOfflinePaymentMethodAlreadyExistsException, PassedIdDoesNotExistException, CustomOfflinePaymentMethodDoesNotExistException {
        var eventAndUser = createEvent(Event.EventFormat.ONLINE);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Authentication.class);
        when(principal.getName()).thenReturn(owner(eventAndUser.getValue()));
        var organizationId = organizationRepository.findAllForUser(eventAndUser.getRight()).get(0).getId();
        var ticketCategoryList = this.ticketCategoryRepository.findAllTicketCategories(event.getId());

        assertEquals(1, ticketCategoryList.size());

        var ticketCategory = ticketCategoryList.get(0);

        var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant bank transfer from any Canadian account.",
                        "Send the payment to `payments@example.com`."
                    )
                )
            ),
            new UserDefinedOfflinePaymentMethod(
                "ec6c5268-4122-4b27-98ee-fa070df11c5b",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Venmo",
                        "Instant money transfers via the Venmo app.",
                        "Send the payment to user `exampleco` on Venmo."
                    )
                )
            )
        );

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(organizationId, pm);
        }
        customOfflineConfigurationManager.setDeniedPaymentMethodsByTicketCategory(
            event,
            ticketCategory,
            List.of(paymentMethods.get(0))
        );

        var response = eventApiController.getDeniedCustomPaymentMethods(
            event.getId(),
            ticketCategory.getId(),
            principal
        );

        var deniedMethodIds = response.getBody();

        assertEquals(1, deniedMethodIds.size());
        assertTrue(deniedMethodIds.stream().allMatch(blItem ->
            paymentMethods.stream().anyMatch(pmItem -> blItem.equals(pmItem.getPaymentMethodId())))
        );
    }

    @Test
    void testCanSetDeniedCustomPaymentMethods() throws PassedIdDoesNotExistException, CustomOfflinePaymentMethodAlreadyExistsException, CustomOfflinePaymentMethodDoesNotExistException {
        var eventAndUser = createEvent(Event.EventFormat.ONLINE);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Authentication.class);
        when(principal.getName()).thenReturn(owner(eventAndUser.getValue()));
        var ticketCategoryList = this.ticketCategoryRepository.findAllTicketCategories(event.getId());

        assertEquals(1, ticketCategoryList.size());

        var ticketCategory = ticketCategoryList.get(0);

        var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant bank transfer from any Canadian account.",
                        "Send the payment to `payments@example.com`."
                    )
                )
            ),
            new UserDefinedOfflinePaymentMethod(
                "ec6c5268-4122-4b27-98ee-fa070df11c5b",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Venmo",
                        "Instant money transfers via the Venmo app.",
                        "Send the payment to user `exampleco` on Venmo."
                    )
                )
            )
        );

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(event.getOrganizationId(), pm);
        }

        eventApiController.setDeniedCustomPaymentMethods(
            event.getId(),
            ticketCategory.getId(),
            List.of(paymentMethods.get(0).getPaymentMethodId()),
            principal
        );

        var storedDeniedPaymentMethods = customOfflineConfigurationManager.getDeniedPaymentMethodsByTicketCategory(
            event,
            ticketCategory
        );
        assertEquals(1, storedDeniedPaymentMethods.size());

        assertTrue(storedDeniedPaymentMethods.stream().allMatch(
            blItem -> paymentMethods.stream().anyMatch(pmItem -> blItem.getPaymentMethodId().equals(pmItem.getPaymentMethodId())))
        );
    }

    private AdminReservationModification getTestAdminReservationModification() {
        DateTimeModification expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        AdminReservationModification.CustomerData customerData = new AdminReservationModification.CustomerData("Integration", "Test", "integration-test@test.ch", "Billing Address", "reference", "en", "1234", "CH", null);
        var ticketCategoryList = this.ticketCategoryRepository.findAllTicketCategories(event.getId());
        AdminReservationModification.Category category = new AdminReservationModification.Category(ticketCategoryList.get(0).getId(), "name", new BigDecimal("100.00"), null);
        List<AdminReservationModification.TicketsInfo> ticketsInfoList = Collections.singletonList(new AdminReservationModification.TicketsInfo(category, Collections.singletonList(generateTestAttendee()), true, false));
        return new AdminReservationModification(expiration, customerData, ticketsInfoList, "en", false, false, null, null, null, null);
    }

    private Pair<Event,String> createEvent(Event.EventFormat format) {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider().getClock()).minusDays(1), LocalTime.now(clockProvider().getClock())),
                new DateTimeModification(LocalDate.now(clockProvider().getClock()).plusDays(1), LocalTime.now(clockProvider().getClock())),
                DESCRIPTION, BigDecimal.ZERO, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        return initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, List.of(), format);

    }

    private String getExpectedHeaderCsvLine() {
        String expectedHeaderCsvLine = String.join(",", FIXED_FIELDS);
        expectedHeaderCsvLine = expectedHeaderCsvLine.replaceAll("Full Name", "\"Full Name\"");
        expectedHeaderCsvLine = expectedHeaderCsvLine.replaceAll("First Name", "\"First Name\"");
        expectedHeaderCsvLine = expectedHeaderCsvLine.replaceAll("Last Name", "\"Last Name\"");
        expectedHeaderCsvLine = expectedHeaderCsvLine.replaceAll("Billing Address", "\"Billing Address\"");
        expectedHeaderCsvLine = expectedHeaderCsvLine.replaceAll("Country Code", "\"Country Code\"");
        expectedHeaderCsvLine = expectedHeaderCsvLine.replaceAll("Promo Code", "\"Promo Code\"");
        expectedHeaderCsvLine = expectedHeaderCsvLine.replaceAll("Payment ID", "\"Payment ID\"");
        expectedHeaderCsvLine = expectedHeaderCsvLine.replaceAll("Payment Method", "\"Payment Method\"");
        expectedHeaderCsvLine = expectedHeaderCsvLine.replaceAll("External Reference", "\"External Reference\"");
        return expectedHeaderCsvLine;
    }

    private AdminReservationModification.Attendee generateTestAttendee() {
        return new AdminReservationModification.Attendee(null, TEST_ATTENDEE_FIRST_NAME, TEST_ATTENDEE_LAST_NAME, TEST_ATTENDEE_EMAIL, TEST_ATTENDEE_USER_LANGUAGE,false, TEST_ATTENDEE_EXTERNAL_REFERENCE, null, Collections.emptyMap(), null);
    }

    @AfterEach
    void tearDown() {
        eventDeleterRepository.deleteAllForEvent(event.getId());
    }
}