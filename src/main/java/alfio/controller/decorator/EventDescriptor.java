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
package alfio.controller.decorator;

import alfio.model.Event;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.StringUtils;

public class EventDescriptor {

    @Delegate(excludes = ExcludeDelegate.class)
    private final Event event;
    private final String eventDescription;

    public EventDescriptor(Event event, String eventDescription) {
        this.event = event;
        this.eventDescription = eventDescription;
    }

    public String getDescription() {
        return eventDescription;
    }

    public String getShortDescription() {
        return StringUtils.abbreviate(getDescription(), 100);
    }


    public boolean getVatIncluded() {
        return event.isVatIncluded();
    }

    @JsonIgnore
    public Event getEvent() {
        return event;
    }

    @JsonIgnore
    public String getPrivateKey() {
        return event.getPrivateKey();
    }

    public interface ExcludeDelegate {
        String getPrivateKey();
    }
}
