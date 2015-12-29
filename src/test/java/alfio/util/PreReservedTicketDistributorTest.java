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

import alfio.model.modification.TicketCategoryWithStatistic;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Mockito.when;

@RunWith(JunitSuiteRunner.class)
public class PreReservedTicketDistributorTest {{
    describe("PreReservedTicketDistributor", it -> {
        TicketCategoryWithStatistic cat1 = it.usesMock(TicketCategoryWithStatistic.class);
        TicketCategoryWithStatistic cat2 = it.usesMock(TicketCategoryWithStatistic.class);
        TicketCategoryWithStatistic cat3 = it.usesMock(TicketCategoryWithStatistic.class);
        it.isSetupWith(() -> {
            when(cat1.getId()).thenReturn(1);
            when(cat2.getId()).thenReturn(2);
            when(cat3.getId()).thenReturn(3);
        });
        int cat1Capacity = 10;
        int cat2Capacity = 12;
        int cat3Capacity = 20;

        List<Pair<Integer, TicketCategoryWithStatistic>> data = Arrays.asList(Pair.of(cat1Capacity, cat1), Pair.of(cat2Capacity, cat2), Pair.of(cat3Capacity, cat3));

        it.should("include all the categories (42 tickets requested)", expect -> {
            List<Pair<Integer, TicketCategoryWithStatistic>> pairs = data.stream().collect(new PreReservedTicketDistributor(42));
            expect.that(pairs.size()).is(3);
            expect.that(pairs.get(0)).is(Pair.of(cat1Capacity, cat1));
            expect.that(pairs.get(1)).is(Pair.of(cat2Capacity, cat2));
            expect.that(pairs.get(2)).is(Pair.of(cat3Capacity, cat3));
        });

        it.should("include all the categories (43 tickets requested)", expect -> {
            List<Pair<Integer, TicketCategoryWithStatistic>> pairs = data.stream().collect(new PreReservedTicketDistributor(43));
            expect.that(pairs.size()).is(3);
            expect.that(pairs.get(0)).is(Pair.of(cat1Capacity, cat1));
            expect.that(pairs.get(1)).is(Pair.of(cat2Capacity, cat2));
            expect.that(pairs.get(2)).is(Pair.of(cat3Capacity, cat3));
        });

        it.should("include only the first category (1 ticket requested)", expect -> {
            List<Pair<Integer, TicketCategoryWithStatistic>> pairs = data.stream().collect(new PreReservedTicketDistributor(1));
            expect.that(pairs.size()).is(1);
            expect.that(pairs.get(0)).is(Pair.of(1, cat1));
        });

        it.should("include only the first two categories (20 tickets requested)", expect -> {
            List<Pair<Integer, TicketCategoryWithStatistic>> pairs = data.stream().collect(new PreReservedTicketDistributor(20));
            expect.that(pairs.size()).is(2);
            expect.that(pairs.get(0)).is(Pair.of(cat1Capacity, cat1));
            expect.that(pairs.get(1)).is(Pair.of(10, cat2));
        });

        it.should("include all the categories (23 tickets requested)", expect -> {
            List<Pair<Integer, TicketCategoryWithStatistic>> pairs = data.stream().collect(new PreReservedTicketDistributor(23));
            expect.that(pairs.size()).is(3);
            expect.that(pairs.get(0)).is(Pair.of(cat1Capacity, cat1));
            expect.that(pairs.get(1)).is(Pair.of(cat2Capacity, cat2));
            expect.that(pairs.get(2)).is(Pair.of(1, cat3));
        });

    });
}}