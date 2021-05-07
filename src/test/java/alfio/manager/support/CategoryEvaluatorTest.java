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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CategoryEvaluatorTest {

    private Ticket ticket;
    private TicketCategory category;
    private TicketCategoryRepository tcr;
    private final int eventId = 2;

    @BeforeEach
    public void init() {

        ticket = mock(Ticket.class);
        category = mock(TicketCategory.class);
        tcr = mock(TicketCategoryRepository.class);
        
        int categoryId = 1;
        when(ticket.getCategoryId()).thenReturn(categoryId);
        when(ticket.getEventId()).thenReturn(eventId);
        when(ticket.getStatus()).thenReturn(Ticket.TicketStatus.ACQUIRED);
        when(tcr.getByIdAndActive(eq(categoryId), eq(eventId))).thenReturn(category);
    }

    @Test
    public void allowTicketCancellationIfItBelongsToAPublicCategory() {
        when(category.isAccessRestricted()).thenReturn(false);
        Assertions.assertTrue(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket));
    }

    @Test
    public void doNotAllowCancellationIfCategoryAllBounded() {
        when(category.isAccessRestricted()).thenReturn(true);
        when(tcr.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(0);
        Assertions.assertFalse(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket));
    }

    @Test
    public void allowCancellationOnRestrictedCategoryIfAtLeastOneBounded() {
        when(category.isAccessRestricted()).thenReturn(true);
        when(tcr.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(1);
        Assertions.assertTrue(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket));
    }

    @Test
    public void doNotAllowCancellationIfTicketStatusIsNotAcquired() {
        when(category.isAccessRestricted()).thenReturn(false);
        when(ticket.getStatus()).thenReturn(Ticket.TicketStatus.CHECKED_IN);
        Assertions.assertFalse(CategoryEvaluator.isTicketCancellationAvailable(tcr, ticket));
    }
}