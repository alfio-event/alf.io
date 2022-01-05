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
package alfio.controller.api.v2.user;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.controller.form.PollVoteForm;
import alfio.manager.EventManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.poll.Poll;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import alfio.util.PinGenerator;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
class PollApiControllerIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollApiControllerIntegrationTest.class);

    @Autowired
    private PollApiController pollApiController;
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
    private PollRepository pollRepository;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketReservationManager ticketReservationManager;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private EventDeleterRepository eventDeleterRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private Event event;
    private Long pollId;
    private Ticket ticket;
    private Long firstOptionId;
    private Long secondOptionId;
    private String username;


    @BeforeEach
    void init() {
        LOGGER.info("init");
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.ZERO, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        username = eventAndUser.getRight();
        event = eventAndUser.getKey();
        var rowCountAndKey = pollRepository.insert(Map.of("en", "test poll"), null, List.of(), 0, event.getId(), event.getOrganizationId());
        pollId = rowCountAndKey.getKey();
        LOGGER.info("pollId {}", pollId);
        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(1);
        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        tr.setTicketCategoryId(category.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        var reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        ticketRepository.updateTicketsStatusWithReservationId(reservationId, Ticket.TicketStatus.ACQUIRED.name());
        pollRepository.updateStatus(Poll.PollStatus.DRAFT, pollId, event.getId());
        firstOptionId = pollRepository.insertOption(pollId, Map.of("en", "first"), null, event.getOrganizationId()).getKey();
        secondOptionId = pollRepository.insertOption(pollId, Map.of("en", "second"), null, event.getOrganizationId()).getKey();
        ticket = ticketRepository.findFirstTicketInReservation(reservationId).orElseThrow();
    }

    @AfterEach
    void deleteAll() {
        BaseIntegrationTest.testTransferEventToAnotherOrg(event.getId(), event.getOrganizationId(), username, jdbcTemplate);
        eventDeleterRepository.deleteAllForEvent(event.getId());
    }

    @Test
    void getAllFailedTicketNotCheckedInAndPollDraft() {
        var response = pollApiController.getAll(event.getShortName(), PinGenerator.uuidToPin(ticket.getUuid()));
        assertFalse(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertNull(response.getBody().getValue());
        assertTrue(response.getBody().getErrorCount() > 0);
    }

    @Test
    void getAllFailedTicketNotCheckedIn() {
        pollRepository.updateStatus(Poll.PollStatus.OPEN, pollId, event.getId());
        var response = pollApiController.getAll(event.getShortName(), PinGenerator.uuidToPin(ticket.getUuid()));
        assertFalse(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertNull(response.getBody().getValue());
        assertTrue(response.getBody().getErrorCount() > 0);
    }

    @Test
    void getAllPollClosedEmptyList() {
        updateVisibility(Poll.PollStatus.CLOSED);
        var response = pollApiController.getAll(event.getShortName(), PinGenerator.uuidToPin(ticket.getUuid()));
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess()); // pin has been successfully validated, therefore we get an empty list
        assertEquals(0, response.getBody().getErrorCount());
        assertNotNull(response.getBody().getValue());
        assertTrue(response.getBody().getValue().isEmpty());
    }

    @Test
    void getAll() {

        // update status of the poll
        updateVisibility(Poll.PollStatus.OPEN);
        LOGGER.info("updated visibility {}", pollRepository.findAllForEvent(event.getId()));
        LOGGER.info("active {}", pollRepository.findActiveForEvent(event.getId()));

        var response = pollApiController.getAll(event.getShortName(), PinGenerator.uuidToPin(ticket.getUuid()));
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getValue());
        assertEquals(1, response.getBody().getValue().size());
    }

    @Test
    void getSingle() {
        updateVisibility(Poll.PollStatus.OPEN);

        var response = pollApiController.getSingle(event.getShortName(), pollId, PinGenerator.uuidToPin(ticket.getUuid()));
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getValue());
        var pollWithOptions = response.getBody().getValue();
        assertEquals(2, pollWithOptions.getOptions().size());
        assertEquals("first", pollWithOptions.getOptions().get(0).getTitle().get("en"));
        assertEquals("second", pollWithOptions.getOptions().get(1).getTitle().get("en"));
    }

    @Test
    void registerAnswerFailedTicketNotCheckedIn() {
        updateVisibility(Poll.PollStatus.OPEN, Ticket.TicketStatus.ACQUIRED);
        var form = new PollVoteForm();
        form.setPin(PinGenerator.uuidToPin(ticket.getUuid()));
        form.setOptionId(firstOptionId);
        var response = pollApiController.registerAnswer(event.getShortName(), pollId, form);
        assertFalse(response.getStatusCode().is2xxSuccessful());
        var statistics = pollRepository.getStatisticsFor(pollId, event.getId());
        assertTrue(statistics.isEmpty());
    }

    @Test
    void registerAnswer() {
        updateVisibility(Poll.PollStatus.OPEN);
        var form = new PollVoteForm();
        form.setPin(PinGenerator.uuidToPin(ticket.getUuid()));
        form.setOptionId(firstOptionId);
        var response = pollApiController.registerAnswer(event.getShortName(), pollId, form);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        var statistics = pollRepository.getStatisticsFor(pollId, event.getId());
        assertEquals(1, statistics.size());
        assertEquals(firstOptionId, statistics.get(0).getOptionId());
        assertEquals(1, statistics.get(0).getVotes());

        // update vote
        form.setOptionId(secondOptionId);
        response = pollApiController.registerAnswer(event.getShortName(), pollId, form);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        statistics = pollRepository.getStatisticsFor(pollId, event.getId());
        assertEquals(1, statistics.size());
        assertEquals(secondOptionId, statistics.get(0).getOptionId());
        assertEquals(1, statistics.get(0).getVotes());
    }

    private void updateVisibility(Poll.PollStatus status) {
        updateVisibility(status, Ticket.TicketStatus.CHECKED_IN);
    }

    private void updateVisibility(Poll.PollStatus status, Ticket.TicketStatus ticketStatus) {
        assertEquals(1, pollRepository.updateStatus(status, pollId, event.getId()));
        assertEquals(1, ticketRepository.updateTicketStatusWithUUID(ticket.getUuid(), ticketStatus.name()));
    }


}