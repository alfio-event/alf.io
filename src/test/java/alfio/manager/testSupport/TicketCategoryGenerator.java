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
package alfio.manager.testSupport;

import alfio.model.TicketCategory;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

public class TicketCategoryGenerator {

    public static Stream<TicketCategory> generateCategoryStream() {
        AtomicInteger generator = new AtomicInteger();
        return Stream.generate(() -> {
            boolean isBounded = generator.incrementAndGet() % 3 != 0;
            TicketCategory tc = Mockito.mock(TicketCategory.class);
            when(tc.isBounded()).thenReturn(isBounded);
            when(tc.getMaxTickets()).thenReturn(isBounded ? 2 : -1);
            return tc;
        });
    }
}
