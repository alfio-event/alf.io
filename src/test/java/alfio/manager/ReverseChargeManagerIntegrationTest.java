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
package alfio.manager;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.controller.api.v2.model.ReservationInfo;
import alfio.controller.api.v2.user.EventApiV2Controller;
import alfio.controller.api.v2.user.ReservationApiV2Controller;
import alfio.controller.form.ContactAndTicketsForm;
import alfio.controller.form.ReservationForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.SummaryRow;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.system.Configuration;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.context.request.ServletWebRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class ReverseChargeManagerIntegrationTest extends BaseIntegrationTest {

    private final ClockProvider clockProvider;
    private final OrganizationRepository organizationRepository;
    private final UserManager userManager;
    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final ConfigurationManager configurationManager;
    private final ConfigurationRepository configurationRepository;
    private final EventApiV2Controller eventApiV2Controller;
    private final ReservationApiV2Controller reservationApiV2Controller;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;

    private Event event;

    @Autowired
    public ReverseChargeManagerIntegrationTest(ClockProvider clockProvider,
                                               OrganizationRepository organizationRepository,
                                               UserManager userManager,
                                               EventManager eventManager,
                                               EventRepository eventRepository,
                                               ConfigurationManager configurationManager,
                                               ConfigurationRepository configurationRepository,
                                               EventApiV2Controller eventApiV2Controller,
                                               ReservationApiV2Controller reservationApiV2Controller,
                                               TicketCategoryRepository ticketCategoryRepository,
                                               TicketRepository ticketRepository) {
        this.clockProvider = clockProvider;
        this.organizationRepository = organizationRepository;
        this.userManager = userManager;
        this.eventManager = eventManager;
        this.eventRepository = eventRepository;
        this.configurationManager = configurationManager;
        this.configurationRepository = configurationRepository;
        this.eventApiV2Controller = eventApiV2Controller;
        this.reservationApiV2Controller = reservationApiV2Controller;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketRepository = ticketRepository;
    }

    @BeforeEach
    void setUp() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.IN_PERSON, AVAILABLE_SEATS - 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "online", TicketCategory.TicketAccessType.ONLINE, -1,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.ONE, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, null, Event.EventFormat.HYBRID);
        event = eventAndUser.getLeft();
        configurationManager.saveConfig(Configuration.from(event, ENABLE_EU_VAT_DIRECTIVE), "true");
        configurationManager.saveConfig(Configuration.from(event, COUNTRY_OF_BUSINESS), "IT");
        configurationManager.saveConfig(Configuration.from(event, APPLY_VAT_FOREIGN_BUSINESS), "false");
        configurationManager.saveConfig(Configuration.from(event.getOrganizationId(), VAT_NR), "1234567890");
        configurationManager.saveConfig(Configuration.from(event.getOrganizationId(), INVOICE_ADDRESS), "test address");
        configurationManager.saveConfig(Configuration.from(event.getOrganizationId(), GENERATE_ONLY_INVOICE), "true");
    }

    @Test
    void applyReverseChargeForOnlineTicket() {
        // disable Reverse Charge for in-person tickets
        configurationManager.saveConfig(Configuration.from(event, ENABLE_REVERSE_CHARGE_IN_PERSON), "false");

        var reservation = createReservation();
        var summary = reservation.getOrderSummary();
        // 20 + 1.98 = 21.98
        assertEquals("0.20", summary.getTotalVAT());
        assertEquals(2198, summary.getPriceInCents());

        // we expect to find two rows for VAT: the first one for in-person (1%), the second one for online (0%)
        var rows = summary.getSummary();
        assertEquals(3, rows.size());
        assertEquals(SummaryRow.SummaryType.TAX_DETAIL, rows.get(1).getType());
        assertEquals("0", rows.get(1).getTaxPercentage());
        assertEquals("", rows.get(1).getPrice());
        assertEquals("0.00", rows.get(1).getSubTotal());
    }

    @Test
    void applyReverseChargeForInPersonTicket() {
        // disable Reverse Charge for in-person tickets
        configurationManager.saveConfig(Configuration.from(event, ENABLE_REVERSE_CHARGE_ONLINE), "false");

        var reservation = createReservation();
        // 19.80 + 2 = 21.80
        assertEquals("0.02", reservation.getOrderSummary().getTotalVAT());
        assertEquals(2180, reservation.getOrderSummary().getPriceInCents());
    }

    @Test
    void defaultReverseCharge() {
        var reservation = createReservation();
        // 19.80 + 1.98 = 21.78
        assertEquals("0", reservation.getOrderSummary().getTotalVAT());
        assertEquals(2178, reservation.getOrderSummary().getPriceInCents());
    }

    @Test
    void revertReverseCharge() {
        configurationManager.saveConfig(Configuration.from(event.getOrganizationId(), GENERATE_ONLY_INVOICE), "false");
        var reservation = createReservation();
        // 19.80 + 1.98 = 21.78
        assertEquals("0", reservation.getOrderSummary().getTotalVAT());
        assertEquals(2178, reservation.getOrderSummary().getPriceInCents());

        var reservationId = reservation.getId();
        reservation = createReservation(reservationId, false);
        assertEquals("0.22", reservation.getOrderSummary().getTotalVAT());
        assertEquals(2200, reservation.getOrderSummary().getPriceInCents());
    }

    @Test
    void reverseChargeDisabled() {
        configurationManager.saveConfig(Configuration.from(event, ENABLE_EU_VAT_DIRECTIVE), "false");
        var reservation = createReservation();
        // 19.80 + 1.98 = 21.78
        assertEquals("0.22", reservation.getOrderSummary().getTotalVAT());
        assertEquals(2200, reservation.getOrderSummary().getPriceInCents());

        configurationManager.saveConfig(Configuration.from(event, ENABLE_REVERSE_CHARGE_IN_PERSON), "true");
        configurationManager.saveConfig(Configuration.from(event, ENABLE_REVERSE_CHARGE_ONLINE), "true");
        // since the global flag is disabled, the specific settings are not effective
        reservation = createReservation();
        // 19.80 + 1.98 = 21.78
        assertEquals("0.22", reservation.getOrderSummary().getTotalVAT());
        assertEquals(2200, reservation.getOrderSummary().getPriceInCents());
    }

    private ReservationInfo createReservation() {
        return createReservation(null, true);
    }

    private ReservationInfo createReservation(String id, boolean requestInvoice) {

        var categories = ticketCategoryRepository.findAllTicketCategories(event.getId());

        var form = new ReservationForm();
        var first = new TicketReservationModification();
        first.setAmount(2);
        first.setTicketCategoryId(categories.get(0).getId());

        var second = new TicketReservationModification();
        second.setAmount(2);
        second.setTicketCategoryId(categories.get(1).getId());

        form.setReservation(List.of(first, second));

        var reservationId = id;

        if(reservationId == null) {
            var res = eventApiV2Controller.reserveTickets(event.getShortName(), "en", form, new BeanPropertyBindingResult(form, "reservation"), new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()), null);
            assertEquals(HttpStatus.OK, res.getStatusCode());
            var resBody = res.getBody();
            assertNotNull(resBody);
            assertTrue(resBody.isSuccess());
            assertEquals(0, resBody.getErrorCount());

            reservationId = resBody.getValue();
        }


        // enter billing data

        var contactForm = new ContactAndTicketsForm();

        // move to overview status
        contactForm = new ContactAndTicketsForm();
        contactForm.setFirstName("First");
        contactForm.setLastName("Last");
        contactForm.setEmail("test@test.com");
        contactForm.setAddCompanyBillingDetails(requestInvoice);
        contactForm.setInvoiceRequested(requestInvoice);
        if(requestInvoice) {
            contactForm.setBillingAddressLine1("Piazza della Riforma");
            contactForm.setBillingAddressCity("Lugano");
            contactForm.setBillingAddressZip("6900");
            contactForm.setVatCountryCode("CH");
            contactForm.setVatNr("123456789");
        }

        var tickets = ticketRepository.findTicketsInReservation(reservationId).stream().map(t -> {
                    var ticketForm = new UpdateTicketOwnerForm();
                    ticketForm.setFirstName("ticketfull");
                    ticketForm.setLastName("ticketname");
                    ticketForm.setEmail("tickettest@test.com");
                    return Map.entry(t.getUuid(), ticketForm);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        contactForm.setTickets(tickets);
        var overviewRes = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactForm, new BeanPropertyBindingResult(contactForm, "paymentForm"), null);
        assertEquals(HttpStatus.OK, overviewRes.getStatusCode());

        var resInfoRes = reservationApiV2Controller.getReservationInfo(reservationId, null);
        assertEquals(HttpStatus.OK, resInfoRes.getStatusCode());
        var reservation = resInfoRes.getBody();
        assertNotNull(reservation);
        assertFalse(reservation.getOrderSummary().isFree());

        return reservation;
    }
}