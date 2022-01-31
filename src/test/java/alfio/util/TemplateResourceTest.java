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
package alfio.util;

import alfio.model.*;
import alfio.model.user.Organization;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static alfio.test.util.TestUtil.clockProvider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplateResourceTest {

    private Organization organization;
    private Event event;
    private TicketReservation ticketReservation;
    private TicketCategory ticketCategory;
    private Ticket ticket;
    private TicketWithMetadataAttributes ticketWithMetadata;

    @BeforeEach
    void setUp() {
        organization = mock(Organization.class);
        event = mock(Event.class);
        ticketReservation = mock(TicketReservation.class);
        ticketCategory = mock(TicketCategory.class);
        ticket = mock(Ticket.class);
        ticketWithMetadata = TicketWithMetadataAttributes.build(ticket, null);
    }

    @Test
    void buildModelForTicketEmail() {
        Pair<ZonedDateTime, ZonedDateTime> dates = getDates();
        Map<String, Object> model = TemplateResource.buildModelForTicketEmail(organization, event, ticketReservation, "Https://test", "Https://test", "Https://test", ticket, ticketCategory, Map.of());
        assertEquals(dates.getLeft(), model.get("validityStart"));
        assertEquals(dates.getRight(), model.get("validityEnd"));
    }

    @Test
    void buildModelForTicketPDF() {
        Pair<ZonedDateTime, ZonedDateTime> dates = getDates();
        when(ticket.ticketCode(anyString())).thenReturn("abcd");
        when(event.getPrivateKey()).thenReturn("key");
        Map<String, Object> model = TemplateResource.buildModelForTicketPDF(organization, event, ticketReservation, ticketCategory, ticketWithMetadata, Optional.empty(), "abcd", Collections.emptyMap());
        assertEquals(dates.getLeft(), model.get("validityStart"));
        assertEquals(dates.getRight(), model.get("validityEnd"));
    }

    private Pair<ZonedDateTime, ZonedDateTime> getDates() {
        ZonedDateTime eventBegin = ZonedDateTime.now(clockProvider().getClock()).plusDays(1);
        ZonedDateTime eventEnd = ZonedDateTime.now(clockProvider().getClock()).plusDays(3);
        ZonedDateTime validityStart = ZonedDateTime.now(clockProvider().getClock()).plusDays(2);

        when(event.getBegin()).thenReturn(eventBegin);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        when(event.getEnd()).thenReturn(eventEnd);
        when(ticketCategory.getTicketValidityStart(eq(ZoneId.systemDefault()))).thenReturn(validityStart);
        when(ticket.ticketCode(anyString())).thenReturn("abcd");
        return Pair.of(validityStart, eventEnd);
    }

}