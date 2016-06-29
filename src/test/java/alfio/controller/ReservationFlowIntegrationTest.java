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
import alfio.controller.api.ReservationApiController;
import alfio.controller.api.admin.CheckInApiController;
import alfio.controller.api.admin.EventApiController;
import alfio.controller.api.support.TicketHelper;
import alfio.controller.form.PaymentForm;
import alfio.controller.form.ReservationForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.TicketDecorator;
import alfio.manager.EventManager;
import alfio.manager.EventStatisticsManager;
import alfio.manager.i18n.I18nManager;
import alfio.manager.support.CheckInStatus;
import alfio.manager.support.TicketAndCheckInResult;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.TemplateManager;
import com.opencsv.CSVReader;
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
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static alfio.test.util.IntegrationTestUtil.*;

/**
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ReservationFlowIntegrationTest.ControllerConfiguration.class})
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
    private EventStatisticsManager eventStatisticsManager;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserManager userManager;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private EventController eventController;

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
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager);

        event = eventAndUser.getKey();
        user = eventAndUser.getValue() + "_owner";


        //
        TemplateManager templateManager = Mockito.mock(TemplateManager.class);
        reservationApiController = new ReservationApiController(ticketHelper, templateManager, i18nManager);
    }


    /**
     * Test a complete offline payment flow.
     * Will not check in detail...
     */
    @Test
    public void reservationFlowTest() throws Exception{

        String eventName = event.getShortName();


        // list events
        String eventList = eventController.listEvents(new BindingAwareModelMap(), Locale.ENGLISH);
        if(eventManager.getActiveEvents().size() == 1) {
            Assert.assertTrue(eventList.startsWith("redirect:/"));
        } else {
            Assert.assertEquals("/event/event-list", eventList);
        }
        //

        // show event
        String showEvent = eventController.showEvent(eventName, new BindingAwareModelMap(), new MockHttpServletRequest(), Locale.ENGLISH);
        Assert.assertEquals("/event/show-event", showEvent);
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
        String reservationPage = reservationController.showPaymentPage(eventName, reservationIdentifier, null, null, null, null, null, null, null, null, new BindingAwareModelMap(), Locale.ENGLISH);
        Assert.assertEquals("/event/reservation-page", reservationPage);
        //

        // pay offline
        String successPage = payOffline(eventName, reservationIdentifier);
        Assert.assertEquals("redirect:/event/" + eventName + "/reservation/" + reservationIdentifier + "/success", successPage);
        //

        //go to success page, payment is still pending
        String confirmationPage = reservationController.showConfirmationPage(eventName, reservationIdentifier, false, false, new BindingAwareModelMap(), Locale.ENGLISH, new MockHttpServletRequest());
        Assert.assertTrue(confirmationPage.endsWith("/waitingPayment"));


        Assert.assertEquals("/event/reservation-waiting-for-payment", reservationController.showWaitingPaymentPage(eventName, reservationIdentifier, new BindingAwareModelMap(), Locale.ENGLISH));

        //
        validatePayment(eventName, reservationIdentifier);
        //

        Assert.assertTrue(reservationController.showWaitingPaymentPage(eventName, reservationIdentifier, new BindingAwareModelMap(), Locale.ENGLISH).endsWith("/success"));

        //
        TicketDecorator ticketDecorator = checkReservationComplete(eventName, reservationIdentifier);
        //




        String ticketIdentifier = ticketDecorator.getUuid();


        //ticket is still not assigned, will redirect
        Assert.assertTrue(ticketController.showTicket(eventName, reservationIdentifier, ticketIdentifier, false, Locale.ENGLISH, new BindingAwareModelMap()).startsWith("redirect:/event/"));
        Assert.assertTrue(ticketController.showTicketForUpdate(eventName, reservationIdentifier, ticketIdentifier, new BindingAwareModelMap(), Locale.ENGLISH).startsWith("redirect:/event/"));
        //

        String name1 = "Test McTest";

        //assign ticket to person
        assignTicket(eventName, reservationIdentifier, ticketIdentifier, name1);

        Assert.assertEquals("/event/update-ticket", ticketController.showTicketForUpdate(eventName, reservationIdentifier, ticketIdentifier, new BindingAwareModelMap(), Locale.ENGLISH));

        //
        Assert.assertEquals("/event/show-ticket", ticketController.showTicket(eventName, reservationIdentifier, ticketIdentifier, false, Locale.ENGLISH, new BindingAwareModelMap()));
        //
        checkCSV(eventName, ticketIdentifier, name1);


        // use api to update
        UpdateTicketOwnerForm updateTicketOwnerForm = new UpdateTicketOwnerForm();
        updateTicketOwnerForm.setFullName("Test Testson");
        updateTicketOwnerForm.setEmail("testmctest@test.com");
        updateTicketOwnerForm.setUserLanguage("en");
        reservationApiController.assignTicketToPerson(eventName, reservationIdentifier, ticketIdentifier, true,
            updateTicketOwnerForm, new BeanPropertyBindingResult(updateTicketOwnerForm, "updateTicketForm"), new MockHttpServletRequest(), new BindingAwareModelMap(),
            null);
        checkCSV(eventName, ticketIdentifier, "Test Testson");
        //

        //update
        String name2 = "Test OTest";
        assignTicket(eventName, reservationIdentifier, ticketIdentifier, name2);
        checkCSV(eventName, ticketIdentifier, name2);

        //lock ticket
        Principal principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(user);
        eventApiController.toggleTicketLocking(eventName, ticketDecorator.getCategoryId(), ticketDecorator.getId(), principal);

        assignTicket(eventName, reservationIdentifier, ticketIdentifier, name1);
        checkCSV(eventName, ticketIdentifier, name2);

        //ticket has changed, update
        ticketDecorator = checkReservationComplete(eventName, reservationIdentifier);

        //--- check in sequence
        String ticketCode = ticketDecorator.ticketCode(event.getPrivateKey());
        TicketAndCheckInResult ticketAndCheckInResult = checkInApiController.findTicketWithUUID(event.getId(), ticketIdentifier, ticketCode);
        Assert.assertEquals(CheckInStatus.OK_READY_TO_BE_CHECKED_IN, ticketAndCheckInResult.getResult().getStatus());
        CheckInApiController.TicketCode tc = new CheckInApiController.TicketCode();
        tc.setCode(ticketCode);
        Assert.assertEquals(CheckInStatus.SUCCESS, checkInApiController.checkIn(event.getId(), ticketIdentifier, tc).getResult().getStatus());


        TicketAndCheckInResult ticketAndCheckInResultOk = checkInApiController.findTicketWithUUID(event.getId(), ticketIdentifier, ticketCode);
        Assert.assertEquals(CheckInStatus.ALREADY_CHECK_IN, ticketAndCheckInResultOk.getResult().getStatus());
        
        //
        
        eventManager.deleteEvent(event.getId(), principal.getName());

    }

    private void checkCalendar(String eventName) throws IOException {
        MockHttpServletResponse resIcal = new MockHttpServletResponse();
        eventController.calendar(eventName, "en", null, resIcal);
        Assert.assertEquals("text/calendar", resIcal.getContentType());

        MockHttpServletResponse resGoogleCal = new MockHttpServletResponse();
        eventController.calendar(eventName, "en", "google", resGoogleCal);
        Assert.assertTrue(resGoogleCal.getRedirectedUrl().startsWith("https://www.google.com/calendar/event"));
    }

    private TicketDecorator checkReservationComplete(String eventName, String reservationIdentifier) {
        Model confirmationPageModel = new BindingAwareModelMap();
        String confirmationPageSuccess = reservationController.showConfirmationPage(eventName, reservationIdentifier, false, false, confirmationPageModel, Locale.ENGLISH, new MockHttpServletRequest());
        Assert.assertEquals("/event/reservation-page-complete", confirmationPageSuccess);
        List<Pair<?, List<TicketDecorator>>> tickets = (List<Pair<?, List<TicketDecorator>>>) confirmationPageModel.asMap().get("ticketsByCategory");
        Assert.assertEquals(1, tickets.size());
        Assert.assertEquals(1, tickets.get(0).getRight().size());
        return tickets.get(0).getRight().get(0);
    }

    private void assignTicket(String eventName, String reservationIdentifier, String ticketIdentifier, String fullName) throws Exception {
        UpdateTicketOwnerForm ticketOwnerForm = new UpdateTicketOwnerForm();
        ticketOwnerForm.setFullName(fullName);
        ticketOwnerForm.setEmail("testmctest@test.com");
        ticketOwnerForm.setUserLanguage("en");
        Assert.assertTrue(reservationController.assignTicketToPerson(eventName, reservationIdentifier, ticketIdentifier, ticketOwnerForm, Mockito.mock(BindingResult.class), new MockHttpServletRequest(), new BindingAwareModelMap()).endsWith("/success"));
    }

    private void checkCSV(String eventName, String ticketIdentifier, String fullName) throws IOException {
        //FIXME get all fields :D and put it in the request...
        Principal principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(user);
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> fields = eventApiController.getAllFields(eventName);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("fields", fields.toArray(new String[]{}));
        eventApiController.downloadAllTicketsCSV(eventName, request, response, principal);
        CSVReader csvReader = new CSVReader(new StringReader(response.getContentAsString()));
        List<String[]> csv = csvReader.readAll();
        Assert.assertEquals(2, csv.size());
        Assert.assertEquals(ticketIdentifier, csv.get(1)[0]);
        Assert.assertEquals("default", csv.get(1)[2]);
        Assert.assertEquals("ACQUIRED", csv.get(1)[4]);
        Assert.assertEquals(fullName, csv.get(1)[8]);
    }

    private void validatePayment(String eventName, String reservationIdentifier) {
        Principal principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(user);
        Assert.assertEquals(1, eventApiController.getPendingPayments(eventName, principal).size());
        Assert.assertEquals("OK", eventApiController.confirmPayment(eventName, reservationIdentifier, principal, new BindingAwareModelMap(), new MockHttpServletRequest()));
        Assert.assertEquals(0, eventApiController.getPendingPayments(eventName, principal).size());
    }

    private String payOffline(String eventName, String reservationIdentifier) {
        PaymentForm paymentForm = new PaymentForm();
        paymentForm.setPaymentMethod(PaymentProxy.OFFLINE);
        paymentForm.setEmail("test@test.com");
        paymentForm.setBillingAddress("my billing address");
        paymentForm.setFullName("full name");
        paymentForm.setTermAndConditionsAccepted(true);
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
        ticketReservation.setTicketCategoryId(eventStatisticsManager.loadTicketCategories(event).stream().findFirst().map(TicketCategory::getId).orElseThrow(IllegalStateException::new));
        reservationForm.setReservation(Collections.singletonList(ticketReservation));


        return eventController.reserveTicket(eventName, reservationForm, bindingResult, model, servletWebRequest, redirectAttributes, Locale.ENGLISH);
    }

}
