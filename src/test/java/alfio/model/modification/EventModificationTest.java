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
package alfio.model.modification;

import alfio.util.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventModificationTest {

    @Test
    void deserializeGeolocation() {
        var json = """
            {
              "shortName": "event",
              "organizationId": 1,
              "geolocation": {"timeZone": "Europe/Zurich", "latitude": "45.55", "longitude": "9.00"}
            }
            """;
        var eventModification = Json.fromJson(json, EventModification.class);
        assertNotNull(eventModification.getGeolocation());
        assertEquals("Europe/Zurich", eventModification.getGeolocation().timeZone());
        assertEquals("45.55", eventModification.getGeolocation().latitude());
    }
}
