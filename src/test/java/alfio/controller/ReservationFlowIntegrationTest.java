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
import alfio.controller.form.PaymentForm;
import alfio.controller.form.ReservationForm;
import alfio.manager.EventManager;
import alfio.manager.EventStatisticsManager;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static alfio.test.util.IntegrationTestUtil.initSystemProperties;

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



    private Event event;

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
        event = initEvent(categories, organizationRepository, userManager, eventManager).getKey();
    }


    @Test
    public void reservationFlowTest() {

        String eventName = event.getShortName();

        String redirectResult = reserveTicket(eventName);
        String redirectStart = "redirect:/event/" + eventName + "/reservation/";
        // check reservation success
        Assert.assertTrue(redirectResult.startsWith(redirectStart));
        Assert.assertTrue(redirectResult.endsWith("/book"));
        //


        String reservationIdentifier = redirectResult.substring(redirectStart.length()).replace("/book", "");


        // check that the payment page is shown
        String reservationPage = reservationController.showPaymentPage(eventName, reservationIdentifier, new BindingAwareModelMap(), new MockHttpServletRequest());
        Assert.assertEquals("/event/reservation-page", reservationPage);
        //

        // pay offline
        String successPage = payOffline(eventName, reservationIdentifier);
        Assert.assertEquals("redirect:/event/" + eventName + "/reservation/" + reservationIdentifier + "/success", successPage);
        //
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
