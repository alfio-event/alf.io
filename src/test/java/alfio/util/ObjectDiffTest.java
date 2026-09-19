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

import alfio.model.PriceContainer;
import alfio.model.Ticket;
import alfio.test.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectDiffTest {

    ZonedDateTime now = ZonedDateTime.now(TestUtil.clockProvider().getClock());
    UUID publicUUID = UUID.randomUUID();

    Ticket preUpdateTicket = new Ticket(42, "42", publicUUID, now, 1, Ticket.TicketStatus.ACQUIRED.name(), 42,
        "42", "full name", "full", "name", "email@email.com",
        false, "en",
        0,  0, 0, 0, null, null, List.of(), null, PriceContainer.VatStatus.INCLUDED);

    Ticket postUpdateTicket = new Ticket(42, "42", publicUUID, now, 1, Ticket.TicketStatus.CANCELLED.name(), 42,
        "42", "full name", "full", "name", "email@email.com",
        false, "en",
        0,  0, 0, 0, null, null, List.of(), null, PriceContainer.VatStatus.INCLUDED);

    @Test
    public void diffMapTest() {
        Map<String, String> empty = Map.of();
        Map<String, String> emptyAfter = Map.of();
        assertTrue(ObjectDiffUtil.diff(empty, emptyAfter).isEmpty());

        var newElement = Map.of("new", "element");
        var newElementRes = ObjectDiffUtil.diff(emptyAfter, newElement);
        assertEquals(1, newElementRes.size());
        assertEquals("/{new}", newElementRes.getFirst().getPropertyName());
        assertEquals("element", newElementRes.getFirst().getNewValue());
        assertNull(newElementRes.getFirst().getOldValue());
        assertEquals(ObjectDiffUtil.State.ADDED, newElementRes.getFirst().getState());

        var removedElementRes = ObjectDiffUtil.diff(newElement, empty);
        assertEquals(1, removedElementRes.size());
        assertEquals("/{new}", removedElementRes.getFirst().getPropertyName());
        assertNull(removedElementRes.getFirst().getNewValue());
        assertEquals("element", removedElementRes.getFirst().getOldValue());
        assertEquals(ObjectDiffUtil.State.REMOVED, removedElementRes.getFirst().getState());

        var changedElem = ObjectDiffUtil.diff(newElement, Map.of("new", "changed"));
        assertEquals(1, changedElem.size());
        assertEquals("/{new}", changedElem.getFirst().getPropertyName());
        assertEquals("changed", changedElem.getFirst().getNewValue());
        assertEquals("element", changedElem.getFirst().getOldValue());
        assertEquals(ObjectDiffUtil.State.CHANGED, changedElem.getFirst().getState());


        var untouchedElem = ObjectDiffUtil.diff(newElement, new HashMap<>(newElement));
        assertEquals(0, untouchedElem.size());
    }

    @Test
    public void testTicketDiff() {
        var res = ObjectDiffUtil.diff(preUpdateTicket, postUpdateTicket);

        assertEquals(1, res.size());
        assertEquals("/status", res.getFirst().getPropertyName());
        assertEquals(Ticket.TicketStatus.CANCELLED, res.getFirst().getNewValue());
        assertEquals(Ticket.TicketStatus.ACQUIRED, res.getFirst().getOldValue());
        assertEquals(ObjectDiffUtil.State.CHANGED, res.getFirst().getState());
    }
}
