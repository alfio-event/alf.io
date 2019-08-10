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
import alfio.controller.IndexController;
import alfio.controller.api.v1.AttendeeApiController;
import alfio.controller.api.admin.AdditionalServiceApiController;
import alfio.controller.api.admin.CheckInApiController;
import alfio.controller.api.admin.EventApiController;
import alfio.controller.api.admin.UsersApiController;
import alfio.controller.api.v2.InfoApiController;
import alfio.controller.api.v2.TranslationsApiController;
import alfio.controller.api.v2.model.EventCode;
import alfio.controller.api.v2.model.Language;
import alfio.controller.form.*;
import alfio.manager.*;
import alfio.manager.support.CheckInStatus;
import alfio.manager.support.TicketAndCheckInResult;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.audit.ScanAudit;
import alfio.model.modification.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.User;
import alfio.repository.*;
import alfio.repository.audit.ScanAuditRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.EventUtil;
import alfio.util.Json;
import ch.digitalfondue.jfiveparse.Element;
import ch.digitalfondue.jfiveparse.Parser;
import ch.digitalfondue.jfiveparse.Selector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.opencsv.CSVReader;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.context.request.ServletWebRequest;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ReservationFlowIntegrationTest.ControllerConfiguration.class})
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

    @Autowired
    private TicketReservationRepository ticketReservationRepository;

    @Autowired
    private EventApiController eventApiController;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketFieldRepository ticketFieldRepository;

    @Autowired
    private AdditionalServiceApiController additionalServiceApiController;

    @Autowired
    private SpecialPriceTokenGenerator specialPriceTokenGenerator;

    @Autowired
    private SpecialPriceRepository specialPriceRepository;

    //
    @Autowired
    private CheckInApiController checkInApiController;

    @Autowired
    private AttendeeApiController attendeeApiController;

    @Autowired
    private UsersApiController usersApiController;

    @Autowired
    private ScanAuditRepository scanAuditRepository;

    @Autowired
    private AdminReservationManager adminReservationManager;

    @Autowired
    private TicketReservationManager ticketReservationManager;

    @Autowired
    private WaitingQueueSubscriptionProcessor waitingQueueSubscriptionProcessor;
    //

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

    @Autowired
    private IndexController indexController;
    //

    private Event event;
    private String user;

    private Integer additionalServiceId;


    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");
    private static final String PROMO_CODE = "MYPROMOCODE";

    private static final String HIDDEN_CODE = "HIDDENNN";

    private static final String URL_CODE_HIDDEN = "CODE_CODE_CODE";

    private int hiddenCategoryId = Integer.MIN_VALUE;

    public void ensureConfiguration() {

        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0),
            new TicketCategoryModification(null, "hidden", 2,
                new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                DESCRIPTION, BigDecimal.ONE, true, "", true, URL_CODE_HIDDEN, null, null, null, null, 0)
            );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        event = eventAndUser.getKey();
        user = eventAndUser.getValue() + "_owner";
        //promo code at event level
        eventManager.addPromoCode(PROMO_CODE, event.getId(), null, ZonedDateTime.now().minusDays(2), event.getEnd().plusDays(2), 10, PromoCodeDiscount.DiscountType.PERCENTAGE, null, 3, "description", "test@test.ch", PromoCodeDiscount.CodeType.DISCOUNT, null);

        hiddenCategoryId = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(TicketCategory::isAccessRestricted).collect(Collectors.toList()).get(0).getId();

        eventManager.addPromoCode(HIDDEN_CODE, event.getId(), null, ZonedDateTime.now().minusDays(2), event.getEnd().plusDays(2), 0, PromoCodeDiscount.DiscountType.NONE, null, null, "hidden", "test@test.ch", PromoCodeDiscount.CodeType.ACCESS, hiddenCategoryId);


        // add additional fields before and after, with one mandatory
        var af = new EventModification.AdditionalField(-1, "field1", "text", true, null, null, null,
            Map.of("en", new EventModification.Description("field en", "", null)), null, null);
        eventManager.addAdditionalField(event, af);

        var afId = ticketFieldRepository.findAdditionalFieldsForEvent(event.getId()).get(0).getId();

        ticketFieldRepository.updateFieldOrder(afId, -1);

        var af2 = new EventModification.AdditionalField(1, "field2", "text", false, null, null, null,
            Map.of("en", new EventModification.Description("field2 en", "", null)), null, null);
        eventManager.addAdditionalField(event, af2);
        //


        // add additional service
        var addServ = new EventModification.AdditionalService(null, new BigDecimal("40.00"), true, 0, 1, 1,

            new DateTimeModification(ZonedDateTime.now().minusDays(2).toLocalDate(), ZonedDateTime.now().minusDays(2).toLocalTime()),
            new DateTimeModification(event.getEnd().plusDays(2).toLocalDate(), event.getEnd().plusDays(2).toLocalTime()),
            event.getVat(), AdditionalService.VatType.INHERITED,
            null,
            Collections.singletonList(new EventModification.AdditionalServiceText(null, "en", "additional title", AdditionalServiceText.TextType.TITLE)),
            Collections.singletonList(new EventModification.AdditionalServiceText(null, "en", "additional desc", AdditionalServiceText.TextType.DESCRIPTION)),

            AdditionalService.AdditionalServiceType.SUPPLEMENT,
            AdditionalService.SupplementPolicy.OPTIONAL_MAX_AMOUNT_PER_TICKET
        );
        var addServRes = additionalServiceApiController.insert(event.getId(), addServ, new BeanPropertyBindingResult(addServ, "additionalService"));
        additionalServiceId = addServRes.getBody().getId();
        //

        var af3 = new EventModification.AdditionalField(2, "field3", "text", true, null, null, null,
            Map.of("en", new EventModification.Description("field3 en", "", null)), addServRes.getBody(), null);
        eventManager.addAdditionalField(event, af3);


        // enable reservation list and pre sales
        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), ConfigurationKeys.ENABLE_WAITING_QUEUE.getValue(), "true", "");
        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), ConfigurationKeys.ENABLE_PRE_REGISTRATION.getValue(), "true", "");
        //

        specialPriceTokenGenerator.generatePendingCodes();
    }

    @Test
    public void reservationFlowTest() throws Exception {


        assertTrue(eventApiV2Controller.listEvents().getBody().isEmpty());
        ensureConfiguration();


        //
        assertEquals(3, translationsApiController.getSupportedLanguages().size());
        assertEquals("or", translationsApiController.getPublicTranslations("en", true).get("common.or"));
        assertEquals("o", translationsApiController.getPublicTranslations("it", true).get("common.or"));
        assertEquals("oder", translationsApiController.getPublicTranslations("de", true).get("common.or"));

        var alfioInfo = infoApiController.getInfo(new MockHttpSession());
        assertEquals(false, alfioInfo.isDemoModeEnabled());
        assertEquals(true, alfioInfo.isDevModeEnabled());
        assertEquals(false, alfioInfo.isProdModeEnabled());
        assertEquals(true, alfioInfo.getAnalyticsConfiguration().isGoogleAnalyticsScrambledInfo());
        assertEquals(null, alfioInfo.getAnalyticsConfiguration().getGoogleAnalyticsKey());
        assertEquals(null, alfioInfo.getAnalyticsConfiguration().getClientId());

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
        assertEquals(HttpStatus.NOT_FOUND, eventApiV2Controller.getEvent("NOT_EXISTS", new MockHttpSession()).getStatusCode());
        //

        var eventRes = eventApiV2Controller.getEvent(event.getShortName(), new MockHttpSession());
        assertEquals(HttpStatus.OK, eventRes.getStatusCode());
        var selectedEvent = eventRes.getBody();
        assertEquals("CHF", selectedEvent.getCurrency());
        assertFalse(selectedEvent.isFree());
        assertEquals(event.getSameDay(), selectedEvent.isSameDay());
        assertTrue(selectedEvent.isVatIncluded());
        assertEquals(event.getShortName(), selectedEvent.getShortName());
        assertEquals(event.getDisplayName(), selectedEvent.getDisplayName());
        assertEquals(event.getFileBlobId(), selectedEvent.getFileBlobId());
        assertEquals(1, selectedEvent.getActivePaymentMethods().size());
        assertTrue(selectedEvent.getActivePaymentMethods().containsKey(PaymentMethod.BANK_TRANSFER));
        assertTrue(selectedEvent.getI18nOverride().isEmpty());

        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(),"TRANSLATION_OVERRIDE", Json.toJson(Map.of("en", Map.of("common.vat", "EVENT.vat"))), "");
        eventRes = eventApiV2Controller.getEvent(event.getShortName(), new MockHttpSession());
        selectedEvent = eventRes.getBody();
        assertFalse(selectedEvent.getI18nOverride().isEmpty());
        assertEquals("EVENT.vat", selectedEvent.getI18nOverride().get("en").get("common.vat"));

        checkCalendar(event.getShortName());

        //it, en, de
        assertEquals(3, selectedEvent.getContentLanguages().size());

        assertEquals(selectedEvent.getContentLanguages().stream().map(Language::getLocale).collect(Collectors.toSet()), Set.of("it", "en", "de"));

        //check if for each language we have the expected locale dependent entries
        for (String lang: Arrays.asList("it", "en", "de")) {
            assertNotNull(selectedEvent.getDescription().get(lang));
            //
            assertNotNull(selectedEvent.getFormattedBeginDate().get(lang));
            assertNotNull(selectedEvent.getFormattedBeginTime().get(lang));
            assertNotNull(selectedEvent.getFormattedEndDate().get(lang));
            assertNotNull(selectedEvent.getFormattedEndTime().get(lang));
        }


        assertEquals("redirect:/api/v2/public/event/" + event.getShortName() + "/code/MY_CODE", indexController.redirectCode(event.getShortName(), "MY_CODE"));


        // check open graph & co
        {
            var res = new MockHttpServletResponse();
            indexController.replyToIndex(event.getShortName(), "not a social share", "en", new ServletWebRequest(new MockHttpServletRequest()), res);
            var htmlParser = new Parser();
            var docWithoutOpenGraph = htmlParser.parse(new String(res.getContentAsByteArray(), StandardCharsets.UTF_8));
            assertTrue(docWithoutOpenGraph.getAllNodesMatching(Selector.select().element("meta").attrValEq("name", "twitter:card").toMatcher()).isEmpty());

            res = new MockHttpServletResponse();
            indexController.replyToIndex(event.getShortName(), "Twitterbot/42", "en", new ServletWebRequest(new MockHttpServletRequest()), res);
            var docWithOpenGraph = htmlParser.parse(new String(res.getContentAsByteArray(), StandardCharsets.UTF_8));
            assertFalse(docWithOpenGraph.getAllNodesMatching(Selector.select().element("meta").attrValEq("name", "twitter:card").toMatcher()).isEmpty());

            var title = (Element) docWithOpenGraph.getAllNodesMatching(Selector.select().element("meta").attrValEq("property", "og:title").toMatcher(), true).get(0);
            assertEquals("Get your tickets for "+event.getDisplayName(), title.getAttribute("content"));
        }

        //


        // check ticket & all, we have 2 ticket categories, 1 hidden
        assertEquals(HttpStatus.NOT_FOUND, eventApiV2Controller.getTicketCategories("NOT_EXISTING", null).getStatusCode());
        {
            var itemsRes = eventApiV2Controller.getTicketCategories(event.getShortName(), null);
            assertEquals(HttpStatus.OK, itemsRes.getStatusCode());

            var items = itemsRes.getBody();


            assertEquals(1, items.getTicketCategories().size());
            var visibleCat = items.getTicketCategories().get(0);
            assertEquals("default", visibleCat.getName());
            assertEquals("10.00", visibleCat.getFormattedFinalPrice());
            assertFalse(visibleCat.isHasDiscount());

            assertEquals(1, items.getAdditionalServices().size());
            var additionalItem = items.getAdditionalServices().get(0);
            assertEquals("40.00", additionalItem.getFormattedFinalPrice());
            assertEquals("1.00", additionalItem.getVatPercentage());
            assertEquals(1, additionalItem.getTitle().size()); //TODO: check: if there are missing lang, we should at least copy them (?)
            assertEquals(1, additionalItem.getDescription().size());
            assertEquals("additional title", additionalItem.getTitle().get("en"));
            assertEquals("<p>additional desc</p>\n", additionalItem.getDescription().get("en"));

            // check presence of reservation list
            assertFalse(items.isWaitingList());
            assertFalse(items.isPreSales());
            //

            // fix dates to enable reservation list
            var tc = ticketCategoryRepository.getById(visibleCat.getId());
            ticketCategoryRepository.fixDates(visibleCat.getId(), tc.getInception(event.getZoneId()).plusDays(2), tc.getExpiration(event.getZoneId()));
            //
            items = eventApiV2Controller.getTicketCategories(event.getShortName(), null).getBody();
            assertTrue(items.isWaitingList());
            assertTrue(items.isPreSales());
            //

            var subForm = new WaitingQueueSubscriptionForm();
            subForm.setFirstName("first");
            subForm.setLastName("last");
            subForm.setPrivacyPolicyAccepted(true);
            subForm.setTermAndConditionsAccepted(true);
            subForm.setUserLanguage(Locale.ENGLISH);
            var subRes = eventApiV2Controller.subscribeToWaitingList(event.getShortName(), subForm, new BeanPropertyBindingResult(subForm, "subForm"));
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, subRes.getStatusCode());
            assertFalse(subRes.getBody().isSuccess());
            assertEquals(1, subRes.getBody().getValidationErrors().size());
            assertEquals("email", subRes.getBody().getValidationErrors().get(0).getFieldName());
            assertEquals("error.email", subRes.getBody().getValidationErrors().get(0).getCode());
            //

            subForm.setEmail("email@email.com");
            subRes = eventApiV2Controller.subscribeToWaitingList(event.getShortName(), subForm, new BeanPropertyBindingResult(subForm, "subForm"));
            assertEquals(HttpStatus.OK, subRes.getStatusCode());
            assertTrue(subRes.getBody().isSuccess());
            assertEquals(0, subRes.getBody().getErrorCount());
            assertTrue(subRes.getBody().getValue());

            //
            ticketCategoryRepository.fixDates(visibleCat.getId(), tc.getInception(event.getZoneId()).minusDays(2), tc.getExpiration(event.getZoneId()));
        }

        // hidden category check
        {

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, eventApiV2Controller.validateCode(event.getShortName(), "NOT_EXISTING").getStatusCode());

            var hiddenCodeRes = eventApiV2Controller.validateCode(event.getShortName(), HIDDEN_CODE);
            assertEquals(HttpStatus.OK, hiddenCodeRes.getStatusCode());
            var hiddenCode = hiddenCodeRes.getBody();
            assertEquals(EventCode.EventCodeType.ACCESS, hiddenCode.getValue().getType());

            var itemsRes2 = eventApiV2Controller.getTicketCategories(event.getShortName(), HIDDEN_CODE);
            var items2 = itemsRes2.getBody();
            assertEquals(2, items2.getTicketCategories().size());

            var hiddenCat = items2.getTicketCategories().stream().filter(t -> t.isAccessRestricted()).findFirst().get();
            assertEquals(hiddenCategoryId, hiddenCat.getId());
            assertEquals("hidden", hiddenCat.getName());
            assertEquals("1.00", hiddenCat.getFormattedFinalPrice());
            assertFalse(hiddenCat.isHasDiscount());
            assertTrue(hiddenCat.isAccessRestricted());

            // do a reservation for a hidden category+cancel
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            form.setPromoCode(HIDDEN_CODE);
            ticketReservation.setAmount(1);
            ticketReservation.setTicketCategoryId(hiddenCat.getId());
            form.setReservation(Collections.singletonList(ticketReservation));
            var res = eventApiV2Controller.reserveTickets(event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
            assertEquals(HttpStatus.OK, res.getStatusCode());
            var reservationInfo = reservationApiV2Controller.getReservationInfo(event.getShortName(), res.getBody().getValue());
            assertEquals(HttpStatus.OK, reservationInfo.getStatusCode());
            assertEquals("1.00", reservationInfo.getBody().getOrderSummary().getTotalPrice());
            assertEquals("hidden", reservationInfo.getBody().getOrderSummary().getSummary().get(0).getName());
            reservationApiV2Controller.cancelPendingReservation(event.getShortName(), res.getBody().getValue());

            // this is run by a job, but given the fact that it's in another separate transaction, it cannot work in this test (WaitingQueueSubscriptionProcessor.handleWaitingTickets)
            assertEquals(1, ticketReservationManager.revertTicketsToFreeIfAccessRestricted(event.getId()));
        }
        //

        // check reservation auto creation with code: TODO: will need to check all the flows
        {

            // code not found
            var notFoundRes = eventApiV2Controller.handleCode(event.getShortName(), "NOT_EXIST", new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
            assertEquals("/event/" + event.getShortName(), notFoundRes.getHeaders().getLocation().getPath());
            assertEquals("errors=error.STEP_1_CODE_NOT_FOUND", notFoundRes.getHeaders().getLocation().getQuery());
            //

            // promo code, we expect a redirect to event with the code in the query string
            var redirectPromoCodeRes = eventApiV2Controller.handleCode(event.getShortName(), PROMO_CODE, new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
            assertEquals("/event/" + event.getShortName(), redirectPromoCodeRes.getHeaders().getLocation().getPath());
            assertEquals("code=MYPROMOCODE", redirectPromoCodeRes.getHeaders().getLocation().getQuery());


            // code existing
            assertEquals(2, specialPriceRepository.findActiveNotAssignedByCategoryId(hiddenCategoryId).size());
            var res = eventApiV2Controller.handleCode(event.getShortName(), URL_CODE_HIDDEN, new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
            var reservationId = res.getHeaders().getLocation().toString().substring(("/event/" + event.getShortName() + "/reservation/").length());
            var reservationInfo = reservationApiV2Controller.getReservationInfo(event.getShortName(), reservationId);
            assertEquals(HttpStatus.OK, reservationInfo.getStatusCode());
            assertEquals(reservationId, reservationInfo.getBody().getId());

            assertEquals(1, specialPriceRepository.findActiveNotAssignedByCategoryId(hiddenCategoryId).size());

            reservationApiV2Controller.cancelPendingReservation(event.getShortName(), reservationId);

            assertEquals(2, specialPriceRepository.findActiveNotAssignedByCategoryId(hiddenCategoryId).size());

            // this is run by a job, but given the fact that it's in another separate transaction, it cannot work in this test (WaitingQueueSubscriptionProcessor.handleWaitingTickets)
            assertEquals(1, ticketReservationManager.revertTicketsToFreeIfAccessRestricted(event.getId()));
        }

        // check reservation auto creation with deletion from the admin side
        {

            assertEquals(2, specialPriceRepository.findActiveNotAssignedByCategoryId(hiddenCategoryId).size());
            var res = eventApiV2Controller.handleCode(event.getShortName(), URL_CODE_HIDDEN, new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
            var reservationId = res.getHeaders().getLocation().toString().substring(("/event/" + event.getShortName() + "/reservation/").length());
            var reservationInfo = reservationApiV2Controller.getReservationInfo(event.getShortName(), reservationId);
            assertEquals(HttpStatus.OK, reservationInfo.getStatusCode());
            assertEquals(reservationId, reservationInfo.getBody().getId());

            assertEquals(1, specialPriceRepository.findActiveNotAssignedByCategoryId(hiddenCategoryId).size());

            adminReservationManager.removeReservation(event.getShortName(), reservationId, false, false, user);

            assertEquals(2, specialPriceRepository.findActiveNotAssignedByCategoryId(hiddenCategoryId).size());

            // this is run by a job, but given the fact that it's in another separate transaction, it cannot work in this test (WaitingQueueSubscriptionProcessor.handleWaitingTickets)
            assertEquals(1, ticketReservationManager.revertTicketsToFreeIfAccessRestricted(event.getId()));

        }


        // discount check
        {
            var discountCodeRes = eventApiV2Controller.validateCode(event.getShortName(), PROMO_CODE);
            var discountCode = discountCodeRes.getBody();
            assertEquals(EventCode.EventCodeType.DISCOUNT, discountCode.getValue().getType());
            var itemsRes3 = eventApiV2Controller.getTicketCategories(event.getShortName(), PROMO_CODE);

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
            var res = eventApiV2Controller.reserveTickets(event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, res.getStatusCode());
            var resBody = res.getBody();
            assertFalse(resBody.isSuccess());
            assertEquals(1, resBody.getErrorCount());
        }

        //cancel a reservation
        {
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            ticketReservation.setAmount(1);
            ticketReservation.setTicketCategoryId(eventApiV2Controller.getTicketCategories(event.getShortName(), null).getBody().getTicketCategories().get(0).getId());
            form.setReservation(Collections.singletonList(ticketReservation));
            var res = eventApiV2Controller.reserveTickets(event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
            assertEquals(HttpStatus.OK, res.getStatusCode());
            var resBody = res.getBody();
            assertTrue(resBody.isSuccess());
            assertEquals(0, resBody.getErrorCount());
            var reservationId = resBody.getValue();

            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING);

            var cancelRes = reservationApiV2Controller.cancelPendingReservation(event.getShortName(), reservationId);
            assertEquals(HttpStatus.OK, cancelRes.getStatusCode());

            checkStatus(reservationId, HttpStatus.NOT_FOUND, null, null);
        }

        //buy 2 ticket, with additional service + field
        {
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            ticketReservation.setAmount(2);
            ticketReservation.setTicketCategoryId(eventApiV2Controller.getTicketCategories(event.getShortName(), null).getBody().getTicketCategories().get(0).getId());
            form.setReservation(Collections.singletonList(ticketReservation));

            var additionalService = new AdditionalServiceReservationModification();
            additionalService.setAdditionalServiceId(additionalServiceId);
            additionalService.setQuantity(1);
            form.setAdditionalService(Collections.singletonList(additionalService));
            var res = eventApiV2Controller.reserveTickets(event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
            assertEquals(HttpStatus.OK, res.getStatusCode());
            var resBody = res.getBody();
            assertTrue(resBody.isSuccess());
            assertEquals(0, resBody.getErrorCount());
            var reservationId = resBody.getValue();
            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING);

            var resInfoRes = reservationApiV2Controller.getReservationInfo(event.getShortName(), reservationId);
            assertEquals(HttpStatus.OK, resInfoRes.getStatusCode());
            var ticketsByCat = resInfoRes.getBody().getTicketsByCategory();
            assertEquals(1, ticketsByCat.size());
            assertEquals(2, ticketsByCat.get(0).getTickets().size());

            var ticket1 = ticketsByCat.get(0).getTickets().get(0);
            assertEquals(1, ticket1.getTicketFieldConfigurationBeforeStandard().size()); // 1
            assertEquals(2, ticket1.getTicketFieldConfigurationAfterStandard().size()); // 1 + 1 additional service related field (appear only on first ticket)

            var ticket2 = ticketsByCat.get(0).getTickets().get(1);
            assertEquals(1, ticket2.getTicketFieldConfigurationBeforeStandard().size()); // 1
            assertEquals(1, ticket2.getTicketFieldConfigurationAfterStandard().size()); // 1


            var contactForm = new ContactAndTicketsForm();

            contactForm.setAddCompanyBillingDetails(true);
            contactForm.setSkipVatNr(false);
            contactForm.setInvoiceRequested(true);
            contactForm.setEmail("test@test.com");
            contactForm.setBillingAddress("my billing address");
            contactForm.setFirstName("full");
            contactForm.setLastName("name");
            var ticketForm1 = new UpdateTicketOwnerForm();
            ticketForm1.setFirstName("ticketfull");
            ticketForm1.setLastName("ticketname");
            ticketForm1.setEmail("tickettest@test.com");
            ticketForm1.setAdditional(new HashMap<>(Map.of("field1", Collections.singletonList("value"))));

            var ticketForm2 = new UpdateTicketOwnerForm();
            ticketForm2.setFirstName("ticketfull");
            ticketForm2.setLastName("ticketname");
            ticketForm2.setEmail("tickettest@test.com");
            ticketForm2.setAdditional(Map.of("field1", Collections.singletonList("value")));

            contactForm.setTickets(Map.of(ticket1.getUuid(), ticketForm1, ticket2.getUuid(), ticketForm2));

            var failure = reservationApiV2Controller.validateToOverview(event.getShortName(), reservationId, "en", contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"));
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, failure.getStatusCode());
            assertEquals(1, failure.getBody().getValidationErrors().stream().filter(f -> f.getFieldName().equals("tickets["+ticket1.getUuid()+"].additional[field3][0]")).count()); //<- missing mandatory

            //check billing errors
            assertEquals(1, failure.getBody().getValidationErrors().stream().filter(f -> f.getFieldName().equals("billingAddressLine1")).count()); //<- missing mandatory
            assertEquals(1, failure.getBody().getValidationErrors().stream().filter(f -> f.getFieldName().equals("billingAddressZip")).count()); //<- missing mandatory
            assertEquals(1, failure.getBody().getValidationErrors().stream().filter(f -> f.getFieldName().equals("billingAddressCity")).count()); //<- missing mandatory
            assertEquals(1, failure.getBody().getValidationErrors().stream().filter(f -> f.getFieldName().equals("vatCountryCode")).count()); //<- missing mandatory
            //


            contactForm.setVatCountryCode("CH");
            contactForm.setBillingAddressLine1("LINE 1");
            contactForm.setBillingAddressCity("CITY");
            contactForm.setBillingAddressZip("ZIP");

            ticketForm1.getAdditional().put("field3", Collections.singletonList("missing value"));
            var success = reservationApiV2Controller.validateToOverview(event.getShortName(), reservationId, "en", contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"));
            assertEquals(HttpStatus.OK, success.getStatusCode());

            reservationApiV2Controller.cancelPendingReservation(event.getShortName(), reservationId);
        }


        //buy one ticket, without discount
        {
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            ticketReservation.setAmount(1);
            ticketReservation.setTicketCategoryId(eventApiV2Controller.getTicketCategories(event.getShortName(), null).getBody().getTicketCategories().get(0).getId());
            form.setReservation(Collections.singletonList(ticketReservation));
            var res = eventApiV2Controller.reserveTickets(event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
            assertEquals(HttpStatus.OK, res.getStatusCode());
            var resBody = res.getBody();
            assertTrue(resBody.isSuccess());
            assertEquals(0, resBody.getErrorCount());
            var reservationId = resBody.getValue();

            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING);


            var resInfoRes = reservationApiV2Controller.getReservationInfo(event.getShortName(), reservationId);
            assertEquals(HttpStatus.OK, resInfoRes.getStatusCode());
            var reservation = resInfoRes.getBody();
            assertEquals(reservationId, reservation.getId());
            assertEquals(1, reservation.getTicketsByCategory().size());
            assertEquals(1, reservation.getTicketsByCategory().get(0).getTickets().size());

            var selectedTicket = reservation.getTicketsByCategory().get(0).getTickets().get(0);
            assertEquals("field1", selectedTicket.getTicketFieldConfigurationBeforeStandard().get(0).getName());
            assertTrue(selectedTicket.getTicketFieldConfigurationBeforeStandard().get(0).isRequired());
            assertEquals("field2", selectedTicket.getTicketFieldConfigurationAfterStandard().get(0).getName());
            assertFalse(selectedTicket.getTicketFieldConfigurationAfterStandard().get(0).isRequired());

            var contactForm = new ContactAndTicketsForm();
            var validationErrorsRes = reservationApiV2Controller.validateToOverview(event.getShortName(), reservationId, "en", contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"));
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
            ticketForm.setFirstName("ticketfull");
            ticketForm.setLastName("ticketname");
            ticketForm.setEmail("tickettest@test.com");
            contactForm.setTickets(Collections.singletonMap(reservation.getTicketsByCategory().get(0).getTickets().get(0).getUuid(), ticketForm));

            var overviewResFailed = reservationApiV2Controller.validateToOverview(event.getShortName(), reservationId, "en", contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"));
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, overviewResFailed.getStatusCode());
            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING);

            //add mandatory additional field
            ticketForm.setAdditional(Collections.singletonMap("field1", Collections.singletonList("value")));
            var overviewRes = reservationApiV2Controller.validateToOverview(event.getShortName(), reservationId, "en", contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"));
            assertEquals(HttpStatus.OK, overviewRes.getStatusCode());
            checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.PENDING);
            //

            reservationApiV2Controller.backToBooking(event.getShortName(), reservationId);

            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING);

            overviewRes = reservationApiV2Controller.validateToOverview(event.getShortName(), reservationId, "en", contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"));
            assertTrue(overviewRes.getBody().getValue());

            checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.PENDING);

            var paymentForm = new PaymentForm();
            var handleResError = reservationApiV2Controller.confirmOverview(event.getShortName(), reservationId, "en", paymentForm, new BeanPropertyBindingResult(paymentForm, "paymentForm"),
                new MockHttpServletRequest());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, handleResError.getStatusCode());


            paymentForm.setPrivacyPolicyAccepted(true);
            paymentForm.setTermAndConditionsAccepted(true);
            paymentForm.setPaymentMethod(PaymentProxy.OFFLINE);

            // bank transfer does not have a transaction, it's created on confirmOverview call
            var tStatus = reservationApiV2Controller.getTransactionStatus(event.getShortName(), reservationId, "BANK_TRANSFER");
            assertEquals(HttpStatus.NOT_FOUND, tStatus.getStatusCode());
            //

            var handleRes = reservationApiV2Controller.confirmOverview(event.getShortName(), reservationId, "en", paymentForm, new BeanPropertyBindingResult(paymentForm, "paymentForm"),
                new MockHttpServletRequest());
            assertEquals(HttpStatus.OK, handleRes.getStatusCode());

            checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT);

            tStatus = reservationApiV2Controller.getTransactionStatus(event.getShortName(), reservationId, "BANK_TRANSFER");
            assertEquals(HttpStatus.OK, tStatus.getStatusCode());
            assertFalse(tStatus.getBody().isSuccess());

            reservation = reservationApiV2Controller.getReservationInfo(event.getShortName(), reservationId).getBody();

            var orderSummary = reservation.getOrderSummary();
            assertTrue(orderSummary.isNotYetPaid());
            assertEquals("10.00", orderSummary.getTotalPrice());
            assertEquals("0.10", orderSummary.getTotalVAT());
            assertEquals("1.00", orderSummary.getVatPercentage());

            validatePayment(event.getShortName(), reservationId);

            checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.COMPLETE);

            tStatus = reservationApiV2Controller.getTransactionStatus(event.getShortName(), reservationId, "BANK_TRANSFER");
            assertEquals(HttpStatus.OK, tStatus.getStatusCode());
            assertTrue(tStatus.getBody().isSuccess());

            reservation = reservationApiV2Controller.getReservationInfo(event.getShortName(), reservationId).getBody();
            orderSummary = reservation.getOrderSummary();
            assertFalse(orderSummary.isNotYetPaid());


            var confRes = reservationApiV2Controller.reSendReservationConfirmationEmail(event.getShortName(), reservationId, "en");
            assertEquals(HttpStatus.OK, confRes.getStatusCode());
            assertTrue(confRes.getBody());

            var ticket = reservation.getTicketsByCategory().stream().findFirst().get().getTickets().get(0);
            assertEquals("tickettest@test.com", ticket.getEmail());
            assertEquals("ticketfull", ticket.getFirstName());
            assertEquals("ticketname", ticket.getLastName());

            var ticketNotFoundRes = ticketApiV2Controller.getTicketInfo(event.getShortName(), "DONT_EXISTS");
            assertEquals(HttpStatus.NOT_FOUND, ticketNotFoundRes.getStatusCode());

            var ticketFoundRes = ticketApiV2Controller.getTicketInfo(event.getShortName(), ticket.getUuid());
            assertEquals(HttpStatus.OK, ticketFoundRes.getStatusCode());
            var ticketFoundBody = ticketFoundRes.getBody();
            assertEquals("tickettest@test.com", ticketFoundBody.getEmail());
            assertEquals("ticketfull ticketname", ticketFoundBody.getFullName());
            assertEquals("full name", ticketFoundBody.getReservationFullName());
            assertTrue(reservationId.startsWith(ticketFoundBody.getReservationId().toLowerCase(Locale.ENGLISH)));

            var sendTicketByEmailRes = ticketApiV2Controller.sendTicketByEmail(event.getShortName(), ticket.getUuid());
            assertEquals(HttpStatus.OK, sendTicketByEmailRes.getStatusCode());
            assertTrue(sendTicketByEmailRes.getBody());

            //update ticket
            var updateTicketOwnerForm = new UpdateTicketOwnerForm();
            updateTicketOwnerForm.setFirstName("Test");
            updateTicketOwnerForm.setLastName("Testson");
            updateTicketOwnerForm.setEmail("testmctest@test.com");
            updateTicketOwnerForm.setAdditional(Collections.singletonMap("field1", Collections.singletonList("value")));
            var updateTicketRes = ticketApiV2Controller.updateTicketInfo(event.getShortName(), ticket.getUuid(), updateTicketOwnerForm, new BeanPropertyBindingResult(updateTicketOwnerForm, "ticket"), null);
            assertTrue(updateTicketRes.getBody().isSuccess());

            //not found
            assertEquals(HttpStatus.NOT_FOUND, ticketApiV2Controller.updateTicketInfo(event.getShortName(), ticket.getUuid()+"42", updateTicketOwnerForm, new BeanPropertyBindingResult(updateTicketOwnerForm, "ticket"), null).getStatusCode());


            ticketFoundRes = ticketApiV2Controller.getTicketInfo(event.getShortName(), ticket.getUuid());
            ticketFoundBody = ticketFoundRes.getBody();
            assertEquals("testmctest@test.com", ticketFoundBody.getEmail());
            assertEquals("Test Testson", ticketFoundBody.getFullName());
            assertEquals("full name", ticketFoundBody.getReservationFullName());
            reservation = reservationApiV2Controller.getReservationInfo(event.getShortName(), reservationId).getBody();
            ticket = reservation.getTicketsByCategory().stream().findFirst().get().getTickets().get(0);
            assertEquals("testmctest@test.com", ticket.getEmail());
            assertEquals("Test", ticket.getFirstName());
            assertEquals("Testson", ticket.getLastName());


            var ticketPdfMockResp = new MockHttpServletResponse();
            ticketApiV2Controller.generateTicketPdf(event.getShortName(), ticket.getUuid(), ticketPdfMockResp);
            assertEquals("application/pdf", ticketPdfMockResp.getContentType());

            var ticketQRCodeResp = new MockHttpServletResponse();
            ticketApiV2Controller.showQrCode(event.getShortName(), ticket.getUuid(), ticketQRCodeResp);
            assertEquals("image/png", ticketQRCodeResp.getContentType());

            var fullTicketInfo = ticketRepository.findByUUID(ticket.getUuid());
            var qrCodeReader = new QRCodeReader();
            var qrCodeRead = qrCodeReader.decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO.read(new ByteArrayInputStream(ticketQRCodeResp.getContentAsByteArray()))))));
            assertEquals(fullTicketInfo.ticketCode(event.getPrivateKey()), qrCodeRead.getText());

            //can only be done for free tickets
            var releaseTicketFailure = ticketApiV2Controller.releaseTicket(event.getShortName(), ticket.getUuid());
            assertEquals(HttpStatus.BAD_REQUEST, releaseTicketFailure.getStatusCode());
            assertEquals(HttpStatus.OK, ticketApiV2Controller.getTicketInfo(event.getShortName(), ticket.getUuid()).getStatusCode());


            //no invoice, but receipt
            assertEquals(HttpStatus.NOT_FOUND, reservationApiV2Controller.getInvoice(event.getShortName(), reservationId, new MockHttpServletResponse(), null).getStatusCode());
            assertEquals(HttpStatus.OK, reservationApiV2Controller.getReceipt(event.getShortName(), reservationId, new MockHttpServletResponse(), null).getStatusCode());



            //

            {

                Principal principal = mock(Principal.class);
                Mockito.when(principal.getName()).thenReturn(user);

                String ticketIdentifier = fullTicketInfo.getUuid();
                String eventName = event.getShortName();

                String ticketCode = fullTicketInfo.ticketCode(event.getPrivateKey());
                TicketAndCheckInResult ticketAndCheckInResult = checkInApiController.findTicketWithUUID(event.getId(), ticketIdentifier, ticketCode);
                assertEquals(CheckInStatus.OK_READY_TO_BE_CHECKED_IN, ticketAndCheckInResult.getResult().getStatus());
                CheckInApiController.TicketCode tc = new CheckInApiController.TicketCode();
                tc.setCode(ticketCode);
                assertEquals(CheckInStatus.SUCCESS, checkInApiController.checkIn(event.getId(), ticketIdentifier, tc, new TestingAuthenticationToken("ciccio", "ciccio")).getResult().getStatus());
                List<ScanAudit> audits = scanAuditRepository.findAllForEvent(event.getId());
                assertFalse(audits.isEmpty());
                assertTrue(audits.stream().anyMatch(sa -> sa.getTicketUuid().equals(ticketIdentifier)));


                TicketAndCheckInResult ticketAndCheckInResultOk = checkInApiController.findTicketWithUUID(event.getId(), ticketIdentifier, ticketCode);
                assertEquals(CheckInStatus.ALREADY_CHECK_IN, ticketAndCheckInResultOk.getResult().getStatus());

                // check stats after check in one ticket
                assertFalse(eventStatisticsManager.getTicketSoldStatistics(event.getId(), new Date(0), DateUtils.addDays(new Date(), 1)).isEmpty());
                EventWithAdditionalInfo eventWithAdditionalInfo3 = eventStatisticsManager.getEventWithAdditionalInfo(event.getShortName(), user);
                assertEquals(2, eventWithAdditionalInfo3.getNotSoldTickets());
                assertEquals(0, eventWithAdditionalInfo3.getSoldTickets());
                assertEquals(20, eventWithAdditionalInfo3.getAvailableSeats());
                assertEquals(1, eventWithAdditionalInfo3.getCheckedInTickets());


                //test revert check in
                assertTrue(checkInApiController.revertCheckIn(event.getId(), ticketIdentifier, principal));
                assertFalse(checkInApiController.revertCheckIn(event.getId(), ticketIdentifier, principal));
                TicketAndCheckInResult ticketAndCheckInResult2 = checkInApiController.findTicketWithUUID(event.getId(), ticketIdentifier, ticketCode);
                assertEquals(CheckInStatus.OK_READY_TO_BE_CHECKED_IN, ticketAndCheckInResult2.getResult().getStatus());

                UsersApiController.UserWithPasswordAndQRCode sponsorUser = usersApiController.insertUser(new UserModification(null, event.getOrganizationId(), "SPONSOR", "sponsor", "first", "last", "email@email.com", User.Type.INTERNAL, null, null), "http://localhost:8080", principal);
                Principal sponsorPrincipal = mock(Principal.class);
                Mockito.when(sponsorPrincipal.getName()).thenReturn(sponsorUser.getUsername());

                // check failures
                assertEquals(CheckInStatus.EVENT_NOT_FOUND, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest("not-existing-event", "not-existing-ticket"), sponsorPrincipal).getBody().getResult().getStatus());
                assertEquals(CheckInStatus.TICKET_NOT_FOUND, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, "not-existing-ticket"), sponsorPrincipal).getBody().getResult().getStatus());
                assertEquals(CheckInStatus.INVALID_TICKET_STATE, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, ticketIdentifier), sponsorPrincipal).getBody().getResult().getStatus());
                //


                // check stats after revert check in one ticket
                assertFalse(eventStatisticsManager.getTicketSoldStatistics(event.getId(), new Date(0), DateUtils.addDays(new Date(), 1)).isEmpty());
                EventWithAdditionalInfo eventWithAdditionalInfo4 = eventStatisticsManager.getEventWithAdditionalInfo(event.getShortName(), user);
                assertEquals(2, eventWithAdditionalInfo4.getNotSoldTickets());
                assertEquals(1, eventWithAdditionalInfo4.getSoldTickets());
                assertEquals(20, eventWithAdditionalInfo4.getAvailableSeats());
                assertEquals(0, eventWithAdditionalInfo4.getCheckedInTickets());


                CheckInApiController.TicketCode tc2 = new CheckInApiController.TicketCode();
                tc2.setCode(ticketCode);
                TicketAndCheckInResult ticketAndcheckInResult = checkInApiController.checkIn(event.getId(), ticketIdentifier, tc2, new TestingAuthenticationToken("ciccio", "ciccio"));
                assertEquals(CheckInStatus.SUCCESS, ticketAndcheckInResult.getResult().getStatus());
                //


                //
                var offlineIdentifiers = checkInApiController.getOfflineIdentifiers(event.getShortName(), 0L, new MockHttpServletResponse(), principal);
                assertFalse("Alf.io-PI integration must be enabled by default", offlineIdentifiers.isEmpty());

                //disable Alf.io-PI
                configurationRepository.insert(ConfigurationKeys.ALFIO_PI_INTEGRATION_ENABLED.name(), "false", null);
                offlineIdentifiers = checkInApiController.getOfflineIdentifiers(event.getShortName(), 0L, new MockHttpServletResponse(), principal);
                assertTrue(offlineIdentifiers.isEmpty());

                //re-enable Alf.io-PI
                configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), ConfigurationKeys.OFFLINE_CHECKIN_ENABLED.name(), "true", null);
                configurationRepository.update(ConfigurationKeys.ALFIO_PI_INTEGRATION_ENABLED.name(), "true");
                offlineIdentifiers = checkInApiController.getOfflineIdentifiers(event.getShortName(), 0L, new MockHttpServletResponse(), principal);
                assertFalse(offlineIdentifiers.isEmpty());
                Map<String, String> payload = checkInApiController.getOfflineEncryptedInfo(event.getShortName(), Collections.emptyList(), offlineIdentifiers, principal);
                assertEquals(1, payload.size());
                TicketWithCategory ticketwc = ticketAndcheckInResult.getTicket();
                String ticketKey = ticketwc.hmacTicketInfo(event.getPrivateKey());
                String hashedTicketKey = DigestUtils.sha256Hex(ticketKey);
                String encJson = payload.get(hashedTicketKey);
                assertNotNull(encJson);
                String ticketPayload = CheckInManager.decrypt(ticketwc.getUuid() + "/" + ticketKey, encJson);
                Map<String, String> jsonPayload = Json.fromJson(ticketPayload, new TypeReference<Map<String, String>>() {
                });
                assertNotNull(jsonPayload);
                assertEquals(8, jsonPayload.size());
                assertEquals("Test", jsonPayload.get("firstName"));
                assertEquals("Testson", jsonPayload.get("lastName"));
                assertEquals("Test Testson", jsonPayload.get("fullName"));
                assertEquals(ticketwc.getUuid(), jsonPayload.get("uuid"));
                assertEquals("testmctest@test.com", jsonPayload.get("email"));
                assertEquals("CHECKED_IN", jsonPayload.get("status"));
                assertEquals("default", jsonPayload.get("category"));
                //

                // check register sponsor scan success flow
                assertTrue(attendeeApiController.getScannedBadges(event.getShortName(), EventUtil.JSON_DATETIME_FORMATTER.format(LocalDateTime.of(1970, 1, 1, 0, 0)), sponsorPrincipal).getBody().isEmpty());
                assertEquals(CheckInStatus.SUCCESS, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, ticketwc.getUuid()), sponsorPrincipal).getBody().getResult().getStatus());
                assertEquals(1, attendeeApiController.getScannedBadges(event.getShortName(), EventUtil.JSON_DATETIME_FORMATTER.format(LocalDateTime.of(1970, 1, 1, 0, 0)), sponsorPrincipal).getBody().size());

                // check export
                MockHttpServletResponse response = new MockHttpServletResponse();
                eventApiController.downloadSponsorScanExport(event.getShortName(), "csv", response, principal);
                response.getContentAsString();
                CSVReader csvReader = new CSVReader(new StringReader(response.getContentAsString()));
                List<String[]> csvSponsorScan = csvReader.readAll();
                Assert.assertEquals(2, csvSponsorScan.size());
                Assert.assertEquals("sponsor", csvSponsorScan.get(1)[0]);
                Assert.assertEquals("Test Testson", csvSponsorScan.get(1)[3]);
                Assert.assertEquals("testmctest@test.com", csvSponsorScan.get(1)[4]);
                //

                eventManager.deleteEvent(event.getId(), principal.getName());
            }
        }

    }

    private void checkStatus(String reservationId, HttpStatus expectedHttpStatus, Boolean validated, TicketReservation.TicketReservationStatus reservationStatus) {
        var statusRes = reservationApiV2Controller.getReservationStatus(event.getShortName(), reservationId);
        assertEquals(expectedHttpStatus, statusRes.getStatusCode());
        var status = statusRes.getBody();
        if (validated != null) {
            assertEquals(validated, status.isValidatedBookingInformations());
        }

        if (reservationStatus != null) {
            assertEquals(reservationStatus, status.getStatus());
        }
    }

    private void validatePayment(String eventName, String reservationIdentifier) {
        Principal principal = mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(user);
        var reservation = ticketReservationRepository.findReservationById(reservationIdentifier);
        assertEquals(1000, reservation.getFinalPriceCts());
        assertEquals(1000, reservation.getSrcPriceCts());
        assertEquals(10, reservation.getVatCts());
        assertEquals(0, reservation.getDiscountCts());
        assertEquals(1, eventApiController.getPendingPayments(eventName).size());
        assertEquals("OK", eventApiController.confirmPayment(eventName, reservationIdentifier, principal));
        assertEquals(0, eventApiController.getPendingPayments(eventName).size());
        assertEquals(1000, eventRepository.getGrossIncome(event.getId()));
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
