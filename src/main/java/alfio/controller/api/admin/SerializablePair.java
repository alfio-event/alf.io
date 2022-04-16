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
package alfio.controller.api.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.tuple.Pair;

public final class SerializablePair<L,R> {
    @Delegate
    @JsonIgnore
    private final Pair<L, R> pair;

    private SerializablePair(Pair<L, R> pair) {
        this.pair = pair;
    }

    static <L,R> SerializablePair<L,R> fromPair(Pair<L,R> pair) {
        return new SerializablePair<>(pair);
    }

    static <L,R> SerializablePair<L,R> of(L left, R right) {
        return new SerializablePair<>(Pair.of(left, right));
    }
}
