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
import alfio.config.RepositoryConfiguration;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.*;
import alfio.model.modification.AdminReservationModification.Attendee;
import alfio.model.modification.AdminReservationModification.Category;
import alfio.model.modification.AdminReservationModification.CustomerData;
import alfio.model.modification.AdminReservationModification.TicketsInfo;
import alfio.model.result.Result;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static alfio.test.util.IntegrationTestUtil.*;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {RepositoryConfiguration.class, DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class AdminReservationManagerIntegrationTest {

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
    }

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

    @Test
    public void testReserveFromExistingCategory() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        performExistingCategoryTest(categories, false, Collections.singletonList(1), false, true, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveFromExistingMultipleCategories() throws Exception {
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null),
            new TicketCategoryModification(null, "2nd", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        performExistingCategoryTest(categories, false, Arrays.asList(10,10), false, true, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsNotBounded() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 1,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        performExistingCategoryTest(categories, false, Collections.singletonList(1), false, true, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveExistingCategoryNotEnoughSeatsNotBoundedSoldOut() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 1,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        performExistingCategoryTest(categories, false, Collections.singletonList(1), true, true, AVAILABLE_SEATS, AVAILABLE_SEATS+1);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsBounded() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 1,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null));
        performExistingCategoryTest(categories, true, Collections.singletonList(2), false, true, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsNoExtensionAllowedBounded() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null));
        performExistingCategoryTest(categories, true, Collections.singletonList(AVAILABLE_SEATS + 1), false, false, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsNoExtensionAllowedNotBounded() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        performExistingCategoryTest(categories, true, Collections.singletonList(AVAILABLE_SEATS + 1), false, false, 0, AVAILABLE_SEATS);
    }

    @Test
    public void testReserveFromNewCategory() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 1,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null));
        Pair<Event, String> eventWithUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventWithUsername.getKey();
        String username = eventWithUsername.getValue();
        DateTimeModification expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now().plusDays(1));
        CustomerData customerData = new CustomerData("Integration", "Test", "integration-test@test.ch", "Billing Address", "reference", "en", "1234", "CH");
        Category category = new Category(null, "name", new BigDecimal("100.00"));
        int attendees = AVAILABLE_SEATS;
        List<TicketsInfo> ticketsInfoList = Collections.singletonList(new TicketsInfo(category, generateAttendees(attendees), true, false));
        AdminReservationModification modification = new AdminReservationModification(expiration, customerData, ticketsInfoList, "en", false, null);
        Result<Pair<TicketReservation, List<Ticket>>> result = adminReservationManager.createReservation(modification, event.getShortName(), username);
        assertTrue(result.isSuccess());
        Pair<TicketReservation, List<Ticket>> data = result.getData();
        List<Ticket> tickets = data.getRight();
        assertTrue(tickets.size() == attendees);
        assertNotNull(data.getLeft());
        int categoryId = tickets.get(0).getCategoryId();
        Event modified = eventManager.getSingleEvent(event.getShortName(), username);
        assertEquals(attendees + 1, eventRepository.countExistingTickets(event.getId()).intValue());
        assertEquals(attendees, ticketRepository.findPendingTicketsInCategories(Collections.singletonList(categoryId)).size());
        TicketCategory categoryModified = ticketCategoryRepository.getByIdAndActive(categoryId, event.getId());
        assertEquals(categoryModified.getMaxTickets(), attendees);
        ticketCategoryRepository.findByEventId(event.getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.PENDING)));
        adminReservationManager.confirmReservation(event.getShortName(), data.getLeft().getId(), username);
        ticketCategoryRepository.findByEventId(event.getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.TAKEN)));
        assertFalse(ticketRepository.findAllReservationsConfirmedButNotAssigned(event.getId()).contains(data.getLeft().getId()));
    }

    @Test
    public void testReserveMixed() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 1,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        Pair<Event, String> eventWithUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventWithUsername.getKey();
        String username = eventWithUsername.getValue();
        DateTimeModification expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now().plusDays(1));
        CustomerData customerData = new CustomerData("Integration", "Test", "integration-test@test.ch", "Billing Address", "reference", "en", "1234", "CH");

        TicketCategory existingCategory = ticketCategoryRepository.findByEventId(event.getId()).get(0);


        Category resExistingCategory = new Category(existingCategory.getId(), "", existingCategory.getPrice());
        Category resNewCategory = new Category(null, "name", new BigDecimal("100.00"));
        int attendees = 1;
        List<TicketsInfo> ticketsInfoList = Arrays.asList(new TicketsInfo(resExistingCategory, generateAttendees(attendees), false, false), new TicketsInfo(resNewCategory, generateAttendees(attendees), false, false),new TicketsInfo(resExistingCategory, generateAttendees(attendees), false, false));
        AdminReservationModification modification = new AdminReservationModification(expiration, customerData, ticketsInfoList, "en", false, null);
        Result<Pair<TicketReservation, List<Ticket>>> result = adminReservationManager.createReservation(modification, event.getShortName(), username);
        assertTrue(result.isSuccess());
        Pair<TicketReservation, List<Ticket>> data = result.getData();
        List<Ticket> tickets = data.getRight();
        assertTrue(tickets.size() == 3);
        assertNotNull(data.getLeft());
        assertTrue(tickets.stream().allMatch(t -> t.getTicketsReservationId().equals(data.getKey().getId())));
        int resExistingCategoryId = tickets.get(0).getCategoryId();
        int resNewCategoryId = tickets.get(2).getCategoryId();

        Event modified = eventManager.getSingleEvent(event.getShortName(), username);
        assertEquals(AVAILABLE_SEATS, eventRepository.countExistingTickets(event.getId()).intValue());
        assertEquals(3, ticketRepository.findPendingTicketsInCategories(Arrays.asList(resExistingCategoryId, resNewCategoryId)).size());
        assertEquals(3, ticketRepository.findTicketsInReservation(data.getLeft().getId()).size());

        String reservationId = data.getLeft().getId();
        assertEquals(ticketRepository.findTicketsInReservation(reservationId).stream().findFirst().get().getId(),
            ticketRepository.findFirstTicketInReservation(reservationId).get().getId());

        ticketCategoryRepository.findByEventId(event.getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.PENDING)));
        adminReservationManager.confirmReservation(event.getShortName(), data.getLeft().getId(), username);
        ticketCategoryRepository.findByEventId(event.getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.TAKEN)));
        assertFalse(ticketRepository.findAllReservationsConfirmedButNotAssigned(event.getId()).contains(data.getLeft().getId()));
    }

    @Test
    public void testConfirmReservation() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 1,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null));
        Triple<Event, String, TicketReservation> testResult = performExistingCategoryTest(categories, true, Collections.singletonList(2), false, true, 0, AVAILABLE_SEATS);
        assertNotNull(testResult);
        Result<Triple<TicketReservation, List<Ticket>, Event>> result = adminReservationManager.confirmReservation(testResult.getLeft().getShortName(), testResult.getRight().getId(), testResult.getMiddle());
        assertTrue(result.isSuccess());
        Triple<TicketReservation, List<Ticket>, Event> triple = result.getData();
        assertEquals(TicketReservation.TicketReservationStatus.COMPLETE, triple.getLeft().getStatus());
        triple.getMiddle().forEach(t -> assertEquals(Ticket.TicketStatus.ACQUIRED, t.getStatus()));
        assertTrue(emailMessageRepository.findByEventId(triple.getRight().getId(), 0, 50, null).isEmpty());
        ticketCategoryRepository.findByEventId(triple.getRight().getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.TAKEN)));
        assertFalse(ticketRepository.findAllReservationsConfirmedButNotAssigned(triple.getRight().getId()).contains(triple.getLeft().getId()));
    }

    private Triple<Event, String, TicketReservation> performExistingCategoryTest(List<TicketCategoryModification> categories, boolean bounded,
                                                                                 List<Integer> attendeesNr, boolean addSeatsIfNotAvailable, boolean expectSuccess,
                                                                                 int reservedTickets, int expectedEventSeats) {
        assertEquals("Test error: categories' size must be equal to attendees' size", categories.size(), attendeesNr.size());
        Pair<Event, String> eventWithUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventWithUsername.getKey();
        String username = eventWithUsername.getValue();
        DateTimeModification expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now().plusDays(1));
        CustomerData customerData = new CustomerData("Integration", "Test", "integration-test@test.ch", "Billing Address", "reference", "en", "1234", "CH");
        Iterator<Integer> attendeesIterator = attendeesNr.iterator();
        List<TicketCategory> existingCategories = ticketCategoryRepository.findByEventId(event.getId());
        List<Attendee> allAttendees = new ArrayList<>();
        List<TicketsInfo> ticketsInfoList = existingCategories.stream()
            .map(existingCategory -> {
                Category category = new Category(existingCategory.getId(), existingCategory.getName(), existingCategory.getPrice());
                List<Attendee> attendees = generateAttendees(attendeesIterator.next());
                allAttendees.addAll(attendees);
                return new TicketsInfo(category, attendees, addSeatsIfNotAvailable, false);
            }).collect(toList());
        AdminReservationModification modification = new AdminReservationModification(expiration, customerData, ticketsInfoList, "en", false, null);

        if(reservedTickets > 0) {
            TicketReservationModification trm = new TicketReservationModification();
            trm.setAmount(reservedTickets);
            trm.setTicketCategoryId(existingCategories.get(0).getId());
            TicketReservationWithOptionalCodeModification r = new TicketReservationWithOptionalCodeModification(trm, Optional.empty());
            ticketReservationManager.createTicketReservation(event, Collections.singletonList(r), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
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
        assertTrue(data.getRight().size() == attendeesNr.stream().mapToInt(i -> i).sum());
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
        ticketCategoryRepository.findByEventId(modified.getId()).forEach(tc -> assertTrue(specialPriceRepository.findAllByCategoryId(tc.getId()).stream().allMatch(sp -> sp.getStatus() == SpecialPrice.Status.PENDING)));
        assertFalse(ticketRepository.findAllReservationsConfirmedButNotAssigned(event.getId()).contains(data.getLeft().getId()));
    }

    private List<Attendee> generateAttendees(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new Attendee(null, "Attendee "+i, "Test" + i, "attendee"+i+"@test.ch", "en",false, null, Collections.emptyMap()))
            .collect(toList());
    }
}