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

import alfio.config.authentication.support.APITokenAuthentication;
import alfio.controller.IndexController;
import alfio.controller.api.admin.AdditionalServiceApiController;
import alfio.controller.api.admin.CheckInApiController;
import alfio.controller.api.admin.EventApiController;
import alfio.controller.api.admin.UsersApiController;
import alfio.controller.api.v1.AttendeeApiController;
import alfio.controller.api.v2.InfoApiController;
import alfio.controller.api.v2.TranslationsApiController;
import alfio.controller.api.v2.model.BasicEventInfo;
import alfio.controller.api.v2.model.EventCode;
import alfio.controller.api.v2.model.Language;
import alfio.controller.api.v2.model.ReservationInfo;
import alfio.controller.api.v2.user.EventApiV2Controller;
import alfio.controller.api.v2.user.ReservationApiV2Controller;
import alfio.controller.api.v2.user.TicketApiV2Controller;
import alfio.controller.api.v2.user.support.ReservationAccessDenied;
import alfio.controller.form.*;
import alfio.extension.Extension;
import alfio.extension.ExtensionService;
import alfio.manager.*;
import alfio.manager.support.CheckInStatus;
import alfio.manager.support.IncompatibleStateException;
import alfio.manager.support.TicketAndCheckInResult;
import alfio.manager.support.extension.ExtensionEvent;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.audit.ScanAudit;
import alfio.model.modification.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.User;
import alfio.repository.*;
import alfio.repository.audit.ScanAuditRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.*;
import ch.digitalfondue.jfiveparse.Element;
import ch.digitalfondue.jfiveparse.Parser;
import ch.digitalfondue.jfiveparse.Selector;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.context.request.ServletWebRequest;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.JDBCType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.config.authentication.support.AuthenticationConstants.SYSTEM_API_CLIENT;
import static alfio.manager.support.extension.ExtensionEvent.*;
import static alfio.model.system.ConfigurationKeys.TRANSLATION_OVERRIDE;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RequiredArgsConstructor
public abstract class BaseReservationFlowTest extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BaseReservationFlowTest.class);
    protected final ConfigurationRepository configurationRepository;
    protected final EventManager eventManager;
    protected final EventRepository eventRepository;
    protected final EventStatisticsManager eventStatisticsManager;
    protected final TicketCategoryRepository ticketCategoryRepository;
    protected final TicketReservationRepository ticketReservationRepository;
    protected final EventApiController eventApiController;
    protected final TicketRepository ticketRepository;
    protected final PurchaseContextFieldRepository purchaseContextFieldRepository;
    protected final AdditionalServiceApiController additionalServiceApiController;
    protected final SpecialPriceTokenGenerator specialPriceTokenGenerator;
    protected final SpecialPriceRepository specialPriceRepository;
    protected final CheckInApiController checkInApiController;
    protected final AttendeeApiController attendeeApiController;
    protected final UsersApiController usersApiController;
    protected final ScanAuditRepository scanAuditRepository;
    protected final AuditingRepository auditingRepository;
    protected final AdminReservationManager adminReservationManager;
    protected final TicketReservationManager ticketReservationManager;
    protected final InfoApiController infoApiController;
    protected final TranslationsApiController translationsApiController;
    protected final EventApiV2Controller eventApiV2Controller;
    protected final ReservationApiV2Controller reservationApiV2Controller;
    protected final TicketApiV2Controller ticketApiV2Controller;
    protected final IndexController indexController;
    protected final NamedParameterJdbcTemplate jdbcTemplate;
    protected final ExtensionLogRepository extensionLogRepository;
    protected final ExtensionService extensionService;
    protected final PollRepository pollRepository;
    protected final ClockProvider clockProvider;
    protected final NotificationManager notificationManager;
    protected final UserRepository userRepository;
    protected final OrganizationDeleter organizationDeleter;
    protected final PromoCodeDiscountRepository promoCodeDiscountRepository;
    protected final PromoCodeRequestManager promoCodeRequestManager;
    protected final ExportManager exportManager;
    protected final PurchaseContextFieldManager purchaseContextFieldManager;

    private Integer additionalServiceId;

    static final String PROMO_CODE = "MYPROMOCODE";
    private static final String HIDDEN_CODE = "HIDDENNN";
    static final String URL_CODE_HIDDEN = "CODE_CODE_CODE";
    private int hiddenCategoryId = Integer.MIN_VALUE;

    private void ensureConfiguration(ReservationFlowContext context) {

        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        //promo code at event level
        eventManager.addPromoCode(PROMO_CODE, context.event.getId(), null, ZonedDateTime.now(clockProvider.getClock()).minusDays(2), context.event.getEnd().plusDays(2), 10, PromoCodeDiscount.DiscountType.PERCENTAGE, null, 3, "description", "test@test.ch", PromoCodeDiscount.CodeType.DISCOUNT, null, null);

        hiddenCategoryId = ticketCategoryRepository.findAllTicketCategories(context.event.getId()).stream().filter(TicketCategory::isAccessRestricted).collect(Collectors.toList()).get(0).getId();

        eventManager.addPromoCode(HIDDEN_CODE, context.event.getId(), null, ZonedDateTime.now(clockProvider.getClock()).minusDays(2), context.event.getEnd().plusDays(2), 0, PromoCodeDiscount.DiscountType.NONE, null, null, "hidden", "test@test.ch", PromoCodeDiscount.CodeType.ACCESS, hiddenCategoryId, null);


        // add additional fields before and after, with one mandatory
        var af = new AdditionalFieldRequest(-1, true, "field1", "text", true, false,null, null, null,
            Map.of("en", new EventModification.Description("field en", "", null)), null, null);
        purchaseContextFieldManager.addAdditionalField(context.event, af);

        var afId = purchaseContextFieldRepository.findAdditionalFieldsForEvent(context.event.getId()).get(0).getId();

        purchaseContextFieldRepository.updateFieldOrder(afId, -1);

        var af2 = new AdditionalFieldRequest(1, true, "field2", "text", false, false,null, null, null,
            Map.of("en", new EventModification.Description("field2 en", "", null)), null, null);
        purchaseContextFieldManager.addAdditionalField(context.event, af2);
        //


        // add additional service
        var addServ = buildAdditionalService(context);
        var addServRes = additionalServiceApiController.insert(context.event.getId(), addServ, new BeanPropertyBindingResult(addServ, "additionalService"), null);
        assertNotNull(addServRes.getBody());
        additionalServiceId = addServRes.getBody().getId();
        //

        var af3 = new AdditionalFieldRequest(2, true, "field3", "text", true, false, null, null, null,
            Map.of("en", new EventModification.Description("field3 en", "", null)), addServRes.getBody(), null);
        purchaseContextFieldManager.addAdditionalField(context.event, af3);


        // enable reservation list and pre sales
        configurationRepository.insertEventLevel(context.event.getOrganizationId(), context.event.getId(), ConfigurationKeys.ENABLE_WAITING_QUEUE.getValue(), "true", "");
        configurationRepository.insertEventLevel(context.event.getOrganizationId(), context.event.getId(), ConfigurationKeys.ENABLE_PRE_REGISTRATION.getValue(), "true", "");
        //

        specialPriceTokenGenerator.generatePendingCodes();
    }

    private EventModification.AdditionalService buildAdditionalService(ReservationFlowContext context, AdditionalService.SupplementPolicy supplementPolicy) {
        return new EventModification.AdditionalService(null, new BigDecimal("40.00"), true, 0, -1, 1,

            new DateTimeModification(ZonedDateTime.now(clockProvider.getClock()).minusDays(2).toLocalDate(), ZonedDateTime.now(clockProvider.getClock()).minusDays(2).toLocalTime()),
            new DateTimeModification(context.event.getEnd().plusDays(2).toLocalDate(), context.event.getEnd().plusDays(2).toLocalTime()),
            context.event.getVat(), AdditionalService.VatType.INHERITED,
            null,
            Collections.singletonList(new EventModification.AdditionalServiceText(null, "en", "additional title", AdditionalServiceText.TextType.TITLE)),
            Collections.singletonList(new EventModification.AdditionalServiceText(null, "en", "additional desc", AdditionalServiceText.TextType.DESCRIPTION)),

            AdditionalService.AdditionalServiceType.SUPPLEMENT,
            supplementPolicy,
            null,
            null
        );
    }

    protected EventModification.AdditionalService buildAdditionalService(ReservationFlowContext context) {
        return buildAdditionalService(context, AdditionalService.SupplementPolicy.OPTIONAL_MAX_AMOUNT_PER_TICKET);
    }

    protected Stream<String> getExtensionEventsToRegister() {
        return allEvents();
    }

    protected void testBasicFlow(Supplier<ReservationFlowContext> contextSupplier) throws Exception {
        // as soon as the test starts, insert the extension in the database (prepare the environment)
        insertExtension(extensionService, "/extension.js", getExtensionEventsToRegister());
        List<BasicEventInfo> body = eventApiV2Controller.listEvents(SearchOptions.empty()).getBody();
        assertNotNull(body);
        assertTrue(body.isEmpty());

        cleanupExtensionLog();

        var context = contextSupplier.get();
        ensureConfiguration(context);
        Assertions.assertEquals(1, eventManager.getEventsCount());

        // check if EVENT_CREATED was logged
        List<ExtensionLog> extLogs = extensionLogRepository.getPage(null, null, null, 100, 0);
        assertEventLogged(extLogs, EVENT_METADATA_UPDATE, 10);
        assertEventLogged(extLogs, EVENT_CREATED, 10);
        assertEventLogged(extLogs, EVENT_VALIDATE_CREATION, 10);


        {
            Principal p = Mockito.mock(Principal.class);
            Mockito.when(p.getName()).thenReturn(context.userId);
            assertTrue(usersApiController.getAllOrganizations(p).stream().anyMatch(o -> context.event.getOrganizationId() == o.getId()));
            assertEquals(context.event.getOrganizationId(), usersApiController.getOrganization(context.event.getOrganizationId(), p).getId());
        }

        //
        assertEquals(ContentLanguage.ALL_LANGUAGES.size(), translationsApiController.getSupportedLanguages().size());
        assertEquals("or", translationsApiController.getPublicTranslations("en", true).get("common.or"));
        assertEquals("o", translationsApiController.getPublicTranslations("it", true).get("common.or"));
        assertEquals("oder", translationsApiController.getPublicTranslations("de", true).get("common.or"));

        // check all public translations
        ContentLanguage.ALL_LANGUAGES.forEach(cl -> assertFalse(translationsApiController.getPublicTranslations(cl.getLanguage(), true).isEmpty()));

        var alfioInfo = infoApiController.getInfo(new MockHttpSession());
        assertFalse(alfioInfo.demoModeEnabled());
        assertTrue(alfioInfo.devModeEnabled());
        assertFalse(alfioInfo.prodModeEnabled());
        assertTrue(alfioInfo.analyticsConfiguration().isGoogleAnalyticsScrambledInfo());
        assertNull(alfioInfo.analyticsConfiguration().getGoogleAnalyticsKey());
        assertNull(alfioInfo.analyticsConfiguration().getClientId());

        //

        assertEquals("Switzerland", translationsApiController.getCountries("en").stream().filter( c-> "CH".equals(c.isoCode())).findFirst().get().name());

        assertEquals("Greece", translationsApiController.getCountries("en").stream().filter(c->"GR".equals(c.isoCode())).findFirst().get().name());

        assertEquals("Suisse", translationsApiController.getCountries("fr").stream().filter( c-> "CH".equals(c.isoCode())).findFirst().get().name());
        assertEquals("Svizzera", translationsApiController.getCountries("it").stream().filter( c-> "CH".equals(c.isoCode())).findFirst().get().name());
        assertEquals("Schweiz", translationsApiController.getCountries("de").stream().filter( c-> "CH".equals(c.isoCode())).findFirst().get().name());

        //EL -> greece for vat
        assertEquals("Greece", translationsApiController.getCountriesForVat("en").stream().filter(c->"EL".equals(c.isoCode())).findFirst().get().name());
        assertEquals(27, translationsApiController.getEuCountriesForVat("en").size()); //
        //


        assertTrue(requireNonNull(eventApiV2Controller.listEvents(SearchOptions.empty()).getBody()).isEmpty());


        //
        List<EventStatistic> eventStatistic = eventStatisticsManager.getAllEventsWithStatistics(context.userId);
        assertEquals(1, eventStatistic.size());
        var statisticsFrom = ZonedDateTime.now(context.event.getZoneId()).minusYears(1);
        var statisticsTo = ZonedDateTime.now(context.event.getZoneId()).plusDays(1);
        assertEquals(0L, eventStatisticsManager.getTicketSoldStatistics(context.event.getId(), statisticsFrom, statisticsTo, "day").stream().mapToLong(TicketsByDateStatistic::getCount).sum());
        EventWithAdditionalInfo eventWithAdditionalInfo = eventStatisticsManager.getEventWithAdditionalInfo(context.event.getShortName(), context.userId);
        assertEquals(2, eventWithAdditionalInfo.getNotSoldTickets()); // <- 2 tickets are the bounded category
        assertEquals(0, eventWithAdditionalInfo.getSoldTickets());
        assertEquals(20, eventWithAdditionalInfo.getAvailableSeats());
        //



        //publish the event
        eventManager.toggleActiveFlag(context.event.getId(), context.userId, true);
        //

        var resListEvents = eventApiV2Controller.listEvents(SearchOptions.empty());
        var events = resListEvents.getBody();

        assertEquals(HttpStatus.OK, resListEvents.getStatusCode());
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(context.event.getShortName(), events.get(0).getShortName());

        //
        assertEquals(HttpStatus.NOT_FOUND, eventApiV2Controller.getEvent("NOT_EXISTS", new MockHttpSession()).getStatusCode());
        //

        var eventRes = eventApiV2Controller.getEvent(context.event.getShortName(), new MockHttpSession());
        assertEquals(HttpStatus.OK, eventRes.getStatusCode());
        var selectedEvent = eventRes.getBody();
        assertNotNull(selectedEvent);
        assertEquals("CHF", selectedEvent.getCurrency());
        assertFalse(selectedEvent.isFree());
        assertEquals(context.event.getSameDay(), selectedEvent.isSameDay());
        assertEquals(context.vatIncluded, selectedEvent.isVatIncluded());
        assertEquals(context.event.getShortName(), selectedEvent.getShortName());
        assertEquals(context.event.getDisplayName(), selectedEvent.getDisplayName());
        assertEquals(context.event.getFileBlobId(), selectedEvent.getFileBlobId());
        assertTrue(selectedEvent.getI18nOverride().isEmpty());

        configurationRepository.insert(TRANSLATION_OVERRIDE.name(), Json.toJson(Map.of("en", Map.of("show-context.event.tickets.left", "{0} left!"))), "");
        configurationRepository.insertEventLevel(context.event.getOrganizationId(), context.event.getId(),"TRANSLATION_OVERRIDE", Json.toJson(Map.of("en", Map.of("common.vat", "context.event.vat"))), "");
        eventRes = eventApiV2Controller.getEvent(context.event.getShortName(), new MockHttpSession());
        selectedEvent = eventRes.getBody();
        assertNotNull(selectedEvent);
        assertFalse(selectedEvent.getI18nOverride().isEmpty());
        assertEquals("context.event.vat", selectedEvent.getI18nOverride().get("en").get("common.vat"));
        assertEquals("{{0}} left!", selectedEvent.getI18nOverride().get("en").get("show-context.event.tickets.left"));

        checkCalendar(context.event.getShortName());

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


        assertEquals("redirect:/api/v2/public/event/" + context.event.getShortName() + "/code/MY_CODE", indexController.redirectCode(context.event.getShortName(), "MY_CODE", "Normal user agent"));

        // vv social preview should redirect to event page when linking a code page instead of following and creating a reservation
        assertEquals("redirect:/event/" + context.event.getShortName(), indexController.redirectCode(context.event.getShortName(), "MY_CODE", "Slackbot"));


        // check open graph & co
        {
            var res = new MockHttpServletResponse();
            indexController.replyToIndex(context.event.getShortName(), null, "not a social share", "en", new ServletWebRequest(new MockHttpServletRequest()),res, new MockHttpSession(), null);
            var htmlParser = new Parser();
            var docWithoutOpenGraph = htmlParser.parse(new String(res.getContentAsByteArray(), StandardCharsets.UTF_8));
            assertTrue(docWithoutOpenGraph.getAllNodesMatching(Selector.select().element("meta").attrValEq("name", "twitter:card").toMatcher()).isEmpty());

            res = new MockHttpServletResponse();
            indexController.replyToIndex(context.event.getShortName(), null,"Twitterbot/42", "en", new ServletWebRequest(new MockHttpServletRequest()), res, new MockHttpSession(), null);
            var docWithOpenGraph = htmlParser.parse(new String(res.getContentAsByteArray(), StandardCharsets.UTF_8));
            assertFalse(docWithOpenGraph.getAllNodesMatching(Selector.select().element("meta").attrValEq("name", "twitter:card").toMatcher()).isEmpty());

            var title = (Element) docWithOpenGraph.getAllNodesMatching(Selector.select().element("meta").attrValEq("property", "og:title").toMatcher(), true).get(0);
            assertEquals("Get your tickets for "+context.event.getDisplayName(), title.getAttribute("content"));
        }

        //


        // check ticket & all, we have 2 ticket categories, 1 hidden
        assertEquals(HttpStatus.NOT_FOUND, eventApiV2Controller.getTicketCategories("NOT_EXISTING", null).getStatusCode());
        {
            var itemsRes = eventApiV2Controller.getTicketCategories(context.event.getShortName(), null);
            assertEquals(HttpStatus.OK, itemsRes.getStatusCode());

            var items = itemsRes.getBody();

            assertNotNull(items);

            assertEquals(1, items.ticketCategories().size());
            var visibleCat = items.ticketCategories().get(0);
            assertEquals("default", visibleCat.getName());
            assertEquals("10.00", visibleCat.getFormattedFinalPrice());
            assertFalse(visibleCat.isHasDiscount());

            assertEquals(1, items.additionalServices().size());
            var additionalItem = items.additionalServices().get(0);
            assertEquals("40.00", additionalItem.getFormattedFinalPrice());
            assertEquals("1.00", additionalItem.getVatPercentage());
            assertEquals(1, additionalItem.getTitle().size()); //TODO: check: if there are missing lang, we should at least copy them (?)
            assertEquals(1, additionalItem.getDescription().size());
            assertEquals("additional title", additionalItem.getTitle().get("en"));
            assertEquals("<p>additional desc</p>\n", additionalItem.getDescription().get("en"));

            // check presence of reservation list
            assertFalse(items.waitingList());
            assertFalse(items.preSales());
            //

            // fix dates to enable reservation list
            var tc = ticketCategoryRepository.getById(visibleCat.getId());
            ticketCategoryRepository.fixDates(visibleCat.getId(), tc.getInception(context.event.getZoneId()).plusDays(2), tc.getExpiration(context.event.getZoneId()));
            //
            items = eventApiV2Controller.getTicketCategories(context.event.getShortName(), null).getBody();
            assertNotNull(items);
            assertTrue(items.waitingList());
            assertTrue(items.preSales());
            //

            var subForm = new WaitingQueueSubscriptionForm();
            subForm.setFirstName("first");
            subForm.setLastName("last");
            subForm.setPrivacyPolicyAccepted(true);
            subForm.setTermAndConditionsAccepted(true);
            subForm.setUserLanguage(Locale.ENGLISH);
            var subRes = eventApiV2Controller.subscribeToWaitingList(context.event.getShortName(), subForm, new BeanPropertyBindingResult(subForm, "subForm"));
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, subRes.getStatusCode());
            assertNotNull(subRes.getBody());
            assertFalse(subRes.getBody().isSuccess());
            assertEquals(1, subRes.getBody().getValidationErrors().size());
            assertEquals("email", subRes.getBody().getValidationErrors().get(0).getFieldName());
            assertEquals("error.email", subRes.getBody().getValidationErrors().get(0).getCode());
            //

            subForm.setEmail("email@email.com");
            subRes = eventApiV2Controller.subscribeToWaitingList(context.event.getShortName(), subForm, new BeanPropertyBindingResult(subForm, "subForm"));
            assertEquals(HttpStatus.OK, subRes.getStatusCode());
            assertNotNull(subRes.getBody());
            assertTrue(subRes.getBody().isSuccess());
            assertEquals(0, subRes.getBody().getErrorCount());
            assertTrue(subRes.getBody().getValue());

            //
            ticketCategoryRepository.fixDates(visibleCat.getId(), tc.getInception(context.event.getZoneId()).minusDays(2), tc.getExpiration(context.event.getZoneId()));
        }

        // dynamic promo codes can be applied only automatically
        {
            eventManager.addPromoCode("DYNAMIC_CODE", context.event.getId(), null, ZonedDateTime.now(clockProvider.getClock()).minusDays(2), context.event.getEnd().plusDays(2), 10, PromoCodeDiscount.DiscountType.PERCENTAGE, null, 3, "description", "test@test.ch", PromoCodeDiscount.CodeType.DYNAMIC, null, null);
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, eventApiV2Controller.validateCode(context.event.getShortName(), "DYNAMIC_CODE").getStatusCode());

            // try to enter it anyway
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            form.setPromoCode("DYNAMIC_CODE");
            ticketReservation.setQuantity(1);
            ticketReservation.setTicketCategoryId(retrieveCategories(context).get(0).getId());
            form.setReservation(Collections.singletonList(ticketReservation));
            var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), null);
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, res.getStatusCode());
        }

        // promo code currency code must match with event's in order to be redeemed
        {
            eventManager.addPromoCode("TEST_TEST_TEST_TEST", null, context.event.getOrganizationId(), ZonedDateTime.now(clockProvider.getClock()).minusDays(2), context.event.getEnd().plusDays(2), 10, PromoCodeDiscount.DiscountType.FIXED_AMOUNT, null, 3, "description", "test@test.ch", PromoCodeDiscount.CodeType.DISCOUNT, null, "JPY");
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, eventApiV2Controller.validateCode(context.event.getShortName(), "TEST_TEST_TEST_TEST").getStatusCode());

            // try to enter it anyway
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            form.setPromoCode("TEST_TEST_TEST_TEST");
            ticketReservation.setQuantity(1);
            ticketReservation.setTicketCategoryId(retrieveCategories(context).get(0).getId());
            form.setReservation(Collections.singletonList(ticketReservation));
            var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), null);
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, res.getStatusCode());
        }

        // hidden category check
        {

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, eventApiV2Controller.validateCode(context.event.getShortName(), "NOT_EXISTING").getStatusCode());

            var hiddenCodeRes = eventApiV2Controller.validateCode(context.event.getShortName(), HIDDEN_CODE);
            assertEquals(HttpStatus.OK, hiddenCodeRes.getStatusCode());
            var hiddenCode = hiddenCodeRes.getBody();
            assertNotNull(hiddenCode);
            assertEquals(EventCode.EventCodeType.ACCESS, hiddenCode.getValue().getType());

            var itemsRes2 = eventApiV2Controller.getTicketCategories(context.event.getShortName(), HIDDEN_CODE);
            var items2 = itemsRes2.getBody();
            assertNotNull(items2);
            assertEquals(2, items2.ticketCategories().size());

            var hiddenCat = items2.ticketCategories().stream().filter(t -> t.isAccessRestricted()).findFirst().get();
            assertEquals(hiddenCategoryId, hiddenCat.getId());
            assertEquals("hidden", hiddenCat.getName());
            assertEquals("1.00", hiddenCat.getFormattedFinalPrice());
            assertFalse(hiddenCat.isHasDiscount());
            assertTrue(hiddenCat.isAccessRestricted());

            // do a reservation for a hidden category+cancel
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            form.setPromoCode(HIDDEN_CODE);
            ticketReservation.setQuantity(1);
            ticketReservation.setTicketCategoryId(hiddenCat.getId());
            form.setReservation(Collections.singletonList(ticketReservation));
            var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            assertEquals(HttpStatus.OK, res.getStatusCode());
            assertNotNull(res.getBody());
            var reservationInfo = reservationApiV2Controller.getReservationInfo(res.getBody().getValue(), context.getPublicUser());
            assertEquals(HttpStatus.OK, reservationInfo.getStatusCode());
            assertNotNull(reservationInfo.getBody());
            assertEquals(context.vatIncluded ? "1.00" : "1.01", reservationInfo.getBody().getOrderSummary().getTotalPrice());
            assertEquals("hidden", reservationInfo.getBody().getOrderSummary().getSummary().get(0).getName());

            var activePaymentMethods = reservationInfo.getBody().getActivePaymentMethods();
            assertFalse(activePaymentMethods.isEmpty());
            assertTrue(activePaymentMethods.containsKey(PaymentMethod.BANK_TRANSFER));

            configurationRepository.insertTicketCategoryLevel(context.event.getOrganizationId(), context.event.getId(), hiddenCategoryId, ConfigurationKeys.PAYMENT_METHODS_BLACKLIST.name(), PaymentProxy.OFFLINE.name(), "");

            reservationInfo = reservationApiV2Controller.getReservationInfo(res.getBody().getValue(), context.getPublicUser());
            assertNotNull(reservationInfo.getBody());
            activePaymentMethods = reservationInfo.getBody().getActivePaymentMethods();
            assertTrue(activePaymentMethods.isEmpty());

            configurationRepository.deleteCategoryLevelByKey(ConfigurationKeys.PAYMENT_METHODS_BLACKLIST.name(), context.event.getId(), hiddenCategoryId);

            // clear the extension_log table so that we can check the very next additions
            // cannot have just one row in the log, every event adds EXACTLY two logs
            // log expected: RESERVATION_CANCELLED
            cleanupExtensionLog();
            reservationApiV2Controller.cancelPendingReservation(res.getBody().getValue());
            extLogs = extensionLogRepository.getPage(null, null, null, 100, 0);
            assertEventLogged(extLogs, RESERVATION_CANCELLED, 2);

            assertEquals(0, jdbcTemplate.queryForObject("select count(*) from ticket where status = 'FREE' and final_price_cts > 0", Map.of(), Integer.class));

            // this is run by a job, but given the fact that it's in another separate transaction, it cannot work in this test (WaitingQueueSubscriptionProcessor.handleWaitingTickets)
            assertEquals(1, ticketReservationManager.revertTicketsToFreeIfAccessRestricted(context.event.getId()));
        }
        //

        // check reservation auto creation with code: TODO: will need to check all the flows
        {

            // code not found
            var notFoundRes = eventApiV2Controller.handleCode(context.event.getShortName(), "NOT_EXIST", new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            assertEquals("/event/" + context.event.getShortName(), notFoundRes.getHeaders().getLocation().getPath());
            assertEquals("errors=error.STEP_1_CODE_NOT_FOUND", notFoundRes.getHeaders().getLocation().getQuery());
            //

            // promo code, we expect a redirect to event with the code in the query string
            var redirectPromoCodeRes = eventApiV2Controller.handleCode(context.event.getShortName(), PROMO_CODE, new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            assertEquals("/event/" + context.event.getShortName(), redirectPromoCodeRes.getHeaders().getLocation().getPath());
            assertEquals("code=MYPROMOCODE", redirectPromoCodeRes.getHeaders().getLocation().getQuery());


            // code existing
            assertEquals(2, specialPriceRepository.countFreeTokens(hiddenCategoryId).intValue());
            var res = eventApiV2Controller.handleCode(context.event.getShortName(), URL_CODE_HIDDEN, new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            var location = requireNonNull(res.getHeaders().getLocation()).toString();
            var reservationId = location.substring(("/event/" + context.event.getShortName() + "/reservation/").length(), location.length() - "/book".length());
            var reservationInfo = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser());
            assertEquals(HttpStatus.OK, reservationInfo.getStatusCode());
            assertNotNull(reservationInfo.getBody());
            assertEquals(reservationId, reservationInfo.getBody().getId());

            assertEquals(1, specialPriceRepository.countFreeTokens(hiddenCategoryId).intValue());

            reservationApiV2Controller.cancelPendingReservation(reservationId);

            assertEquals(2, specialPriceRepository.countFreeTokens(hiddenCategoryId).intValue());

            // this is run by a job, but given the fact that it's in another separate transaction, it cannot work in this test (WaitingQueueSubscriptionProcessor.handleWaitingTickets)
            assertEquals(1, ticketReservationManager.revertTicketsToFreeIfAccessRestricted(context.event.getId()));
        }

        // check reservation auto creation with deletion from the admin side
        {

            assertEquals(2, specialPriceRepository.countFreeTokens(hiddenCategoryId).intValue());
            var res = eventApiV2Controller.handleCode(context.event.getShortName(), URL_CODE_HIDDEN, new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            var location = requireNonNull(res.getHeaders().getLocation()).toString();
            var reservationId = location.substring(("/event/" + context.event.getShortName() + "/reservation/").length(), location.length() - "/book".length());
            var reservationInfo = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser());
            assertEquals(HttpStatus.OK, reservationInfo.getStatusCode());
            assertNotNull(reservationInfo.getBody());
            assertEquals(reservationId, reservationInfo.getBody().getId());
            assertEquals(1, reservationInfo.getBody().getActivePaymentMethods().size());
            assertTrue(reservationInfo.getBody().getActivePaymentMethods().containsKey(PaymentMethod.BANK_TRANSFER));

            assertEquals(1, specialPriceRepository.countFreeTokens(hiddenCategoryId).intValue());

            adminReservationManager.removeReservation(PurchaseContextType.event, context.event.getShortName(), reservationId, false, false, false, context.userId);

            assertEquals(2, specialPriceRepository.countFreeTokens(hiddenCategoryId).intValue());

            // this is run by a job, but given the fact that it's in another separate transaction, it cannot work in this test (WaitingQueueSubscriptionProcessor.handleWaitingTickets)
            assertEquals(1, ticketReservationManager.revertTicketsToFreeIfAccessRestricted(context.event.getId()));

        }


        // discount check
        {
            var discountCodeRes = eventApiV2Controller.validateCode(context.event.getShortName(), PROMO_CODE);
            var discountCode = discountCodeRes.getBody();
            assertNotNull(discountCode);
            assertEquals(EventCode.EventCodeType.DISCOUNT, discountCode.getValue().getType());
            var itemsRes3 = eventApiV2Controller.getTicketCategories(context.event.getShortName(), PROMO_CODE);

            var items3 = itemsRes3.getBody();
            assertNotNull(items3);

            assertEquals(1, items3.ticketCategories().size());
            var visibleCat = items3.ticketCategories().get(0);
            assertEquals("default", visibleCat.getName());
            assertEquals("10.00", visibleCat.getFormattedFinalPrice());
            assertTrue(visibleCat.isHasDiscount());
            assertEquals("9.00", visibleCat.getFormattedDiscountedPrice());
        }


        //validation error: select at least one
        {
            var form = new ReservationForm();
            var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, res.getStatusCode());
            var resBody = res.getBody();
            assertNotNull(resBody);
            assertFalse(resBody.isSuccess());
            assertEquals(1, resBody.getErrorCount());
        }

        //cancel a reservation
        {
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            ticketReservation.setQuantity(1);
            ticketReservation.setTicketCategoryId(retrieveCategories(context).get(0).getId());
            form.setReservation(Collections.singletonList(ticketReservation));
            var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            assertEquals(HttpStatus.OK, res.getStatusCode());
            var resBody = res.getBody();
            assertNotNull(resBody);
            assertTrue(resBody.isSuccess());
            assertEquals(0, resBody.getErrorCount());
            var reservationId = resBody.getValue();

            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING, context);

            var cancelRes = reservationApiV2Controller.cancelPendingReservation(reservationId);
            assertEquals(HttpStatus.OK, cancelRes.getStatusCode());

            checkStatus(reservationId, HttpStatus.NOT_FOUND, null, null, context);
        }

        //check blacklist payment methods
        {
            var form = new ReservationForm();
            var categories = eventApiV2Controller.getTicketCategories(context.event.getShortName(), HIDDEN_CODE).getBody().ticketCategories();

            var c1 = new TicketReservationModification();
            c1.setQuantity(1);
            int firstCategoryId = categories.get(0).getId();
            c1.setTicketCategoryId(firstCategoryId);

            var c2 = new TicketReservationModification();
            c2.setQuantity(1);
            c2.setTicketCategoryId(categories.get(1).getId());

            form.setReservation(List.of(c1, c2));
            form.setPromoCode(HIDDEN_CODE);

            var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            assertEquals(HttpStatus.OK, res.getStatusCode());
            var resBody = res.getBody();
            assertNotNull(resBody);
            assertTrue(resBody.isSuccess());
            assertEquals(0, resBody.getErrorCount());
            var reservationId = resBody.getValue();

            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING, context);

            var cancelRes = reservationApiV2Controller.cancelPendingReservation(reservationId);
            assertEquals(HttpStatus.OK, cancelRes.getStatusCode());

            checkStatus(reservationId, HttpStatus.NOT_FOUND, null, null, context);
        }

        // try to reserve more tickets than available
        {
            var category = retrieveCategories(context).get(0);
            Integer categoryIdFilter = category.isBounded() ? category.getId() : null;
            var freeTickets = jdbcTemplate.queryForList("select id from ticket where event_id = :eventId and status = 'FREE' and ((:categoryFilter::int is null and category_id is null) or category_id = :categoryFilter::int)", new MapSqlParameterSource("eventId", context.event.getId()).addValue("categoryFilter", categoryIdFilter), Integer.class);
            // we leave one ticket
            var lockedTickets = freeTickets.subList(1, freeTickets.size());
            int result = jdbcTemplate.update("update ticket set status = 'PENDING', category_id = :categoryId where id in (:freeTickets)", new MapSqlParameterSource("freeTickets", lockedTickets).addValue("categoryId", category.getId()));
            assertEquals(result, lockedTickets.size());

            var freeTicketsCount = jdbcTemplate.queryForObject("select count(*) from ticket where category_id "+ (category.isBounded() ? " = :categoryId" : "is null") + " and status = 'FREE' and event_id = :eventId", Map.of("eventId", context.event.getId(), "categoryId", category.getId()), Integer.class);
            assertEquals(1, freeTicketsCount);

            // try to reserve two ticket
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            ticketReservation.setQuantity(2);
            ticketReservation.setTicketCategoryId(category.getId());
            form.setReservation(Collections.singletonList(ticketReservation));
            var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, res.getStatusCode());
            assertEquals(ErrorsCode.STEP_1_NOT_ENOUGH_TICKETS, requireNonNull(res.getBody()).getValidationErrors().get(0).getCode());
            // ensure that reservation was not created
            assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject("select exists(select id from tickets_reservation where status = 'PENDING' and event_id_fk = :eventId)", new MapSqlParameterSource("eventId", context.event.getId()), Boolean.class));
            // restore data
            result = jdbcTemplate.update("update ticket set status = 'FREE', category_id = :categoryId where id in (:freeTickets)", new MapSqlParameterSource("freeTickets", lockedTickets).addValue("categoryId", category.isBounded() ? category.getId() : null, JDBCType.INTEGER.getVendorTypeNumber()));
            assertEquals(result, lockedTickets.size());
        }

        //buy 2 ticket, with additional service + field
        {
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            ticketReservation.setQuantity(2);
            ticketReservation.setTicketCategoryId(retrieveCategories(context).get(0).getId());
            form.setReservation(Collections.singletonList(ticketReservation));

            var additionalService = new AdditionalServiceReservationModification();
            additionalService.setAdditionalServiceId(additionalServiceId);
            additionalService.setQuantity(1);
            form.setAdditionalService(Collections.singletonList(additionalService));
            var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            assertEquals(HttpStatus.OK, res.getStatusCode());
            var resBody = res.getBody();
            assertNotNull(resBody);
            assertTrue(resBody.isSuccess());
            assertEquals(0, resBody.getErrorCount());
            var reservationId = resBody.getValue();
            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING, context);

            var resInfoRes = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser());
            assertEquals(HttpStatus.OK, resInfoRes.getStatusCode());
            assertNotNull(resInfoRes.getBody());
            var ticketsByCat = resInfoRes.getBody().getTicketsByCategory();
            assertEquals(1, ticketsByCat.size());
            assertEquals(2, ticketsByCat.get(0).getTickets().size());

            var ticket1 = ticketsByCat.get(0).getTickets().get(0);
            assertEquals(1, ticket1.getTicketFieldConfigurationBeforeStandard().size()); // 1
            assertEquals(1, ticket1.getTicketFieldConfigurationAfterStandard().size()); // 1 + 1 additional service related field (appear only on first ticket)
            assertEquals(1, resInfoRes.getBody().getAdditionalServiceWithData().size());

            var ticket2 = ticketsByCat.get(0).getTickets().get(1);
            assertEquals(1, ticket2.getTicketFieldConfigurationBeforeStandard().size()); // 1
            assertEquals(1, ticket2.getTicketFieldConfigurationAfterStandard().size()); // 1

            var additionalServiceWithData = resInfoRes.getBody().getAdditionalServiceWithData();
            assertEquals(1, additionalServiceWithData.size());
            assertEquals(1, additionalServiceWithData.get(0).getTicketFieldConfiguration().size());

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
            ticketForm2.setAdditional(new HashMap<>(Map.of("field1", Collections.singletonList("value"))));

            contactForm.setTickets(Map.ofEntries(
                Map.entry(ticket1.getUuid(), ticketForm1),
                Map.entry(ticket2.getUuid(), ticketForm2)
            ));

            var additionalServiceLinkForm = new AdditionalServiceLinkForm();
            additionalServiceLinkForm.setAdditionalServiceItemId(additionalServiceWithData.get(0).getItemId());
            additionalServiceLinkForm.setTicketUUID(ticket2.getUuid());
            contactForm.getAdditionalServices().put(ticket2.getUuid(), List.of(additionalServiceLinkForm));
            var failure = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), context.getPublicAuthentication());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, failure.getStatusCode());
            assertNotNull(failure.getBody());
            assertEquals(1, failure.getBody().getValidationErrors().stream().filter(f -> f.getFieldName().equals("additionalServices["+ticket2.getUuid()+"][0].additional[field3][0]")).count()); //<- missing mandatory

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
            var linkForm = new AdditionalServiceLinkForm();
            linkForm.setAdditionalServiceItemId(additionalServiceWithData.get(0).getItemId());
            linkForm.setTicketUUID(ticket2.getUuid());
            linkForm.setAdditional(new HashMap<>(Map.of("field3", Collections.singletonList("value"))));
            contactForm.getAdditionalServices().put(ticket2.getUuid(), List.of(linkForm));
            var success = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), context.getPublicAuthentication());
            assertEquals(HttpStatus.OK, success.getStatusCode());
            var values = purchaseContextFieldRepository.findAllValuesByTicketIds(List.of(ticketRepository.findByPublicUUID(UUID.fromString(ticket1.getUuid())).getId()));
            assertEquals(1, values.size());
            int ticket2Id = ticketRepository.findByPublicUUID(UUID.fromString(ticket2.getUuid())).getId();
            var ticket2Ids = List.of(ticket2Id);
            values = purchaseContextFieldRepository.findAllValuesByTicketIds(ticket2Ids);
            assertEquals(2, values.size());
            assertTrue(values.stream().anyMatch(f -> f.getName().equals("field3") && f.getValue().equals("value")));
            // verify that additional service field was actually saved.
            var asValues = purchaseContextFieldRepository.findAdditionalServicesValueByTicketIds(ticket2Ids);
            assertTrue(asValues.stream().anyMatch(f -> f.getName().equals("field3") && f.getValue().equals("value")));
            var valuesForCheckIn = purchaseContextFieldRepository.findValueForTicketId(ticket2Id, Set.of("field3"));
            assertFalse(valuesForCheckIn.isEmpty());
            assertEquals("field3", valuesForCheckIn.get(0).getName());
            assertEquals("value", valuesForCheckIn.get(0).getValue());
            reservationApiV2Controller.cancelPendingReservation(reservationId);
            // make sure that there is no pending additional service item anymore
            assertFalse(requireNonNull(jdbcTemplate.queryForObject("select exists (select id from additional_service_item where status = 'PENDING' and tickets_reservation_uuid = :reservationId) as res", new MapSqlParameterSource("reservationId", reservationId), Boolean.class)));
        }

        // buy 2 tickets + additional service with 100% discount
        {

            var addServ = buildAdditionalService(context, AdditionalService.SupplementPolicy.MANDATORY_ONE_FOR_TICKET);
            var addServRes = additionalServiceApiController.insert(context.event.getId(), addServ, new BeanPropertyBindingResult(addServ, "additionalService"), null);
            assertNotNull(addServRes.getBody());

            // add 100% promo code
            String promoCode = "100_PROMO";
            int categoryId = retrieveCategories(context).get(0).getId();
            eventManager.addPromoCode(promoCode, context.event.getId(), null, ZonedDateTime.now(clockProvider.getClock()).minusDays(2), context.event.getEnd().plusDays(2), 100, PromoCodeDiscount.DiscountType.PERCENTAGE, List.of(categoryId), null, "100% discount", "test@test.ch", PromoCodeDiscount.CodeType.DISCOUNT, null, null);

            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            ticketReservation.setQuantity(2);
            ticketReservation.setTicketCategoryId(categoryId);
            form.setReservation(Collections.singletonList(ticketReservation));
            form.setPromoCode(promoCode);

            var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), context.getPublicUser());
            assertEquals(HttpStatus.OK, res.getStatusCode());
            var resBody = res.getBody();
            assertNotNull(resBody);
            assertTrue(resBody.isSuccess());
            assertEquals(0, resBody.getErrorCount());
            var reservationId = resBody.getValue();
            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING, context);

            var resInfoRes = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser());
            assertEquals(HttpStatus.OK, resInfoRes.getStatusCode());
            assertNotNull(resInfoRes.getBody());
            var ticketsByCat = resInfoRes.getBody().getTicketsByCategory();
            assertEquals(1, ticketsByCat.size());
            assertEquals(2, ticketsByCat.get(0).getTickets().size());

            var ticket1 = ticketsByCat.get(0).getTickets().get(0);
            var ticket2 = ticketsByCat.get(0).getTickets().get(1);
            var additionalServiceWithData = resInfoRes.getBody().getAdditionalServiceWithData();
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
            ticketForm2.setAdditional(new HashMap<>(Map.of("field1", Collections.singletonList("value"))));

            contactForm.setTickets(Map.of(ticket1.getUuid(), ticketForm1, ticket2.getUuid(), ticketForm2));

            var additionalServiceLinkForm = new AdditionalServiceLinkForm();
            additionalServiceLinkForm.setAdditionalServiceItemId(additionalServiceWithData.get(0).getItemId());
            additionalServiceLinkForm.setTicketUUID(ticket1.getUuid());
            contactForm.getAdditionalServices().put(ticket1.getUuid(), List.of(additionalServiceLinkForm));
            var additionalServiceLinkForm2 = new AdditionalServiceLinkForm();
            additionalServiceLinkForm2.setAdditionalServiceItemId(additionalServiceWithData.get(1).getItemId());
            additionalServiceLinkForm2.setTicketUUID(ticket2.getUuid());
            contactForm.getAdditionalServices().put(ticket2.getUuid(), List.of(additionalServiceLinkForm));
            contactForm.setVatCountryCode("CH");
            contactForm.setBillingAddressLine1("LINE 1");
            contactForm.setBillingAddressCity("CITY");
            contactForm.setBillingAddressZip("ZIP");
            var linkForm = new AdditionalServiceLinkForm();
            linkForm.setAdditionalServiceItemId(additionalServiceWithData.get(0).getItemId());
            linkForm.setTicketUUID(ticket2.getUuid());
            linkForm.setAdditional(new HashMap<>(Map.of("field3", Collections.singletonList("value"))));
            contactForm.getAdditionalServices().put(ticket2.getUuid(), List.of(linkForm));
            var success = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), context.getPublicAuthentication());
            assertEquals(HttpStatus.OK, success.getStatusCode());
            assertNotNull(success.getBody());
            resInfoRes = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser());
            assertEquals(HttpStatus.OK, resInfoRes.getStatusCode());
            assertNotNull(resInfoRes.getBody());
            var resInfoBody = resInfoRes.getBody();
            assertTrue(resInfoBody.getOrderSummary().isFree());
            reservationApiV2Controller.cancelPendingReservation(reservationId);

            jdbcTemplate.update("delete from additional_service_description where additional_service_id_fk = :id", Map.of("id", addServRes.getBody().getId()));
            assertEquals(1, jdbcTemplate.update("delete from additional_service where id = :id", Map.of("id", addServRes.getBody().getId())));
        }

        //buy one ticket
        {
            var form = new ReservationForm();
            var ticketReservation = new TicketReservationModification();
            ticketReservation.setQuantity(1);
            ticketReservation.setTicketCategoryId(retrieveCategories(context).get(0).getId());
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

            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING, context);


            var resInfoRes = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser());
            assertEquals(HttpStatus.OK, resInfoRes.getStatusCode());
            var reservation = resInfoRes.getBody();
            assertNotNull(reservation);
            assertEquals(reservationId, reservation.getId());
            assertEquals(1, reservation.getTicketsByCategory().size());
            assertEquals(1, reservation.getTicketsByCategory().get(0).getTickets().size());

            var selectedTicket = reservation.getTicketsByCategory().get(0).getTickets().get(0);
            assertEquals("field1", selectedTicket.getTicketFieldConfigurationBeforeStandard().get(0).getName());
            assertTrue(selectedTicket.getTicketFieldConfigurationBeforeStandard().get(0).isRequired());
            assertEquals("field2", selectedTicket.getTicketFieldConfigurationAfterStandard().get(0).getName());
            assertFalse(selectedTicket.getTicketFieldConfigurationAfterStandard().get(0).isRequired());

            var contactForm = new ContactAndTicketsForm();
            var validationErrorsRes = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), context.getPublicAuthentication());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, validationErrorsRes.getStatusCode());
            assertNotNull(validationErrorsRes.getBody());
            assertFalse(validationErrorsRes.getBody().isSuccess());
            assertEquals(4, validationErrorsRes.getBody().getErrorCount()); // first name, last name, email + MISSING_ATTENDEE DATA


            // move to overview status
            contactForm = new ContactAndTicketsForm();
            contactForm.setEmail("test@test.com");
            contactForm.setBillingAddress("my billing address");
            contactForm.setFirstName("full");
            contactForm.setLastName("name");

            customizeContactFormForSuccessfulReservation(contactForm);

            var ticketForm = new UpdateTicketOwnerForm();
            ticketForm.setFirstName("ticketfull");
            ticketForm.setLastName("ticketname");
            ticketForm.setEmail("tickettest@test.com");
            contactForm.setTickets(Collections.singletonMap(reservation.getTicketsByCategory().get(0).getTickets().get(0).getUuid(), ticketForm));

            var overviewResFailed = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), context.getPublicAuthentication());
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, overviewResFailed.getStatusCode());
            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING, context);

            //add mandatory additional field
            ticketForm.setAdditional(new HashMap<>(Collections.singletonMap("field1", Collections.singletonList("value"))));
            var overviewRes = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), context.getPublicAuthentication());
            assertEquals(HttpStatus.OK, overviewRes.getStatusCode());
            checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.PENDING, context);
            //

            reservationApiV2Controller.backToBooking(reservationId);

            checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING, context);

            overviewRes = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), context.getPublicAuthentication());
            assertNotNull(overviewRes.getBody());
            assertTrue(overviewRes.getBody().getValue());

            checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.PENDING, context);
            var owner = ticketReservationRepository.getReservationOwnerAndOrganizationId(reservationId);
            if(context.publicUserId != null) {
                assertTrue(owner.isPresent());
                assertEquals(context.publicUserId, owner.get().getUserId());

                // make sure that the profile has been persisted
                var optionalProfile = userRepository.loadUserProfile(context.publicUserId);
                assertTrue(optionalProfile.isPresent());

                // access to the reservation must be denied for anonymous users
                assertThrows(ReservationAccessDenied.class, () -> reservationApiV2Controller.getReservationInfo(reservationId, null));
            } else {
                assertTrue(owner.isEmpty());
            }

            int promoCodeId = promoCodeDiscountRepository.findPromoCodeInEventOrOrganization(context.event.getId(), PROMO_CODE).orElseThrow().getId();

            // initialize and confirm payment
            performAndValidatePayment(context, reservationId, promoCodeId, this::cleanupExtensionLog);

            ensureReservationIsComplete(reservationId, context);

            reservation = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser()).getBody();
            assertNotNull(reservation);
            var orderSummary = reservation.getOrderSummary();
            assertFalse(orderSummary.isNotYetPaid());

            checkDiscountUsage(reservationId, promoCodeId, context);

            var confRes = reservationApiV2Controller.reSendReservationConfirmationEmail(PurchaseContextType.event, context.event.getShortName(), reservationId, "en", context.getPublicUser());
            assertEquals(HttpStatus.OK, confRes.getStatusCode());
            assertNotNull(confRes.getBody());
            assertTrue(confRes.getBody());

            // trigger email processing
            triggerEmailProcessingAndCheck(context, reservationId);

            var ticket = reservation.getTicketsByCategory().stream().findFirst().orElseThrow().getTickets().get(0);
            assertEquals("tickettest@test.com", ticket.getEmail());
            assertEquals("ticketfull", ticket.getFirstName());
            assertEquals("ticketname", ticket.getLastName());

            var ticketNotFoundRes = ticketApiV2Controller.getTicketInfo(context.event.getShortName(), UUID.fromString("00000000-0000-0000-0000-000000000000"));
            assertEquals(HttpStatus.NOT_FOUND, ticketNotFoundRes.getStatusCode());

            var ticketFoundRes = ticketApiV2Controller.getTicketInfo(context.event.getShortName(), UUID.fromString(ticket.getUuid()));
            assertEquals(HttpStatus.OK, ticketFoundRes.getStatusCode());
            var ticketFoundBody = ticketFoundRes.getBody();
            assertNotNull(ticketFoundBody);
            assertEquals("tickettest@test.com", ticketFoundBody.getEmail());
            assertEquals("ticketfull ticketname", ticketFoundBody.getFullName());
            assertEquals("full name", ticketFoundBody.getReservationFullName());
            assertTrue(reservationId.startsWith(ticketFoundBody.getReservationId().toLowerCase(Locale.ENGLISH)));

            var sendTicketByEmailRes = ticketApiV2Controller.sendTicketByEmail(context.event.getShortName(), UUID.fromString(ticket.getUuid()));
            assertEquals(HttpStatus.OK, sendTicketByEmailRes.getStatusCode());
            assertNotNull(sendTicketByEmailRes.getBody());
            assertTrue(sendTicketByEmailRes.getBody());

            // trigger email processing
            triggerEmailProcessingAndCheck(context, reservationId);

            //update ticket
            var updateTicketOwnerForm = new UpdateTicketOwnerForm();
            updateTicketOwnerForm.setFirstName("Test");
            updateTicketOwnerForm.setLastName("Testson");
            updateTicketOwnerForm.setEmail("testmctest@test.com");
            updateTicketOwnerForm.setAdditional(new HashMap<>(Collections.singletonMap("field1", Collections.singletonList("value"))));
            var updateTicketRes = ticketApiV2Controller.updateTicketInfo(context.event.getShortName(), UUID.fromString(ticket.getUuid()), updateTicketOwnerForm, new BeanPropertyBindingResult(updateTicketOwnerForm, "ticket"), context.getPublicAuthentication());
            assertNotNull(updateTicketRes.getBody());
            assertTrue(updateTicketRes.getBody().isSuccess());

            //not found
            assertEquals(HttpStatus.NOT_FOUND, ticketApiV2Controller.updateTicketInfo(context.event.getShortName(), null, updateTicketOwnerForm, new BeanPropertyBindingResult(updateTicketOwnerForm, "ticket"), context.getPublicAuthentication()).getStatusCode());


            ticketFoundRes = ticketApiV2Controller.getTicketInfo(context.event.getShortName(), UUID.fromString(ticket.getUuid()));
            ticketFoundBody = ticketFoundRes.getBody();
            assertNotNull(ticketFoundBody);
            assertEquals("testmctest@test.com", ticketFoundBody.getEmail());
            assertEquals("Test Testson", ticketFoundBody.getFullName());
            assertEquals("full name", ticketFoundBody.getReservationFullName());
            reservation = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser()).getBody();
            assertNotNull(reservation);
            ticket = reservation.getTicketsByCategory().stream().findFirst().orElseThrow().getTickets().get(0);
            assertEquals("testmctest@test.com", ticket.getEmail());
            assertEquals("Test", ticket.getFirstName());
            assertEquals("Testson", ticket.getLastName());


            var ticketPdfMockResp = new MockHttpServletResponse();
            ticketApiV2Controller.generateTicketPdf(context.event.getShortName(), UUID.fromString(ticket.getUuid()), ticketPdfMockResp);
            assertEquals("application/pdf", ticketPdfMockResp.getContentType());
            assertTrue(ticketPdfMockResp.containsHeader(ExportUtils.X_ROBOTS_TAG));

            var ticketQRCodeResp = new MockHttpServletResponse();
            ticketApiV2Controller.showQrCode(context.event.getShortName(), UUID.fromString(ticket.getUuid()), ticketQRCodeResp);
            assertEquals("image/png", ticketQRCodeResp.getContentType());
            assertTrue(ticketQRCodeResp.containsHeader(ExportUtils.X_ROBOTS_TAG));

            var fullTicketInfo = ticketRepository.findByPublicUUID(UUID.fromString(ticket.getUuid()));
            var qrCodeReader = new QRCodeReader();
            var qrCodeRead = qrCodeReader.decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO.read(new ByteArrayInputStream(ticketQRCodeResp.getContentAsByteArray()))))), Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE));
            var text = qrCodeRead.getText();
            assertTrue(text.startsWith(fullTicketInfo.getUuid() + "/")); // ensure that we put
            assertEquals(fullTicketInfo.ticketCode(context.event.getPrivateKey(), context.event.supportsQRCodeCaseInsensitive()), text);

            //can only be done for free tickets
            var releaseTicketFailure = ticketApiV2Controller.releaseTicket(context.event.getShortName(), UUID.fromString(ticket.getUuid()));
            assertEquals(HttpStatus.BAD_REQUEST, releaseTicketFailure.getStatusCode());
            assertEquals(HttpStatus.OK, ticketApiV2Controller.getTicketInfo(context.event.getShortName(), UUID.fromString(ticket.getUuid())).getStatusCode());


            //no invoice, but receipt
            assertEquals(contactForm.isInvoiceRequested() ? HttpStatus.OK : HttpStatus.NOT_FOUND, reservationApiV2Controller.getInvoice(context.event.getShortName(), reservationId, new MockHttpServletResponse(), context.getPublicAuthentication()).getStatusCode());
            assertEquals(contactForm.isInvoiceRequested() ? HttpStatus.NOT_FOUND : HttpStatus.OK, reservationApiV2Controller.getReceipt(context.event.getShortName(), reservationId, new MockHttpServletResponse(), context.getPublicAuthentication()).getStatusCode());



            //

            {
                //clear the extension_log table so that we can check the expectation
                cleanupExtensionLog();

                Principal principal = mock(Principal.class);
                Mockito.when(principal.getName()).thenReturn(context.userId);
                String internalTicketIdentifier = fullTicketInfo.getUuid();
                String eventName = context.event.getShortName();

                // try to search ticket
                var results = checkInApiController.searchAttendees(eventName, fullTicketInfo.getEmail(), 0, principal);
                switch (context.event.getFormat()) {
                    case IN_PERSON, HYBRID:
                        assertTrue(results.getStatusCode().is2xxSuccessful());
                        assertNotNull(results.getBody());
                        var searchResults = results.getBody();
                        assertEquals(1, searchResults.totalPages());
                        var attendees = searchResults.attendees();
                        int count = searchResults.totalResults();
                        assertFalse(searchResults.hasMorePages());
                        assertFalse(attendees.isEmpty());
                        assertEquals(count, attendees.size());
                        assertTrue(attendees.stream().anyMatch(sr -> sr.lastName().equals(fullTicketInfo.getLastName())));
                        assertTrue(attendees.stream().allMatch(sr -> sr.additionalInfo() != null));
                        assertEquals(List.of("value"), attendees.get(0).additionalInfo().get("field1"));
                        break;
                    case ONLINE:
                        assertTrue(results.getStatusCode().is2xxSuccessful());
                        assertNotNull(results.getBody());
                        assertEquals(0, results.getBody().totalResults());
                        break;
                }

                String ticketCode = fullTicketInfo.ticketCode(context.event.getPrivateKey(), context.event.supportsQRCodeCaseInsensitive());
                TicketAndCheckInResult ticketAndCheckInResult = checkInApiController.findTicketWithUUID(context.event.getId(), internalTicketIdentifier, ticketCode, principal);
                assertEquals(CheckInStatus.OK_READY_TO_BE_CHECKED_IN, ticketAndCheckInResult.getResult().getStatus());
                var basicCheckInStatus = checkInApiController.getTicketStatus(context.event.getShortName(), internalTicketIdentifier, principal);
                assertEquals(CheckInStatus.OK_READY_TO_BE_CHECKED_IN, basicCheckInStatus.getCheckInStatus());
                assertNotNull(basicCheckInStatus.getFirstName());
                assertNotNull(basicCheckInStatus.getLastName());
                CheckInApiController.TicketCode tc = new CheckInApiController.TicketCode();
                tc.setCode(ticketCode);
                assertEquals(CheckInStatus.SUCCESS, checkInApiController.checkIn(context.event.getId(), internalTicketIdentifier, tc, principal).getResult().getStatus());
                basicCheckInStatus = checkInApiController.getTicketStatus(context.event.getShortName(), internalTicketIdentifier, principal);
                assertEquals(CheckInStatus.ALREADY_CHECK_IN, basicCheckInStatus.getCheckInStatus());
                assertNotNull(basicCheckInStatus.getFirstName());
                assertNotNull(basicCheckInStatus.getLastName());
                List<ScanAudit> audits = scanAuditRepository.findAllForEvent(context.event.getId());
                assertFalse(audits.isEmpty());
                assertTrue(audits.stream().anyMatch(sa -> sa.getTicketUuid().equals(internalTicketIdentifier)));

                extLogs = extensionLogRepository.getPage(null, null, null, 100, 0);
                assertEventLogged(extLogs, TICKET_CHECKED_IN, 2);

                validateCheckInData(context);

                TicketAndCheckInResult ticketAndCheckInResultOk = checkInApiController.findTicketWithUUID(context.event.getId(), internalTicketIdentifier, ticketCode, principal);
                assertEquals(CheckInStatus.ALREADY_CHECK_IN, ticketAndCheckInResultOk.getResult().getStatus());

                // check stats after check in one ticket
                assertTrue(eventStatisticsManager.getTicketSoldStatistics(context.event.getId(), statisticsFrom, statisticsTo, "day").stream().mapToLong(TicketsByDateStatistic::getCount).sum() > 0);
                EventWithAdditionalInfo eventWithAdditionalInfo3 = eventStatisticsManager.getEventWithAdditionalInfo(context.event.getShortName(), context.userId);
                assertEquals(2, eventWithAdditionalInfo3.getNotSoldTickets());
                assertEquals(0, eventWithAdditionalInfo3.getSoldTickets());
                assertEquals(20, eventWithAdditionalInfo3.getAvailableSeats());
                assertEquals(1, eventWithAdditionalInfo3.getCheckedInTickets());

                // at this point we should not be able to cancel the reservation anymore
                var removeResult = adminReservationManager.removeReservation(
                    PurchaseContextType.event,
                    context.event.getShortName(),
                    reservationId,
                    true,
                    true,
                    true,
                    context.userId
                );
                assertFalse(removeResult.isSuccess());

                // trying to remove the ticket would result in a runtime exception
                assertThrows(IncompatibleStateException.class, () -> adminReservationManager.removeTickets(
                    context.event.getShortName(),
                    reservationId,
                    List.of(fullTicketInfo.getId()),
                    List.of(),
                    false,
                    false,
                    context.userId
                ));

                checkReservationExport(context);

                //test revert check in
                assertTrue(checkInApiController.revertCheckIn(context.event.getId(), internalTicketIdentifier, principal));

                assertFalse(checkInApiController.revertCheckIn(context.event.getId(), internalTicketIdentifier, principal));
                TicketAndCheckInResult ticketAndCheckInResult2 = checkInApiController.findTicketWithUUID(context.event.getId(), internalTicketIdentifier, ticketCode, principal);
                assertEquals(CheckInStatus.OK_READY_TO_BE_CHECKED_IN, ticketAndCheckInResult2.getResult().getStatus());

                UsersApiController.UserWithPasswordAndQRCode sponsorUser = usersApiController.insertUser(new UserModification(null, context.event.getOrganizationId(), "SPONSOR", "sponsor", "first", "last", "email@email.com", User.Type.INTERNAL, null, null), "http://localhost:8080", principal);
                Principal sponsorPrincipal = mock(Principal.class);
                Mockito.when(sponsorPrincipal.getName()).thenReturn(sponsorUser.getUsername());

                // check failures
                assertEquals(CheckInStatus.EVENT_NOT_FOUND, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest("not-existing-event", "not-existing-ticket", null, null, null), sponsorPrincipal, null).getBody().getResult().getStatus());
                assertEquals(CheckInStatus.TICKET_NOT_FOUND, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, "not-existing-ticket", null, null, null), sponsorPrincipal, null).getBody().getResult().getStatus());
                assertEquals(CheckInStatus.INVALID_TICKET_STATE, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, internalTicketIdentifier, null, null, null), sponsorPrincipal, null).getBody().getResult().getStatus());
                //


                // check stats after revert check in one ticket
                assertTrue(eventStatisticsManager.getTicketSoldStatistics(context.event.getId(), statisticsFrom, statisticsTo, "day").stream().mapToLong(TicketsByDateStatistic::getCount).sum() > 0);
                EventWithAdditionalInfo eventWithAdditionalInfo4 = eventStatisticsManager.getEventWithAdditionalInfo(context.event.getShortName(), context.userId);
                assertEquals(2, eventWithAdditionalInfo4.getNotSoldTickets());
                assertEquals(1, eventWithAdditionalInfo4.getSoldTickets());
                assertEquals(20, eventWithAdditionalInfo4.getAvailableSeats());
                assertEquals(0, eventWithAdditionalInfo4.getCheckedInTickets());


                cleanupExtensionLog();

                CheckInApiController.TicketCode tc2 = new CheckInApiController.TicketCode();
                tc2.setCode(ticketCode);
                TicketAndCheckInResult ticketAndcheckInResult = checkInApiController.checkIn(context.event.getId(), internalTicketIdentifier, tc2, principal);
                assertEquals(CheckInStatus.SUCCESS, ticketAndcheckInResult.getResult().getStatus());

                extLogs = extensionLogRepository.getPage(null, null, null, 100, 0);
                assertEventLogged(extLogs, TICKET_CHECKED_IN, 2);

                var offlineIdentifiers = checkInApiController.getOfflineIdentifiers(context.event.getShortName(), 0L, new MockHttpServletResponse(), principal);

                if(context.checkInStationsEnabled) {
                    assertFalse(offlineIdentifiers.isEmpty(), "Alf.io-PI integration must be enabled by default");

                    //disable Alf.io-PI
                    configurationRepository.insert(ConfigurationKeys.ALFIO_PI_INTEGRATION_ENABLED.name(), "false", null);
                    offlineIdentifiers = checkInApiController.getOfflineIdentifiers(context.event.getShortName(), 0L, new MockHttpServletResponse(), principal);
                    assertTrue(offlineIdentifiers.isEmpty());

                    //re-enable Alf.io-PI
                    configurationRepository.insertEventLevel(context.event.getOrganizationId(), context.event.getId(), ConfigurationKeys.OFFLINE_CHECKIN_ENABLED.name(), "true", null);
                    configurationRepository.update(ConfigurationKeys.ALFIO_PI_INTEGRATION_ENABLED.name(), "true");
                    offlineIdentifiers = checkInApiController.getOfflineIdentifiers(context.event.getShortName(), 0L, new MockHttpServletResponse(), principal);
                    assertFalse(offlineIdentifiers.isEmpty());
                    // download encrypted ticket data
                    TicketWithCategory ticketwc = testEncryptedCheckInPayload(principal, ticketAndcheckInResult, offlineIdentifiers, false, context);

                    // insert a poll and download again encrypted data. This time we expect a pin to be present because we haven't specified a tag
                    var rowCountAndKey = pollRepository.insert(Map.of("en", "test poll"), null, List.of(), 0, context.event.getId(), context.event.getOrganizationId());
                    testEncryptedCheckInPayload(principal, ticketAndcheckInResult, offlineIdentifiers, true, context);

                    // we define a tag for the poll, this time we won't have a pin in the result
                    pollRepository.update(Map.of("en", "test poll"), null, List.of("blabla"), 0, rowCountAndKey.getKey(), context.event.getId());
                    testEncryptedCheckInPayload(principal, ticketAndcheckInResult, offlineIdentifiers, false, context);

                    // now we add a matching tag to the ticket. As a result, the pin must be included in the result
                    ticketRepository.updateTicketTags(List.of(ticketwc.getId()), List.of("blabla"));
                    testEncryptedCheckInPayload(principal, ticketAndcheckInResult, offlineIdentifiers, true, context);

                    //

                    // check register sponsor scan success flow
                    assertTrue(attendeeApiController.getScannedBadges(context.event.getShortName(), EventUtil.JSON_DATETIME_FORMATTER.format(LocalDateTime.of(1970, 1, 1, 0, 0)), sponsorPrincipal).getBody().isEmpty());
                    assertEquals(CheckInStatus.SUCCESS, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, ticketwc.getUuid(), null, null, null), sponsorPrincipal, null).getBody().getResult().getStatus());
                    assertEquals(CheckInStatus.SUCCESS, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, ticketwc.getUuid(), null, null, null), sponsorPrincipal, null).getBody().getResult().getStatus());
                    // scanned badges returns only unique values for a limited subset of columns
                    assertEquals(1, attendeeApiController.getScannedBadges(context.event.getShortName(), EventUtil.JSON_DATETIME_FORMATTER.format(LocalDateTime.of(1970, 1, 1, 0, 0)), sponsorPrincipal).getBody().size());

                    // check export
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    eventApiController.downloadSponsorScanExport(context.event.getShortName(), "csv", response, principal);
                    response.getContentAsString();
                    var schema = CsvSchema.builder().setUseHeader(true).setQuoteChar('"').build();
                    var mapper = new CsvMapper();
                    record SponsorScanRecord(
                        @JsonProperty("Username/Api Key") String username,
                        @JsonProperty("Description") String description,
                        @JsonProperty("Timestamp") String timestamp,
                        @JsonProperty("Full name") String fullName,
                        @JsonProperty("Email") String email,
                        @JsonProperty("field1") String field1,
                        @JsonProperty("field2") String field2,
                        @JsonProperty("field3") String field3,
                        @JsonProperty("Sponsor notes") String notes,
                        @JsonProperty("Lead Status") SponsorScan.LeadStatus leadStatus,
                        @JsonProperty("Operator") String operator
                    ) {}

                    MappingIterator<SponsorScanRecord> values = mapper.readerFor(SponsorScanRecord.class)
                        .with(schema)
                        .readValues(BOMInputStream.builder().setReader(new StringReader(response.getContentAsString())).get());

                    List<SponsorScanRecord> csvSponsorScan = values.readAll();
                    assertEquals(1, csvSponsorScan.size());
                    assertEquals("sponsor", csvSponsorScan.get(0).username);
                    assertEquals("Test Testson", csvSponsorScan.get(0).fullName);
                    assertEquals("testmctest@test.com", csvSponsorScan.get(0).email);
                    assertEquals("", csvSponsorScan.get(0).notes);
                    assertEquals(SponsorScan.LeadStatus.WARM, csvSponsorScan.get(0).leadStatus);
                    assertEquals(AttendeeManager.DEFAULT_OPERATOR_ID, csvSponsorScan.get(0).operator);
                    //

                    // check update notes
                    // we need to load the internal UUID before scanning
                    var internalUUID = ticketRepository.getInternalUuid(UUID.fromString(ticket.getUuid()), context.event.getId()).orElseThrow();
                    assertEquals(CheckInStatus.SUCCESS, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, internalUUID, "this is a very good lead!", "HOT", null), sponsorPrincipal, null).getBody().getResult().getStatus());
                    var scannedBadges = attendeeApiController.getScannedBadges(context.event.getShortName(), EventUtil.JSON_DATETIME_FORMATTER.format(LocalDateTime.of(1970, 1, 1, 0, 0)), sponsorPrincipal).getBody();
                    assertEquals(1, requireNonNull(scannedBadges).size());
                    assertEquals(CheckInStatus.SUCCESS, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, internalUUID, "this is a very good lead!", "HOT", null), sponsorPrincipal, null).getBody().getResult().getStatus());
                    scannedBadges = attendeeApiController.getScannedBadges(context.event.getShortName(), EventUtil.JSON_DATETIME_FORMATTER.format(LocalDateTime.of(1970, 1, 1, 0, 0)), sponsorPrincipal).getBody();
                    assertEquals(1, requireNonNull(scannedBadges).size());
                    response = new MockHttpServletResponse();
                    eventApiController.downloadSponsorScanExport(context.event.getShortName(), "csv", response, principal);
                    values = mapper.readerFor(SponsorScanRecord.class)
                        .with(schema)
                        .readValues(BOMInputStream.builder().setReader(new StringReader(response.getContentAsString())).get());
                    csvSponsorScan = values.readAll();
                    assertEquals(1, csvSponsorScan.size());
                    assertEquals("sponsor", csvSponsorScan.get(0).username);
                    assertEquals("Test Testson", csvSponsorScan.get(0).fullName);
                    assertEquals("testmctest@test.com", csvSponsorScan.get(0).email);
                    assertEquals("this is a very good lead!", csvSponsorScan.get(0).notes);
                    assertEquals(SponsorScan.LeadStatus.HOT, csvSponsorScan.get(0).leadStatus);
                    assertEquals(AttendeeManager.DEFAULT_OPERATOR_ID, csvSponsorScan.get(0).operator);

                    // scan from a different operator
                    response = new MockHttpServletResponse();
                    assertEquals(CheckInStatus.SUCCESS, attendeeApiController.scanBadge(new AttendeeApiController.SponsorScanRequest(eventName, ticketwc.getUuid(), null, null, null), sponsorPrincipal, "OP2").getBody().getResult().getStatus());
                    eventApiController.downloadSponsorScanExport(context.event.getShortName(), "csv", response, principal);
                    values = mapper.readerFor(SponsorScanRecord.class)
                        .with(schema)
                        .readValues(BOMInputStream.builder().setReader(new StringReader(response.getContentAsString())).get());
                    csvSponsorScan = values.readAll();
                    assertEquals(2, csvSponsorScan.size());
                    assertEquals("sponsor", csvSponsorScan.get(0).username);
                    assertEquals("Test Testson", csvSponsorScan.get(0).fullName);
                    assertEquals("testmctest@test.com", csvSponsorScan.get(0).email);
                    assertEquals("this is a very good lead!", csvSponsorScan.get(0).notes);
                    assertEquals(SponsorScan.LeadStatus.HOT, csvSponsorScan.get(0).leadStatus);
                    assertEquals(AttendeeManager.DEFAULT_OPERATOR_ID, csvSponsorScan.get(0).operator);

                    assertEquals("sponsor", csvSponsorScan.get(1).username);
                    assertEquals("Test Testson", csvSponsorScan.get(1).fullName);
                    assertEquals("testmctest@test.com", csvSponsorScan.get(1).email);
                    assertEquals("", csvSponsorScan.get(1).notes);
                    assertEquals("OP2", csvSponsorScan.get(1).operator);

                    // #742 - test multiple check-ins

                    // since on the badge we don't have the full ticket info, we will pass in "null" as scanned code
                    CheckInApiController.TicketCode badgeScan = new CheckInApiController.TicketCode();
                    badgeScan.setCode(null);
                    ticketAndcheckInResult = checkInApiController.checkIn(context.event.getId(), internalTicketIdentifier, badgeScan, principal);
                    // ONCE_PER_DAY is disabled by default, therefore we get an error
                    assertEquals(CheckInStatus.EMPTY_TICKET_CODE, ticketAndcheckInResult.getResult().getStatus());
                    // enable ONCE_PER_DAYFalse
                    TicketCategory category = ticketCategoryRepository.getById(ticketwc.getCategoryId());
                    ticketCategoryRepository.update(category.getId(), category.getName(), category.getInception(context.event.getZoneId()), category.getExpiration(context.event.getZoneId()), category.getMaxTickets(), category.isAccessRestricted(),
                        MonetaryUtil.unitToCents(category.getPrice(), category.getCurrencyCode()), category.getCode(), category.getValidCheckInFrom(), category.getValidCheckInTo(), category.getTicketValidityStart(), category.getTicketValidityEnd(),
                        TicketCategory.TicketCheckInStrategy.ONCE_PER_DAY,
                        category.getTicketAccessType()
                    );
                    ticketAndcheckInResult = checkInApiController.checkIn(context.event.getId(), internalTicketIdentifier, badgeScan, principal);
                    // the event start date is in one week, so we expect an error here
                    assertEquals(CheckInStatus.INVALID_TICKET_CATEGORY_CHECK_IN_DATE, ticketAndcheckInResult.getResult().getStatus());

                    eventRepository.updateHeader(context.event.getId(), context.event.getDisplayName(), context.event.getWebsiteUrl(), context.event.getExternalUrl(), context.event.getTermsAndConditionsUrl(), context.event.getPrivacyPolicyUrl(), context.event.getImageUrl(),
                        context.event.getFileBlobId(), context.event.getLocation(), context.event.getLatitude(), context.event.getLongitude(), context.event.now(clockProvider).minusSeconds(1), context.event.getEnd(), context.event.getTimeZone(),
                        context.event.getOrganizationId(), context.event.getLocales(), context.event.getFormat());

                    ticketAndcheckInResult = checkInApiController.checkIn(context.event.getId(), internalTicketIdentifier, badgeScan, principal);
                    // we have already scanned the ticket today, so we expect to receive a warning
                    assertEquals(CheckInStatus.BADGE_SCAN_ALREADY_DONE, ticketAndcheckInResult.getResult().getStatus());
                    assertEquals(1, (int) auditingRepository.countAuditsOfTypeForReservation(reservationId, Audit.EventType.BADGE_SCAN));

                    // move the scans to yesterday
                    // we expect 3 rows because:
                    // 1 check-in
                    // 1 revert
                    // 1 badge scan
                    assertEquals(3, jdbcTemplate.update("update auditing set event_time = event_time - interval '1 day' where reservation_id = :reservationId and event_type in ('BADGE_SCAN', 'CHECK_IN')", Map.of("reservationId", reservationId)));

                    ticketAndcheckInResult = checkInApiController.checkIn(context.event.getId(), internalTicketIdentifier, badgeScan, principal);
                    // we now expect to receive a successful message
                    assertEquals(CheckInStatus.BADGE_SCAN_SUCCESS, ticketAndcheckInResult.getResult().getStatus());
                    assertEquals(2, (int) auditingRepository.countAuditsOfTypeForReservation(reservationId, Audit.EventType.BADGE_SCAN));
                } else {
                    assertTrue(offlineIdentifiers.isEmpty(), "Alf.io-PI integration must be disabled");
                }
            }
            performAdditionalTests(context);
            eventManager.deleteEvent(context.event.getId(), context.userId);
            assertTrue(organizationDeleter.deleteOrganization(context.event.getOrganizationId(), new APITokenAuthentication("TEST", "", List.of(new SimpleGrantedAuthority("ROLE_" + SYSTEM_API_CLIENT)))));
        }

    }

    protected void customizeContactFormForSuccessfulReservation(ContactAndTicketsForm contactForm) {

    }

    protected void ensureReservationIsComplete(String reservationId, ReservationFlowContext context) {
        checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.COMPLETE, context);
    }

    private void checkReservationExport(ReservationFlowContext context) {
        Principal principal = mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(context.userId);
        // load all reservations
        var now = LocalDate.now(clockProvider.getClock());
        var reservationsByEvent = exportManager.reservationsForInterval(now.minusDays(1), now, principal);
        assertEquals(1, reservationsByEvent.size());
        assertEquals(1, reservationsByEvent.get(0).getReservations().size());
        assertEquals(1, reservationsByEvent.get(0).getReservations().get(0).getTickets().size());

        // ensure that the filtering works as expected
        reservationsByEvent = exportManager.reservationsForInterval(now.plusDays(1), now.plusDays(2), principal);
        assertEquals(0, reservationsByEvent.size());

        // ensure that we get error if the interval is wrong
        var wrongFrom = now.plusDays(1);
        assertThrows(IllegalArgumentException.class, () -> exportManager.reservationsForInterval(wrongFrom, now, principal));
    }

    static void insertExtension(ExtensionService extensionService, String path, Stream<String> events) throws IOException {
        insertExtension(extensionService, path, true, true, "Extension", events);
    }

    static Stream<String> allEvents() {
        return Arrays.stream(ExtensionEvent.values()).map(ee -> "'"+ee.name()+"'");
    }

    static void insertExtension(ExtensionService extensionService, String path, boolean async, boolean sync, String name, Stream<String> events) throws IOException {
        try (var extensionInputStream = requireNonNull(BaseReservationFlowTest.class.getResourceAsStream(path))) {
            List<String> extensionStream = IOUtils.readLines(new InputStreamReader(extensionInputStream, StandardCharsets.UTF_8));
            String concatenation = String.join("\n", extensionStream).replace("EVENTS", events.collect(Collectors.joining(",")));
            if (sync) {
                extensionService.createOrUpdate(null, null, new Extension("-", "sync"+name, concatenation.replace("placeHolder", "false"), true));
            }
            if (async) {
                extensionService.createOrUpdate(null, null, new Extension("-", "async"+name, concatenation.replace("placeHolder", "true"), true));
            }
        }
    }

    protected void validateCheckInData(ReservationFlowContext context) {

    }

    protected void performAndValidatePayment(ReservationFlowContext context,
                                             String reservationId,
                                             int promoCodeId,
                                             Runnable cleanupExtensionLog) {
        ReservationInfo reservation;
        var paymentForm = new PaymentForm();
        var handleResError = reservationApiV2Controller.confirmOverview(reservationId, "en", paymentForm, new BeanPropertyBindingResult(paymentForm, "paymentForm"),
            new MockHttpServletRequest(), context.getPublicUser());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, handleResError.getStatusCode());


        paymentForm.setPrivacyPolicyAccepted(true);
        paymentForm.setTermAndConditionsAccepted(true);
        paymentForm.setPaymentProxy(PaymentProxy.OFFLINE);
        paymentForm.setSelectedPaymentMethod(PaymentMethod.BANK_TRANSFER);

        // bank transfer does not have a transaction, it's created on confirmOverview call
        var tStatus = reservationApiV2Controller.getTransactionStatus(reservationId, "BANK_TRANSFER");
        assertEquals(HttpStatus.NOT_FOUND, tStatus.getStatusCode());
        //
        var promoCodeUsage = promoCodeRequestManager.retrieveDetailedUsage(promoCodeId, context.event.getId());
        assertTrue(promoCodeUsage.isEmpty());

        var handleRes = reservationApiV2Controller.confirmOverview(reservationId, "en", paymentForm, new BeanPropertyBindingResult(paymentForm, "paymentForm"),
            new MockHttpServletRequest(), context.getPublicUser());

        assertEquals(HttpStatus.OK, handleRes.getStatusCode());

        checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT, context);

        tStatus = reservationApiV2Controller.getTransactionStatus(reservationId, "BANK_TRANSFER");
        assertEquals(HttpStatus.OK, tStatus.getStatusCode());
        assertNotNull(tStatus.getBody());
        assertFalse(tStatus.getBody().isSuccess());

        reservation = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser()).getBody();
        assertNotNull(reservation);
        checkOrderSummary(reservation, context);
        cleanupExtensionLog.run();
        validatePayment(context.event.getShortName(), reservationId, context);

        var extLogs = extensionLogRepository.getPage(null, null, null, 100, 0);

        boolean online = containsOnlineTickets(context, reservationId);
        assertEventLogged(extLogs, RESERVATION_CONFIRMED, online ? 12 : 10);
        assertEventLogged(extLogs, CONFIRMATION_MAIL_CUSTOM_TEXT, online ? 12 : 10);
        assertEventLogged(extLogs, TICKET_ASSIGNED, online ? 12 : 10);
        if(online) {
            assertEventLogged(extLogs, CUSTOM_ONLINE_JOIN_URL, 12);
        }
        assertEventLogged(extLogs, TICKET_ASSIGNED_GENERATE_METADATA, online ? 12 : 10);
        assertEventLogged(extLogs, TICKET_MAIL_CUSTOM_TEXT, online ? 12 : 10);

        tStatus = reservationApiV2Controller.getTransactionStatus(reservationId, "BANK_TRANSFER");
        assertEquals(HttpStatus.OK, tStatus.getStatusCode());
        assertNotNull(tStatus.getBody());
        assertTrue(tStatus.getBody().isSuccess());
    }

    protected void checkDiscountUsage(String reservationId, int promoCodeId, ReservationFlowContext context) {
        var promoCodeUsage = promoCodeRequestManager.retrieveDetailedUsage(promoCodeId, context.event.getId());
        assertTrue(promoCodeUsage.isEmpty());
    }

    protected void checkOrderSummary(ReservationInfo reservation, ReservationFlowContext context) {
        var orderSummary = reservation.getOrderSummary();
        assertTrue(orderSummary.isNotYetPaid());
        assertEquals(context.vatIncluded ? "10.00" : "10.10", orderSummary.getTotalPrice());
        assertEquals("0.10", orderSummary.getTotalVAT());
        assertEquals("1.00", orderSummary.getVatPercentage());
    }

    private List<alfio.controller.api.v2.model.TicketCategory> retrieveCategories(ReservationFlowContext context) {
        var response = requireNonNull(eventApiV2Controller.getTicketCategories(context.event.getShortName(), null));
        return requireNonNull(response.getBody()).ticketCategories();
    }

    protected void performAdditionalTests(ReservationFlowContext reservationFlowContext) {}

    private TicketWithCategory testEncryptedCheckInPayload(Principal principal,
                                                           TicketAndCheckInResult ticketAndcheckInResult,
                                                           List<Integer> offlineIdentifiers,
                                                           boolean expectPin,
                                                           ReservationFlowContext context) {
        Map<String, String> payload = checkInApiController.getOfflineEncryptedInfo(context.event.getShortName(), Collections.emptyList(), offlineIdentifiers, principal);
        assertEquals(1, payload.size());
        TicketWithCategory ticketwc = ticketAndcheckInResult.getTicket();
        String ticketKey = ticketwc.ticket().hmacTicketInfo(context.event.getPrivateKey(), true);
        assertNotEquals(ticketKey, ticketwc.ticket().hmacTicketInfo(context.event.getPrivateKey(), false));
        String hashedTicketKey = DigestUtils.sha256Hex(ticketKey);
        String encJson = payload.get(hashedTicketKey);
        assertNotNull(encJson);
        String ticketPayload = CheckInManagerInvoker.decrypt(ticketwc.getUuid() + "/" + ticketKey, encJson);
        Map<String, String> jsonPayload = Json.fromJson(ticketPayload, new TypeReference<>() {
        });
        assertNotNull(jsonPayload);
        assertEquals(expectPin ? 10 : 9, jsonPayload.size());
        assertEquals("Test", jsonPayload.get("firstName"));
        assertEquals("Testson", jsonPayload.get("lastName"));
        assertEquals("Test Testson", jsonPayload.get("fullName"));
        assertEquals(ticketwc.getUuid(), jsonPayload.get("uuid"));
        assertEquals("testmctest@test.com", jsonPayload.get("email"));
        assertEquals("CHECKED_IN", jsonPayload.get("status"));
        assertEquals("default", jsonPayload.get("category"));
        if(expectPin) {
            assertEquals(PinGenerator.uuidToPin(ticketwc.getUuid()), jsonPayload.get("pin"));
        }
        assertEquals(TicketCategory.TicketCheckInStrategy.ONCE_PER_EVENT.name(), jsonPayload.get("categoryCheckInStrategy"));
        return ticketwc;
    }

    protected void testAddSubscription(ReservationFlowContext context, int numberOfTickets, String categoryName) {
        var form = new ReservationForm();
        var ticketReservation = new TicketReservationModification();
        ticketReservation.setQuantity(numberOfTickets);
        var categoriesResponse = eventApiV2Controller.getTicketCategories(context.event.getShortName(), null);
        assertTrue(categoriesResponse.getStatusCode().is2xxSuccessful());
        assertNotNull(categoriesResponse.getBody());
        var categoryId = categoriesResponse.getBody().ticketCategories().stream()
            .filter(t -> t.getName().equals(categoryName))
            .findFirst()
            .orElseThrow()
            .getId();
        ticketReservation.setTicketCategoryId(categoryId);
        form.setReservation(Collections.singletonList(ticketReservation));
        var res = eventApiV2Controller.reserveTickets(context.event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), null);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        var resBody = res.getBody();
        assertNotNull(resBody);
        assertTrue(resBody.isSuccess());
        assertEquals(0, resBody.getErrorCount());
        var reservationId = resBody.getValue();

        checkStatus(reservationId, HttpStatus.OK, false, TicketReservation.TicketReservationStatus.PENDING, context);

        var resInfoRes = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser());
        assertEquals(HttpStatus.OK, resInfoRes.getStatusCode());
        var reservation = resInfoRes.getBody();
        assertNotNull(reservation);
        assertEquals(reservationId, reservation.getId());
        assertEquals(1, reservation.getTicketsByCategory().size());
        assertEquals(numberOfTickets, reservation.getTicketsByCategory().get(0).getTickets().size());

        // move to overview status
        var contactForm = new ContactAndTicketsForm();
        contactForm.setEmail("test@test.com");
        contactForm.setBillingAddress("my billing address");
        contactForm.setFirstName("full");
        contactForm.setLastName("name");

        var tickets = reservation.getTicketsByCategory().get(0).getTickets().stream()
            .map(t -> {
                var ticketForm = new UpdateTicketOwnerForm();
                ticketForm.setFirstName("ticketfull");
                ticketForm.setLastName("ticketname");
                ticketForm.setEmail("tickettest@test.com");
                ticketForm.setAdditional(new HashMap<>(Collections.singletonMap("field1", Collections.singletonList("value"))));
                return Map.entry(t.getUuid(), ticketForm);
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        contactForm.setTickets(tickets);
        var overviewRes = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), context.getPublicAuthentication());
        assertEquals(HttpStatus.OK, overviewRes.getStatusCode());
        checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.PENDING, context);

        var reservationCodeForm = new ReservationCodeForm();
        reservationCodeForm.setCode(Objects.requireNonNullElseGet(context.subscriptionPin, context.subscriptionId::toString));
        reservationCodeForm.setType(ReservationCodeForm.ReservationCodeType.SUBSCRIPTION);
        var bindingResult = new BeanPropertyBindingResult(reservationCodeForm, "reservationCodeForm");
        var codeApplicationResult = reservationApiV2Controller.applyCode(reservationId, reservationCodeForm, bindingResult);

        assertTrue(codeApplicationResult.getStatusCode().is2xxSuccessful());
        var codeApplicationResponse = codeApplicationResult.getBody();
        assertNotNull(codeApplicationResponse);
        assertTrue(codeApplicationResponse.isSuccess());
        assertFalse(bindingResult.hasErrors(), bindingResult::toString);
        assertEquals(true, codeApplicationResponse.getValue());

        // reload reservation, and assert it is now free of charge
        resInfoRes = reservationApiV2Controller.getReservationInfo(reservationId, context.getPublicUser());
        assertEquals(HttpStatus.OK, resInfoRes.getStatusCode());
        reservation = resInfoRes.getBody();
        assertNotNull(reservation);
        assertTrue(reservation.getOrderSummary().isFree());

        // assert that there is a row in the summary for the subscription
        var summaryRowSubscription = reservation.getOrderSummary().getSummary().stream().filter(r -> r.getType() == SummaryRow.SummaryType.APPLIED_SUBSCRIPTION).findFirst();
        assertTrue(summaryRowSubscription.isPresent());
        assertEquals(numberOfTickets, summaryRowSubscription.get().getAmount());

        // proceed with the confirmation
        var paymentForm = new PaymentForm();
        paymentForm.setPrivacyPolicyAccepted(true);
        paymentForm.setTermAndConditionsAccepted(true);
        paymentForm.setSelectedPaymentMethod(PaymentMethod.NONE);

        var propertyBindingResult = new BeanPropertyBindingResult(paymentForm, "paymentForm");
        var handleRes = reservationApiV2Controller.confirmOverview(reservationId, "en", paymentForm, propertyBindingResult, new MockHttpServletRequest(), null);

        log.warn("received {}", propertyBindingResult);

        assertEquals(HttpStatus.OK, handleRes.getStatusCode());

        checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.COMPLETE, context);

        // trigger email processing
        triggerEmailProcessingAndCheck(context, reservationId);
    }

    private void triggerEmailProcessingAndCheck(ReservationFlowContext context, String reservationId) {
        int result = notificationManager.sendWaitingMessages();
        assertTrue(result > 0);
        assertTrue(notificationManager.loadAllMessagesForReservationId(context.event, reservationId).stream().allMatch(m -> m.getStatus() == EmailMessage.Status.SENT));
    }

    private void cleanupExtensionLog() {
        jdbcTemplate.update("delete from extension_log", Map.of());
    }

    protected void assertEventLogged(List<ExtensionLog> extLog, ExtensionEvent event, int logSize) {
        assertEquals(logSize, extLog.size()); // each event logs exactly two logs
        assertTrue(extLog.stream().anyMatch(l -> l.getDescription().equals(event.name())));
    }

    protected void assertEventLogged(List<ExtensionLog> extLog, ExtensionEvent event) {
        assertTrue(extLog.stream().anyMatch(l -> l.getDescription().equals(event.name())), event.name() + " not found");
    }

    protected final void checkStatus(String reservationId,
                             HttpStatus expectedHttpStatus,
                             Boolean validated,
                             TicketReservation.TicketReservationStatus reservationStatus,
                             ReservationFlowContext context) {
        var statusRes = reservationApiV2Controller.getReservationStatus(reservationId);
        assertEquals(expectedHttpStatus, statusRes.getStatusCode());
        var status = statusRes.getBody();
        if (validated != null) {
            assertEquals(validated, requireNonNull(status).isValidatedBookingInformation());
        }

        if (reservationStatus != null) {
            assertEquals(reservationStatus, requireNonNull(status).getStatus());
        }
    }

    protected void validatePayment(String eventName, String reservationIdentifier, ReservationFlowContext context) {
        Principal principal = mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(context.userId);
        var reservation = ticketReservationRepository.findReservationById(reservationIdentifier);
        assertEquals(context.vatIncluded ? 1000 : 1010, reservation.getFinalPriceCts());
        assertEquals(1000, reservation.getSrcPriceCts());
        assertEquals(10, reservation.getVatCts());
        assertEquals(0, reservation.getDiscountCts());
        assertEquals(1, eventApiController.getPendingPayments(eventName, principal).size());
        confirmPayment(eventName, reservationIdentifier, principal);
        assertEquals(0, eventApiController.getPendingPayments(eventName, principal).size());
        assertEquals(context.vatIncluded ? 1000 : 1010, eventRepository.getGrossIncome(context.event.getId()));
    }

    private void confirmPayment(String eventName, String reservationIdentifier, Principal principal) {
        assertEquals("OK", eventApiController.confirmPayment(eventName, reservationIdentifier, null, principal));
    }

    private void checkCalendar(String eventName) {
        MockHttpServletResponse resIcal = new MockHttpServletResponse();
        eventApiV2Controller.getCalendar(eventName, "en", null, null, resIcal);
        assertEquals("text/calendar", resIcal.getContentType());

        MockHttpServletResponse resGoogleCal = new MockHttpServletResponse();
        eventApiV2Controller.getCalendar(eventName, "en", "google", null, resGoogleCal);
        assertTrue(requireNonNull(resGoogleCal.getRedirectedUrl()).startsWith("https://www.google.com/calendar/event"));
    }

    private boolean containsOnlineTickets(ReservationFlowContext context, String reservationId) {
        if(context.event.getFormat() == Event.EventFormat.IN_PERSON) {
            return false;
        }
        if(context.event.getFormat() == Event.EventFormat.ONLINE) {
            return true;
        }

        Integer count = jdbcTemplate.queryForObject("select count(*) from ticket t join ticket_category tc on t.category_id = tc.id where t.tickets_reservation_id = :reservationId and tc.ticket_access_type = 'ONLINE'",
            Map.of("reservationId", reservationId),
            Integer.class);
        return count != null && count > 0;
    }

}
