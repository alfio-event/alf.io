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
package alfio.controller;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.config.RepositoryConfiguration;
import alfio.controller.api.AttendeeApiController;
import alfio.controller.api.ReservationApiController;
import alfio.controller.api.admin.CheckInApiController;
import alfio.controller.api.admin.EventApiController;
import alfio.controller.api.admin.SerializablePair;
import alfio.controller.api.admin.UsersApiController;
import alfio.controller.api.support.TicketHelper;
import alfio.controller.form.PaymentForm;
import alfio.controller.form.ReservationForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.TicketDecorator;
import alfio.manager.*;
import alfio.manager.i18n.I18nManager;
import alfio.manager.support.CheckInStatus;
import alfio.manager.support.TicketAndCheckInResult;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.audit.ScanAudit;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.UserModification;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.audit.ScanAuditRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.EventUtil;
import alfio.util.Json;
import alfio.util.TemplateManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.opencsv.CSVReader;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.support.BindingAwareModelMap;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.Assert.*;

/**
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {RepositoryConfiguration.class, DataSourceConfiguration.class, TestConfiguration.class, ReservationFlowIntegrationTest.ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class ReservationFlowIntegrationTest {


    @Configuration
    @ComponentScan(basePackages = {"alfio.controller"})
    public static class ControllerConfiguration {

    }

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");

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
    private EuVatChecker euVatChecker;

    @Autowired
    private EventController eventController;

    @Autowired
    private EventStatisticsManager eventStatisticsManager;

    @Autowired
    private ReservationController reservationController;

    @Autowired
    private TicketController ticketController;

    @Autowired
    private EventApiController eventApiController;

    @Autowired
    private CheckInApiController checkInApiController;

    @Autowired
    private TicketHelper ticketHelper;
    @Autowired
    private I18nManager i18nManager;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Autowired
    private ScanAuditRepository scanAuditRepository;
    @Autowired
    private TicketReservationManager ticketReservationManager;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private CheckInManager checkInManager;

    @Autowired
    private AttendeeApiController attendeeApiController;

    @Autowired
    private UsersApiController usersApiController;

    private ReservationApiController reservationApiController;



    private Event event;
    private String user;

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
    }


    @Before
    public void ensureConfiguration() {

        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        event = eventAndUser.getKey();
        user = eventAndUser.getValue() + "_owner";

        //
        TemplateManager templateManager = Mockito.mock(TemplateManager.class);
        reservationApiController = new ReservationApiController(eventRepository, ticketHelper, templateManager, i18nManager, euVatChecker, ticketReservationRepository, ticketReservationManager);
    }


    /**
     * Test a complete offline payment flow.
     * Will not check in detail...
     */
    @Test
    public void reservationFlowTest() throws Exception{

        String eventName = event.getShortName();

        assertTrue(checkInManager.findAllFullTicketInfo(event.getId()).isEmpty());

        List<EventStatistic> eventStatistic = eventStatisticsManager.getAllEventsWithStatistics(user);
        assertEquals(1, eventStatistic.size());
        assertTrue(eventStatisticsManager.getTicketSoldStatistics(event.getId(), new Date(0), DateUtils.addDays(new Date(), 1)).isEmpty());
        EventWithAdditionalInfo eventWithAdditionalInfo = eventStatisticsManager.getEventWithAdditionalInfo(event.getShortName(), user);
        assertEquals(0, eventWithAdditionalInfo.getNotSoldTickets());
        assertEquals(0, eventWithAdditionalInfo.getSoldTickets());
        assertEquals(20, eventWithAdditionalInfo.getAvailableSeats());


        eventManager.toggleActiveFlag(event.getId(), user, true);
        // list events
        String eventList = eventController.listEvents(new BindingAwareModelMap(), Locale.ENGLISH);
        if(eventManager.getPublishedEvents().size() == 1) {
            Assert.assertTrue(eventList.startsWith("redirect:/"));
        } else {
            assertEquals("/event/event-list", eventList);
        }
        //

        // show event
        String showEvent = eventController.showEvent(eventName, new BindingAwareModelMap(), new MockHttpServletRequest(), Locale.ENGLISH);
        assertEquals("/event/show-event", showEvent);
        //

        // check calendar
        checkCalendar(eventName);
        //

        String redirectResult = reserveTicket(eventName);
        String redirectStart = "redirect:/event/" + eventName + "/reservation/";
        // check reservation success
        Assert.assertTrue(redirectResult.startsWith(redirectStart));
        Assert.assertTrue(redirectResult.endsWith("/book"));
        //


        String reservationIdentifier = redirectResult.substring(redirectStart.length()).replace("/book", "");


        // check that the payment page is shown
        String reservationPage = reservationController.showPaymentPage(eventName, reservationIdentifier, null, null, null, null, null, null, null, null, null, null, null, null, null, new BindingAwareModelMap(), Locale.ENGLISH);
        assertEquals("/event/reservation-page", reservationPage);
        //

        // pay offline
        String successPage = payOffline(eventName, reservationIdentifier);
        assertEquals("redirect:/event/" + eventName + "/reservation/" + reservationIdentifier + "/success", successPage);
        //

        //go to success page, payment is still pending
        String confirmationPage = reservationController.showConfirmationPage(eventName, reservationIdentifier, false, false, new BindingAwareModelMap(), Locale.ENGLISH, new MockHttpServletRequest());
        Assert.assertTrue(confirmationPage.endsWith("/waitingPayment"));


        assertEquals("/event/reservation-waiting-for-payment", reservationController.showWaitingPaymentPage(eventName, reservationIdentifier, new BindingAwareModelMap(), Locale.ENGLISH));

        //
        validatePayment(eventName, reservationIdentifier);
        //

        Assert.assertTrue(reservationController.showWaitingPaymentPage(eventName, reservationIdentifier, new BindingAwareModelMap(), Locale.ENGLISH).endsWith("/success"));

        //
        TicketDecorator ticketDecorator = checkReservationComplete(eventName, reservationIdentifier);
        //




        String ticketIdentifier = ticketDecorator.getUuid();


        //ticket is still not assigned, will redirect
        Assert.assertTrue(ticketController.showTicket(eventName, ticketIdentifier, false, Locale.ENGLISH, new BindingAwareModelMap()).startsWith("redirect:/event/"));
        Assert.assertTrue(ticketController.showTicketForUpdate(eventName, ticketIdentifier, new BindingAwareModelMap(), Locale.ENGLISH).startsWith("redirect:/event/"));
        //

        String fname1 = "Test";
        String lname1 = "McTest";

        //assign ticket to person
        assignTicket(eventName, reservationIdentifier, ticketIdentifier, fname1, lname1);

        assertEquals(1, checkInManager.findAllFullTicketInfo(event.getId()).size());

        assertEquals("/event/update-ticket", ticketController.showTicketForUpdate(eventName, ticketIdentifier, new BindingAwareModelMap(), Locale.ENGLISH));

        //
        assertEquals("/event/show-ticket", ticketController.showTicket(eventName, ticketIdentifier, false, Locale.ENGLISH, new BindingAwareModelMap()));
        //
        checkCSV(eventName, ticketIdentifier, fname1 + " " + lname1);


        // use api to update
        UpdateTicketOwnerForm updateTicketOwnerForm = new UpdateTicketOwnerForm();
        updateTicketOwnerForm.setFirstName("Test");
        updateTicketOwnerForm.setLastName("Testson");
        updateTicketOwnerForm.setEmail("testmctest@test.com");
        updateTicketOwnerForm.setUserLanguage("en");
        reservationApiController.assignTicketToPerson(eventName, ticketIdentifier, true,
            updateTicketOwnerForm, new BeanPropertyBindingResult(updateTicketOwnerForm, "updateTicketForm"), new MockHttpServletRequest(), new BindingAwareModelMap(),
            null);
        checkCSV(eventName, ticketIdentifier, "Test Testson");
        //

        //update
        String fname2 = "Test";
        String lname2 = "OTest";
        assignTicket(eventName, reservationIdentifier, ticketIdentifier, fname2, lname2);
        checkCSV(eventName, ticketIdentifier, fname2 + " " + lname2);

        //lock ticket
        Principal principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(user);
        eventApiController.toggleTicketLocking(eventName, ticketDecorator.getCategoryId(), ticketDecorator.getId(), principal);

        assignTicket(eventName, reservationIdentifier, ticketIdentifier, fname1, fname2);
        checkCSV(eventName, ticketIdentifier, fname2 + " " + lname2);

        //ticket has changed, update
        ticketDecorator = checkReservationComplete(eventName, reservationIdentifier);


        // check stats after selling one ticket
        assertFalse(eventStatisticsManager.getTicketSoldStatistics(event.getId(), new Date(0), DateUtils.addDays(new Date(), 2)).isEmpty());
        EventWithAdditionalInfo eventWithAdditionalInfo2 = eventStatisticsManager.getEventWithAdditionalInfo(event.getShortName(), user);
        assertEquals(0, eventWithAdditionalInfo2.getNotSoldTickets());
        assertEquals(1, eventWithAdditionalInfo2.getSoldTickets());
        assertEquals(20, eventWithAdditionalInfo2.getAvailableSeats());
        assertEquals(0, eventWithAdditionalInfo2.getCheckedInTickets());


        //--- check in sequence
        String ticketCode = ticketDecorator.ticketCode(event.getPrivateKey());
        TicketAndCheckInResult ticketAndCheckInResult = checkInApiController.findTicketWithUUID(event.getId(), ticketIdentifier, ticketCode);
        assertEquals(CheckInStatus.OK_READY_TO_BE_CHECKED_IN, ticketAndCheckInResult.getResult().getStatus());
        CheckInApiController.TicketCode tc = new CheckInApiController.TicketCode();
        tc.setCode(ticketCode);
        assertEquals(CheckInStatus.SUCCESS, checkInApiController.checkIn(event.getId(), ticketIdentifier, tc, new TestingAuthenticationToken("ciccio","ciccio")).getResult().getStatus());
        List<ScanAudit> audits = scanAuditRepository.findAllForEvent(event.getId());
        assertFalse(audits.isEmpty());
        assertTrue(audits.stream().anyMatch(sa -> sa.getTicketUuid().equals(ticketIdentifier)));


        TicketAndCheckInResult ticketAndCheckInResultOk = checkInApiController.findTicketWithUUID(event.getId(), ticketIdentifier, ticketCode);
        assertEquals(CheckInStatus.ALREADY_CHECK_IN, ticketAndCheckInResultOk.getResult().getStatus());

        // check stats after check in one ticket
        assertFalse(eventStatisticsManager.getTicketSoldStatistics(event.getId(), new Date(0), DateUtils.addDays(new Date(), 1)).isEmpty());
        EventWithAdditionalInfo eventWithAdditionalInfo3 = eventStatisticsManager.getEventWithAdditionalInfo(event.getShortName(), user);
        assertEquals(0, eventWithAdditionalInfo3.getNotSoldTickets());
        assertEquals(0, eventWithAdditionalInfo3.getSoldTickets());
        assertEquals(20, eventWithAdditionalInfo3.getAvailableSeats());
        assertEquals(1, eventWithAdditionalInfo3.getCheckedInTickets());



        //test revert check in
        assertTrue(checkInApiController.revertCheckIn(event.getId(), ticketIdentifier, principal));
        assertFalse(checkInApiController.revertCheckIn(event.getId(), ticketIdentifier, principal));
        TicketAndCheckInResult ticketAndCheckInResult2 = checkInApiController.findTicketWithUUID(event.getId(), ticketIdentifier, ticketCode);
        assertEquals(CheckInStatus.OK_READY_TO_BE_CHECKED_IN, ticketAndCheckInResult2.getResult().getStatus());

        UsersApiController.UserWithPasswordAndQRCode sponsorUser = usersApiController.insertUser(new UserModification(null, event.getOrganizationId(), "SPONSOR", "sponsor", "first", "last", "email@email.com"), "http://localhost:8080", principal);
        Principal sponsorPrincipal = Mockito.mock(Principal.class);
        Mockito.when(sponsorPrincipal.getName()).thenReturn(sponsorUser.getUsername());

        // check failures
        assertEquals(CheckInStatus.EVENT_NOT_FOUND, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest("not-existing-event", "not-existing-ticket"), sponsorPrincipal).getBody().getResult().getStatus());
        assertEquals(CheckInStatus.TICKET_NOT_FOUND, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, "not-existing-ticket"), sponsorPrincipal).getBody().getResult().getStatus());
        assertEquals(CheckInStatus.INVALID_TICKET_STATE, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, ticketIdentifier), sponsorPrincipal).getBody().getResult().getStatus());
        //



        // check stats after revert check in one ticket
        assertFalse(eventStatisticsManager.getTicketSoldStatistics(event.getId(), new Date(0), DateUtils.addDays(new Date(), 1)).isEmpty());
        EventWithAdditionalInfo eventWithAdditionalInfo4 = eventStatisticsManager.getEventWithAdditionalInfo(event.getShortName(), user);
        assertEquals(0, eventWithAdditionalInfo4.getNotSoldTickets());
        assertEquals(1, eventWithAdditionalInfo4.getSoldTickets());
        assertEquals(20, eventWithAdditionalInfo4.getAvailableSeats());
        assertEquals(0, eventWithAdditionalInfo4.getCheckedInTickets());


        CheckInApiController.TicketCode tc2 = new CheckInApiController.TicketCode();
        tc2.setCode(ticketCode);
        TicketAndCheckInResult ticketAndcheckInResult = checkInApiController.checkIn(event.getId(), ticketIdentifier, tc2, new TestingAuthenticationToken("ciccio", "ciccio"));
        assertEquals(CheckInStatus.SUCCESS, ticketAndcheckInResult.getResult().getStatus());
        //


        //
        List<Integer> offlineIdentifiers = checkInApiController.getOfflineIdentifiers(event.getShortName(), 0L, new MockHttpServletResponse(), principal);
        assertTrue(offlineIdentifiers.isEmpty());
        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), ConfigurationKeys.OFFLINE_CHECKIN_ENABLED.name(), "true", null);
        configurationRepository.insert(ConfigurationKeys.ALFIO_PI_INTEGRATION_ENABLED.name(), "true", null);
        offlineIdentifiers = checkInApiController.getOfflineIdentifiers(event.getShortName(), 0L, new MockHttpServletResponse(), principal);
        assertFalse(offlineIdentifiers.isEmpty());
        Map<String, String> payload = checkInApiController.getOfflineEncryptedInfo(event.getShortName(), Collections.emptyList(), offlineIdentifiers, principal);
        assertEquals(1, payload.size());
        Ticket ticket = ticketAndcheckInResult.getTicket();
        String ticketKey = ticket.hmacTicketInfo(event.getPrivateKey());
        String hashedTicketKey = DigestUtils.sha256Hex(ticketKey);
        String encJson = payload.get(hashedTicketKey);
        assertNotNull(encJson);
        String ticketPayload = CheckInManager.decrypt(ticket.getUuid() + "/" + ticketKey, encJson);
        Map<String, String> jsonPayload = Json.fromJson(ticketPayload, new TypeReference<Map<String, String>>() {
        });
        assertNotNull(jsonPayload);
        assertEquals(8, jsonPayload.size());
        assertEquals("Test", jsonPayload.get("firstName"));
        assertEquals("OTest", jsonPayload.get("lastName"));
        assertEquals("Test OTest", jsonPayload.get("fullName"));
        assertEquals(ticket.getUuid(), jsonPayload.get("uuid"));
        assertEquals("testmctest@test.com", jsonPayload.get("email"));
        assertEquals("CHECKED_IN", jsonPayload.get("status"));
        String categoryName = ticketCategoryRepository.findByEventId(event.getId()).stream().findFirst().orElseThrow(IllegalStateException::new).getName();
        assertEquals(categoryName, jsonPayload.get("category"));
        //

        // check register sponsor scan success flow
        assertTrue(attendeeApiController.getScannedBadges(event.getShortName(), EventUtil.JSON_DATETIME_FORMATTER.format(LocalDateTime.of(1970, 1, 1, 0, 0)), sponsorPrincipal).getBody().isEmpty());
        assertEquals(CheckInStatus.SUCCESS, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, ticket.getUuid()), sponsorPrincipal).getBody().getResult().getStatus());
        assertEquals(1, attendeeApiController.getScannedBadges(event.getShortName(), EventUtil.JSON_DATETIME_FORMATTER.format(LocalDateTime.of(1970, 1, 1, 0, 0)), sponsorPrincipal).getBody().size());
        //
        
        eventManager.deleteEvent(event.getId(), principal.getName());

    }

    private void checkCalendar(String eventName) throws IOException {
        MockHttpServletResponse resIcal = new MockHttpServletResponse();
        eventController.calendar(eventName, "en", null, null, resIcal);
        assertEquals("text/calendar", resIcal.getContentType());

        MockHttpServletResponse resGoogleCal = new MockHttpServletResponse();
        eventController.calendar(eventName, "en", "google", null, resGoogleCal);
        Assert.assertTrue(resGoogleCal.getRedirectedUrl().startsWith("https://www.google.com/calendar/event"));
    }

    private TicketDecorator checkReservationComplete(String eventName, String reservationIdentifier) {
        Model confirmationPageModel = new BindingAwareModelMap();
        String confirmationPageSuccess = reservationController.showConfirmationPage(eventName, reservationIdentifier, false, false, confirmationPageModel, Locale.ENGLISH, new MockHttpServletRequest());
        assertEquals("/event/reservation-page-complete", confirmationPageSuccess);
        @SuppressWarnings("unchecked")
        List<Pair<?, List<TicketDecorator>>> tickets = (List<Pair<?, List<TicketDecorator>>>) confirmationPageModel.asMap().get("ticketsByCategory");
        assertEquals(1, tickets.size());
        assertEquals(1, tickets.get(0).getRight().size());
        return tickets.get(0).getRight().get(0);
    }

    private void assignTicket(String eventName, String reservationIdentifier, String ticketIdentifier, String firstName, String lastName) throws Exception {
        UpdateTicketOwnerForm ticketOwnerForm = new UpdateTicketOwnerForm();
        ticketOwnerForm.setFirstName(firstName);
        ticketOwnerForm.setLastName(lastName);
        ticketOwnerForm.setEmail("testmctest@test.com");
        ticketOwnerForm.setUserLanguage("en");
        Assert.assertTrue(reservationController.assignTicketToPerson(eventName, reservationIdentifier, ticketIdentifier, ticketOwnerForm, Mockito.mock(BindingResult.class), new MockHttpServletRequest(), new BindingAwareModelMap()).endsWith("/success"));
    }

    private void checkCSV(String eventName, String ticketIdentifier, String fullName) throws IOException {
        //FIXME get all fields :D and put it in the request...
        Principal principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(user);
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<SerializablePair<String, String>> fields = eventApiController.getAllFields(eventName);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("fields", fields.stream().map(SerializablePair::getKey).toArray(String[]::new));
        eventApiController.downloadAllTicketsCSV(eventName, request, response, principal);
        CSVReader csvReader = new CSVReader(new StringReader(response.getContentAsString()));
        List<String[]> csv = csvReader.readAll();
        assertEquals(2, csv.size());
        assertEquals(ticketIdentifier, csv.get(1)[0]);
        assertEquals("default", csv.get(1)[2]);
        assertEquals("ACQUIRED", csv.get(1)[4]);
        assertEquals(fullName, csv.get(1)[10]);
    }

    private void validatePayment(String eventName, String reservationIdentifier) {
        Principal principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(user);
        assertEquals(1, eventApiController.getPendingPayments(eventName, principal).size());
        assertEquals("OK", eventApiController.confirmPayment(eventName, reservationIdentifier, principal, new BindingAwareModelMap(), new MockHttpServletRequest()));
        assertEquals(0, eventApiController.getPendingPayments(eventName, principal).size());
    }

    private String payOffline(String eventName, String reservationIdentifier) {
        PaymentForm paymentForm = new PaymentForm();
        paymentForm.setPaymentMethod(PaymentProxy.OFFLINE);
        paymentForm.setEmail("test@test.com");
        paymentForm.setBillingAddress("my billing address");
        paymentForm.setFirstName("full");
        paymentForm.setLastName("name");
        paymentForm.setTermAndConditionsAccepted(true);
        paymentForm.setPrivacyPolicyAccepted(true);
        paymentForm.setPostponeAssignment(true);
        BindingResult bindingResult = new BeanPropertyBindingResult(paymentForm, "paymentForm");
        Model model = new BindingAwareModelMap();
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        return reservationController.handleReservation(eventName, reservationIdentifier, paymentForm, bindingResult, model, request, Locale.ENGLISH, redirectAttributes);
    }

    private String reserveTicket(String eventName) {
        ReservationForm reservationForm = new ReservationForm();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        ServletWebRequest servletWebRequest = new ServletWebRequest(request);
        BindingResult bindingResult = new BeanPropertyBindingResult(reservationForm, "reservation");
        Model model = new BindingAwareModelMap();
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        TicketReservationModification ticketReservation = new TicketReservationModification();
        ticketReservation.setAmount(1);
        ticketReservation.setTicketCategoryId(ticketCategoryRepository.findByEventId(event.getId()).stream().findFirst().map(TicketCategory::getId).orElseThrow(IllegalStateException::new));
        reservationForm.setReservation(Collections.singletonList(ticketReservation));


        return eventController.reserveTicket(eventName, reservationForm, bindingResult, model, servletWebRequest, redirectAttributes, Locale.ENGLISH);
    }

}
