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
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.modification.*;
import alfio.model.result.Result;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
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

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {RepositoryConfiguration.class, DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class EventManagerIntegrationTest {

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
    }

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventStatisticsManager eventStatisticsManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Autowired
    private WaitingQueueSubscriptionProcessor waitingQueueSubscriptionProcessor;

    @Test
    public void testUnboundedTicketsGeneration() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));
    }

    @Test
    public void testEventGeneration() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", true));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testUnboundedEventGeneration() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(AVAILABLE_SEATS, tickets.stream().filter(t -> t.getCategoryId() == null).count());
        EventWithStatistics eventWithStatistics = eventStatisticsManager.fillWithStatistics(event);
        List<TicketCategoryWithStatistic> ticketCategories = eventWithStatistics.getTicketCategories();
        assertEquals(1, ticketCategories.size());
        assertEquals(0, ticketCategories.get(0).getMaxTickets());
    }

    @Test
    public void testEventGenerationWithUnboundedCategory() {
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true),
            new TicketCategoryModification(null, "default", 9,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true),
            new TicketCategoryModification(null, "default", 0,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(1, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEventGenerationWithOverflow() {
        List<TicketCategoryModification> categories = Arrays.asList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", true),
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", true),
                new TicketCategoryModification(null, "default", 0,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertTrue(tickets.stream().noneMatch(t -> t.getCategoryId() == null));
    }

    /**
     * When adding an unbounded category, we won't update the tickets status, because:
     * 1) if the unbounded category is using existing seats, the event cannot be already sold-out
     * 2) if the unbounded category has been added after an event edit (add seats), then the tickets are already "RELEASED"
     * 3) if there is another unbounded category, then it is safe not to update the tickets' status, in order to not
     *    interfere with the existing category
     *
     */
    @Test
    public void testAddUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", true));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", -1,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false);
        eventManager.insertCategory(event.getId(), tcm, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testAddUnboundedCategoryShrinkBoundedCategory() {
        //create the event with a single category which contains all the tickets
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", true));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        //shrink the original category to AVAILABLE_SEATS - 2, this would free two seats
        int categoryId = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0).getId();
        TicketCategoryModification shrink = new TicketCategoryModification(categoryId, "default", AVAILABLE_SEATS - 2,
            new DateTimeModification(LocalDate.now(), LocalTime.now()),
            new DateTimeModification(LocalDate.now(), LocalTime.now()),
            DESCRIPTION, BigDecimal.TEN, false, "", true);
        eventManager.updateCategory(categoryId, event.getId(), shrink, pair.getRight());

        //now insert an unbounded ticket category
        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false);
        eventManager.insertCategory(event.getId(), tcm, pair.getValue());

        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(18, tickets.stream().filter(t -> t.getCategoryId() != null && t.getCategoryId() == categoryId).count());
        assertEquals(2, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testIncreaseEventSeatsWithAnUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), Event.EventType.INTERNAL, null, null, null, null, null, null, null, event.getOrganizationId(), null, null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 40, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null, 7, null, null);
        eventManager.updateEventPrices(event, update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(20, tickets.size());
        assertEquals(20, ticketRepository.countReleasedUnboundedTickets(event.getId()).intValue());
        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        tickets = ticketRepository.findFreeByEventId(event.getId());
        assertEquals(40, tickets.size());
        assertEquals(40, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testIncreaseEventSeatsWithABoundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", true));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), Event.EventType.INTERNAL, null, null, null, null, null, null, null, event.getOrganizationId(), null, null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 40, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null, 7, null, null);
        eventManager.updateEventPrices(event, update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(20, tickets.size());
        assertEquals(20, ticketRepository.countReleasedUnboundedTickets(event.getId()).intValue());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() != null).count());
    }

    @Test
    public void testDecreaseEventSeatsWithAnUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), Event.EventType.INTERNAL, null, null, null, null, null, null, null, event.getOrganizationId(), null, null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 10, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null, 7, null, null);
        eventManager.updateEventPrices(event, update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(10, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testDecreaseEventSeatsWithABoundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", true));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), Event.EventType.INTERNAL, null, null, null, null, null, null, null, event.getOrganizationId(), null, null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 10, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null, 7, null, null);
        eventManager.updateEventPrices(event, update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(10, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() != null));
    }

    @Test
    public void testAddBoundedCategoryToUnboundedEvent() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 0,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true);
        Result<Integer> result = eventManager.insertCategory(event, tcm, pair.getValue());
        assertTrue(result.isSuccess());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(10, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
        assertEquals(10, ticketRepository.countReleasedTicketInCategory(event.getId(), result.getData()).intValue());
    }

    @Test
    public void testUpdateBoundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        TicketCategoryWithStatistic category = eventStatisticsManager.fillWithStatistics(event).getTicketCategories().get(0);
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), "default", 20,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false);
        eventManager.updateCategory(category.getId(), event.getId(), tcm, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(0, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    public void testUpdateEventHeader() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getLeft();
        String username = pair.getRight();

        Map<String, String> desc = new HashMap<>();
        desc.put("en", "muh description new");
        desc.put("it", "muh description new");
        desc.put("de", "muh description new");

        EventModification em = new EventModification(event.getId(), Event.EventType.INTERNAL, "http://example.com/new", null, "http://example.com/tc", "https://example.com/img.png", null, event.getShortName(), "new display name",
            event.getOrganizationId(), event.getLocation(), desc,
            DateTimeModification.fromZonedDateTime(event.getBegin()),
            DateTimeModification.fromZonedDateTime(event.getEnd().plusDays(42)),
            event.getRegularPrice(),
            event.getCurrency(),
            event.getAvailableSeats(),
            event.getVat(),
            event.isVatIncluded(),
            event.getAllowedPaymentProxies(),
            Collections.emptyList(),
            false,
            null,
            7,
            null,
            null);

        eventManager.updateEventHeader(event, em, username);

        Event updatedEvent = eventRepository.findById(event.getId());

        Assert.assertEquals("http://example.com/new", updatedEvent.getWebsiteUrl());
        Assert.assertEquals("http://example.com/tc", updatedEvent.getTermsAndConditionsUrl());
        Assert.assertEquals("https://example.com/img.png", updatedEvent.getImageUrl());
        Assert.assertEquals("new display name", updatedEvent.getDisplayName());
    }

    @Test
    public void testUpdateBoundedFlagToTrue() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getLeft();
        String username = pair.getRight();
        assertEquals(new Integer(AVAILABLE_SEATS), ticketRepository.countFreeTicketsForUnbounded(event.getId()));
        TicketCategoryWithStatistic category = eventStatisticsManager.getSingleEventWithStatistics(event.getShortName(), username).getTicketCategories().get(0);
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), AVAILABLE_SEATS,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            category.getDescription(), category.getPrice(), false, "", true);
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(new Integer(0), ticketRepository.countFreeTicketsForUnbounded(event.getId()));
    }

    @Test
    public void testUpdateBoundedFlagToFalse() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getLeft();
        String username = pair.getRight();
        assertEquals(new Integer(0), ticketRepository.countFreeTicketsForUnbounded(event.getId()));
        TicketCategoryWithStatistic category = eventStatisticsManager.getSingleEventWithStatistics(event.getShortName(), username).getTicketCategories().get(0);
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), AVAILABLE_SEATS,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            category.getDescription(), category.getPrice(), false, "", false);
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(new Integer(AVAILABLE_SEATS), ticketRepository.countFreeTicketsForUnbounded(event.getId()));
    }

    @Test
    public void testValidationBoundedFailedRestrictedFlag() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, true, "", true));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getLeft();
        String username = pair.getRight();
        TicketCategoryWithStatistic category = eventStatisticsManager.getSingleEventWithStatistics(event.getShortName(), username).getTicketCategories().get(0);
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), 10,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            category.getDescription(), category.getPrice(), true, "", false);
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertFalse(result.isSuccess());
    }

    @Test
    public void testValidationBoundedFailedPendingTickets() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getLeft();
        String username = pair.getRight();
        TicketCategoryWithStatistic category = eventStatisticsManager.getSingleEventWithStatistics(event.getShortName(), username).getTicketCategories().get(0);
        List<Integer> tickets = ticketRepository.selectTicketInCategoryForUpdate(event.getId(), category.getId(), 1, Collections.singletonList(Ticket.TicketStatus.FREE.name()));
        String reservationId = "12345678";
        ticketReservationRepository.createNewReservation(reservationId, DateUtils.addDays(new Date(), 1), null, "en", event.getId());
        ticketRepository.reserveTickets(reservationId, tickets, category.getId(), "en", 100);
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), 10,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            category.getDescription(), category.getPrice(), false, "", false);
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertFalse(result.isSuccess());
    }

    @Test
    public void testIncreaseRestrictedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, true, "", true));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getLeft();
        String username = pair.getRight();
        TicketCategoryWithStatistic category = eventStatisticsManager.getSingleEventWithStatistics(event.getShortName(), username).getTicketCategories().get(0);
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), 11,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            category.getDescription(), category.getPrice(), true, "", true);
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(11, ticketRepository.countFreeTickets(event.getId(), category.getId()).intValue());
    }

    @Test
    public void testDecreaseRestrictedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, true, "", true));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getLeft();
        String username = pair.getRight();
        TicketCategoryWithStatistic category = eventStatisticsManager.getSingleEventWithStatistics(event.getShortName(), username).getTicketCategories().get(0);
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), 9,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            category.getDescription(), category.getPrice(), true, "", true);
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(9, ticketRepository.countFreeTickets(event.getId(), category.getId()).intValue());
        assertEquals(1, ticketRepository.countReleasedUnboundedTickets(event.getId()).intValue());
    }

    @Test
    public void testNewCategoryBoundedAddReleasedTickets() {
        Pair<Event, String> eventAndUser = generateAndEditEvent(AVAILABLE_SEATS + 10);
        //now we have 20 free seats, 10 of which RELEASED
        Event event = eventAndUser.getLeft();
        String username = eventAndUser.getRight();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "additional", 20,
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(event.getZoneId()).minusMinutes(1)),
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(event.getZoneId()).plusDays(5)),
            Collections.emptyMap(), BigDecimal.TEN, false, "", true);
        Result<Integer> result = eventManager.insertCategory(event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(20, ticketRepository.countReleasedTicketInCategory(event.getId(), result.getData()).intValue());
    }

    @Test
    public void testNewRestrictedCategory() {
        Pair<Event, String> eventAndUser = generateAndEditEvent(AVAILABLE_SEATS + 10);
        //now we have 20 free seats, 10 of which RELEASED
        Event event = eventAndUser.getLeft();
        String username = eventAndUser.getRight();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "additional", 20,
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(event.getZoneId()).minusMinutes(1)),
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(event.getZoneId()).plusDays(5)),
            Collections.emptyMap(), BigDecimal.TEN, true, "", true);
        Result<Integer> result = eventManager.insertCategory(event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(20, ticketRepository.countFreeTickets(event.getId(), result.getData()).intValue());
    }



    private Pair<Event, String> generateAndEditEvent(int newEventSize) {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        if(newEventSize != AVAILABLE_SEATS) {
            EventModification update = new EventModification(event.getId(), Event.EventType.INTERNAL, null, null, null, null, null, null, null, event.getOrganizationId(), null, null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), newEventSize, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null, 7, null, null);
            eventManager.updateEventPrices(event, update, pair.getValue());
        }
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        if(newEventSize > AVAILABLE_SEATS) {
            assertEquals(newEventSize - AVAILABLE_SEATS, ticketRepository.countReleasedUnboundedTickets(event.getId()).intValue());
        }
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() != null).count());
        return Pair.of(eventRepository.findById(event.getId()), pair.getRight());
    }

}
