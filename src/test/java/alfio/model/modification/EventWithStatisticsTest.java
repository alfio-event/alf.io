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

import java.util.Collections;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JunitSuiteRunner.class)
public class EventWithStatisticsTest {{

    describe("EventWithStatistics", it -> {
        Event event = mock(Event.class);
        when(event.getAvailableSeats()).thenReturn(10);
        it.should("Ignore unbounded categories while counting allocated tickets and report dynamic allocation", expect -> {
            TicketCategoryWithStatistic bounded = it.usesMock(TicketCategoryWithStatistic.class);
            when(bounded.isBounded()).thenReturn(true);
            when(bounded.getMaxTickets()).thenReturn(1);
            TicketCategoryWithStatistic unbounded = it.usesMock(TicketCategoryWithStatistic.class);
            when(unbounded.isBounded()).thenReturn(false);
            when(unbounded.getMaxTickets()).thenReturn(-1);
            TicketCategoryWithStatistic bounded2 = it.usesMock(TicketCategoryWithStatistic.class);
            when(bounded2.isBounded()).thenReturn(true);
            when(bounded2.getMaxTickets()).thenReturn(5);
            EventWithStatistics eventWithStatistics = new EventWithStatistics(event, asList(), asList(bounded, unbounded, bounded2), 0);
            expect.that(eventWithStatistics.getAllocatedTickets()).is(6);
            expect.that(eventWithStatistics.getNotAllocatedTickets()).is(0);
            expect.that(eventWithStatistics.getDynamicAllocation()).is(4);
        });
        it.should("not report dynamic allocation if there are only bounded categories", expect -> {
            TicketCategoryWithStatistic bounded = it.usesMock(TicketCategoryWithStatistic.class);
            when(bounded.isBounded()).thenReturn(true);
            when(bounded.getMaxTickets()).thenReturn(1);
            TicketCategoryWithStatistic bounded2 = it.usesMock(TicketCategoryWithStatistic.class);
            when(bounded2.isBounded()).thenReturn(true);
            when(bounded2.getMaxTickets()).thenReturn(5);
            EventWithStatistics eventWithStatistics = new EventWithStatistics(event, asList(), asList(bounded, bounded2), 0);
            expect.that(eventWithStatistics.getAllocatedTickets()).is(6);
            expect.that(eventWithStatistics.getNotAllocatedTickets()).is(4);
            expect.that(eventWithStatistics.getDynamicAllocation()).is(0);
        });
        it.should("consider unbounded tickets in the statistic", expect -> {
            TicketCategoryWithStatistic first = it.usesMock(TicketCategoryWithStatistic.class);
            when(first.isBounded()).thenReturn(true);
            when(first.getMaxTickets()).thenReturn(2);
            when(first.getSoldTickets()).thenReturn(1);
            when(first.getCheckedInTickets()).thenReturn(1);
            TicketCategoryWithStatistic second = it.usesMock(TicketCategoryWithStatistic.class);
            when(second.isBounded()).thenReturn(false);
            when(second.getNotSoldTickets()).thenReturn(1);
            EventWithStatistics eventWithStatistics = new EventWithStatistics(event, asList(), asList(first, second), 0);
            expect.that(eventWithStatistics.getAllocatedTickets()).is(2);
            expect.that(eventWithStatistics.getCheckedInTickets()).is(1);
            expect.that(eventWithStatistics.getNotSoldTickets()).is(0);
            expect.that(eventWithStatistics.getSoldTickets()).is(1);
            expect.that(eventWithStatistics.getNotAllocatedTickets()).is(0);
            expect.that(eventWithStatistics.getDynamicAllocation()).is(8);
        });

        it.should("consider unbounded tickets in the statistic (corner case)", expect -> {
            TicketCategoryWithStatistic first = it.usesMock(TicketCategoryWithStatistic.class);
            when(first.isBounded()).thenReturn(true);
            when(first.getMaxTickets()).thenReturn(2);
            when(first.getSoldTickets()).thenReturn(1);
            when(first.getCheckedInTickets()).thenReturn(1);
            TicketCategoryWithStatistic second = it.usesMock(TicketCategoryWithStatistic.class);
            when(second.isBounded()).thenReturn(false);
            when(second.getSoldTickets()).thenReturn(8);
            EventWithStatistics eventWithStatistics = new EventWithStatistics(event, asList(), asList(first, second), 0);
            expect.that(eventWithStatistics.getAllocatedTickets()).is(2);
            expect.that(eventWithStatistics.getCheckedInTickets()).is(1);
            expect.that(eventWithStatistics.getNotSoldTickets()).is(0);
            expect.that(eventWithStatistics.getSoldTickets()).is(9);
            expect.that(eventWithStatistics.getCheckedInTickets()).is(1);
            expect.that(eventWithStatistics.getNotAllocatedTickets()).is(0);
            expect.that(eventWithStatistics.getDynamicAllocation()).is(0);
        });

        it.should("do the proper calculation even if there is only one category", expect -> {
            TicketCategoryWithStatistic first = it.usesMock(TicketCategoryWithStatistic.class);
            when(first.isBounded()).thenReturn(false);
            when(first.getSoldTickets()).thenReturn(8);
            when(first.getCheckedInTickets()).thenReturn(1);
            EventWithStatistics eventWithStatistics = new EventWithStatistics(event, asList(), Collections.singletonList(first), 0);
            expect.that(eventWithStatistics.getAllocatedTickets()).is(0);
            expect.that(eventWithStatistics.getCheckedInTickets()).is(1);
            expect.that(eventWithStatistics.getNotSoldTickets()).is(0);
            expect.that(eventWithStatistics.getSoldTickets()).is(8);
            expect.that(eventWithStatistics.getCheckedInTickets()).is(1);
            expect.that(eventWithStatistics.getNotAllocatedTickets()).is(0);
            expect.that(eventWithStatistics.getDynamicAllocation()).is(1);
        });
    });
}}