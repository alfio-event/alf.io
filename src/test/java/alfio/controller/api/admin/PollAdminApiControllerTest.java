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
package alfio.controller.api.admin;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.PollModification;
import alfio.model.modification.PollOptionModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.poll.Poll;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
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
import java.time.ZonedDateTime;
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.*;
import static alfio.test.util.TestUtil.clockProvider;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
class PollAdminApiControllerTest {

    @Autowired
    private PollAdminApiController controller;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventDeleterRepository eventDeleterRepository;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private PollRepository pollRepository;
    @Autowired
    private AuditingRepository auditingRepository;

    private Event event;

    @BeforeEach
    void init() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider().getClock()).minusDays(1), LocalTime.now(clockProvider().getClock())),
                new DateTimeModification(LocalDate.now(clockProvider().getClock()).plusDays(1), LocalTime.now(clockProvider().getClock())),
                DESCRIPTION, BigDecimal.ZERO, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        event = eventAndUser.getKey();
    }

    @Test
    void createAndUpdatePoll() {
        // create poll

        var options = List.of(new PollOptionModification(null, Map.of("en", "Homer J. Simpson"), null), new PollOptionModification(null, Map.of("en", "Bender B. Rodriguez"), Map.of()));
        var form = new PollModification(null, Map.of("en", "Best Employee of the Year"), null, null, options, false, Poll.PollStatus.OPEN); // this must not have an impact
        var eventName = event.getShortName();
        var createResponse = controller.createNewPoll(eventName, form);
        assertTrue(createResponse.getStatusCode().is2xxSuccessful());
        assertNotNull(createResponse.getBody());
        var pollId = createResponse.getBody();

        // retrieve poll and check
        var getResponse = controller.getPollDetail(eventName, pollId);
        assertTrue(getResponse.getStatusCode().is2xxSuccessful());
        assertNotNull(getResponse.getBody());
        var poll = getResponse.getBody();
        assertEquals(2, poll.getOptions().size());
        assertEquals("Homer J. Simpson", poll.getOptions().get(0).getTitle().get("en"));
        assertEquals(Poll.PollStatus.DRAFT, poll.getStatus());

        // update poll status
        var updateStatusResponse = controller.updateStatus(eventName, pollId, new PollAdminApiController.UpdatePollStatusForm(Poll.PollStatus.OPEN));
        assertTrue(updateStatusResponse.getStatusCode().is2xxSuccessful());
        assertNotNull(updateStatusResponse.getBody());
        assertEquals(Poll.PollStatus.OPEN, updateStatusResponse.getBody().getStatus());

        // update poll
        var newOptionsList = new ArrayList<>(poll.getOptions());
        newOptionsList.addAll(List.of(new PollOptionModification(null, Map.of("en", "Lisa M. Simpson"), null), new PollOptionModification(null, Map.of("en", "Turanga Leela"), Map.of())));
        var updateForm = new PollModification(poll.getId(), poll.getTitle(), poll.getDescription(), poll.getOrder(), newOptionsList, false, Poll.PollStatus.DRAFT);
        var updatePollResponse = controller.updatePoll(eventName, pollId, updateForm);
        assertTrue(updatePollResponse.getStatusCode().is2xxSuccessful());
        assertNotNull(updatePollResponse.getBody());
        assertEquals(Poll.PollStatus.OPEN, updatePollResponse.getBody().getStatus());
        assertEquals(4, updatePollResponse.getBody().getOptions().size());
        assertEquals("Homer J. Simpson", updatePollResponse.getBody().getOptions().get(0).getTitle().get("en"));
    }

    @Test
    void allowPeopleToVote() {
        var options = List.of(new PollOptionModification(null, Map.of("en", "Homer J. Simpson"), null), new PollOptionModification(null, Map.of("en", "Bender B. Rodriguez"), Map.of()));
        var form = new PollModification(null, Map.of("en", "Best Employee of the Year"), null, null, options, true, Poll.PollStatus.OPEN); // this must not have an impact
        var eventName = event.getShortName();
        var createResponse = controller.createNewPoll(eventName, form);
        assertTrue(createResponse.getStatusCode().is2xxSuccessful());
        assertNotNull(createResponse.getBody());
        var pollId = createResponse.getBody();

        var reservationId = UUID.randomUUID().toString();
        ticketReservationRepository.createNewReservation(reservationId, ZonedDateTime.now(event.getZoneId()), DateUtils.addMinutes(new Date(), 1), null, "en", event.getId(), null, null, null, event.getOrganizationId(), null);
        var firstCategory = CollectionUtils.get(ticketCategoryRepository.findByEventIdAsMap(event.getId()), 0);
        int categoryId = firstCategory.getKey();
        var tickets = ticketRepository.findFreeByEventId(event.getId());
        var firstTicket = tickets.get(0);
        int ticketId = firstTicket.getId();
        ticketRepository.reserveTickets(reservationId, List.of(ticketId), firstCategory.getValue(), "en", event.getVatStatus(), i -> null);
        ticketReservationRepository.updateReservationStatus(reservationId, TicketReservation.TicketReservationStatus.COMPLETE.name());
        ticketRepository.updateTicketOwner(firstTicket.getUuid(), "test@test.ch", "First Last", "First", "Last");
        ticketRepository.updateTicketsStatusWithReservationId(reservationId, Ticket.TicketStatus.ACQUIRED.name());

        // find compatible tickets
        var res = controller.findAdditionalAttendees(event.getShortName(), pollId, "First");
        assertTrue(res.getStatusCode().is2xxSuccessful());
        assertTrue(CollectionUtils.isNotEmpty(res.getBody()));
        assertEquals(1, res.getBody().size());
        assertEquals(firstTicket.getId(), res.getBody().get(0).getId());

        // allow tickets to vote
        var poll = pollRepository.findSingleForEvent(event.getId(), pollId).orElseThrow();
        assertNotNull(poll);
        var participantForm = new PollAdminApiController.UpdateParticipantsForm(List.of(ticketId));
        var allowRes = controller.allowAttendees(event.getShortName(), pollId, participantForm);
        assertTrue(allowRes.getStatusCode().is2xxSuccessful());
        assertEquals(1, auditingRepository.countAuditsOfTypeForReservation(reservationId, Audit.EventType.TAG_TICKET));
        assertEquals(0, auditingRepository.countAuditsOfTypeForReservation(reservationId, Audit.EventType.UNTAG_TICKET));

        var participantRes = controller.getAllowedAttendees(eventName, pollId);
        assertTrue(participantRes.getStatusCode().is2xxSuccessful());
        assertTrue(CollectionUtils.isNotEmpty(participantRes.getBody()));
        assertEquals(1, participantRes.getBody().size());
        assertEquals(firstTicket.getId(), participantRes.getBody().get(0).getId());

        // now ticket should not be returned anymore
        res = controller.findAdditionalAttendees(event.getShortName(), pollId, "First");
        assertTrue(res.getStatusCode().is2xxSuccessful());
        assertTrue(CollectionUtils.isEmpty(res.getBody()));

        // get statistics
        var statsRes = controller.getStatisticsForEvent(event.getShortName(), pollId);
        assertTrue(statsRes.getStatusCode().is2xxSuccessful());
        assertNotNull(statsRes.getBody());
        assertTrue(CollectionUtils.isEmpty(statsRes.getBody().getOptionStatistics()));
        assertEquals("0", statsRes.getBody().getParticipationPercentage());

        // forbid access to attendee
        var forbidRes = controller.forbidAttendees(event.getShortName(), pollId, participantForm);
        assertTrue(forbidRes.getStatusCode().is2xxSuccessful());
        assertTrue(CollectionUtils.isEmpty(forbidRes.getBody()));
        assertEquals(1, auditingRepository.countAuditsOfTypeForReservation(reservationId, Audit.EventType.TAG_TICKET));
        assertEquals(1, auditingRepository.countAuditsOfTypeForReservation(reservationId, Audit.EventType.UNTAG_TICKET));

        // remove option
        var pollWithOptions = controller.getPollDetail(event.getShortName(), pollId).getBody();
        assertNotNull(pollWithOptions);
        var firstOptionId = pollWithOptions.getOptions().get(0).getId();
        var removeOptionResponse = controller.removeOption(event.getShortName(), pollId, firstOptionId);
        assertTrue(removeOptionResponse.getStatusCode().is2xxSuccessful());
        assertTrue(Objects.requireNonNull(removeOptionResponse.getBody()).getOptions().stream().noneMatch(po -> firstOptionId.equals(po.getId())));

        // delete poll
        var deletePollResponse = controller.deletePoll(eventName, pollId);
        assertTrue(deletePollResponse.getStatusCode().is2xxSuccessful());
        assertNotNull(deletePollResponse.getBody());
        assertTrue(deletePollResponse.getBody());

    }

    @AfterEach
    void deleteAll() {
        eventDeleterRepository.deleteAllForEvent(event.getId());
    }
}