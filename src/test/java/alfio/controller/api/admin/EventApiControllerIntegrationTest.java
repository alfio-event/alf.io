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
import alfio.manager.AdminReservationRequestManager;
import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.AdminReservationModification;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.repository.EventDeleterRepository;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static alfio.test.util.IntegrationTestUtil.*;
import static alfio.test.util.TestUtil.clockProvider;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class EventApiControllerIntegrationTest {

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
    private EventApiController eventApiController;
    @Autowired
    private AttendeeBulkImportApiController attendeeBulkImportApiController;
    @Autowired
    private AdminReservationRequestManager adminReservationRequestManager;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketRepository ticketRepository;

    private Event event;

    @Test
    void getAllEventsForExternalInPerson() {
        var eventAndUser = createEvent(Event.EventFormat.IN_PERSON);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Authentication.class);
        when(principal.getName()).thenReturn(eventAndUser.getValue());
        var events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), false);

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(event.getShortName(), events.get(0).getKey());
    }

    @Test
    void getAllEventsForExternalHybrid() {
        var eventAndUser = createEvent(Event.EventFormat.HYBRID);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Authentication.class);
        when(principal.getName()).thenReturn(eventAndUser.getValue());
        var events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), false);

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(event.getShortName(), events.get(0).getKey());
    }

    @Test
    void getAllEventsForExternalOnline() {
        var eventAndUser = createEvent(Event.EventFormat.ONLINE);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Authentication.class);
        when(principal.getName()).thenReturn(eventAndUser.getValue());
        var events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), false);

        assertNotNull(events);
        assertEquals(0, events.size());

        events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), true);
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(event.getShortName(), events.get(0).getKey());
    }

    @Test
    void testGivenListOfAttendeesWithFieldsUploadThenSameFieldsAvailableOnDownload() throws IOException {
        // GIVEN - creation of event and registration of attendees
        var eventAndUser = createEvent(Event.EventFormat.HYBRID);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Authentication.class);
        when(principal.getName()).thenReturn(owner(eventAndUser.getValue()));
        var modification = getTestAdminReservationModification();
        var result = this.attendeeBulkImportApiController.createReservations(eventAndUser.getKey().getShortName(), modification, false, principal);
        // GIVEN - invocation of async processing job
        var requestStatus = this.attendeeBulkImportApiController.getRequestsStatus(eventAndUser.getKey().getShortName(), result.getData(), principal);
        assertEquals(1, requestStatus.getData().getCountPending());
        // WHEN - processing of pending reservations completes
        this.adminReservationRequestManager.processPendingReservations();
        // THEN - assert correctness of data persisted
        var tickets = this.ticketRepository.findAllConfirmedForCSV(event.getId());
        assertEquals(1, tickets.size());
        var foundTicket = tickets.get(0).getTicket();
        assertEquals("123", foundTicket.getExtReference());
        assertEquals("Attendee 0", foundTicket.getFirstName());
        assertEquals("Test0", foundTicket.getLastName());
        assertEquals("attendee0@test.ch", foundTicket.getEmail());
        assertEquals("en", foundTicket.getUserLanguage());
        // THEN - assert correct order of CSV fields upon download
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addParameter("fields", EventApiController.FIXED_FIELDS.toArray(new String[0]));
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        this.eventApiController.downloadAllTicketsCSV(event.getShortName(), "csv", mockRequest, mockResponse, principal);
        String expected = "\""+foundTicket.getUuid()+"\""+",default,"+"\""+event.getShortName()+"\""+",ACQUIRED,0,0,0,0,"+"\""+foundTicket.getTicketsReservationId()+"\""+",\"Attendee 0 Test0\",\"Attendee 0\",Test0,attendee0@test.ch,false,en";
        assertTrue(String.join(",",mockResponse.getContentAsString().trim()).contains(expected));
        assertTrue(String.join(",",mockResponse.getContentAsString().trim()).endsWith("\"Billing Address\",,,,123"));
    }

    private AdminReservationModification getTestAdminReservationModification() {
        DateTimeModification expiration = DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        AdminReservationModification.CustomerData customerData = new AdminReservationModification.CustomerData("Integration", "Test", "integration-test@test.ch", "Billing Address", "reference", "en", "1234", "CH", null);
        var ticketCategoryList = this.ticketCategoryRepository.findAllTicketCategories(event.getId());
        AdminReservationModification.Category category = new AdminReservationModification.Category(ticketCategoryList.get(0).getId(), "name", new BigDecimal("100.00"), null);
        List<AdminReservationModification.TicketsInfo> ticketsInfoList = Collections.singletonList(new AdminReservationModification.TicketsInfo(category, generateAttendees(1), true, false));
        return new AdminReservationModification(expiration, customerData, ticketsInfoList, "en", false, false, null, null, null, null);
    }

    private Pair<Event,String> createEvent(Event.EventFormat format) {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider().getClock()).minusDays(1), LocalTime.now(clockProvider().getClock())),
                new DateTimeModification(LocalDate.now(clockProvider().getClock()).plusDays(1), LocalTime.now(clockProvider().getClock())),
                DESCRIPTION, BigDecimal.ZERO, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        return initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, List.of(), format);

    }

    private List<AdminReservationModification.Attendee> generateAttendees(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new AdminReservationModification.Attendee(null, "Attendee "+i, "Test" + i, "attendee"+i+"@test.ch", "en",false, "123", null, Collections.emptyMap(), null))
            .collect(toList());
    }

    @AfterEach
    void tearDown() {
        eventDeleterRepository.deleteAllForEvent(event.getId());
    }
}