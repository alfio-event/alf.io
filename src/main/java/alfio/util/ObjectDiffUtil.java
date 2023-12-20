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
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ReflectionUtils;

import java.util.*;
import java.util.stream.Stream;

/**
 * Replace https://github.com/SQiShER/java-object-diff . As our use case is way more restricted and limited.
 */
public class ObjectDiffUtil {

    public static <T> List<Change> diff(Map<String, T> before, Map<String, T> after) {
        return diffUntyped(before, after, "/{", "}");
    }

    private static String formatPropertyName(String k, String propertyNameBefore, String propertyNameAfter) {
        return propertyNameBefore + k + propertyNameAfter;
    }

    private static List<Change> diffUntyped(Map<String, ?> before, Map<String, ?> after, String propertyNameBefore, String propertyNameAfter) {
        var removed = new HashSet<>(before.keySet());
        removed.removeAll(after.keySet());

        var added = new HashSet<>(after.keySet());
        added.removeAll(before.keySet());

        var changedOrUntouched = new HashSet<>(after.keySet());
        changedOrUntouched.removeAll(removed);
        changedOrUntouched.removeAll(added);

        var changes = new ArrayList<Change>();

        removed.stream().map(k -> new Change(formatPropertyName(k, propertyNameBefore, propertyNameAfter), State.REMOVED, before.get(k), null)).forEach(changes::add);
        added.stream().map(k -> new Change(formatPropertyName(k, propertyNameBefore, propertyNameAfter), State.ADDED, null, after.get(k))).forEach(changes::add);
        changedOrUntouched.forEach(k -> {
            var beforeValue = before.get(k);
            var afterValue = after.get(k);
            if(!Objects.equals(beforeValue, afterValue)) {
                changes.add(new Change(formatPropertyName(k, propertyNameBefore, propertyNameAfter), State.CHANGED, beforeValue, afterValue));
            }
        });
        changes.sort(Change::compareTo);
        return changes;
    }

    public static List<Change> diff(Ticket before, Ticket after) {
        return diff(before, after, Ticket.class);
    }

    public static <T> List<Change> diff(T before, T after, Class<T> objectType) {
        var beforeAsMap = new HashMap<String, Object>();
        var afterAsMap = new HashMap<String, Object>();
        Stream.of(BeanUtils.getPropertyDescriptors(objectType)).forEach(propertyDescriptor -> {
            var method = propertyDescriptor.getReadMethod();
            var name = propertyDescriptor.getName();
            if (method != null) {
                beforeAsMap.put(name, ReflectionUtils.invokeMethod(method, before));
                afterAsMap.put(name, ReflectionUtils.invokeMethod(method, after));
            }
        });
        return diffUntyped(beforeAsMap, afterAsMap, "/", "");
    }

    @AllArgsConstructor
    @Getter
    public static class Change implements Comparable<Change> {
        private final String propertyName;
        private final State state;
        private final Object oldValue;
        private final Object newValue;

        @Override
        public int compareTo(Change change) {
            return new CompareToBuilder()
                .append(propertyName, change.propertyName)
                .append(state.ordinal(), change.state.ordinal())
                .append(oldValue, change.oldValue)
                .append(newValue, change.newValue).toComparison();
        }
    }

    public enum State {
        ADDED,
        CHANGED,
        REMOVED
    }
}
