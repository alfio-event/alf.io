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

import alfio.model.Ticket;
import de.danielbechler.diff.ObjectDifferBuilder;
import de.danielbechler.diff.node.DiffNode;
import de.danielbechler.diff.node.Visit;
import org.junit.Assert;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectDiffTest {

    ZonedDateTime now = ZonedDateTime.now();

    Ticket preUpdateTicket = new Ticket(42, "42", now, 1, Ticket.TicketStatus.ACQUIRED.name(), 42,
        "42", "full name", "full", "name", "email@email.com",
        false, "en",
        0,  0, 0, 0, null, null);

    Ticket postUpdateTicket = new Ticket(42, "42", now, 1, Ticket.TicketStatus.CANCELLED.name(), 42,
        "42", "full name", "full", "name", "email@email.com",
        false, "en",
        0,  0, 0, 0, null, null);


    @Test
    public void diffTest() {



        var postUpdateTicketFields =  new HashMap<String, String>();
        postUpdateTicketFields.put("hello", "world");
        var preUpdateTicketFields = new HashMap<String, String>();

        DiffNode diffTicket = ObjectDifferBuilder.buildDefault().compare(postUpdateTicket, preUpdateTicket);
        DiffNode diffTicketFields = ObjectDifferBuilder.buildDefault().compare(postUpdateTicketFields, preUpdateTicketFields);
        FieldChangesSaver diffTicketVisitor = new FieldChangesSaver(preUpdateTicket, postUpdateTicket);
        FieldChangesSaver diffTicketFieldsVisitor = new FieldChangesSaver(preUpdateTicketFields, postUpdateTicketFields);
        diffTicket.visit(diffTicketVisitor);
        diffTicketFields.visit(diffTicketFieldsVisitor);

        List<Map<String, Object>> changes = new ArrayList<>(diffTicketVisitor.changes);
        changes.addAll(diffTicketFieldsVisitor.changes);

        Assert.assertEquals(2, changes.size());

    }

    @Test
    public void diffMapTest() {
        Map<String, String> empty = Map.of();
        Map<String, String> emptyAfter = Map.of();
        Assert.assertTrue(ObjectDiffUtil.diff(empty, emptyAfter).isEmpty());

        var newElement = Map.of("new", "element");
        var newElementRes = ObjectDiffUtil.diff(emptyAfter, newElement);
        Assert.assertEquals(1, newElementRes.size());
        Assert.assertEquals("new", newElementRes.get(0).getPropertyName());
        Assert.assertEquals("element", newElementRes.get(0).getNewValue());
        Assert.assertEquals(null, newElementRes.get(0).getOldValue());
        Assert.assertEquals(ObjectDiffUtil.State.ADDED, newElementRes.get(0).getState());

        var removedElementRes = ObjectDiffUtil.diff(newElement, empty);
        Assert.assertEquals(1, removedElementRes.size());
        Assert.assertEquals("new", removedElementRes.get(0).getPropertyName());
        Assert.assertEquals(null, removedElementRes.get(0).getNewValue());
        Assert.assertEquals("element", removedElementRes.get(0).getOldValue());
        Assert.assertEquals(ObjectDiffUtil.State.REMOVED, removedElementRes.get(0).getState());

        var changedElem = ObjectDiffUtil.diff(newElement, Map.of("new", "changed"));
        Assert.assertEquals(1, changedElem.size());
        Assert.assertEquals("new", changedElem.get(0).getPropertyName());
        Assert.assertEquals("changed", changedElem.get(0).getNewValue());
        Assert.assertEquals("element", changedElem.get(0).getOldValue());
        Assert.assertEquals(ObjectDiffUtil.State.CHANGED, changedElem.get(0).getState());


        var untouchedElem = ObjectDiffUtil.diff(newElement, new HashMap<>(newElement));
        Assert.assertEquals(0, untouchedElem.size());
    }

    @Test
    public void testTicketDiff() {
        var res = ObjectDiffUtil.diff(preUpdateTicket, postUpdateTicket);

        Assert.assertEquals(1, res.size());
        Assert.assertEquals("status", res.get(0).getPropertyName());
        Assert.assertEquals(Ticket.TicketStatus.CANCELLED, res.get(0).getNewValue());
        Assert.assertEquals(Ticket.TicketStatus.ACQUIRED, res.get(0).getOldValue());
        Assert.assertEquals(ObjectDiffUtil.State.CHANGED, res.get(0).getState());
    }

    private static class FieldChangesSaver implements DiffNode.Visitor {

        private final Object preBase;
        private final Object postBase;

        private final List<Map<String, Object>> changes = new ArrayList<>();


        FieldChangesSaver(Object preBase, Object postBase) {
            this.preBase = preBase;
            this.postBase = postBase;
        }

        @Override
        public void node(DiffNode node, Visit visit) {
            if(node.hasChanges() && node.getState() != DiffNode.State.UNTOUCHED && !node.isRootNode()) {
                Object baseValue = node.canonicalGet(preBase);
                Object workingValue = node.canonicalGet(postBase);
                HashMap<String, Object> change = new HashMap<>();
                change.put("propertyName", node.getPath().toString());
                change.put("state", node.getState());
                change.put("oldValue", baseValue);
                change.put("newValue", workingValue);
                changes.add(change);
            }
        }
    }
}
