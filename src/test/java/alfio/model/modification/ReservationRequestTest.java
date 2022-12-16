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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReservationRequestTest {

    @Test
    void single() {
        var metadata = Map.of("key", "value");
        var request = new DummyReservationRequest(1, List.of(metadata));
        var attendees = request.getAttendees();
        assertEquals(1, attendees.size());
        var attendee = attendees.get(0);
        assertNull(attendee.getFirstName());
        assertNull(attendee.getLastName());
        assertNull(attendee.getEmail());
        assertFalse(attendee.hasContactData());
        assertTrue(attendee.hasMetadata());
        assertEquals(metadata, attendee.getMetadata());
    }

    @Test
    void multiple() {
        var metadata = List.of(Map.of("key1", "value1"), Map.of("key2", "value2"));
        var request = new DummyReservationRequest(2, metadata);
        var attendees = request.getAttendees();
        assertEquals(2, attendees.size());
        for (int i = 0; i < attendees.size(); i++) {
            var attendee = attendees.get(i);
            assertNull(attendee.getFirstName());
            assertNull(attendee.getLastName());
            assertNull(attendee.getEmail());
            assertFalse(attendee.hasContactData());
            assertTrue(attendee.hasMetadata());
            assertEquals(metadata.get(i), attendee.getMetadata());
        }
    }

    @Test
    void multipleWithoutMetadata() {
        var metadata = List.of(Map.of("key1", "value1"));
        var request = new DummyReservationRequest(2, metadata);
        var attendees = request.getAttendees();
        assertEquals(2, attendees.size());
        for (int i = 0; i < attendees.size(); i++) {
            var attendee = attendees.get(i);
            assertNull(attendee.getFirstName());
            assertNull(attendee.getLastName());
            assertNull(attendee.getEmail());
            assertFalse(attendee.hasContactData());
            if (i == 0) {
                assertTrue(attendee.hasMetadata());
                assertNotNull(attendee.getMetadata());
                assertEquals(metadata.get(i), attendee.getMetadata());
            } else {
                assertFalse(attendee.hasMetadata());
            }
        }
    }

    @Test
    void multipleMetadataNull() {
        var request = new DummyReservationRequest(2, null);
        var attendees = request.getAttendees();
        assertEquals(2, attendees.size());
        attendees.forEach(attendeeData -> assertFalse(attendeeData.hasMetadata()));
        attendees.forEach(attendeeData -> assertFalse(attendeeData.hasContactData()));
    }

    private static class DummyReservationRequest implements ReservationRequest {

        private final Integer quantity;
        private final List<Map<String, String>> metadata;

        private DummyReservationRequest(Integer quantity, List<Map<String, String>> metadata) {
            this.quantity = quantity;
            this.metadata = metadata;
        }

        @Override
        public Integer getTicketCategoryId() {
            return 1;
        }

        @Override
        public Integer getQuantity() {
            return quantity;
        }

        @Override
        public List<Map<String, String>> getMetadata() {
            return metadata;
        }
    }
}