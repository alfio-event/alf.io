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

import alfio.model.ContentLanguage;
import alfio.model.Event;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class EventFeedTestData {

    public static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private EventFeedTestData() {
    }

    public static Event mockEvent(String shortName, String title, String location, Event.EventFormat format) {
        var event = mock(Event.class);
        when(event.getId()).thenReturn(Math.abs(shortName.hashCode()));
        when(event.getOrganizationId()).thenReturn(1);
        when(event.getShortName()).thenReturn(shortName);
        when(event.getDisplayName()).thenReturn(title);
        when(event.getLocation()).thenReturn(location);
        when(event.getFormat()).thenReturn(format);
        when(event.getZoneId()).thenReturn(ZONE);
        when(event.getBegin()).thenReturn(ZonedDateTime.of(2030, 5, 10, 9, 0, 0, 0, ZONE));
        when(event.getEnd()).thenReturn(ZonedDateTime.of(2030, 5, 10, 18, 0, 0, 0, ZONE));
        when(event.getWebsiteUrl()).thenReturn("https://example.org/" + shortName);
        when(event.getContentLanguages()).thenReturn(List.of(ContentLanguage.ENGLISH, ContentLanguage.ITALIAN));
        return event;
    }
}
