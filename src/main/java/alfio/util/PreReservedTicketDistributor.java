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

import alfio.model.TicketCategoryStatisticView;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class PreReservedTicketDistributor implements Collector<Pair<Integer, TicketCategoryStatisticView>, List<Pair<Integer, TicketCategoryStatisticView>>, List<Pair<Integer, TicketCategoryStatisticView>>> {

    private final AtomicInteger requestedTickets;

    public PreReservedTicketDistributor(int requestedTickets) {
        this.requestedTickets = new AtomicInteger(requestedTickets);
    }

    @Override
    public Supplier<List<Pair<Integer, TicketCategoryStatisticView>>> supplier() {
        return ArrayList::new;
    }

    @Override
    public BiConsumer<List<Pair<Integer, TicketCategoryStatisticView>>, Pair<Integer, TicketCategoryStatisticView>> accumulator() {
        return (accumulator, candidate) -> {
            int requested = requestedTickets.get();
            if (requested > 0) {
                int capacity = Math.min(requested, candidate.getKey());
                accumulator.add(Pair.of(capacity, candidate.getValue()));
                requestedTickets.addAndGet(-capacity);
            }
        };
    }

    /**
     * This collector is not designed run in parallel. Thus the combiner here doesn't do nothing
     * @return the first parameter
     */
    @Override
    public BinaryOperator<List<Pair<Integer, TicketCategoryStatisticView>>> combiner() {
        return (a, b) -> a;
    }

    @Override
    public Function<List<Pair<Integer, TicketCategoryStatisticView>>, List<Pair<Integer, TicketCategoryStatisticView>>> finisher() {
        return Function.identity();
    }


    @Override
    public Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.IDENTITY_FINISH);
    }
}
