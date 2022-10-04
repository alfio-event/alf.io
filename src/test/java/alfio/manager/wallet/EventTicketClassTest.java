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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventTicketClassTest {

    @Test
    void build() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        EventTicketClass object = EventTicketClass.builder()
            .id("ISSUER_ID.EVENT_CLASS_ID")
            .eventOrGroupingId("1")
            .eventName("Devoxx Belgium 2022")
            .ticketType("Speaker")
            .logoUri("https://reg.devoxx.be/file/c73315d4890c5fade112165c40338b72271e43939eed4e1e57a4e4891ee19cc8")
            .venue("Antwerp Kinepolis Belgium")
            .start(ZonedDateTime.of(2022, Month.OCTOBER.getValue(), 10, 8, 0, 0, 0, ZoneId.of("UTC")))
            .end(ZonedDateTime.of(2022, Month.OCTOBER.getValue(), 14, 14, 0, 0, 0, ZoneId.of("UTC")))
            .build();
        String build = object.build(objectMapper);

        var resource = getClass().getResource("/wallet-json/event-class.json");
        assertNotNull(resource);
        var payload = Files.readString(Path.of(resource.toURI()));
        assertEquals(objectMapper.readTree(resource), objectMapper.readTree(build));
    }

}