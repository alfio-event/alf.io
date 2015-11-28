package alfio.controller;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.config.MvcConfiguration;
import alfio.controller.form.ReservationForm;
import alfio.manager.EventManager;
import alfio.manager.EventStatisticsManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventWithStatistics;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
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
import org.springframework.validation.BindingResult;
import org.springframework.validation.support.BindingAwareModelMap;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        event = initEvent(categories, organizationRepository, userManager, eventManager).getKey();
    }


    @Test
    public void reservationFlowTest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        ServletWebRequest servletWebRequest = new ServletWebRequest(request);
        BindingResult bindingResult = Mockito.mock(BindingResult.class);
        Model model = new BindingAwareModelMap();
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();



        TicketReservationModification ticketReservation = new TicketReservationModification();

        ticketReservation.setAmount(1);
        ticketReservation.setTicketCategoryId(eventStatisticsManager.loadTicketCategories(event).stream().findFirst().map(TicketCategory::getId).orElseThrow(IllegalStateException::new));

        ReservationForm reservationForm = new ReservationForm();
        reservationForm.setReservation(Collections.singletonList(ticketReservation));

        String eventName = event.getShortName();

        String redirectResult = eventController.reserveTicket(eventName, reservationForm, bindingResult, model, servletWebRequest, redirectAttributes, Locale.ENGLISH);


        // check reservation success
        Assert.assertTrue(redirectResult.startsWith("redirect:/event/" + eventName + "/reservation/"));
        Assert.assertTrue(redirectResult.endsWith("/book"));
        //


    }

}
