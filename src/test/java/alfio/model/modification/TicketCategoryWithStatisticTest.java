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
import alfio.model.PriceContainer;
import alfio.model.TicketCategory;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JunitSuiteRunner.class)
public class TicketCategoryWithStatisticTest {{

    Map<String, String> description = Collections.singletonMap("en", "desc");
    Event event = mock(Event.class);
    when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
    when(event.getVatStatus()).thenReturn(PriceContainer.VatStatus.NOT_INCLUDED);

    describe("Statistic calculation", it -> {

        describe("with checked-in tickets", _it -> {
            TicketCategory category = it.usesMock(TicketCategory.class);
            TicketWithStatistic first = it.usesMock(TicketWithStatistic.class);
            TicketWithStatistic second = it.usesMock(TicketWithStatistic.class);

            when(category.getMaxTickets()).thenReturn(10);
            when(category.getSrcPriceCts()).thenReturn(100);
            when(category.getExpiration(any(ZoneId.class))).thenReturn(ZonedDateTime.now().plusSeconds(1));
            when(category.isBounded()).thenReturn(true);

            when(first.hasBeenSold()).thenReturn(true);
            when(first.isCheckedIn()).thenReturn(true);

            when(second.hasBeenSold()).thenReturn(true);
            when(second.isCheckedIn()).thenReturn(false);

            TicketCategoryWithStatistic ticketCategoryWithStatistic = new TicketCategoryWithStatistic(category, asList(first, second), Collections.emptyList(), event, description);

            _it.should("report only one checked-in ticket", expect -> expect.that(ticketCategoryWithStatistic.getCheckedInTickets()).is(1));
            _it.should("report only one sold ticket", expect -> expect.that(ticketCategoryWithStatistic.getSoldTickets()).is(1));
            _it.should("report 8 available tickets", expect -> expect.that(ticketCategoryWithStatistic.getNotSoldTickets()).is(8));
            _it.should("report no orphans", expect -> expect.that(ticketCategoryWithStatistic.isContainingOrphans()).is(false));
            _it.should("report no stuck tickets", expect -> expect.that(ticketCategoryWithStatistic.isContainingStuckTickets()).is(false));
        });

        describe("category is unbounded", _it -> {
            TicketCategory category = it.usesMock(TicketCategory.class);
            TicketWithStatistic first = it.usesMock(TicketWithStatistic.class);
            TicketWithStatistic second = it.usesMock(TicketWithStatistic.class);

            when(category.getMaxTickets()).thenReturn(-1);
            when(category.getSrcPriceCts()).thenReturn(100);
            when(category.getExpiration(any(ZoneId.class))).thenReturn(ZonedDateTime.now().plusSeconds(1));
            when(category.isBounded()).thenReturn(false);

            when(first.hasBeenSold()).thenReturn(true);
            when(first.isCheckedIn()).thenReturn(true);

            when(second.hasBeenSold()).thenReturn(true);
            when(second.isCheckedIn()).thenReturn(false);

            TicketCategoryWithStatistic ticketCategoryWithStatistic = new TicketCategoryWithStatistic(category, asList(first, second), Collections.emptyList(), event, description);

            _it.should("report 0 tickets remaining", expect -> expect.that(ticketCategoryWithStatistic.getNotSoldTickets()).is(0));
            _it.should("report only one sold ticket", expect -> expect.that(ticketCategoryWithStatistic.getSoldTickets()).is(1));
            _it.should("report no orphans", expect -> expect.that(ticketCategoryWithStatistic.isContainingOrphans()).is(false));
            _it.should("report no stuck tickets", expect -> expect.that(ticketCategoryWithStatistic.isContainingStuckTickets()).is(false));
        });

        describe("all ticket sold, no checked-in", _it -> {

            TicketCategory category = it.usesMock(TicketCategory.class);
            TicketWithStatistic first = it.usesMock(TicketWithStatistic.class);
            TicketWithStatistic second = it.usesMock(TicketWithStatistic.class);

            when(category.getMaxTickets()).thenReturn(2);
            when(category.getSrcPriceCts()).thenReturn(100);
            when(category.getExpiration(any(ZoneId.class))).thenReturn(ZonedDateTime.now().plusSeconds(1));
            when(category.isBounded()).thenReturn(true);

            when(first.hasBeenSold()).thenReturn(true);
            when(first.isCheckedIn()).thenReturn(false);
            when(second.hasBeenSold()).thenReturn(true);
            when(second.isCheckedIn()).thenReturn(false);

            TicketCategoryWithStatistic ticketCategoryWithStatistic = new TicketCategoryWithStatistic(category, asList(first, second), Collections.emptyList(), event, description);

            _it.should("report no checked-in tickets", expect -> expect.that(ticketCategoryWithStatistic.getCheckedInTickets()).is(0));
            _it.should("report two sold tickets", expect -> expect.that(ticketCategoryWithStatistic.getSoldTickets()).is(2));
            _it.should("report no available tickets", expect -> expect.that(ticketCategoryWithStatistic.getNotSoldTickets()).is(0));
            _it.should("report no orphans", expect -> expect.that(ticketCategoryWithStatistic.isContainingOrphans()).is(false));
            _it.should("report no stuck tickets", expect -> expect.that(ticketCategoryWithStatistic.isContainingStuckTickets()).is(false));
        });

        describe("no ticket have been sold (sad but true)", _it -> {
            TicketCategory category = it.usesMock(TicketCategory.class);
            TicketWithStatistic first = it.usesMock(TicketWithStatistic.class);
            TicketWithStatistic second = it.usesMock(TicketWithStatistic.class);

            when(category.getMaxTickets()).thenReturn(2);
            when(category.getSrcPriceCts()).thenReturn(100);
            when(category.getExpiration(any(ZoneId.class))).thenReturn(ZonedDateTime.now().plusSeconds(1));
            when(category.isBounded()).thenReturn(true);

            when(first.hasBeenSold()).thenReturn(false);
            when(first.isCheckedIn()).thenReturn(false);
            when(second.hasBeenSold()).thenReturn(false);
            when(second.isCheckedIn()).thenReturn(false);

            TicketCategoryWithStatistic ticketCategoryWithStatistic = new TicketCategoryWithStatistic(category, asList(first, second), Collections.emptyList(), event, description);

            _it.should("report no checked-in tickets", expect -> expect.that(ticketCategoryWithStatistic.getCheckedInTickets()).is(0));
            _it.should("report no sold tickets", expect -> expect.that(ticketCategoryWithStatistic.getSoldTickets()).is(0));
            _it.should("report 2 available tickets", expect -> expect.that(ticketCategoryWithStatistic.getNotSoldTickets()).is(2));
            _it.should("report no orphans", expect -> expect.that(ticketCategoryWithStatistic.isContainingOrphans()).is(false));
            _it.should("report no stuck tickets", expect -> expect.that(ticketCategoryWithStatistic.isContainingStuckTickets()).is(false));
        });

        describe("category is valid, but some tickets stuck", _it -> {
            TicketCategory category = it.usesMock(TicketCategory.class);
            TicketWithStatistic first = it.usesMock(TicketWithStatistic.class);
            TicketWithStatistic second = it.usesMock(TicketWithStatistic.class);

            when(category.getMaxTickets()).thenReturn(2);
            when(category.getSrcPriceCts()).thenReturn(100);
            when(category.getExpiration(any(ZoneId.class))).thenReturn(ZonedDateTime.now().plusSeconds(1));
            when(category.isBounded()).thenReturn(true);

            when(first.hasBeenSold()).thenReturn(true);
            when(first.isCheckedIn()).thenReturn(false);
            when(second.hasBeenSold()).thenReturn(false);
            when(second.isCheckedIn()).thenReturn(false);
            when(second.isStuck()).thenReturn(true);

            TicketCategoryWithStatistic ticketCategoryWithStatistic = new TicketCategoryWithStatistic(category, asList(first, second), Collections.emptyList(), event, description);
            _it.should("report no checked-in tickets", expect -> expect.that(ticketCategoryWithStatistic.getCheckedInTickets()).is(0));
            _it.should("report 1 sold ticket", expect -> expect.that(ticketCategoryWithStatistic.getSoldTickets()).is(1));
            _it.should("report 1 available tickets", expect -> expect.that(ticketCategoryWithStatistic.getNotSoldTickets()).is(1));
            _it.should("report no orphans", expect -> expect.that(ticketCategoryWithStatistic.isContainingOrphans()).is(false));
            _it.should("report 1 stuck tickets", expect -> expect.that(ticketCategoryWithStatistic.isContainingStuckTickets()).is(true));
        });

        describe("category is expired, but some tickets left", _it -> {
            TicketCategory category = it.usesMock(TicketCategory.class);
            TicketWithStatistic first = it.usesMock(TicketWithStatistic.class);
            TicketWithStatistic second = it.usesMock(TicketWithStatistic.class);

            when(category.getMaxTickets()).thenReturn(2);
            when(category.getSrcPriceCts()).thenReturn(100);
            when(category.getExpiration(any(ZoneId.class))).thenReturn(ZonedDateTime.now().minusSeconds(10));
            when(category.isBounded()).thenReturn(true);

            when(first.hasBeenSold()).thenReturn(true);
            when(first.isCheckedIn()).thenReturn(false);
            when(second.hasBeenSold()).thenReturn(false);
            when(second.isCheckedIn()).thenReturn(false);
            when(second.isStuck()).thenReturn(false);

            TicketCategoryWithStatistic ticketCategoryWithStatistic = new TicketCategoryWithStatistic(category, asList(first, second), Collections.emptyList(), event, description);
            _it.should("report no checked-in tickets", expect -> expect.that(ticketCategoryWithStatistic.getCheckedInTickets()).is(0));
            _it.should("report 1 sold ticket", expect -> expect.that(ticketCategoryWithStatistic.getSoldTickets()).is(1));
            _it.should("report 1 available tickets", expect -> expect.that(ticketCategoryWithStatistic.getNotSoldTickets()).is(1));
            _it.should("report no orphans", expect -> expect.that(ticketCategoryWithStatistic.isContainingOrphans()).is(true));
            _it.should("report 1 stuck tickets", expect -> expect.that(ticketCategoryWithStatistic.isContainingStuckTickets()).is(false));
        });


    });
}}