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
package alfio.manager.support.extension;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static alfio.manager.support.extension.ExtensionEvent.EVENT_METADATA_UPDATE;
import static alfio.manager.support.extension.ExtensionEvent.ONLINE_CHECK_IN_REDIRECT;

public enum ExtensionCapability {
    // online event
    GENERATE_MEETING_LINK(EnumSet.of(EVENT_METADATA_UPDATE)),
    CREATE_VIRTUAL_ROOM(EnumSet.of(EVENT_METADATA_UPDATE, ONLINE_CHECK_IN_REDIRECT)),
    CREATE_ANONYMOUS_GUEST_LINK(EnumSet.of(EVENT_METADATA_UPDATE, ONLINE_CHECK_IN_REDIRECT)),
    CREATE_GUEST_LINK(EnumSet.of(EVENT_METADATA_UPDATE, ONLINE_CHECK_IN_REDIRECT)),

    // link with external applications
    LINK_EXTERNAL_APPLICATION(EnumSet.of(EVENT_METADATA_UPDATE))
    ;

    private final Set<ExtensionEvent> compatibleEvents;

    ExtensionCapability(Set<ExtensionEvent> compatibleEvents) {
        this.compatibleEvents = compatibleEvents;
    }

    public Set<ExtensionEvent> getCompatibleEvents() {
        return compatibleEvents;
    }

    public Collection<String> getCompatibleEventNames() {
        return compatibleEvents.stream().map(ExtensionEvent::name).collect(Collectors.toList());
    }

    public static Collection<String> toString(Set<ExtensionCapability> capabilities) {
        return capabilities.stream().map(ExtensionCapability::name).collect(Collectors.toSet());
    }

    public static Set<ExtensionCapability> fromString(Collection<String> capabilities) {
        return capabilities.stream().map(ExtensionCapability::valueOf).collect(Collectors.toSet());
    }
}
