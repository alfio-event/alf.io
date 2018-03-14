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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CategoryEvaluatorTest {

    @Mock
    private Ticket ticket;
    @Mock
    private TicketCategory category;
    @Mock
    private TicketCategoryRepository tcr;
    private final int eventId = 2;

    @Before
    public void init() {
        int categoryId = 1;
        when(ticket.getCategoryId()).thenReturn(categoryId);
        when(ticket.getEventId()).thenReturn(eventId);
        when(ticket.getStatus()).thenReturn(Ticket.TicketStatus.ACQUIRED);
        when(tcr.getByIdAndActive(eq(categoryId), eq(eventId))).thenReturn(category);
    }

    @Test
    public void allowTicketCancellationIfItBelongsToAPublicCategory() {
        when(category.isAccessRestricted()).thenReturn(false);
        assertTrue(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket));
    }

    @Test
    public void doNotAllowCancellationIfCategoryAllBounded() {
        when(category.isAccessRestricted()).thenReturn(true);
        when(tcr.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(0);
        assertFalse(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket));
    }

    @Test
    public void allowCancellationOnRestrictedCategoryIfAtLeastOneBounded() {
        when(category.isAccessRestricted()).thenReturn(true);
        when(tcr.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(1);
        assertTrue(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket));
    }

    @Test
    public void doNotAllowCancellationIfTicketStatusIsNotAcquired() throws Exception {
        when(category.isAccessRestricted()).thenReturn(false);
        when(ticket.getStatus()).thenReturn(Ticket.TicketStatus.CHECKED_IN);
        assertFalse(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket));
    }
}