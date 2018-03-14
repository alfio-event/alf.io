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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PreReservedTicketDistributorTest {

    private TicketCategoryStatisticView cat1;
    private TicketCategoryStatisticView cat2;
    private TicketCategoryStatisticView cat3;
    private final int cat1Capacity = 10;
    private final int cat2Capacity = 12;
    private final int cat3Capacity = 20;
    private List<Pair<Integer, TicketCategoryStatisticView>> data;

    @BeforeEach
    void setUp() {
        cat1 = mock(TicketCategoryStatisticView.class);
        cat2 = mock(TicketCategoryStatisticView.class);
        cat3 = mock(TicketCategoryStatisticView.class);
        when(cat1.getId()).thenReturn(1);
        when(cat2.getId()).thenReturn(2);
        when(cat3.getId()).thenReturn(3);
        data = Arrays.asList(Pair.of(cat1Capacity, cat1), Pair.of(cat2Capacity, cat2), Pair.of(cat3Capacity, cat3));
    }

    @Test
    @DisplayName("include all the categories (42 tickets requested)")
    void includeAllCategories42() {
        List<Pair<Integer, TicketCategoryStatisticView>> pairs = data.stream().collect(new PreReservedTicketDistributor(42));
        assertEquals(3, pairs.size());
        assertEquals(Pair.of(cat1Capacity, cat1), pairs.get(0));
        assertEquals(Pair.of(cat2Capacity, cat2), pairs.get(1));
        assertEquals(Pair.of(cat3Capacity, cat3), pairs.get(2));
    }

    @Test
    @DisplayName("include all the categories (43 tickets requested)")
    void includeAllCategories43() {
        List<Pair<Integer, TicketCategoryStatisticView>> pairs = data.stream().collect(new PreReservedTicketDistributor(43));
        assertEquals(3, pairs.size());
        assertEquals(Pair.of(cat1Capacity, cat1), pairs.get(0));
        assertEquals(Pair.of(cat2Capacity, cat2), pairs.get(1));
        assertEquals(Pair.of(cat3Capacity, cat3), pairs.get(2));
    }

    @Test
    @DisplayName("include only the first category (1 ticket requested)")
    void includeOnlyFirst() {
        List<Pair<Integer, TicketCategoryStatisticView>> pairs = data.stream().collect(new PreReservedTicketDistributor(1));
        assertEquals(1, pairs.size());
        assertEquals(Pair.of(1, cat1), pairs.get(0));
    }

    @Test
    @DisplayName("include only the first two categories (20 tickets requested)")
    void includeFirstTwoCategories() {
        List<Pair<Integer, TicketCategoryStatisticView>> pairs = data.stream().collect(new PreReservedTicketDistributor(20));
        assertEquals(2, pairs.size());
        assertEquals(Pair.of(cat1Capacity, cat1), pairs.get(0));
        assertEquals(Pair.of(10, cat2), pairs.get(1));
    }

    @Test
    @DisplayName("include all the categories (23 tickets requested)")
    void includeAllCategories() {
        List<Pair<Integer, TicketCategoryStatisticView>> pairs = data.stream().collect(new PreReservedTicketDistributor(23));
        assertEquals(3, pairs.size());
        assertEquals(Pair.of(cat1Capacity, cat1), pairs.get(0));
        assertEquals(Pair.of(cat2Capacity, cat2), pairs.get(1));
        assertEquals(Pair.of(1, cat3), pairs.get(2));
    }
}