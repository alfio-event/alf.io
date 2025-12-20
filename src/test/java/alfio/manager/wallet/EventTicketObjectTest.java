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
package alfio.manager.wallet;

import alfio.util.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventTicketObjectTest {

    @Test
    void build() throws Exception {
        ObjectMapper objectMapper = Json.OBJECT_MAPPER;

        EventTicketObject object = new EventTicketObject(
            "ISSUER_ID.MEMBER_ID",
            "ISSUER_ID.EVENT_CLASS_ID",
            "Alf",
            "123-321-000",
            "123456789"
        );
        String build = object.build(objectMapper);

        var resource = getClass().getResource("/wallet-json/event-object.json");
        assertNotNull(resource);
        assertEquals(objectMapper.readTree(resource), objectMapper.readTree(build));
    }

}