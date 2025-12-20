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
package alfio.controller.api.v2.user.reservation;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.support.extension.ExtensionEvent;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.ExtensionLog;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.metadata.TicketMetadataContainer;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.util.Json;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static alfio.manager.support.extension.ExtensionEvent.TICKET_ASSIGNED_GENERATE_METADATA;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class ReservationFlowTicketMetadataIntegrationTest extends BaseReservationFlowTest {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;

    private ReservationFlowContext createContext() {
        try {
            insertExtension(extensionService, "/ticket-custom-metadata-extension.js", false, true, "Metadata", Stream.of(TICKET_ASSIGNED_GENERATE_METADATA.name()));
            List<TicketCategoryModification> categories = Arrays.asList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                    DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
                new TicketCategoryModification(null, "hidden", TicketCategory.TicketAccessType.INHERIT, 2,
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                    DESCRIPTION, BigDecimal.ONE, true, "", true, URL_CODE_HIDDEN, null, null, null, null, 0, null, null, AlfioMetadata.empty())
            );
            Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
            return new ReservationFlowContext(eventAndUser.getLeft(), owner(eventAndUser.getRight()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void inPersonEvent() throws Exception {
        super.testBasicFlow(this::createContext);
    }

    @Override
    protected void performAdditionalTests(ReservationFlowContext context) {
        var event = context.event;
        var metadataJson = jdbcTemplate.queryForObject("select metadata from ticket where event_id = :eventId and metadata <> '{}' limit 1", new MapSqlParameterSource("eventId", event.getId()), String.class);
        assertNotNull(metadataJson);
        var metadataObj = Json.fromJson(metadataJson, TicketMetadataContainer.class);
        assertTrue(metadataObj.getMetadataForKey(TicketMetadataContainer.GENERAL).isPresent());
        var attributes = metadataObj.getMetadataForKey(TicketMetadataContainer.GENERAL).get().getAttributes();
        assertEquals("fixedValue", attributes.get("metadataAttribute"));
        assertNotNull(attributes.get("pdfLink"));
        assertTrue(attributes.get("pdfLink").endsWith("/download-ticket"));
    }

    @Override
    protected void assertEventLogged(List<ExtensionLog> extLog, ExtensionEvent event, int logSize) {
        // we are not interested in testing log
    }
}
