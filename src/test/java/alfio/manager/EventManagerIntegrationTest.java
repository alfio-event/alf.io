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
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.*;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
class EventManagerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventManager eventManager;
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
    @Autowired
    private TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    @Autowired
    private SpecialPriceManager specialPriceManager;
    @Autowired
    private SpecialPriceTokenGenerator specialPriceTokenGenerator;
    @Autowired
    private TicketReservationManager ticketReservationManager;
    @Autowired
    private ClockProvider clockProvider;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void testUnboundedTicketsGeneration() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        var eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));
        BaseIntegrationTest.testTransferEventToAnotherOrg(event.getId(), event.getOrganizationId(), eventAndUsername.getRight(), jdbcTemplate);
    }

    @Test
    void testEventGeneration() {

        assertFalse(eventManager.eventExistsById(-9000));

        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        var eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();
        assertTrue(eventManager.eventExistsById(event.getId()));
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
        BaseIntegrationTest.testTransferEventToAnotherOrg(event.getId(), event.getOrganizationId(), eventAndUsername.getRight(), jdbcTemplate);
    }

    @Test
    void testUnboundedEventGeneration() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        var eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(AVAILABLE_SEATS, tickets.stream().filter(t -> t.getCategoryId() == null).count());
        List<TicketCategory> ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        assertEquals(1, ticketCategories.size());
        assertEquals(0, ticketCategories.get(0).getMaxTickets());
        BaseIntegrationTest.testTransferEventToAnotherOrg(event.getId(), event.getOrganizationId(), eventAndUsername.getRight(), jdbcTemplate);
    }

    @Test
    void testEventGenerationWithUnboundedCategory() {
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 9,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 0,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),

                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(1, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    void testEventGenerationWithOverflow() {
        List<TicketCategoryModification> categories = Arrays.asList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 0,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        assertThrows(IllegalArgumentException.class, () -> initEvent(categories, organizationRepository, userManager, eventManager, eventRepository));
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
    void testAddUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, -1,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        eventManager.insertCategory(event.getId(), tcm, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    void testAddUnboundedCategoryShrinkBoundedCategory() {
        //create the event with a single category which contains all the tickets
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        //shrink the original category to AVAILABLE_SEATS - 2, this would free two seats
        int categoryId = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0).getId();
        TicketCategoryModification shrink = new TicketCategoryModification(categoryId, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS - 2,
            new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
            new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
            DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        eventManager.updateCategory(categoryId, event.getId(), shrink, pair.getRight());

        //now insert an unbounded ticket category
        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
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
    void testIncreaseEventSeatsWithAnUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), Event.EventFormat.IN_PERSON, null, null, null, null, null, null, null, null, event.getOrganizationId(), null,
            "0.0", "0.0", ZoneId.systemDefault().getId(), null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 40, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null, 7, null, null, AlfioMetadata.empty(), List.of());
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
    void testIncreaseEventSeatsWithABoundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), Event.EventFormat.IN_PERSON, null, null, null, null, null, null, null, null, event.getOrganizationId(), null,
            "0.0", "0.0", ZoneId.systemDefault().getId(), null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 40, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null, 7, null, null, AlfioMetadata.empty(), List.of());
        eventManager.updateEventPrices(event, update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(20, tickets.size());
        assertEquals(20, ticketRepository.countReleasedUnboundedTickets(event.getId()).intValue());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() != null).count());
    }

    @Test
    void testDecreaseEventSeatsWithAnUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), Event.EventFormat.IN_PERSON, null, null, null, null, null, null, null, null, event.getOrganizationId(), null,
            "0.0", "0.0", ZoneId.systemDefault().getId(), null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 10, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null, 7, null, null, AlfioMetadata.empty(), List.of());
        eventManager.updateEventPrices(event, update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(10, tickets.size());
        assertEquals(10, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    void testDecreaseEventSeatsWithABoundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        EventModification update = new EventModification(event.getId(), Event.EventFormat.IN_PERSON, null, null, null, null, null, null, null, null, event.getOrganizationId(), null,
            "0.0", "0.0", ZoneId.systemDefault().getId(), null,
                DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(), event.getCurrency(), 10, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(), null, event.isFreeOfCharge(), null, 7, null, null, AlfioMetadata.empty(), List.of());
        eventManager.updateEventPrices(event, update, pair.getValue());
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(10, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() != null));
    }

    @Test
    void testAddBoundedCategoryToUnboundedEvent() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 0,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
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
    void testUpdateBoundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), "default", TicketCategory.TicketAccessType.INHERIT, 20,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        eventManager.updateCategory(category.getId(), event.getId(), tcm, pair.getValue());
        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals(AVAILABLE_SEATS, tickets.size());
        assertEquals(0, tickets.stream().filter(t -> t.getCategoryId() == null).count());
    }

    @Test
    void testUpdateEventHeader() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getLeft();
        String username = pair.getRight();

        Map<String, String> desc = new HashMap<>();
        desc.put("en", "muh description new");
        desc.put("it", "muh description new");
        desc.put("de", "muh description new");

        EventModification em = new EventModification(event.getId(),
            Event.EventFormat.IN_PERSON,
            "http://example.com/new",
            null,
            "http://example.com/tc",
            "http://example.com/pp",
            "https://example.com/img.png",
            null,
            event.getShortName(),
            "new display name",
            event.getOrganizationId(),
            event.getLocation(),
            "0.0",
            "0.0",
            ZoneId.systemDefault().getId(),
            desc,
            DateTimeModification.fromZonedDateTime(event.getBegin()),
            DateTimeModification.fromZonedDateTime(event.getEnd().plusDays(42)),
            event.getRegularPrice(),
            event.getCurrency(),
            eventRepository.countExistingTickets(event.getId()),
            event.getVat(),
            event.isVatIncluded(),
            event.getAllowedPaymentProxies(),
            Collections.emptyList(),
            false,
            null,
            7,
            null,
            null,
            AlfioMetadata.empty(),
            List.of());

        eventManager.updateEventHeader(event, em, username);

        Event updatedEvent = eventRepository.findById(event.getId());

        assertEquals("http://example.com/new", updatedEvent.getWebsiteUrl());
        assertEquals("http://example.com/tc", updatedEvent.getTermsAndConditionsUrl());
        assertEquals("http://example.com/pp", updatedEvent.getPrivacyPolicyUrl());
        assertEquals("https://example.com/img.png", updatedEvent.getImageUrl());
        assertEquals("new display name", updatedEvent.getDisplayName());
    }

    @Test
    void testUpdateBoundedFlagToTrue() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getLeft();
        String username = pair.getRight();
        assertEquals(Integer.valueOf(AVAILABLE_SEATS), ticketRepository.countFreeTicketsForUnbounded(event.getId()));
        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        Map<String, String> categoryDescription = ticketCategoryDescriptionRepository.descriptionForTicketCategory(category.getId());
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            categoryDescription, category.getPrice(), false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(Integer.valueOf(0), ticketRepository.countFreeTicketsForUnbounded(event.getId()));
    }

    @Test
    void testUpdateBoundedFlagToFalse() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getLeft();
        String username = pair.getRight();
        assertEquals(Integer.valueOf(0), ticketRepository.countFreeTicketsForUnbounded(event.getId()));

        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        Map<String, String> categoryDescription = ticketCategoryDescriptionRepository.descriptionForTicketCategory(category.getId());

        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            categoryDescription, category.getPrice(), false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(AVAILABLE_SEATS, ticketRepository.countReleasedUnboundedTickets(event.getId()).intValue());
        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        assertEquals(AVAILABLE_SEATS, ticketRepository.countFreeTicketsForUnbounded(event.getId()).intValue());
    }

    @Test
    void testValidationBoundedFailedRestrictedFlag() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),

                DESCRIPTION, BigDecimal.TEN, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        Event event = pair.getLeft();
        String username = pair.getRight();

        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        Map<String, String> categoryDescription = ticketCategoryDescriptionRepository.descriptionForTicketCategory(category.getId());

        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), TicketCategory.TicketAccessType.INHERIT, 10,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            categoryDescription, category.getPrice(), true, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertFalse(result.isSuccess());
    }

    @Test
    void testValidationBoundedFailedPendingTickets() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getLeft();
        String username = pair.getRight();

        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        Map<String, String> categoryDescription = ticketCategoryDescriptionRepository.descriptionForTicketCategory(category.getId());

        List<Integer> tickets = ticketRepository.selectTicketInCategoryForUpdate(event.getId(), category.getId(), 1, Collections.singletonList(Ticket.TicketStatus.FREE.name()));
        String reservationId = "12345678";
        ticketReservationRepository.createNewReservation(reservationId, ZonedDateTime.now(clockProvider.getClock()), DateUtils.addDays(new Date(), 1), null, "en", event.getId(), event.getVat(), event.isVatIncluded(), event.getCurrency(), event.getOrganizationId(), null);
        ticketRepository.reserveTickets(reservationId, tickets, category, "en", event.getVatStatus(), i -> null);
        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), TicketCategory.TicketAccessType.INHERIT, 10,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            categoryDescription, category.getPrice(), false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertFalse(result.isSuccess());
    }

    @Test
    void testIncreaseRestrictedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),

                DESCRIPTION, BigDecimal.TEN, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        Event event = pair.getLeft();
        String username = pair.getRight();

        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        Map<String, String> categoryDescription = ticketCategoryDescriptionRepository.descriptionForTicketCategory(category.getId());

        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), TicketCategory.TicketAccessType.INHERIT, 11,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            categoryDescription, category.getPrice(), true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(11, ticketRepository.countFreeTickets(event.getId(), category.getId()).intValue());
    }

    @Test
    void testDecreaseRestrictedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),

                DESCRIPTION, BigDecimal.TEN, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        Event event = pair.getLeft();
        String username = pair.getRight();

        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        Map<String, String> categoryDescription = ticketCategoryDescriptionRepository.descriptionForTicketCategory(category.getId());

        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), TicketCategory.TicketAccessType.INHERIT, 9,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            categoryDescription, category.getPrice(), true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<TicketCategory> result = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(9, ticketRepository.countFreeTickets(event.getId(), category.getId()).intValue());
        assertEquals(1, ticketRepository.countReleasedUnboundedTickets(event.getId()).intValue());
    }

    //https://github.com/alfio-event/alf.io/issues/335
    @Test
    void testDecreaseRestrictedCategoryWithAlreadySentToken() {

        ensureMinimalConfiguration(configurationRepository);

        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 4,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),

                DESCRIPTION, BigDecimal.TEN, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        Event event = pair.getLeft();
        String username = pair.getRight();

        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        Map<String, String> categoryDescription = ticketCategoryDescriptionRepository.descriptionForTicketCategory(category.getId());

        specialPriceTokenGenerator.generatePendingCodesForCategory(category.getId());

        List<SendCodeModification> linked = specialPriceManager.linkAssigneeToCode(Arrays.asList(
            new SendCodeModification(null, "test1", "test@test.com", "it"),
            new SendCodeModification(null, "test2", "test@test.com", "it")),event.getShortName(), category.getId(), username);

        specialPriceManager.sendCodeToAssignee(linked, event.getShortName(), category.getId(), username);


        TicketCategoryModification tcmOk = new TicketCategoryModification(category.getId(), category.getName(), TicketCategory.TicketAccessType.INHERIT, 2,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            categoryDescription, category.getPrice(), true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<TicketCategory> resOk = eventManager.updateCategory(category.getId(), event, tcmOk, username);
        assertTrue(resOk.isSuccess());


        TicketCategoryModification tcm = new TicketCategoryModification(category.getId(), category.getName(), TicketCategory.TicketAccessType.INHERIT, 1,
            DateTimeModification.fromZonedDateTime(category.getUtcInception()),
            DateTimeModification.fromZonedDateTime(category.getUtcExpiration()),
            categoryDescription, category.getPrice(), true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<TicketCategory> res = eventManager.updateCategory(category.getId(), event, tcm, username);
        assertFalse(res.isSuccess());
        assertTrue(res.getErrors().contains(ErrorCode.CategoryError.NOT_ENOUGH_FREE_TOKEN_FOR_SHRINK));
    }

    @Test
    void testNewCategoryBoundedAddReleasedTickets() {
        Pair<Event, String> eventAndUser = generateAndEditEvent(AVAILABLE_SEATS + 10);
        //now we have 20 free seats, 10 of which RELEASED
        Event event = eventAndUser.getLeft();
        String username = eventAndUser.getRight();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "additional", TicketCategory.TicketAccessType.INHERIT, 20,
            DateTimeModification.fromZonedDateTime(event.now(clockProvider).minusMinutes(1)),
            DateTimeModification.fromZonedDateTime(event.now(clockProvider).plusDays(5)),
            Collections.emptyMap(), BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<Integer> result = eventManager.insertCategory(event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(20, ticketRepository.countReleasedTicketInCategory(event.getId(), result.getData()).intValue());
    }

    @Test
    void testNewRestrictedCategory() {
        Pair<Event, String> eventAndUser = generateAndEditEvent(AVAILABLE_SEATS + 10);
        //now we have 20 free seats, 10 of which RELEASED
        Event event = eventAndUser.getLeft();
        String username = eventAndUser.getRight();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "additional", TicketCategory.TicketAccessType.INHERIT, 20,
            DateTimeModification.fromZonedDateTime(event.now(clockProvider).minusMinutes(1)),
            DateTimeModification.fromZonedDateTime(event.now(clockProvider).plusDays(5)),
            Collections.emptyMap(), BigDecimal.TEN, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<Integer> result = eventManager.insertCategory(event, tcm, username);
        assertTrue(result.isSuccess());
        assertEquals(20, ticketRepository.countFreeTickets(event.getId(), result.getData()).intValue());
    }

    @Test
    void testNewBoundedCategoryWithExistingBoundedAndPendingTicket() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getLeft();
        String username = pair.getRight();
        assertEquals(Integer.valueOf(AVAILABLE_SEATS), ticketRepository.countFreeTicketsForUnbounded(event.getId()));
        TicketReservationModification trm = new TicketReservationModification();
        trm.setQuantity(1);
        trm.setTicketCategoryId(ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0).getId());
        TicketReservationWithOptionalCodeModification reservation = new TicketReservationWithOptionalCodeModification(trm, Optional.empty());
        ticketReservationManager.createTicketReservation(event, Collections.singletonList(reservation), Collections.emptyList(),
            DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        TicketCategoryModification tcm = new TicketCategoryModification(null, "new", TicketCategory.TicketAccessType.INHERIT, 1,
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(clockProvider.getClock())),
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(clockProvider.getClock()).plusDays(1)),
            Collections.emptyMap(), BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<Integer> insertResult = eventManager.insertCategory(event, tcm, username);
        assertTrue(insertResult.isSuccess());
        Integer categoryID = insertResult.getData();
        tcm = new TicketCategoryModification(categoryID, tcm.getName(), TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
            tcm.getInception(), tcm.getExpiration(), tcm.getDescription(), tcm.getPrice(), false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<TicketCategory> result = eventManager.updateCategory(categoryID, event, tcm, username);
        assertFalse(result.isSuccess());
    }

    @Test
    void deleteUnboundedTicketCategorySuccess() {
        List<TicketCategoryModification> cat = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(cat, organizationRepository, userManager, eventManager, eventRepository);
        var event = pair.getLeft();
        var categories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        assertEquals(1, categories.size());
        int categoryId = categories.get(0).getId();
        eventManager.deleteCategory(event.getShortName(), categoryId, pair.getRight());
        assertEquals(0, ticketCategoryRepository.findAllTicketCategories(event.getId()).size());
        assertEquals(AVAILABLE_SEATS, (int) ticketRepository.countFreeTicketsForUnbounded(event.getId()));
    }

    @Test
    void deleteUnboundedTicketCategoryFailure() {
        List<TicketCategoryModification> cat = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(cat, organizationRepository, userManager, eventManager, eventRepository);
        var event = pair.getLeft();
        var tickets = ticketRepository.selectNotAllocatedTicketsForUpdate(event.getId(), 1, List.of(Ticket.TicketStatus.FREE.name()));
        var categories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        assertEquals(1, categories.size());
        int categoryId = categories.get(0).getId();
        String reservationId = UUID.randomUUID().toString();
        ticketReservationRepository.createNewReservation(reservationId, ZonedDateTime.now(clockProvider.getClock()), DateUtils.addDays(new Date(), 1), null, "en", event.getId(), event.getVat(), event.isVatIncluded(), event.getCurrency(), event.getOrganizationId(), null);
        int result = ticketRepository.reserveTickets(reservationId, tickets, categories.get(0), "en", event.getVatStatus(), i -> null);
        assertEquals(1, result);
        assertThrows(IllegalStateException.class, () -> eventManager.deleteCategory(event.getShortName(), categoryId, pair.getRight()));
    }

    @Test
    void deleteBoundedTicketCategorySuccess() {
        List<TicketCategoryModification> cat = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(cat, organizationRepository, userManager, eventManager, eventRepository);
        var event = pair.getLeft();
        var categories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        assertEquals(1, categories.size());
        int categoryId = categories.get(0).getId();
        eventManager.deleteCategory(event.getShortName(), categoryId, pair.getRight());
        assertEquals(0, ticketCategoryRepository.findAllTicketCategories(event.getId()).size());
        waitingQueueSubscriptionProcessor.handleWaitingTickets();
        assertEquals(AVAILABLE_SEATS, (int) ticketRepository.countFreeTicketsForUnbounded(event.getId()));
    }

    @Test
    void deleteBoundedTicketCategoryFailure() {
        List<TicketCategoryModification> cat = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(cat, organizationRepository, userManager, eventManager, eventRepository);
        var event = pair.getLeft();
        var categories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        assertEquals(1, categories.size());
        int categoryId = categories.get(0).getId();
        var tickets = ticketRepository.selectTicketInCategoryForUpdate(event.getId(), categoryId, 1, List.of(Ticket.TicketStatus.FREE.name()));
        String reservationId = UUID.randomUUID().toString();
        ticketReservationRepository.createNewReservation(reservationId, ZonedDateTime.now(clockProvider.getClock()), DateUtils.addDays(new Date(), 1), null, "en", event.getId(), event.getVat(), event.isVatIncluded(), event.getCurrency(), event.getOrganizationId(), null);
        int result = ticketRepository.reserveTickets(reservationId, tickets, categories.get(0), "en", event.getVatStatus(), i -> null);
        assertEquals(1, result);
        assertThrows(IllegalStateException.class, () -> eventManager.deleteCategory(event.getShortName(), categoryId, pair.getRight()));
    }

    @Test
    void deletedBoundedCategoriesNotIncludedInStatistics() {
        /*
        String reservationId = UUID.randomUUID().toString();
        var tickets = ticketRepository.selectNotAllocatedTicketsForUpdate(event.getId(), 1, List.of(Ticket.TicketStatus.FREE.name()));
        int result = ticketRepository.reserveTickets(reservationId, tickets, categoryId, "en", 0, "CHF");
        assertEquals(1, result);
        //var statistics = eventRepository.findStatisticsFor(event.getId());
        //        statistics.
         */
        var to_be_deleted = "to be deleted";
        List<TicketCategoryModification> cat = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS - 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, to_be_deleted, TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
            );
        Pair<Event, String> pair = initEvent(cat, organizationRepository, userManager, eventManager, eventRepository);
        var event = pair.getLeft();
        var categories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        assertEquals(2, categories.size());
        // check statistics
        var statistics = eventRepository.findStatisticsFor(event.getId());
        assertEquals(AVAILABLE_SEATS, statistics.getNotSoldTickets());
        assertEquals(AVAILABLE_SEATS, statistics.getAvailableSeats());
        var categoryToBeDeleted = categories.stream().filter(c -> c.getName().equals(to_be_deleted)).findFirst().orElseThrow();
        var username = pair.getRight();
        eventManager.deleteCategory(event.getShortName(), categoryToBeDeleted.getId(), username);
        statistics = eventRepository.findStatisticsFor(event.getId());
        // statistics now should report only 10 tickets to sell
        assertEquals(AVAILABLE_SEATS - 10, statistics.getNotSoldTickets());
        assertEquals(AVAILABLE_SEATS, statistics.getAvailableSeats());

        // expand the existing category and check again
        var activeCategory = categories.stream().filter(c -> c.getId() != categoryToBeDeleted.getId()).findFirst().orElseThrow();
        var updateResult = eventManager.updateCategory(
            activeCategory.getId(),
            event,
            new TicketCategoryModification(activeCategory.getId(), "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            username
        );
        assertTrue(updateResult.isSuccess());
        // statistics should now report 20 tickets to sell
        statistics = eventRepository.findStatisticsFor(event.getId());
        assertEquals(AVAILABLE_SEATS, statistics.getNotSoldTickets());
        assertEquals(AVAILABLE_SEATS, statistics.getAvailableSeats());
    }

    @Test
    void rearrangeTicketCategories() {
        List<TicketCategoryModification> cat = List.of(
            new TicketCategoryModification(null, "first", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 2, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "second", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 1, null, null, AlfioMetadata.empty()));

        Pair<Event, String> pair = initEvent(cat, organizationRepository, userManager, eventManager, eventRepository);
        var event = pair.getLeft();
        var categories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        assertEquals(2, categories.size());
        assertEquals("second", categories.get(0).getName());

        // swap categories
        var first = categories.get(0);
        var second = categories.get(1);

        var categoryModifications = List.of(new CategoryOrdinalModification(first.getId(), first.getName(), second.getOrdinal()), new CategoryOrdinalModification(second.getId(), second.getName(), first.getOrdinal()));

        eventManager.rearrangeCategories(event.getShortName(), categoryModifications, pair.getRight());

        categories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        assertEquals(2, categories.size());
        assertEquals("first", categories.get(0).getName());

    }

    @Test
    void updateEventFormat() {
        var categories = List.of(
            new TicketCategoryModification(null, "first", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 2, null, null, AlfioMetadata.empty()));

        var eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        var event = eventAndUsername.getLeft();

        // add ON_SITE payment method
        var withOnSite = List.of(PaymentProxy.OFFLINE, PaymentProxy.ON_SITE);
        var onSitePaymentMethodModification = createEventModification(AVAILABLE_SEATS, event, Event.EventFormat.IN_PERSON, withOnSite, event.getLocation());
        var username = eventAndUsername.getRight();
        eventManager.updateEventPrices(event, onSitePaymentMethodModification, username);

        event = eventRepository.findById(event.getId());
        assertEquals(withOnSite, event.getAllowedPaymentProxies());

        try {
            eventManager.updateEventHeader(event, createEventModification(AVAILABLE_SEATS, event, Event.EventFormat.ONLINE, withOnSite, event.getLocation()), username);
            fail();
        } catch(IllegalArgumentException ex) {
            assertEquals(EventManager.ERROR_ONLINE_ON_SITE_NOT_COMPATIBLE, ex.getMessage());
        }

        event = eventRepository.findById(event.getId());
        // update header and remove ON_SITE
        eventManager.updateEventPrices(event, createEventModification(AVAILABLE_SEATS, event, event.getFormat(), List.of(PaymentProxy.OFFLINE), event.getLocation()), username);
        // retry
        event = eventRepository.findById(event.getId());
        eventManager.updateEventHeader(event, createEventModification(AVAILABLE_SEATS, event, Event.EventFormat.ONLINE, event.getAllowedPaymentProxies(), event.getLocation()), username);
    }

    @Test
    void updateInPersonEventToHybridAndBack() {
        var categories = List.of(
            new TicketCategoryModification(null, "first", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS - 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 2, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "second", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS - 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 2, null, null, AlfioMetadata.empty())
            );

        var eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        var event = eventAndUsername.getLeft();
        var username = eventAndUsername.getRight();

        eventManager.updateEventHeader(event, createEventModification(AVAILABLE_SEATS, event, Event.EventFormat.HYBRID, event.getAllowedPaymentProxies(), event.getLocation()), username);

        // check that all the categories have been converted to IN_PERSON
        assertTrue(ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().allMatch(tc -> tc.getTicketAccessType() == TicketCategory.TicketAccessType.IN_PERSON));

        event = eventRepository.findById(event.getId());

        // revert modification
        eventManager.updateEventHeader(event, createEventModification(AVAILABLE_SEATS, event, Event.EventFormat.IN_PERSON, event.getAllowedPaymentProxies(), event.getLocation()), username);
        // we expect all the access types to be INHERIT
        assertTrue(ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().allMatch(tc -> tc.getTicketAccessType() == TicketCategory.TicketAccessType.INHERIT));

    }

    @Test
    void testFailedUpdateOnlineEventToHybrid() {
        var categories = List.of(
            new TicketCategoryModification(null, "first", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS - 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 2, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "second", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS - 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 2, null, null, AlfioMetadata.empty())
        );
        var eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, List.of(), Event.EventFormat.ONLINE);
        var event = eventAndUsername.getLeft();
        var username = eventAndUsername.getRight();

        // This must give an error because the "check_location_if_in_person" constraint will kick in
        assertThrows(DataIntegrityViolationException.class, () -> eventManager.updateEventHeader(event, createEventModification(AVAILABLE_SEATS, event, Event.EventFormat.HYBRID, event.getAllowedPaymentProxies(), null), username));
    }

    @Test
    void updateOnlineEventToHybridAndBack() {
        var categories = List.of(
            new TicketCategoryModification(null, "first", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS - 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 2, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "second", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS - 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 2, null, null, AlfioMetadata.empty())
        );

        var eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, List.of(), Event.EventFormat.ONLINE);
        var event = eventAndUsername.getLeft();
        var username = eventAndUsername.getRight();

        // if we set the location, everything should work as expected
        eventManager.updateEventHeader(event, createEventModification(AVAILABLE_SEATS, event, Event.EventFormat.HYBRID, event.getAllowedPaymentProxies(), "location"), username);

        // check that all the categories have been converted to ONLINE
        assertTrue(ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().allMatch(tc -> tc.getTicketAccessType() == TicketCategory.TicketAccessType.ONLINE));

        event = eventRepository.findById(event.getId());

        // revert modification
        eventManager.updateEventHeader(event, createEventModification(AVAILABLE_SEATS, event, Event.EventFormat.ONLINE, event.getAllowedPaymentProxies(), null), username);
        // we expect all the access types to be INHERIT
        assertTrue(ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().allMatch(tc -> tc.getTicketAccessType() == TicketCategory.TicketAccessType.INHERIT));
        BaseIntegrationTest.testTransferEventToAnotherOrg(event.getId(), event.getOrganizationId(), username, jdbcTemplate);
    }


    private Pair<Event, String> generateAndEditEvent(int newEventSize) {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        Event event = pair.getKey();
        if(newEventSize != AVAILABLE_SEATS) {
            EventModification update = createEventModification(newEventSize, event);
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

    private EventModification createEventModification(int availableSeats, Event event) {
        return createEventModification(availableSeats, event, Event.EventFormat.IN_PERSON, event.getAllowedPaymentProxies(), event.getLocation());
    }

    private EventModification createEventModification(int availableSeats, Event event, Event.EventFormat format, List<PaymentProxy> allowedPaymentProxies, String location) {
        return new EventModification(event.getId(), format, "http://website-url", null, "http://website-url/tc", null, null, null, null, null, event.getOrganizationId(), location, null,
            null, event.getZoneId().toString(), Collections.emptyMap(), DateTimeModification.fromZonedDateTime(event.getBegin()), DateTimeModification.fromZonedDateTime(event.getEnd()),
            event.getRegularPrice(), event.getCurrency(), availableSeats, event.getVat(), event.isVatIncluded(), allowedPaymentProxies, null, event.isFreeOfCharge(), null, 7, null, null, AlfioMetadata.empty(), List.of());
    }
}
