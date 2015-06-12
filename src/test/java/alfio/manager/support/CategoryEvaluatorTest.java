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
package alfio.manager.support;

import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.repository.TicketCategoryRepository;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JunitSuiteRunner.class)
public class CategoryEvaluatorTest {{

    describe("CategoryEvaluator", it -> {
        int categoryId = 1;
        int eventId = 2;
        Ticket ticket = mock(Ticket.class);
        when(ticket.getCategoryId()).thenReturn(categoryId);
        when(ticket.getEventId()).thenReturn(eventId);
        TicketCategoryRepository tcr = mock(TicketCategoryRepository.class);
        TicketCategory category = it.usesMock(TicketCategory.class);
        when(tcr.getById(eq(categoryId), eq(eventId))).thenReturn(category);
        it.should("allow a cancellation of a ticket if it belongs to a public category", expect -> {
            when(category.isAccessRestricted()).thenReturn(false);
            expect.that(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket)).is(true);
        });

        it.should("not allow a cancellation of a ticket if it belongs to a restricted category and there aren't any unbounded categories", expect -> {
            when(category.isAccessRestricted()).thenReturn(true);
            when(tcr.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(0);
            expect.that(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket)).is(false);
        });

        it.should("allow a cancellation of a ticket if it belongs to a restricted category and there is at least one unbounded category", expect -> {
            when(category.isAccessRestricted()).thenReturn(true);
            when(tcr.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(1);
            expect.that(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket)).is(true);
        });
    });

}}