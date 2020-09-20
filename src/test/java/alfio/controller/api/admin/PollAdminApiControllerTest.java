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
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.PollModification;
import alfio.model.modification.PollOptionModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.poll.Poll;
import alfio.repository.EventDeleterRepository;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static alfio.test.util.IntegrationTestUtil.*;
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

    private Event event;
    private Long pollId;
    private Ticket ticket;
    private Long firstOptionId;
    private Long secondOptionId;

    @BeforeEach
    void init() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
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

    @AfterEach
    void deleteAll() {
        eventDeleterRepository.deleteAllForEvent(event.getId());
    }
}