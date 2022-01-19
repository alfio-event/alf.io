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
package alfio.repository;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.config.WebSecurityConfig;
import alfio.manager.EventManager;
import alfio.manager.EventStatisticsManager;
import alfio.manager.support.CheckInStatistics;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.result.Result;
import alfio.repository.user.OrganizationRepository;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static alfio.test.util.IntegrationTestUtil.DESCRIPTION;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, WebSecurityConfig.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class EventRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final String NEW_YORK_TZ = "America/New_York";
    private static final String ORG_NAME = "name";

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventStatisticsManager eventStatisticsManager;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;

    @BeforeEach
    public void setUp() {
        //setup hsqldb and make it usable from eventRepository
        organizationRepository.create(ORG_NAME, "description", "email@pippobaudo.com", null, null);
    }


    @Test
    public void testJavaInsertedDatesRespectTheirTimeZone() {
        //these are the values of what we have inserted in the SQL insert script
        ZonedDateTime beginEventDate = ZonedDateTime.of(2015, 4, 18, 0, 0, 0, 0, ZoneId.of("America/New_York"));
        ZonedDateTime endEventDate = ZonedDateTime.of(2015, 4, 19, 23, 59, 59, 0, ZoneId.of("America/New_York"));

        int orgId = organizationRepository.getIdByName(ORG_NAME);


        AffectedRowCountAndKey<Integer> pair = eventRepository.insert("unittest", Event.EventFormat.IN_PERSON, "display Name", "http://localhost:8080/", "http://localhost:8080",
            "http://localhost:8080", null,null, null, "Lugano", "9", "8", beginEventDate, endEventDate, NEW_YORK_TZ, "CHF", 4, true,
            new BigDecimal(1), "", "", orgId, 7, PriceContainer.VatStatus.INCLUDED, 0, null, Event.Status.PUBLIC, AlfioMetadata.empty());
        Event e = eventRepository.findById(pair.getKey());
        assertNotNull(e, "Event not found in DB");

        assertEquals(beginEventDate, e.getBegin(), "Begin date is not correct");
        assertEquals(endEventDate, e.getEnd(), "End date is not correct");

        //since when debugging the toString method is used .... and it rely on the system TimeZone, we test it too
        System.out.println(e.getBegin().toString());
        System.out.println(e.getEnd().toString());
    }

    @Test
    public void testCheckInStatistics() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 0,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
            new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
            new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
            DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        Result<Integer> result = eventManager.insertCategory(event, tcm, pair.getValue());
        assertTrue(result.isSuccess());

        //initial state
        CheckInStatistics checkInStatistics = eventRepository.retrieveCheckInStatisticsForEvent(event.getId());
        assertEquals(0, checkInStatistics.getCheckedIn());
        assertEquals(0, checkInStatistics.getTotalAttendees());

        EventWithAdditionalInfo eventWithAdditionalInfo = eventStatisticsManager.getEventWithAdditionalInfo(event.getShortName(), pair.getRight());
        TicketCategoryWithAdditionalInfo firstCategory = eventWithAdditionalInfo.getTicketCategories().get(0);
        List<Integer> ids = ticketRepository.selectNotAllocatedTicketsForUpdate(event.getId(), 5, Collections.singletonList(TicketRepository.FREE));
        String reservationId = "12345678";
        ticketReservationRepository.createNewReservation(reservationId, ZonedDateTime.now(ClockProvider.clock()), DateUtils.addDays(new Date(), 1), null, "en", event.getId(), event.getVat(), event.isVatIncluded(), event.getCurrency(), event.getOrganizationId(), null);
        int reserved = ticketRepository.reserveTickets(reservationId, ids, firstCategory.getTicketCategory(), "it", event.getVatStatus(), i -> null);
        assertEquals(5, reserved);

        ticketRepository.updateTicketsStatusWithReservationId(reservationId, Ticket.TicketStatus.ACQUIRED.name());
        checkInStatistics = eventRepository.retrieveCheckInStatisticsForEvent(event.getId());
        //after buying 5 tickets we expect to have them in the total attendees
        assertEquals(0, checkInStatistics.getCheckedIn());
        assertEquals(5, checkInStatistics.getTotalAttendees());


        List<Ticket> ticketsInReservation = ticketRepository.findTicketsInReservation(reservationId);
        ticketRepository.updateTicketStatusWithUUID(ticketsInReservation.get(0).getUuid(), Ticket.TicketStatus.CHECKED_IN.name());
        checkInStatistics = eventRepository.retrieveCheckInStatisticsForEvent(event.getId());
        //checked in ticket must be taken into account
        assertEquals(1, checkInStatistics.getCheckedIn());
        assertEquals(5, checkInStatistics.getTotalAttendees());
    }
}