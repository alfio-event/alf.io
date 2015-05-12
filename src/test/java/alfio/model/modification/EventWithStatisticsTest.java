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

import alfio.model.Event;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Mockito.when;

@RunWith(JunitSuiteRunner.class)
public class EventWithStatisticsTest {{

    describe("EventWithStatistics", it -> {
        Event event = it.usesMock(Event.class);
        it.should("Ignore unbounded categories while counting allocated tickets", expect -> {
            TicketCategoryWithStatistic bounded = it.usesMock(TicketCategoryWithStatistic.class);
            when(bounded.isBounded()).thenReturn(true);
            when(bounded.getMaxTickets()).thenReturn(1);
            TicketCategoryWithStatistic unbounded = it.usesMock(TicketCategoryWithStatistic.class);
            when(unbounded.isBounded()).thenReturn(false);
            when(unbounded.getMaxTickets()).thenReturn(-1);
            TicketCategoryWithStatistic bounded2 = it.usesMock(TicketCategoryWithStatistic.class);
            when(bounded2.isBounded()).thenReturn(true);
            when(bounded2.getMaxTickets()).thenReturn(5);
            EventWithStatistics eventWithStatistics = new EventWithStatistics(event, Arrays.asList(bounded, unbounded, bounded2));
            expect.that(eventWithStatistics.getAllocatedTickets()).is(6);
        });
    });
}}