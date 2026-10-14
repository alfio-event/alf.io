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
import alfio.model.EventFeed;
import alfio.model.EventFeedEntry;
import biweekly.Biweekly;
import biweekly.ICalendar;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static alfio.util.EventFeedTestData.mockEvent;
import static org.junit.jupiter.api.Assertions.*;

class EventFeedWriterTest {

    private static final String BASE = "https://tickets.example.org";

    private static EventFeedEntry entry(String shortName, String title, String location, Event.EventFormat format) {
        return new EventFeedEntry(mockEvent(shortName, title, location, format), "Description of " + title, BASE + "/event/" + shortName + "/");
    }

    private static ICalendar parseICal(byte[] bytes) {
        return Biweekly.parse(new String(bytes, StandardCharsets.UTF_8)).first();
    }

    @Test
    void iCalContainsOneVEventPerEntryWithStableUid() {
        var feed = new EventFeed("My Org", BASE, List.of(
            entry("conf-a", "Conf A", "Zurich", Event.EventFormat.IN_PERSON),
            entry("conf-b", "Conf B", "Lugano", Event.EventFormat.IN_PERSON)));

        var ical = parseICal(EventFeedWriter.toICal(feed).orElseThrow());

        assertEquals(2, ical.getEvents().size());
        assertEquals("Conf A", ical.getEvents().get(0).getSummary().getValue());
        assertEquals(BASE + "/event/conf-a/", ical.getEvents().get(0).getUid().getValue());
        assertEquals(BASE + "/event/conf-b/", ical.getEvents().get(1).getUid().getValue());
        assertEquals(BASE + "/event/conf-b/", ical.getEvents().get(1).getUrl().getValue());
        assertEquals("My Org", ical.getExperimentalProperty("X-WR-CALNAME").getValue());
    }

    @Test
    void iCalUidIsTheSameAcrossGenerations() {
        var feed = new EventFeed("My Org", BASE, List.of(entry("conf-a", "Conf A", "Zurich", Event.EventFormat.IN_PERSON)));

        var first = parseICal(EventFeedWriter.toICal(feed).orElseThrow()).getEvents().get(0).getUid().getValue();
        var second = parseICal(EventFeedWriter.toICal(feed).orElseThrow()).getEvents().get(0).getUid().getValue();

        assertEquals(first, second);
    }

    @Test
    void iCalWithoutEntriesIsAValidEmptyCalendar() {
        var bytes = EventFeedWriter.toICal(new EventFeed("Empty", BASE, List.of())).orElseThrow();
        var content = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(content.startsWith("BEGIN:VCALENDAR"));
        assertTrue(parseICal(bytes).getEvents().isEmpty());
    }

    @Test
    void iCalOnlineEventHasNoLocation() {
        var feed = new EventFeed("Org", BASE, List.of(entry("webinar", "Webinar", null, Event.EventFormat.ONLINE)));

        var vEvent = parseICal(EventFeedWriter.toICal(feed).orElseThrow()).getEvents().get(0);

        assertNull(vEvent.getLocation());
    }
}
