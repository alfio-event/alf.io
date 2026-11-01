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
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.*;
import alfio.model.modification.AdminReservationModification.*;
import alfio.model.result.Result;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static alfio.model.modification.AdminReservationModification.Notification.EMPTY;
import static alfio.test.util.IntegrationTestUtil.*;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;


@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class AdminReservationManagerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AdminReservationManager adminReservationManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private EmailMessageRepository emailMessageRepository;
    @Autowired
    private TicketReservationManager ticketReservationManager;
    @Autowired
    private SpecialPriceRepository specialPriceRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Autowired
    private PurchaseContextFieldManager purchaseContextFieldManager;
    @Autowired
    private PurchaseContextFieldRepository purchaseContextFieldRepository;
    @Autowired
    private AuditingRepository auditingRepository;
    @Autowired
    private PromoCodeDiscountRepository promoCodeDiscountRepository;

    @BeforeEach
    public void init() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
    }

    @Test
    public void testReserveFromExistingCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        performExistingCategoryTest(categories, false, Collections.singletonList(1), false, true, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveFromExistingMultipleCategories() {
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "2nd", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        performExistingCategoryTest(categories, false, Arrays.asList(10,10), false, true, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsNotBounded() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 1,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        performExistingCategoryTest(categories, false, Collections.singletonList(1), false, true, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveExistingCategoryNotEnoughSeatsNotBoundedSoldOut() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 1,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        performExistingCategoryTest(categories, false, Collections.singletonList(1), true, true, AVAILABLE_SEATS, AVAILABLE_SEATS+1);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsBounded() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 1,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        performExistingCategoryTest(categories, true, Collections.singletonList(2), false, true, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsNoExtensionAllowedBounded() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        performExistingCategoryTest(categories, true, Collections.singletonList(AVAILABLE_SEATS + 1), false, false, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsNoExtensionAllowedNotBounded() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        performExistingCategoryTest(categories, true, Collections.singletonList(AVAILABLE_SEATS + 1), false, false, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveFromNewCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 1,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventWithUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventWithUsername.getKey();
        String username = eventWithUsername.getValue();
        DateTimeModification expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        CustomerData customerData = new CustomerData("Integration", "Test", "integration-test@test.ch", "Billing Address", "reference", "en", "1234", "CH", null);
        Category category = new Category(null, "name", new BigDecimal("100.00"), null);
        int attendees = AVAILABLE_SEATS;
        List<TicketsInfo> ticketsInfoList = Collections.singletonList(new TicketsInfo(category, generateAttendees(attendees), true, false));
        AdminReservationModification modification = new AdminReservationModification(expiration, customerData, ticketsInfoList, "en", false, false, null, null, null, null);
        Result<Pair<TicketReservation, List<Ticket>>> result = adminReservationManager.createReservation(modification, event.getShortName(), username);
        assertTrue(result.isSuccess());
        Pair<TicketReservation, List<Ticket>> data = result.getData();
        List<Ticket> tickets = data.getRight();
        assertEquals(tickets.size(), attendees);
        assertNotNull(data.getLeft());
        int categoryId = tickets.getFirst().getCategoryId();
        eventManager.getSingleEvent(event.getShortName(), username);
        assertEquals(attendees + 1, eventRepository.countExistingTickets(event.getId()).intValue());
        assertEquals(attendees, ticketRepository.findPendingTicketsInCategories(Collections.singletonList(categoryId)).size());
        TicketCategory categoryModified = ticketCategoryRepository.getByIdAndActive(categoryId, event.getId());
        assertEquals(categoryModified.getMaxTickets(), attendees);
        ticketCategoryRepository.findAllTicketCategories(event.getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.PENDING)));
        adminReservationManager.confirmReservation(PurchaseContextType.event, event.getShortName(), data.getLeft().getId(), username, EMPTY);
        ticketCategoryRepository.findAllTicketCategories(event.getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.TAKEN)));
        assertFalse(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(event.getId()).contains(data.getLeft().getId()));
    }

    @Test
    public void testReserveMixed() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 1,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventWithUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventWithUsername.getKey();
        String username = eventWithUsername.getValue();
        DateTimeModification expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        CustomerData customerData = new CustomerData("Integration", "Test", "integration-test@test.ch", "Billing Address", "reference", "en", "1234", "CH", null);

        TicketCategory existingCategory = ticketCategoryRepository.findAllTicketCategories(event.getId()).getFirst();


        Category resExistingCategory = new Category(existingCategory.getId(), "", existingCategory.getPrice(), null);
        Category resNewCategory = new Category(null, "name", new BigDecimal("100.00"), null);
        int attendees = 1;
        List<TicketsInfo> ticketsInfoList = Arrays.asList(new TicketsInfo(resExistingCategory, generateAttendees(attendees), false, false), new TicketsInfo(resNewCategory, generateAttendees(attendees), false, false),new TicketsInfo(resExistingCategory, generateAttendees(attendees), false, false));
        AdminReservationModification modification = new AdminReservationModification(expiration, customerData, ticketsInfoList, "en", false,false, null, null, null, null);
        Result<Pair<TicketReservation, List<Ticket>>> result = adminReservationManager.createReservation(modification, event.getShortName(), username);
        assertTrue(result.isSuccess());
        Pair<TicketReservation, List<Ticket>> data = result.getData();
        List<Ticket> tickets = data.getRight();
        assertEquals(3, tickets.size());
        assertNotNull(data.getLeft());
        assertTrue(tickets.stream().allMatch(t -> t.getTicketsReservationId().equals(data.getKey().getId())));
        int resExistingCategoryId = tickets.getFirst().getCategoryId();
        int resNewCategoryId = tickets.get(2).getCategoryId();

        eventManager.getSingleEvent(event.getShortName(), username);
        assertEquals(AVAILABLE_SEATS, eventRepository.countExistingTickets(event.getId()).intValue());
        assertEquals(3, ticketRepository.findPendingTicketsInCategories(Arrays.asList(resExistingCategoryId, resNewCategoryId)).size());
        assertEquals(3, ticketRepository.findTicketsInReservation(data.getLeft().getId()).size());

        String reservationId = data.getLeft().getId();
        assertEquals(ticketRepository.findTicketsInReservation(reservationId).stream().findFirst().get().getId(),
            ticketRepository.findFirstTicketInReservation(reservationId).get().getId());

        ticketCategoryRepository.findAllTicketCategories(event.getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.PENDING)));
        adminReservationManager.confirmReservation(PurchaseContextType.event, event.getShortName(), data.getLeft().getId(), username, EMPTY);
        ticketCategoryRepository.findAllTicketCategories(event.getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.TAKEN)));
        assertFalse(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(event.getId()).contains(data.getLeft().getId()));
    }

    @Test
    public void testConfirmReservation() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 1,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Triple<Event, String, TicketReservation> testResult = performExistingCategoryTest(categories, true, Collections.singletonList(2), false, true, 0, AVAILABLE_SEATS);
        assertNotNull(testResult);
        var result = adminReservationManager.confirmReservation(PurchaseContextType.event, testResult.getLeft().getShortName(), testResult.getRight().getId(), testResult.getMiddle(), EMPTY);
        assertTrue(result.isSuccess());
        var triple = result.getData();
        assertEquals(PurchaseContextType.event, triple.getRight().getType());
        assertEquals(TicketReservation.TicketReservationStatus.COMPLETE, triple.getLeft().getStatus());
        triple.getMiddle().forEach(t -> assertEquals(Ticket.TicketStatus.ACQUIRED, t.getStatus()));
        int eventId = triple.getRight().event().orElseThrow().getId();
        assertTrue(emailMessageRepository.findByEventId(eventId, 0, 50, null).isEmpty());
        ticketCategoryRepository.findAllTicketCategories(eventId).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.TAKEN)));
        assertFalse(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(eventId).contains(triple.getLeft().getId()));
    }

    @Test
    public void testConfirmReservationSendConfirmationEmail() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 1,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        int attendees = 2;
        Triple<Event, String, TicketReservation> testResult = performExistingCategoryTest(categories, true, Collections.singletonList(attendees), false, true, 0, AVAILABLE_SEATS);
        assertNotNull(testResult);
        var result = adminReservationManager.confirmReservation(PurchaseContextType.event, testResult.getLeft().getShortName(), testResult.getRight().getId(), testResult.getMiddle(), new Notification(true, true));
        assertTrue(result.isSuccess());
        var triple = result.getData();
        assertEquals(TicketReservation.TicketReservationStatus.COMPLETE, triple.getLeft().getStatus());
        assertEquals(PurchaseContextType.event, triple.getRight().getType());
        triple.getMiddle().forEach(t -> assertEquals(Ticket.TicketStatus.ACQUIRED, t.getStatus()));
        int eventId = triple.getRight().event().orElseThrow().getId();
        assertEquals(attendees + 2, emailMessageRepository.findByEventId(eventId, 0, 50, null).size());
        ticketCategoryRepository.findAllTicketCategories(eventId).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.TAKEN)));
        assertFalse(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(eventId).contains(triple.getLeft().getId()));
    }

    @Test
    void updateCustomerFromPrivateToCompanyAndBack() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 1,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        var testResult = performExistingCategoryTest(categories, true, Collections.singletonList(1), false, true, 0, AVAILABLE_SEATS);
        assertNotNull(testResult);
        var event = testResult.getLeft();
        var reservationId = testResult.getRight().getId();

        // the customer becomes a company
        var company = new CustomerBillingDetails(true, " ACME Inc. ", "Main Street 1", null, "8000", "Zurich", null);
        var toCompany = updateCustomer(event, reservationId, testResult.getMiddle(), "CHE-123.456.789", company);
        assertTrue(toCompany.isSuccess());
        var companyInfo = ticketReservationRepository.getAdditionalInfo(reservationId);
        assertTrue(companyInfo.getAddCompanyBillingDetails());
        assertEquals("ACME Inc.", companyInfo.getBillingAddressCompany());
        assertEquals("Main Street 1", companyInfo.getBillingAddressLine1());
        var companyReservation = ticketReservationRepository.findReservationById(reservationId);
        assertEquals("CHE-123.456.789", companyReservation.getVatNr());
        // the billing address is rebuilt from the structured fields, like the checkout does
        assertTrue(companyReservation.getBillingAddress().startsWith("ACME Inc.\nIntegration Test\nMain Street 1"), companyReservation.getBillingAddress());

        // and then back to a private customer: company name and VAT number are removed
        var privateCustomer = new CustomerBillingDetails(false, "ACME Inc.", "Main Street 1", null, "8000", "Zurich", null);
        assertTrue(updateCustomer(event, reservationId, testResult.getMiddle(), "", privateCustomer).isSuccess());
        var privateInfo = ticketReservationRepository.getAdditionalInfo(reservationId);
        assertFalse(privateInfo.getAddCompanyBillingDetails());
        assertNull(privateInfo.getBillingAddressCompany());
        var privateReservation = ticketReservationRepository.findReservationById(reservationId);
        assertNull(privateReservation.getVatNr());
        assertFalse(privateReservation.getBillingAddress().contains("ACME"), privateReservation.getBillingAddress());
    }

    @Test
    void createAndUpdateAttendeeAdditionalFields() {
        var eventWithUsername = initEvent(singleCategory(), organizationRepository, userManager, eventManager, eventRepository);
        var event = eventWithUsername.getLeft();
        var username = eventWithUsername.getRight();
        addAttendeeFields(event);

        // the names of the fields can be written in any case, and the organizer can set the read-only fields
        var created = createReservation(event, username, Map.of(
            "Company", List.of("ACME"),
            "diet", List.of("vegan"),
            "badge", List.of("B-1")));
        assertTrue(created.isSuccess(), created.getFormattedErrors());
        var reservationId = created.getData().getLeft().getId();
        var ticket = created.getData().getRight().getFirst();
        assertEquals(Map.of("company", List.of("ACME"), "diet", List.of("vegan"), "badge", List.of("B-1")),
            purchaseContextFieldRepository.findAllValuesForTicketId(ticket.getId()));
        assertEquals(Optional.of(Map.of("company", List.of("ACME"), "diet", List.of("vegan"), "badge", List.of("B-1"))),
            adminReservationManager.loadAttendeeFieldValues(event.getShortName(), reservationId, ticket.getUuid()));

        // an empty value removes the field
        var updated = updateAttendee(event, reservationId, username, ticket.getId(), Map.of(
            "company", List.of("Initech"),
            "diet", List.of(""),
            "badge", List.of("B-2")));
        assertTrue(updated.isSuccess(), updated.getFormattedErrors());
        assertEquals(Map.of("company", List.of("Initech"), "badge", List.of("B-2")),
            purchaseContextFieldRepository.findAllValuesForTicketId(ticket.getId()));
        var updatedTicket = ticketRepository.findById(ticket.getId(), ticket.getCategoryId());
        assertEquals("Updated", updatedTicket.getFirstName());

        // the changes are in the history of the reservation
        var audit = auditingRepository.findAllForReservation(reservationId).stream()
            .filter(a -> a.getEventType() == Audit.EventType.UPDATE_TICKET)
            .findFirst()
            .orElseThrow();
        var changedFields = audit.getModifications().stream()
            .map(m -> m.get("propertyName"))
            .filter(Objects::nonNull)
            .collect(toList());
        assertEquals(List.of("/{badge}", "/{company}", "/{diet}"), changedFields.stream().sorted().toList());
    }

    @Test
    void updateAdditionalFieldsOfSingleTicket() {
        var eventWithUsername = initEvent(singleCategory(), organizationRepository, userManager, eventManager, eventRepository);
        var event = eventWithUsername.getLeft();
        var username = eventWithUsername.getRight();
        addAttendeeFields(event);
        var first = createReservation(event, username, Map.of("company", List.of("ACME"), "diet", List.of("vegan"), "badge", List.of("B-1")));
        var second = createReservation(event, username, Map.of("company", List.of("Globex")));
        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        var reservationId = first.getData().getLeft().getId();
        var ticket = first.getData().getRight().getFirst();

        // the fields not included are not modified, the read-only ones can be changed, an empty value removes the field
        var updated = adminReservationManager.updateAttendeeFields(event.getShortName(), reservationId, ticket.getUuid(),
            Map.of("Company", List.of("Initech"), "badge", List.of("B-2"), "diet", List.of("")), username);
        assertTrue(updated.isSuccess(), updated.getFormattedErrors());
        assertEquals(Map.of("company", List.of("Initech"), "badge", List.of("B-2")),
            purchaseContextFieldRepository.findAllValuesForTicketId(ticket.getId()));
        var audit = auditingRepository.findAllForReservation(reservationId).stream()
            .filter(a -> a.getEventType() == Audit.EventType.UPDATE_TICKET)
            .findFirst()
            .orElseThrow();
        assertEquals(List.of("/{badge}", "/{company}", "/{diet}"), audit.getModifications().stream()
            .map(m -> String.valueOf(m.get("propertyName")))
            .sorted()
            .toList());

        // invalid values are rejected
        assertFalse(adminReservationManager.updateAttendeeFields(event.getShortName(), reservationId, ticket.getUuid(),
            Map.of("diet", List.of("meat")), username).isSuccess());
        assertEquals(Map.of("company", List.of("Initech"), "badge", List.of("B-2")),
            purchaseContextFieldRepository.findAllValuesForTicketId(ticket.getId()));

        // the tickets of other reservations can't be read or changed
        var otherTicket = second.getData().getRight().getFirst();
        assertTrue(adminReservationManager.loadAttendeeFieldValues(event.getShortName(), reservationId, otherTicket.getUuid()).isEmpty());
        assertFalse(adminReservationManager.updateAttendeeFields(event.getShortName(), reservationId, otherTicket.getUuid(),
            Map.of("company", List.of("Initech")), username).isSuccess());
        assertEquals(Map.of("company", List.of("Globex")), purchaseContextFieldRepository.findAllValuesForTicketId(otherTicket.getId()));
    }

    @Test
    void rejectInvalidAttendeeAdditionalFields() {
        var eventWithUsername = initEvent(singleCategory(), organizationRepository, userManager, eventManager, eventRepository);
        var event = eventWithUsername.getLeft();
        var username = eventWithUsername.getRight();
        addAttendeeFields(event);

        assertFalse(createReservation(event, username, Map.of("diet", List.of("meat"))).isSuccess());
        assertFalse(createReservation(event, username, Map.of("unknown", List.of("value"))).isSuccess());

        var created = createReservation(event, username, Map.of("diet", List.of("vegan")));
        assertTrue(created.isSuccess());
        var reservationId = created.getData().getLeft().getId();
        var ticket = created.getData().getRight().getFirst();
        var updated = updateAttendee(event, reservationId, username, ticket.getId(), Map.of("diet", List.of("meat")));
        assertFalse(updated.isSuccess());
        // nothing has been modified
        assertEquals(Map.of("diet", List.of("vegan")), purchaseContextFieldRepository.findAllValuesForTicketId(ticket.getId()));
        assertEquals("Attendee", ticketRepository.findById(ticket.getId(), ticket.getCategoryId()).getFirstName());
    }

    @Test
    void attendeesOfOtherReservationsCannotBeUpdated() {
        var eventWithUsername = initEvent(singleCategory(), organizationRepository, userManager, eventManager, eventRepository);
        var event = eventWithUsername.getLeft();
        var username = eventWithUsername.getRight();
        addAttendeeFields(event);

        var first = createReservation(event, username, Map.of());
        var second = createReservation(event, username, Map.of("company", List.of("ACME")));
        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        var otherTicket = second.getData().getRight().getFirst();

        var updated = updateAttendee(event, first.getData().getLeft().getId(), username, otherTicket.getId(), Map.of("company", List.of("Initech")));
        assertFalse(updated.isSuccess());
        assertEquals("Attendee", ticketRepository.findById(otherTicket.getId(), otherTicket.getCategoryId()).getFirstName());
        assertEquals(Map.of("company", List.of("ACME")), purchaseContextFieldRepository.findAllValuesForTicketId(otherTicket.getId()));
    }

    private static List<TicketCategoryModification> singleCategory() {
        return Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
    }

    /**
     * "company" (text), "diet" (select: vegan, standard) and "badge" (read-only text)
     */
    private void addAttendeeFields(Event event) {
        var description = Map.of("en", new EventModification.Description("label", "", null));
        purchaseContextFieldManager.addAdditionalField(event, new AdditionalFieldRequest(0, true, "company", "input:text", false, false,
            null, 50, null, description, null, null, false));
        var options = List.of(new EventModification.RestrictedValue("vegan", true), new EventModification.RestrictedValue("standard", true));
        purchaseContextFieldManager.addAdditionalField(event, new AdditionalFieldRequest(1, true, "diet", "select", true, false,
            null, null, options, Map.of("en", new EventModification.Description("diet", "", Map.of("vegan", "Vegan", "standard", "Standard"))), null, null, false));
        purchaseContextFieldManager.addAdditionalField(event, new AdditionalFieldRequest(2, true, "badge", "input:text", false, false,
            null, null, null, description, null, null, false));
        var badge = purchaseContextFieldRepository.findAdditionalFieldsForEvent(event.getId()).stream()
            .filter(f -> f.getName().equals("badge"))
            .findFirst()
            .orElseThrow();
        purchaseContextFieldRepository.updateField(badge.getId(), false, false, null, null, null, false);
    }

    private Result<Pair<TicketReservation, List<Ticket>>> createReservation(Event event, String username, Map<String, List<String>> additionalInfo) {
        var category = ticketCategoryRepository.findAllTicketCategories(event.getId()).getFirst();
        var attendee = new Attendee(null, "Attendee", "Test", "attendee@test.ch", "en", false, null, null, additionalInfo, null);
        var ticketsInfo = new TicketsInfo(new Category(category.getId(), category.getName(), category.getPrice(), null), List.of(attendee), true, false);
        var customerData = new CustomerData("Integration", "Test", "integration-test@test.ch", "Billing Address", "reference", "en", null, null, null);
        var expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        var modification = new AdminReservationModification(expiration, customerData, List.of(ticketsInfo), "en", false, false, null, null, null, null);
        return adminReservationManager.createReservation(modification, event.getShortName(), username);
    }

    private Result<Boolean> updateAttendee(Event event, String reservationId, String username, int ticketId, Map<String, List<String>> additionalInfo) {
        var category = ticketCategoryRepository.findAllTicketCategories(event.getId()).getFirst();
        var attendee = new Attendee(ticketId, "Updated", "Test", "attendee@test.ch", "en", false, null, null, additionalInfo, null);
        var ticketsInfo = new TicketsInfo(new Category(category.getId(), category.getName(), category.getPrice(), null), List.of(attendee), false, true);
        var expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        var modification = new AdminReservationModification(expiration, null, List.of(ticketsInfo), "en", false, false, null, null, null, null);
        return adminReservationManager.updateReservation(PurchaseContextType.event, event.getShortName(), reservationId, modification, username);
    }

    private Result<Boolean> updateCustomer(Event event, String reservationId, String username, String vatNr, CustomerBillingDetails billingDetails) {
        var customerData = new CustomerData("Integration", "Test", "integration-test@test.ch", "free text address", "reference", "en",
            vatNr, "CH", null, billingDetails);
        var expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        var modification = new AdminReservationModification(expiration, customerData, List.of(), "en", true, false, null, null, null, null);
        return adminReservationManager.updateReservation(PurchaseContextType.event, event.getShortName(), reservationId, modification, username);
    }

    private Triple<Event, String, TicketReservation> performExistingCategoryTest(List<TicketCategoryModification> categories, boolean bounded,
                                                                                 List<Integer> attendeesNr, boolean addSeatsIfNotAvailable, boolean expectSuccess,
                                                                                 int reservedTickets, int expectedEventSeats) {
        assertEquals(categories.size(), attendeesNr.size(), "Test error: categories' size must be equal to attendees' size");
        Pair<Event, String> eventWithUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventWithUsername.getKey();
        String username = eventWithUsername.getValue();
        DateTimeModification expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        CustomerData customerData = new CustomerData("Integration", "Test", "integration-test@test.ch", "Billing Address", "reference", "en", "1234", "CH", null);
        Iterator<Integer> attendeesIterator = attendeesNr.iterator();
        List<TicketCategory> existingCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        List<Attendee> allAttendees = new ArrayList<>();
        List<TicketsInfo> ticketsInfoList = existingCategories.stream()
            .map(existingCategory -> {
                Category category = new Category(existingCategory.getId(), existingCategory.getName(), existingCategory.getPrice(), null);
                List<Attendee> attendees = generateAttendees(attendeesIterator.next());
                allAttendees.addAll(attendees);
                return new TicketsInfo(category, attendees, addSeatsIfNotAvailable, false);
            }).collect(toList());
        AdminReservationModification modification = new AdminReservationModification(expiration, customerData, ticketsInfoList, "en", false,false, null, null, null, null);

        if(reservedTickets > 0) {
            TicketReservationModification trm = new TicketReservationModification();
            trm.setAmount(reservedTickets);
            trm.setTicketCategoryId(existingCategories.getFirst().getId());
            TicketReservationWithOptionalCodeModification r = new TicketReservationWithOptionalCodeModification(trm, Optional.empty());
            ticketReservationManager.createTicketReservation(event, Collections.singletonList(r), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        }

        Result<Pair<TicketReservation, List<Ticket>>> result = adminReservationManager.createReservation(modification, event.getShortName(), username);
        if(expectSuccess) {
            validateSuccess(bounded, attendeesNr, event, username, existingCategories, result, allAttendees, expectedEventSeats, reservedTickets);
        } else {
            assertFalse(result.isSuccess());
            return null;
        }
        return Triple.of(eventWithUsername.getLeft(), eventWithUsername.getRight(), result.getData().getKey());
    }

    private void validateSuccess(boolean bounded, List<Integer> attendeesNr, Event event, String username, List<TicketCategory> existingCategories, Result<Pair<TicketReservation,
        List<Ticket>>> result, List<Attendee> allAttendees, int expectedEventSeats, int reservedTickets) {

        assertTrue(result.isSuccess());
        Pair<TicketReservation, List<Ticket>> data = result.getData();
        assertEquals(data.getRight().size(), attendeesNr.stream().mapToInt(i -> i).sum());
        assertNotNull(data.getLeft());
        Event modified = eventManager.getSingleEvent(event.getShortName(), username);
        assertEquals(expectedEventSeats, eventRepository.countExistingTickets(event.getId()).intValue());
        List<Ticket> tickets = ticketRepository.findPendingTicketsInCategories(existingCategories.stream().map(TicketCategory::getId).collect(toList()));
        assertEquals(attendeesNr.stream().mapToInt(i -> i).sum(), tickets.size() - reservedTickets);
        if(bounded) {
            final Iterator<Integer> iterator = attendeesNr.iterator();
            existingCategories.forEach(existingCategory -> {
                TicketCategory categoryModified = ticketCategoryRepository.getByIdAndActive(existingCategory.getId(), event.getId());
                assertEquals(categoryModified.getMaxTickets(), iterator.next().intValue());
            });
        }
        for (int i = 0; i < tickets.size() - reservedTickets; i++) {
            Attendee attendee = allAttendees.get(i);
            if(!attendee.isEmpty()) {
                Ticket ticket = data.getRight().get(i);
                assertTrue(ticket.getAssigned());
                assertNotNull(ticket.getFullName());
                assertEquals(attendee.getFullName(), ticket.getFullName());
                assertEquals(attendee.getEmailAddress(), ticket.getEmail());
                assertEquals(Ticket.TicketStatus.PENDING, ticket.getStatus());
                assertEquals(data.getLeft().getId(), ticket.getTicketsReservationId());
            }
        }
        ticketCategoryRepository.findAllTicketCategories(modified.getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.PENDING)));
        assertFalse(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(event.getId()).contains(data.getLeft().getId()));
    }

    @Test
    void createReservationWithDiscountCode() {
        var eventWithUsername = initEvent(discountTestCategories(), organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventWithUsername.getKey();
        String username = eventWithUsername.getValue();
        var category = ticketCategoryRepository.findAllTicketCategories(event.getId()).getFirst();
        addDiscountCode(event, "TEN_PERCENT", PromoCodeDiscount.CodeType.DISCOUNT, null, 3);

        var result = adminReservationManager.createReservation(discountModification(category, 2, "  TEN_PERCENT "), event.getShortName(), username);

        assertTrue(result.isSuccess(), () -> result.getFormattedErrors());
        var reservation = ticketReservationRepository.findReservationById(result.getData().getLeft().getId());
        var discount = promoCodeDiscountRepository.findPromoCodeInEventOrOrganization(event.getId(), "TEN_PERCENT").orElseThrow();
        assertEquals(discount.getId(), reservation.getPromoCodeDiscountId());
        // 10% of 10.00
        result.getData().getRight().forEach(ticket -> assertEquals(100, ticketRepository.findById(ticket.getId(), category.getId()).getDiscountCts()));
    }

    @Test
    void refuseDiscountCodeNotApplicable() {
        var eventWithUsername = initEvent(discountTestCategories(), organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventWithUsername.getKey();
        String username = eventWithUsername.getValue();
        var categories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        var category = categories.getFirst();
        addDiscountCode(event, "ACCESS_CODE", PromoCodeDiscount.CodeType.ACCESS, null, null);
        addDiscountCode(event, "ONLY_ONCE", PromoCodeDiscount.CodeType.DISCOUNT, null, 1);
        addDiscountCode(event, "OTHER_CATEGORY", PromoCodeDiscount.CodeType.DISCOUNT, List.of(categories.get(1).getId()), null);

        assertDiscountRefused(event, username, discountModification(category, 1, "MISSING_CODE"), "error.promoCode.notfound");
        assertDiscountRefused(event, username, discountModification(category, 1, "ACCESS_CODE"), "error.promoCode.notfound");
        assertDiscountRefused(event, username, discountModification(category, 2, "ONLY_ONCE"), "error.promoCode.maxUsage");
        assertDiscountRefused(event, username, discountModification(category, 1, "OTHER_CATEGORY"), "error.promoCode.categories");
        // nothing has been reserved
        assertTrue(ticketRepository.findPendingTicketsInCategories(categories.stream().map(TicketCategory::getId).toList()).isEmpty());
    }

    private void assertDiscountRefused(Event event, String username, AdminReservationModification modification, String expectedCode) {
        var result = adminReservationManager.createReservation(modification, event.getShortName(), username);
        assertFalse(result.isSuccess());
        assertEquals(expectedCode, result.getErrors().getFirst().getCode());
    }

    private List<TicketCategoryModification> discountTestCategories() {
        return List.of("first", "second").stream()
            .map(name -> new TicketCategoryModification(null, name, TicketCategory.TicketAccessType.INHERIT, 5,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()))
            .toList();
    }

    private void addDiscountCode(Event event, String code, PromoCodeDiscount.CodeType codeType, List<Integer> categories, Integer maxUsage) {
        var now = ZonedDateTime.now(ClockProvider.clock());
        String categoriesJson = "[]";
        if(categories != null) {
            categoriesJson = categories.toString();
        }
        Integer hiddenCategoryId = null;
        if(codeType == PromoCodeDiscount.CodeType.ACCESS) {
            hiddenCategoryId = ticketCategoryRepository.findAllTicketCategories(event.getId()).getFirst().getId();
        }
        promoCodeDiscountRepository.addPromoCode(code, event.getId(), event.getOrganizationId(), now.minusDays(1), now.plusDays(1), 10,
            PromoCodeDiscount.DiscountType.PERCENTAGE, categoriesJson, maxUsage, code, null, codeType, hiddenCategoryId, null);
    }

    private AdminReservationModification discountModification(TicketCategory category, int attendees, String promoCode) {
        var expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        var customerData = new CustomerData("Integration", "Test", "integration-test@test.ch", null, "en", null, null, null, null);
        var ticketsInfo = new TicketsInfo(new Category(category.getId(), "", category.getPrice(), null), generateAttendees(attendees), false, false);
        return new AdminReservationModification(expiration, customerData, List.of(ticketsInfo), "en", false, false, null, null, null, null, promoCode);
    }

    private List<Attendee> generateAttendees(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new Attendee(null, "Attendee "+i, "Test" + i, "attendee"+i+"@test.ch", "en",false, null, null, Collections.emptyMap(), null))
            .collect(toList());
    }
}