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
package alfio.model.metadata;

import alfio.manager.support.extension.ExtensionEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public class TicketMetadataContainer {

    public static final String GENERAL = "general";
    private static final Set<String> ALLOWED_KEYS = Set.of(
        GENERAL,
        ExtensionEvent.CUSTOM_ONLINE_JOIN_URL.name()
    );

    private final Map<String, TicketMetadata> metadataMap;

    @JsonCreator
    public TicketMetadataContainer(@JsonProperty("metadataMap") Map<String, TicketMetadata> metadataMap) {
        this.metadataMap = Objects.requireNonNullElse(metadataMap, new HashMap<>());
    }

    /**
     * This method returns a read-only copy of the underlying map.
     * Use
     * @return a read-only copy of the metadataMap
     */
    public Map<String, TicketMetadata> getMetadataMap() {
        return Map.copyOf(metadataMap);
    }

    /**
     * Returns the stored metadata for the given key, if present
     *
     * @param key metadata key
     * @return TicketMetadata, if present
     */
    public Optional<TicketMetadata> getMetadataForKey(String key) {
        return Optional.ofNullable(metadataMap.get(key));
    }

    /**
     * Adds the metadata value, only if the key is supported
     *
     * @param key the key for the metadata object
     * @param value the metadata object to save
     * @return {@code true} if key was accepted, otherwise {@code false}
     */
    public boolean putMetadata(String key, TicketMetadata value) {
        if(ALLOWED_KEYS.contains(key)) {
            metadataMap.put(key, value);
            return true;
        }
        return false;
    }

    public static TicketMetadataContainer empty() {
        return new TicketMetadataContainer(new HashMap<>());
    }

    public static TicketMetadataContainer fromMetadata(TicketMetadata metadata) {
        if (metadata != null) {
            var map = new HashMap<String, TicketMetadata>();
            map.put(GENERAL, metadata);
            return new TicketMetadataContainer(map);
        }
        return null;
    }

    public static TicketMetadataContainer copyOf(TicketMetadataContainer src) {
        if (src != null) {
            Map<String, TicketMetadata> newMap = new HashMap<>();
            src.metadataMap.forEach((key, tm) -> newMap.put(key, TicketMetadata.copyOf(tm)));
            return new TicketMetadataContainer(newMap);
        }
        return null;
    }
}
