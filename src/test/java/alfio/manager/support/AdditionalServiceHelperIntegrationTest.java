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
package alfio.manager.support;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.form.AdditionalServiceLinkForm;
import alfio.manager.*;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.extension.LinkedAdditionalService;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.*;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.StaticPaymentMethods;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;

import static alfio.model.EventCheckInInfo.VERSION_FOR_CODE_CASE_INSENSITIVE;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class AdditionalServiceHelperIntegrationTest {

    @Autowired
    private AdditionalServiceHelper additionalServiceHelper;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketReservationManager ticketReservationManager;
    @Autowired
    private AdminReservationManager adminReservationManager;
    @Autowired
    private CheckInManager checkInManager;
    @Autowired
    private AdditionalServiceManager additionalServiceManager;
    @Autowired
    private AdditionalServiceItemRepository additionalServiceItemRepository;
    @Autowired
    private PurchaseContextFieldManager purchaseContextFieldManager;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private Event event;
    private String username;
    private EventModification.AdditionalService shirt;
    private EventModification.AdditionalService parking;

    @BeforeEach
    void setUp() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false,
                "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        var eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, null,
            Event.EventFormat.IN_PERSON, PriceContainer.VatStatus.INCLUDED, List.of(PaymentProxy.OFFLINE, PaymentProxy.ON_SITE));
        event = eventAndUser.getLeft();
        username = eventAndUser.getRight();
        shirt = additionalServiceManager.insertAdditionalService(event, additionalServiceRequest(1, "shirt"));
        parking = additionalServiceManager.insertAdditionalService(event, additionalServiceRequest(2, "parking"));
        insertField(shirt, "size", 0);
        insertField(shirt, "notes", 1);
    }

    @Test
    void findLinkedAdditionalServices() {
        String reservationId = createReservation(2, Map.of(shirt.getId(), 3, parking.getId(), 1));
        var tickets = ticketReservationManager.findTicketsInReservation(reservationId);
        assertEquals(2, tickets.size());
        var ticket = tickets.get(0);
        var otherTicket = tickets.get(1);
        var shirts = findItems(reservationId, shirt.getId());
        var parkingItem = findItems(reservationId, parking.getId()).get(0);

        // two shirts with the same size are grouped together. Empty values are ignored
        var links = Map.of(
            ticket.getPublicUuid().toString(), List.of(
                link(shirts.get(0), ticket, Map.of("size", "M", "notes", "")),
                link(shirts.get(1), ticket, Map.of("size", "M")),
                link(parkingItem, ticket, Map.of())
            ),
            otherTicket.getPublicUuid().toString(), List.of(
                link(shirts.get(2), otherTicket, Map.of("size", "L"))
            )
        );
        linkItems(reservationId, links, tickets);
        assertTrue(additionalServiceHelper.findLinkedAdditionalServices(ticket, event).isEmpty());

        // items stay pending until the bank transfer is confirmed
        pay(reservationId, PaymentProxy.OFFLINE, StaticPaymentMethods.BANK_TRANSFER);
        assertTrue(additionalServiceHelper.findLinkedAdditionalServices(ticket, event).isEmpty());

        ticketReservationManager.confirmOfflinePayment(event, reservationId, null, username);
        var expectedForTicket = List.of(
            new LinkedAdditionalService(shirt.getId(), 2, Map.of("size", "M")),
            new LinkedAdditionalService(parking.getId(), 1, Map.of())
        );
        var expectedForOtherTicket = List.of(new LinkedAdditionalService(shirt.getId(), 1, Map.of("size", "L")));
        assertLinkedAdditionalServices(expectedForTicket, additionalServiceHelper.findLinkedAdditionalServices(ticket, event));
        assertLinkedAdditionalServices(expectedForOtherTicket, additionalServiceHelper.findLinkedAdditionalServices(otherTicket, event));

        assertTrue(checkInManager.manualCheckIn(event.getId(), otherTicket.getUuid(), username));
        assertLinkedAdditionalServices(expectedForOtherTicket, additionalServiceHelper.findLinkedAdditionalServices(otherTicket, event));

        // disable link support
        jdbcTemplate.update("update event set version = :version where id = :id", new MapSqlParameterSource("id", event.getId()).addValue("version", VERSION_FOR_CODE_CASE_INSENSITIVE));
        assertTrue(additionalServiceHelper.findLinkedAdditionalServices(ticket, eventRepository.findById(event.getId())).isEmpty());
    }

    @Test
    void additionalServicesToBePaidOnSite() {
        String reservationId = createReservation(1, Map.of(shirt.getId(), 1));
        var tickets = ticketReservationManager.findTicketsInReservation(reservationId);
        var ticket = tickets.get(0);
        linkItems(reservationId, Map.of(ticket.getPublicUuid().toString(), List.of(link(findItems(reservationId, shirt.getId()).get(0), ticket, Map.of("size", "S")))), tickets);
        pay(reservationId, PaymentProxy.ON_SITE, StaticPaymentMethods.ON_SITE);
        assertLinkedAdditionalServices(List.of(new LinkedAdditionalService(shirt.getId(), 1, Map.of("size", "S"))),
            additionalServiceHelper.findLinkedAdditionalServices(ticket, event));
    }

    @Test
    void cancelledAdditionalServicesAreIgnored() {
        String reservationId = createReservation(1, Map.of(shirt.getId(), 1));
        var tickets = ticketReservationManager.findTicketsInReservation(reservationId);
        var ticket = tickets.get(0);
        linkItems(reservationId, Map.of(ticket.getPublicUuid().toString(), List.of(link(findItems(reservationId, shirt.getId()).get(0), ticket, Map.of("size", "XL")))), tickets);
        pay(reservationId, PaymentProxy.OFFLINE, StaticPaymentMethods.BANK_TRANSFER);
        ticketReservationManager.confirmOfflinePayment(event, reservationId, null, username);
        assertLinkedAdditionalServices(List.of(new LinkedAdditionalService(shirt.getId(), 1, Map.of("size", "XL"))),
            additionalServiceHelper.findLinkedAdditionalServices(ticket, event));

        assertTrue(adminReservationManager.removeReservation(PurchaseContextType.event, event.getShortName(), reservationId, false, false, false, username).isSuccess());
        assertTrue(additionalServiceHelper.findLinkedAdditionalServices(ticket, event).isEmpty());
    }

    private static void assertLinkedAdditionalServices(List<LinkedAdditionalService> expected, List<LinkedAdditionalService> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getAdditionalServiceId(), actual.get(i).getAdditionalServiceId());
            assertEquals(expected.get(i).getQuantity(), actual.get(i).getQuantity());
            assertEquals(expected.get(i).getAdditional(), actual.get(i).getAdditional());
        }
    }

    private String createReservation(int ticketsQuantity, Map<Integer, Integer> quantityByAdditionalServiceId) {
        var category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(ticketsQuantity);
        tr.setTicketCategoryId(category.getId());
        var additionalServices = quantityByAdditionalServiceId.entrySet().stream()
            .map(entry -> {
                var modification = new AdditionalServiceReservationModification();
                modification.setAdditionalServiceId(entry.getKey());
                modification.setQuantity(entry.getValue());
                return new ASReservationWithOptionalCodeModification(modification, Optional.empty());
            }).toList();
        return ticketReservationManager.createTicketReservation(event,
            List.of(new TicketReservationWithOptionalCodeModification(tr, Optional.empty())), additionalServices,
            DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
    }

    private void linkItems(String reservationId, Map<String, List<AdditionalServiceLinkForm>> links, List<Ticket> tickets) {
        additionalServiceManager.linkItemsToTickets(reservationId, links, tickets);
        additionalServiceManager.persistFieldsForAdditionalItems(event.getId(), event.getOrganizationId(), links, tickets);
    }

    private void pay(String reservationId, PaymentProxy paymentProxy, PaymentMethod paymentMethod) {
        var reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId).getLeft();
        var specification = new PaymentSpecification(reservationId, null, null, reservationCost.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        assertTrue(ticketReservationManager.performPayment(specification, reservationCost, paymentProxy, paymentMethod, null).isSuccessful());
    }

    private List<AdditionalServiceItem> findItems(String reservationId, int additionalServiceId) {
        return additionalServiceItemRepository.findByReservationUuid(event.getId(), reservationId).stream()
            .filter(item -> item.getAdditionalServiceId() == additionalServiceId)
            .sorted(Comparator.comparing(AdditionalServiceItem::getId))
            .toList();
    }

    private static AdditionalServiceLinkForm link(AdditionalServiceItem item, Ticket ticket, Map<String, String> values) {
        var form = new AdditionalServiceLinkForm();
        form.setAdditionalServiceItemId(item.getId());
        form.setTicketUUID(ticket.getPublicUuid().toString());
        var additional = new HashMap<String, List<String>>();
        values.forEach((name, value) -> additional.put(name, List.of(value)));
        form.setAdditional(additional);
        return form;
    }

    private void insertField(EventModification.AdditionalService additionalService, String name, int order) {
        var request = new AdditionalFieldRequest(order, true, name, "input:text", false, false, null, null, null,
            Map.of(), additionalService, null, false);
        purchaseContextFieldManager.insertAdditionalField(event, request, order);
    }

    private static EventModification.AdditionalService additionalServiceRequest(int ordinal, String title) {
        return new EventModification.AdditionalService(
            null,
            BigDecimal.ONE,
            true,
            ordinal,
            -1,
            5,
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).minusDays(1)),
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1)),
            BigDecimal.ZERO,
            AdditionalService.VatType.INHERITED,
            List.of(),
            List.of(new EventModification.AdditionalServiceText(null, "en", title, AdditionalServiceText.TextType.TITLE)),
            List.of(new EventModification.AdditionalServiceText(null, "en", "description", AdditionalServiceText.TextType.DESCRIPTION)),
            AdditionalService.AdditionalServiceType.SUPPLEMENT,
            AdditionalService.SupplementPolicy.OPTIONAL_UNLIMITED_AMOUNT, null, null
        );
    }
}
