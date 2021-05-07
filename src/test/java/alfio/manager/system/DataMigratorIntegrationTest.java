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
package alfio.manager.system;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.EventManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.*;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.EventMigration;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.system.EventMigrationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
public class DataMigratorIntegrationTest extends BaseIntegrationTest {

    private static final int AVAILABLE_SEATS = 20;
    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");

    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private DataMigrator dataMigrator;
    @Autowired
    private EventMigrationRepository eventMigrationRepository;
    @Autowired
    private TicketReservationManager ticketReservationManager;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Value("${alfio.version}")
    private String currentVersion;

    private Pair<Event, String> initEvent(List<TicketCategoryModification> categories) {
        return initEvent(categories, "display name");
    }

    private Pair<Event,String> initEvent(List<TicketCategoryModification> categories, String displayName) {
        String organizationName = UUID.randomUUID().toString();
        String username = UUID.randomUUID().toString();
        String eventName = UUID.randomUUID().toString();

        organizationRepository.create(organizationName, "org", "email@example.com", null, null);
        Organization organization = organizationRepository.findByName(organizationName).get();
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com", Role.OPERATOR, User.Type.INTERNAL);

        Map<String, String> desc = new HashMap<>();
        desc.put("en", "muh description");
        desc.put("it", "muh description");
        desc.put("de", "muh description");

        EventModification em = new EventModification(null, Event.EventFormat.IN_PERSON, "url", "url", "url", "privacy", null, null,
                eventName, displayName, organization.getId(),
                "muh location",
                "0.0", "0.0", ZoneId.systemDefault().getId(), desc,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(5), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(5), LocalTime.now(ClockProvider.clock()).plusHours(1)),
                BigDecimal.TEN, "CHF", AVAILABLE_SEATS, BigDecimal.ONE, true, List.of(PaymentProxy.ON_SITE), categories, false, new LocationDescriptor("","","",""), 7, null, null, AlfioMetadata.empty(), List.of());
        eventManager.createEvent(em, username);
        return Pair.of(eventManager.getSingleEvent(eventName, username), username);
    }

    @Test
    public void testMigration() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventUsername = initEvent(categories);
        Event event = eventUsername.getKey();

        try {
	        eventRepository.updatePrices("CHF", 40, false, BigDecimal.ONE, "STRIPE", event.getId(), PriceContainer.VatStatus.NOT_INCLUDED, 1000);

	        dataMigrator.migrateEventsToCurrentVersion();
	        EventMigration eventMigration = eventMigrationRepository.loadEventMigration(event.getId());
	        assertNotNull(eventMigration);
	        //assertEquals(buildTimestamp, eventMigration.getBuildTimestamp().toString());
	        assertEquals(currentVersion, eventMigration.getCurrentVersion());

	        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
	        assertNotNull(tickets);
	        assertFalse(tickets.isEmpty());
	        assertEquals(AVAILABLE_SEATS, tickets.size());
	        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));
        } finally {
        	eventManager.deleteEvent(event.getId(), eventUsername.getValue());
        }
    }

    @Test
    public void testMigrationWithExistingRecord() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventUsername = initEvent(categories); 
        Event event = eventUsername.getKey();

        try {
	        eventMigrationRepository.insertMigrationData(event.getId(), "1.4", ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1), EventMigration.Status.COMPLETE.toString());
	        eventRepository.updatePrices("CHF", 40, false, BigDecimal.ONE, "STRIPE", event.getId(), PriceContainer.VatStatus.NOT_INCLUDED, 1000);
	        dataMigrator.migrateEventsToCurrentVersion();
	        EventMigration eventMigration = eventMigrationRepository.loadEventMigration(event.getId());
	        assertNotNull(eventMigration);
	        //assertEquals(buildTimestamp, eventMigration.getBuildTimestamp().toString());
	        assertEquals(currentVersion, eventMigration.getCurrentVersion());
	
	        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
	        assertNotNull(tickets);
	        assertFalse(tickets.isEmpty());
	        assertEquals(20, tickets.size());
	        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));
        } finally {
        	eventManager.deleteEvent(event.getId(), eventUsername.getValue());
        }
    }

    @Test
    public void testAlreadyMigratedEvent() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventUsername = initEvent(categories); 
        Event event = eventUsername.getKey();
        
        try {
	        ZonedDateTime migrationTs = ZonedDateTime.now(ZoneId.of("UTC"));
	        eventMigrationRepository.insertMigrationData(event.getId(), currentVersion, migrationTs, EventMigration.Status.COMPLETE.toString());
	        eventRepository.updatePrices("CHF", 40, false, BigDecimal.ONE, "STRIPE", event.getId(), PriceContainer.VatStatus.NOT_INCLUDED, 1000);
	        dataMigrator.migrateEventsToCurrentVersion();
	        EventMigration eventMigration = eventMigrationRepository.loadEventMigration(event.getId());
	        assertNotNull(eventMigration);
	        //assertEquals(migrationTs.toString(), eventMigration.getBuildTimestamp().toString());
	        assertEquals(currentVersion, eventMigration.getCurrentVersion());
	
	        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
	        assertNotNull(tickets);
	        assertFalse(tickets.isEmpty());
	        assertEquals(AVAILABLE_SEATS, tickets.size());//<-- the migration has not been done
	        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));
        } finally {
        	eventManager.deleteEvent(event.getId(), eventUsername.getValue());
        }
    }

    @Test
    public void testUpdateDisplayName() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventUsername = initEvent(categories, null); 
        Event event = eventUsername.getKey();

        try {
	        dataMigrator.migrateEventsToCurrentVersion();
	        EventMigration eventMigration = eventMigrationRepository.loadEventMigration(event.getId());
	        assertNotNull(eventMigration);
	        //assertEquals(buildTimestamp, eventMigration.getBuildTimestamp().toString());
	        assertEquals(currentVersion, eventMigration.getCurrentVersion());
	
	        Event withDescription = eventRepository.findById(event.getId());
	        assertNotNull(withDescription.getDisplayName());
	        assertEquals(event.getShortName(), withDescription.getShortName());
	        assertEquals(event.getShortName(), withDescription.getDisplayName());
        } finally {
        	eventManager.deleteEvent(event.getId(), eventUsername.getValue());
        }
    }

    @Test
    public void testUpdateTicketReservation() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventUsername = initEvent(categories); 
        Event event = eventUsername.getKey();
        try {
	        TicketReservationModification trm = new TicketReservationModification();
	        trm.setAmount(1);
	        trm.setTicketCategoryId(eventManager.loadTicketCategories(event).get(0).getId());
	        TicketReservationWithOptionalCodeModification r = new TicketReservationWithOptionalCodeModification(trm, Optional.empty());
	        Date expiration = DateUtils.addDays(new Date(), 1);
	        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(r), Collections.emptyList(), expiration, Optional.empty(), Locale.ENGLISH, false, null);
	        dataMigrator.fillReservationsLanguage();
	        TicketReservation ticketReservation = ticketReservationManager.findById(reservationId).get();
	        assertEquals("en", ticketReservation.getUserLanguage());
        } finally {
        	eventManager.deleteEvent(event.getId(), eventUsername.getValue());
        }
    }

    @Test
    public void testFixCategoriesSize() {
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS -1,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 1,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventUsername = initEvent(categories);
        Event event = eventUsername.getKey();
        TicketCategory firstCategory = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(TicketCategory::isBounded).findFirst().orElseThrow(IllegalStateException::new);
        int firstCategoryID = firstCategory.getId();
        ticketCategoryRepository.updateSeatsAvailability(firstCategoryID, AVAILABLE_SEATS + 1);
        dataMigrator.fixCategoriesSize(event);
        assertEquals(AVAILABLE_SEATS - 1, ticketRepository.countAllocatedTicketsForEvent(event.getId()).intValue());
        assertEquals(1, ticketRepository.countFreeTicketsForUnbounded(event.getId()).intValue());
        assertEquals(AVAILABLE_SEATS - 1, ticketRepository.countFreeTickets(event.getId(), firstCategoryID).intValue());
        assertEquals(AVAILABLE_SEATS - 1, firstCategory.getMaxTickets());
    }

    @Test
    public void testFixStuckTickets() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventUsername = initEvent(categories);
        Event event = eventUsername.getKey();
        TicketReservationModification trm = new TicketReservationModification();
        trm.setAmount(1);
        trm.setTicketCategoryId(eventManager.loadTicketCategories(event).get(0).getId());
        TicketReservationWithOptionalCodeModification r = new TicketReservationWithOptionalCodeModification(trm, Optional.empty());
        Date expiration = DateUtils.addDays(new Date(), 1);
        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(r), Collections.emptyList(), expiration, Optional.empty(), Locale.ENGLISH, false, null);
        //simulate the effect of a reservation cancellation after #392, as described in #391
        ticketReservationRepository.updateReservationStatus(reservationId, TicketReservation.TicketReservationStatus.CANCELLED.name());
        List<Ticket> ticketsInReservation = ticketRepository.findTicketsInReservation(reservationId);
        assertEquals(1, ticketsInReservation.size());
        String uuid = ticketsInReservation.get(0).getUuid();
        assertTrue(ticketsInReservation.stream().allMatch(t -> t.getStatus() == Ticket.TicketStatus.PENDING));
        dataMigrator.fixStuckTickets(event.getId());
        assertSame(Ticket.TicketStatus.RELEASED, ticketRepository.findByUUID(uuid).getStatus());
    }
}
