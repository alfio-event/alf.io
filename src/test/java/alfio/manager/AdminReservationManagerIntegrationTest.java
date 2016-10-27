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
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.modification.AdminReservationModification;
import alfio.model.modification.AdminReservationModification.Attendee;
import alfio.model.modification.AdminReservationModification.Category;
import alfio.model.modification.AdminReservationModification.CustomerData;
import alfio.model.modification.AdminReservationModification.TicketsInfo;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.result.Result;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.tuple.Pair;
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
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
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

    @Test
    public void testReserveFromExistingCategory() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        performExistingCategoryTest(categories, false, Collections.singletonList(1), false, true);
    }

    @Test
    public void testReserveFromExistingMultipleCategories() throws Exception {
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false),
            new TicketCategoryModification(null, "2nd", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        performExistingCategoryTest(categories, false, Arrays.asList(10,10), false, true);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsNotBounded() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 1,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        performExistingCategoryTest(categories, false, Collections.singletonList(1), false, true);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsBounded() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 1,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true));
        performExistingCategoryTest(categories, true, Collections.singletonList(2), false, true);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsNoExtensionAllowedBounded() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true));
        performExistingCategoryTest(categories, true, Collections.singletonList(AVAILABLE_SEATS + 1), false, false);
    }

    @Test
    public void testReserveFromExistingCategoryNotEnoughSeatsNoExtensionAllowedNotBounded() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        performExistingCategoryTest(categories, true, Collections.singletonList(AVAILABLE_SEATS + 1), false, false);
    }

    @Test
    public void testReserveFromNewCategory() throws Exception {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 1,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true));
        Pair<Event, String> eventWithUsername = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = eventWithUsername.getKey();
        String username = eventWithUsername.getValue();
        DateTimeModification expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now().plusDays(1));
        CustomerData customerData = new CustomerData("Integration", "Test", "integration-test@test.ch");
        Category category = new Category(null, "name", new BigDecimal("100.00"));
        int attendees = AVAILABLE_SEATS;
        List<TicketsInfo> ticketsInfoList = Collections.singletonList(new TicketsInfo(category, generateAttendees(attendees), true));
        AdminReservationModification modification = new AdminReservationModification(expiration, customerData, ticketsInfoList, "en");
        Result<Pair<TicketReservation, List<Ticket>>> result = adminReservationManager.createReservation(modification, event.getShortName(), username);
        assertTrue(result.isSuccess());
        Pair<TicketReservation, List<Ticket>> data = result.getData();
        List<Ticket> tickets = data.getRight();
        assertTrue(tickets.size() == attendees);
        assertNotNull(data.getLeft());
        int categoryId = tickets.get(0).getCategoryId();
        Event modified = eventManager.getSingleEvent(event.getShortName(), username);
        assertEquals(attendees + 1, modified.getAvailableSeats());
        assertEquals(attendees, ticketRepository.findPendingTicketsInCategories(Collections.singletonList(categoryId)).size());
        TicketCategory categoryModified = ticketCategoryRepository.getById(categoryId, event.getId());
        assertEquals(categoryModified.getMaxTickets(), attendees);
    }

    private void performExistingCategoryTest(List<TicketCategoryModification> categories, boolean bounded, List<Integer> attendeesNr, boolean addSeatsIfNotAvailable, boolean expectSuccess) {
        assertEquals("Test error: categories' size must be equal to attendees' size", categories.size(), attendeesNr.size());
        Pair<Event, String> eventWithUsername = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = eventWithUsername.getKey();
        String username = eventWithUsername.getValue();
        DateTimeModification expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now().plusDays(1));
        CustomerData customerData = new CustomerData("Integration", "Test", "integration-test@test.ch");
        Iterator<Integer> attendeesIterator = attendeesNr.iterator();
        List<TicketCategory> existingCategories = ticketCategoryRepository.findByEventId(event.getId());
        List<Attendee> allAttendees = new ArrayList<>();
        List<TicketsInfo> ticketsInfoList = existingCategories.stream()
            .map(existingCategory -> {
                Category category = new Category(existingCategory.getId(), existingCategory.getName(), existingCategory.getPrice());
                List<Attendee> attendees = generateAttendees(attendeesIterator.next());
                allAttendees.addAll(attendees);
                return new TicketsInfo(category, attendees, addSeatsIfNotAvailable);
            }).collect(toList());
        AdminReservationModification modification = new AdminReservationModification(expiration, customerData, ticketsInfoList, "en");
        Result<Pair<TicketReservation, List<Ticket>>> result = adminReservationManager.createReservation(modification, event.getShortName(), username);
        if(expectSuccess) {
            validateSuccess(bounded, attendeesNr, event, username, existingCategories, result, allAttendees);
        } else {
            assertFalse(result.isSuccess());
        }
    }

    private void validateSuccess(boolean bounded, List<Integer> attendeesNr, Event event, String username, List<TicketCategory> existingCategories, Result<Pair<TicketReservation, List<Ticket>>> result, List<Attendee> allAttendees) {
        assertTrue(result.isSuccess());
        Pair<TicketReservation, List<Ticket>> data = result.getData();
        assertTrue(data.getRight().size() == attendeesNr.stream().mapToInt(i -> i).sum());
        assertNotNull(data.getLeft());
        Event modified = eventManager.getSingleEvent(event.getShortName(), username);
        assertEquals(AVAILABLE_SEATS, modified.getAvailableSeats());
        List<Ticket> tickets = ticketRepository.findPendingTicketsInCategories(existingCategories.stream().map(TicketCategory::getId).collect(toList()));
        assertEquals(attendeesNr.stream().mapToInt(i -> i).sum(), tickets.size());
        if(bounded) {
            final Iterator<Integer> iterator = attendeesNr.iterator();
            existingCategories.forEach(existingCategory -> {
                TicketCategory categoryModified = ticketCategoryRepository.getById(existingCategory.getId(), event.getId());
                assertEquals(categoryModified.getMaxTickets(), iterator.next().intValue());
            });
        }
        for (int i = 0; i < tickets.size(); i++) {
            Attendee attendee = allAttendees.get(i);
            if(!attendee.isEmpty()) {
                Ticket ticket = data.getRight().get(i);
                assertTrue(ticket.getAssigned());
                assertEquals(attendee.getFullName(), ticket.getFullName());
                assertEquals(attendee.getEmailAddress(), ticket.getEmail());
            }
        }
    }

    private List<Attendee> generateAttendees(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new Attendee("Attendee "+i, "Test" + i, "attendee"+i+"@test.ch"))
            .collect(toList());
    }
}