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
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;

/**
 * Replace https://github.com/SQiShER/java-object-diff . As our use case is way more restricted and limited.
 */
public class ObjectDiffUtil {

    public static List<Change> diff(Map<String, String> before, Map<String, String> after) {

        var removed = new HashSet<>(before.keySet());
        removed.removeAll(after.keySet());

        var added = new HashSet<>(after.keySet());
        added.removeAll(before.keySet());

        var changedOrUntouched = new HashSet<>(after.keySet());
        changedOrUntouched.removeAll(removed);
        changedOrUntouched.removeAll(added);

        var changes = new ArrayList<Change>();

        removed.stream().map(k -> new Change(k, State.REMOVED, before.get(k), null)).forEach(changes::add);
        added.stream().map(k -> new Change(k, State.ADDED, null, after.get(k))).forEach(changes::add);
        changedOrUntouched.stream().map(k -> {
            var beforeValue = before.get(k);
            var afterValue = after.get(k);
            return new Change(k, Objects.equals(beforeValue, afterValue) ? State.UNTOUCHED : State.CHANGED, beforeValue, afterValue);
        }).forEach(changes::add);

        return changes;
    }

    public static List<Change> diff(Ticket before, Ticket after) {
        return List.of();
    }

    @AllArgsConstructor
    @Getter
    public static class Change {
        private final String propertyName;
        private final State state;
        private final Object oldValue;
        private final Object newValue;
    }

    public enum State {
        ADDED,
        CHANGED,
        REMOVED,
        UNTOUCHED
    }
}
