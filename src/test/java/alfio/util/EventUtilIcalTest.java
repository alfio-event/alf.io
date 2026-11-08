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

import alfio.model.Event;
import biweekly.Biweekly;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static alfio.util.EventFeedTestData.mockEvent;
import static org.junit.jupiter.api.Assertions.*;

class EventUtilIcalTest {

    @Test
    void singleEventCalendarContainsSummaryLocationAndDates() {
        var event = mockEvent("devconf", "Dev Conf", "Main\nStreet 1", Event.EventFormat.IN_PERSON);

        var bytes = EventUtil.getIcalForEvent(event, null, "**Hello**").orElseThrow();
        var ical = Biweekly.parse(new String(bytes, StandardCharsets.UTF_8)).first();

        assertEquals(1, ical.getEvents().size());
        var vEvent = ical.getEvents().get(0);
        assertEquals("Dev Conf", vEvent.getSummary().getValue());
        assertEquals("Main Street 1", vEvent.getLocation().getValue());
        assertEquals(event.getBegin().toInstant(), vEvent.getDateStart().getValue().toInstant());
        assertEquals(event.getEnd().toInstant(), vEvent.getDateEnd().getValue().toInstant());
        assertEquals("https://example.org/devconf", vEvent.getUrl().getValue());
    }

    @Test
    void onlineEventHasNoLocation() {
        var event = mockEvent("webinar", "Webinar", null, Event.EventFormat.ONLINE);

        var bytes = EventUtil.getIcalForEvent(event, null, "").orElseThrow();
        var vEvent = Biweekly.parse(new String(bytes, StandardCharsets.UTF_8)).first().getEvents().get(0);

        assertNull(vEvent.getLocation());
    }
}
