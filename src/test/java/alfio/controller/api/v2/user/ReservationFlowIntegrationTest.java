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
package alfio.controller.api.v2.user;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.v2.InfoApiController;
import alfio.controller.api.v2.TranslationsApiController;
import alfio.controller.api.v2.model.EventCode;
import alfio.controller.api.v2.model.ItemsByCategory;
import alfio.controller.api.v2.model.Language;
import alfio.controller.form.ContactAndTicketsForm;
import alfio.controller.form.PaymentForm;
import alfio.controller.form.ReservationForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.manager.EventManager;
import alfio.manager.EventStatisticsManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.support.BindingAwareModelMap;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, alfio.controller.ReservationFlowIntegrationTest.ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class ReservationFlowIntegrationTest extends BaseIntegrationTest {

    @Configuration
    @ComponentScan(basePackages = {"alfio.controller"})
    public static class ControllerConfiguration {

    }

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserManager userManager;

    @Autowired
    private EventStatisticsManager eventStatisticsManager;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    //
    @Autowired
    private InfoApiController infoApiController;

    @Autowired
    private TranslationsApiController translationsApiController;

    @Autowired
    private EventApiV2Controller eventApiV2Controller;

    @Autowired
    private ReservationApiV2Controller reservationApiV2Controller;

    @Autowired
    private TicketApiV2Controller ticketApiV2Controller;
    //

    private Event event;
    private String user;


    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");
    private static final String PROMO_CODE = "MYPROMOCODE";

    private static final String HIDDEN_CODE = "HIDDENNN";

    private int hiddenCategoryId = Integer.MIN_VALUE;

    public void ensureConfiguration() {

        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null),
            new TicketCategoryModification(null, "hidden", 2,
                new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                DESCRIPTION, BigDecimal.ONE, true, "", true, null, null, null, null, null)
            );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        event = eventAndUser.getKey();
        user = eventAndUser.getValue() + "_owner";
        //promo code at event level
        eventManager.addPromoCode(PROMO_CODE, event.getId(), null, ZonedDateTime.now().minusDays(2), event.getEnd().plusDays(2), 10, PromoCodeDiscount.DiscountType.PERCENTAGE, null, 3, "description", "test@test.ch", PromoCodeDiscount.CodeType.DISCOUNT, null);

        hiddenCategoryId = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(t -> t.isAccessRestricted()).collect(Collectors.toList()).get(0).getId();

        eventManager.addPromoCode(HIDDEN_CODE, event.getId(), null, ZonedDateTime.now().minusDays(2), event.getEnd().plusDays(2), 0, PromoCodeDiscount.DiscountType.NONE, null, null, "hidden", "test@test.ch", PromoCodeDiscount.CodeType.ACCESS, hiddenCategoryId);
    }

    @Test
    public void reservationFlowTest() throws Exception {

        assertTrue(eventApiV2Controller.listEvents().getBody().isEmpty());
        ensureConfiguration();


        //
        assertEquals(3, translationsApiController.getSupportedLanguages().size());
        assertEquals("or", translationsApiController.getPublicTranslations("en").get("common.or"));
        assertEquals("o", translationsApiController.getPublicTranslations("it").get("common.or"));
        assertEquals("oder", translationsApiController.getPublicTranslations("de").get("common.or"));

        var alfioInfo = infoApiController.getInfo();
        assertEquals(false, alfioInfo.isDemoModeEnabled());
        assertEquals(true, alfioInfo.isDevModeEnabled());
        assertEquals(false, alfioInfo.isProdModeEnabled());

        //

        assertEquals("Switzerland", translationsApiController.getCountries("en").stream().filter( c-> "CH".equals(c.getIsoCode())).findFirst().get().getName());

        assertEquals("Greece", translationsApiController.getCountries("en").stream().filter(c->"GR".equals(c.getIsoCode())).findFirst().get().getName());

        assertEquals("Suisse", translationsApiController.getCountries("fr").stream().filter( c-> "CH".equals(c.getIsoCode())).findFirst().get().getName());
        assertEquals("Svizzera", translationsApiController.getCountries("it").stream().filter( c-> "CH".equals(c.getIsoCode())).findFirst().get().getName());
        assertEquals("Schweiz", translationsApiController.getCountries("de").stream().filter( c-> "CH".equals(c.getIsoCode())).findFirst().get().getName());

        //EL -> greece for vat
        assertEquals("Greece", translationsApiController.getCountriesForVat("en").stream().filter(c->"EL".equals(c.getIsoCode())).findFirst().get().getName());
        assertEquals(28, translationsApiController.getEuCountriesForVat("en").size()); //
        //


        assertTrue(eventApiV2Controller.listEvents().getBody().isEmpty());


        //
        List<EventStatistic> eventStatistic = eventStatisticsManager.getAllEventsWithStatistics(user);
        assertEquals(1, eventStatistic.size());
        assertTrue(eventStatisticsManager.getTicketSoldStatistics(event.getId(), new Date(0), DateUtils.addDays(new Date(), 1)).isEmpty());
        EventWithAdditionalInfo eventWithAdditionalInfo = eventStatisticsManager.getEventWithAdditionalInfo(event.getShortName(), user);
        assertEquals(2, eventWithAdditionalInfo.getNotSoldTickets()); // <- 2 tickets are the bounded category
        assertEquals(0, eventWithAdditionalInfo.getSoldTickets());
        assertEquals(20, eventWithAdditionalInfo.getAvailableSeats());
        //



        //publish the event
        eventManager.toggleActiveFlag(event.getId(), user, true);
        //

        var resListEvents = eventApiV2Controller.listEvents();
        var events = eventApiV2Controller.listEvents().getBody();

        assertEquals(HttpStatus.OK, resListEvents.getStatusCode());
        assertEquals(1, events.size());
        assertEquals(event.getShortName(), events.get(0).getShortName());

        //
        assertEquals(HttpStatus.NOT_FOUND, eventApiV2Controller.getEvent("NOT_EXISTS").getStatusCode());
        //

        var eventRes = eventApiV2Controller.getEvent(event.getShortName());
        assertEquals(HttpStatus.OK, eventRes.getStatusCode());
        var selectedEvent = eventRes.getBody();
        assertEquals("CHF", selectedEvent.getCurrency());

        checkCalendar(event.getShortName());

        //it, en, de
        assertEquals(3, selectedEvent.getContentLanguages().size());
        assertEquals(new HashSet<>(Arrays.asList("it", "en", "de")), new HashSet<>(eventApiV2Controller.getLanguages(event.getShortName()).getBody()));
        assertEquals(selectedEvent.getContentLanguages().stream().map(Language::getLocale).collect(Collectors.toSet()), new HashSet<>(eventApiV2Controller.getLanguages(event.getShortName()).getBody()));

        //check if for each language we have the expected locale dependent entries
        for (String lang: Arrays.asList("it", "en", "de")) {
            assertNotNull(selectedEvent.getDescription().get(lang));
            //
            assertNotNull(selectedEvent.getFormattedBeginDate().get(lang));
            assertNotNull(selectedEvent.getFormattedBeginTime().get(lang));
            assertNotNull(selectedEvent.getFormattedEndDate().get(lang));
            assertNotNull(selectedEvent.getFormattedEndTime().get(lang));
        }


        // check ticket & all, we have 2 ticket categories, 1 hidden
        assertEquals(HttpStatus.NOT_FOUND, eventApiV2Controller.getTicketCategories("NOT_EXISTING", null, new BindingAwareModelMap(), new MockHttpServletRequest()).getStatusCode());
        {
            var itemsRes = eventApiV2Controller.getTicketCategories(event.getShortName(), null, new BindingAwareModelMap(), new MockHttpServletRequest());
            assertEquals(HttpStatus.OK, itemsRes.getStatusCode());

            var items = itemsRes.getBody();


            assertEquals(1, items.getTicketCategories().size());
            var visibleCat = items.getTicketCategories().get(0);
            assertEquals("default", visibleCat.getName());
            assertEquals("10.00", visibleCat.getFormattedFinalPrice());
            assertFalse(visibleCat.isHasDiscount());
        }

        // hidden category check
        {

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, eventApiV2Controller.validateCode(event.getShortName(), "NOT_EXISTING").getStatusCode());

            var hiddenCodeRes = eventApiV2Controller.validateCode(event.getShortName(), HIDDEN_CODE);
            assertEquals(HttpStatus.OK, hiddenCodeRes.getStatusCode());
            var hiddenCode = hiddenCodeRes.getBody();
            assertEquals(EventCode.EventCodeType.ACCESS, hiddenCode.getValue().getType());

            var itemsRes2 = eventApiV2Controller.getTicketCategories(event.getShortName(), HIDDEN_CODE, new BindingAwareModelMap(), new MockHttpServletRequest());
            var items2 = itemsRes2.getBody();
            assertEquals(2, items2.getTicketCategories().size());

            var hiddenCat = items2.getTicketCategories().stream().filter(t -> t.isAccessRestricted()).findFirst().get();
            assertEquals(hiddenCategoryId, hiddenCat.getId());
            assertEquals("hidden", hiddenCat.getName());
            assertEquals("1.00", hiddenCat.getFormattedFinalPrice());
            assertFalse(hiddenCat.isHasDiscount());
            assertTrue(hiddenCat.isAccessRestricted());
        }
        //


        // discount check
        {
            var discountCodeRes = eventApiV2Controller.validateCode(event.getShortName(), PROMO_CODE);
            var discountCode = discountCodeRes.getBody();
            assertEquals(EventCode.EventCodeType.DISCOUNT, discountCode.getValue().getType());
            var itemsRes3 = eventApiV2Controller.getTicketCategories(event.getShortName(), PROMO_CODE, new BindingAwareModelMap(), new MockHttpServletRequest());

            var items3 = itemsRes3.getBody();


            assertEquals(1, items3.getTicketCategories().size());
            var visibleCat = items3.getTicketCategories().get(0);
            assertEquals("default", visibleCat.getName());
            assertEquals("10.00", visibleCat.getFormattedFinalPrice());
            assertTrue(visibleCat.isHasDiscount());
            assertEquals("9.00", visibleCat.getFormattedDiscountedPrice());
        }


        //validation error: select at least one
        {
            var form = new ReservationForm();
            var res = eventApiV2Controller.reserveTicket(event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), new RedirectAttributesModelMap());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, res.getStatusCode());
            var resBody = res.getBody();
            assertFalse(resBody.isSuccess());
            assertEquals(1, resBody.getErrorCount());
        }


        //buy one ticket, without discount
        {
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            ticketReservation.setAmount(1);
            ticketReservation.setTicketCategoryId(eventApiV2Controller.getTicketCategories(event.getShortName(), null, new BindingAwareModelMap(), new MockHttpServletRequest()).getBody().getTicketCategories().get(0).getId());
            form.setReservation(Collections.singletonList(ticketReservation));
            var res = eventApiV2Controller.reserveTicket(event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), new RedirectAttributesModelMap());
            assertEquals(HttpStatus.OK, res.getStatusCode());
            var resBody = res.getBody();
            assertTrue(resBody.isSuccess());
            assertEquals(0, resBody.getErrorCount());
            var reservationId = resBody.getValue();

            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING);


            var resInfoRes = reservationApiV2Controller.getReservationInfo(event.getShortName(), reservationId, new MockHttpSession());
            assertEquals(HttpStatus.OK, resInfoRes.getStatusCode());
            var reservation = resInfoRes.getBody();
            assertEquals(reservationId, reservation.getId());
            assertEquals(1, reservation.getTicketsByCategory().size());
            assertEquals(1, reservation.getTicketsByCategory().get(0).getTickets().size());

            var contactForm = new ContactAndTicketsForm();
            var validationErrorsRes = reservationApiV2Controller.validateToOverview(event.getShortName(), reservationId, "en", contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), new MockHttpServletRequest(), new RedirectAttributesModelMap());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, validationErrorsRes.getStatusCode());
            assertFalse(validationErrorsRes.getBody().isSuccess());
            assertEquals(4, validationErrorsRes.getBody().getErrorCount()); // first name, last name, email + MISSING_ATTENDEE DATA


            // move to overview status
            contactForm = new ContactAndTicketsForm();
            contactForm.setEmail("test@test.com");
            contactForm.setBillingAddress("my billing address");
            contactForm.setFirstName("full");
            contactForm.setLastName("name");

            var ticketForm = new UpdateTicketOwnerForm();
            ticketForm.setFirstName("full");
            ticketForm.setLastName("name");
            ticketForm.setEmail("test@test.com");
            contactForm.setTickets(Collections.singletonMap(reservation.getTicketsByCategory().get(0).getTickets().get(0).getUuid(), ticketForm));

            var overviewRes = reservationApiV2Controller.validateToOverview(event.getShortName(), reservationId, "en", contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), new MockHttpServletRequest(), new RedirectAttributesModelMap());
            assertEquals(HttpStatus.OK, overviewRes.getStatusCode());
            checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.PENDING);

            reservationApiV2Controller.backToBook(event.getShortName(), reservationId);

            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING);

            overviewRes = reservationApiV2Controller.validateToOverview(event.getShortName(), reservationId, "en", contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), new MockHttpServletRequest(), new RedirectAttributesModelMap());
            assertTrue(overviewRes.getBody().getValue());

            checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.PENDING);

            var paymentForm = new PaymentForm();
            var handleResError = reservationApiV2Controller.handleReservation(event.getShortName(), reservationId, "en", paymentForm, new BeanPropertyBindingResult(paymentForm, "paymentForm"),
                new BindingAwareModelMap(), new MockHttpServletRequest(), new RedirectAttributesModelMap(), new MockHttpSession());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, handleResError.getStatusCode());


            paymentForm.setPrivacyPolicyAccepted(true);
            paymentForm.setTermAndConditionsAccepted(true);
            paymentForm.setPaymentMethod(PaymentProxy.OFFLINE);
            var handleRes = reservationApiV2Controller.handleReservation(event.getShortName(), reservationId, "en", paymentForm, new BeanPropertyBindingResult(paymentForm, "paymentForm"),
                new BindingAwareModelMap(), new MockHttpServletRequest(), new RedirectAttributesModelMap(), new MockHttpSession());
            assertEquals(HttpStatus.OK, handleRes.getStatusCode());

            checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT);
        }

    }

    private void checkStatus(String reservationId, HttpStatus expectedHttpStatus, boolean validated, TicketReservation.TicketReservationStatus reservationStatus) {
        var statusRes = reservationApiV2Controller.getReservationStatus(event.getShortName(), reservationId);
        assertEquals(expectedHttpStatus, statusRes.getStatusCode());
        var status = statusRes.getBody();
        assertEquals(validated, status.isValidatedBookingInformations());
        assertEquals(reservationStatus, status.getStatus());
    }

    private void checkCalendar(String eventName) throws IOException {
        MockHttpServletResponse resIcal = new MockHttpServletResponse();
        eventApiV2Controller.getCalendar(eventName, "en", null, null, resIcal);
        assertEquals("text/calendar", resIcal.getContentType());

        MockHttpServletResponse resGoogleCal = new MockHttpServletResponse();
        eventApiV2Controller.getCalendar(eventName, "en", "google", null, resGoogleCal);
        assertTrue(resGoogleCal.getRedirectedUrl().startsWith("https://www.google.com/calendar/event"));
    }
}
