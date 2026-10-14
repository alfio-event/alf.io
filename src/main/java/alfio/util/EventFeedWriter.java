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

import alfio.model.EventFeed;

import java.util.Optional;

/**
 * Serializes an {@link EventFeed} as iCalendar or RSS 2.0.
 */
public final class EventFeedWriter {

    private EventFeedWriter() {
    }

    public static Optional<byte[]> toICal(EventFeed feed) {
        var ical = EventUtil.newCalendar();
        ical.setExperimentalProperty("X-WR-CALNAME", feed.title());
        for (var entry : feed.entries()) {
            var vEvent = EventUtil.buildVEvent(entry.event(), null, entry.description(), null);
            // the public event page is a stable identifier, so calendar clients can update existing entries
            vEvent.setUid(entry.eventUrl());
            vEvent.setUrl(entry.eventUrl());
            ical.addEvent(vEvent);
        }
        return EventUtil.writeCalendar(ical, "feed " + feed.title());
    }
}
